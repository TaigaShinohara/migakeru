package com.taigashinohara.migakeru

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Migakeru v0.6.0 — UX review v0.5.1 fixes (docs/UX_REVIEW_v0.5.1.md):
 * U1 graduation proposals can never drain a pillar (last 2 stay), U2 a
 * reserve item unlocks with each graduation and a graduated item rests
 * 60 days before quietly returning, so the pool never thins for good,
 * U3 least-recently-picked selection for every pillar (even rotation),
 * U4 the buddy reacts to the day's state, U5 completions answer back
 * (haptic + the buddy's line, a toast on the third).
 * v0.5.0 — design overhaul to 黒×金×炎 design A (user's pick from
 * the 6-variant board, 2026-11-22): black base, dark glass cards with gold
 * edges, gold selected-tab pill, flame orange kept as the point colour
 * (buddy, primary buttons, completion marks). Simple Glass material rules
 * stay; the navy base and light glass of v0.1.2–v0.4.x are retired.
 * v0.4.0 — hot-blooded flame buddy at the top-right of the today
 * screen, with a speech bubble that shows a time-of-day greeting
 * (BuddyView.kt; greetings only for now).
 * v0.2.0 — strict UX review fixes K1–K5 (docs/UX_REVIEW_v0.1.2.md):
 * graduation threshold 5/21d (reachable), graduation signal on the today
 * screen, mirror week navigation + past journals, first-run intro card,
 * and a swap button that names its target and destination.
 * v0.1.2 — Simple Glass applied (docs/GLASS_REVIEW_v0.1.1.md).
 * Tokens (v0.5.0, ART_DIRECTION v0.2):
 * - Base: near-black gradient (#121216 -> #070708) + 2 blurred lights
 *   (gold top-left, flame bottom-right; ART_DIRECTION caps lights at 2).
 * - Reading glass on cards (dark side): white 14% -> 10%, gold edge 40%
 *   (done cards: warm wash + gold edge 59%). Main text #F4F1E8, on-card
 *   sub #D8CFC2 (Simple Glass canonical dark sub), on-base sub #A79E8A.
 * - Chips and the selected tab are gold; completion marks and primary
 *   buttons stay flame orange — gold decorates, flame acts.
 * - Input surface: dark glass, focus edge turns orange.
 * - Type scale: 26 / 17 / 15 / 13 only. Line spacing 1.6.
 * - Spacing scale: 4 / 8 / 12 / 16 / 24 / 32 (screen side margin 20 per quality.md).
 * - Press feedback: views dip to alpha .82 while touched.
 * Known limits (plain Views): no backdrop blur; the system dialog is not glass.
 */
class MainActivity : Activity() {

    // v0.5.0 palette — 黒×金×炎, design A (user's pick, 2026-11-22):
    // black base, dark glass with gold edges, flame orange as the point
    // colour. Variable names keep their old roles (ink = main text on
    // glass, paleBlue = sub text on the base) so call sites stay put.
    private val navy = Color.rgb(0x07, 0x07, 0x08)
    private val navyLight = Color.rgb(0x12, 0x12, 0x16)
    private val orange = Color.rgb(0xE6, 0x51, 0x00)
    private val orangeHi = Color.rgb(0xEB, 0x6A, 0x1F)
    private val orangeLo = Color.rgb(0xDC, 0x4E, 0x00)
    private val gold = Color.rgb(0xD4, 0xAF, 0x37)
    private val goldHi = Color.rgb(0xF5, 0xD6, 0x7B)
    private val goldText = Color.rgb(0xE9, 0xC7, 0x66)
    private val goldDark = Color.rgb(0x17, 0x11, 0x06)
    private val ink = Color.rgb(0xF4, 0xF1, 0xE8)
    private val subOnGlass = Color.rgb(0xD8, 0xCF, 0xC2)
    private val paleBlue = Color.rgb(0xA7, 0x9E, 0x8A)
    private val white = Color.WHITE

    private lateinit var content: FrameLayout
    private lateinit var tabButtons: List<Button>
    private var currentTab = 0
    private val scrollY = mutableMapOf<Int, Int>()
    private var journalOpen = false
    private var weekOffset = 0
    private var openGuide = -1
    private var pendingCatalog = false

    // Onboarding state (mobile_onboarding skill; design v0.5 §5.5).
    private var obPage = 0
    private var obFocus: String? = null
    private var obAnswerLabel = ""
    private var lastPraise = -1
    private var trialDoneNow = false
    private lateinit var obContainer: FrameLayout
    private lateinit var obDots: LinearLayout
    private lateinit var obBackBtn: Button
    private lateinit var obSkipBtn: Button
    private val obHandler = Handler(Looper.getMainLooper())
    private var pendingAdvance: Runnable? = null

    private val choicePraises = listOf(
        "教えてくれてありがとう。",
        "なるほど。それがいちばん気になるんだね。",
        "いいね。そこから整えていこう。",
    )
    private val trialPraises = listOf(
        "できた。それが最初の一歩。",
        "その調子。タップ1つで十分。",
        "うん、もう始まってる。",
    )
    private val prefs by lazy { getSharedPreferences("migakeru", MODE_PRIVATE) }

    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (!prefs.getBoolean("ob_done", false)) {
                showOnboarding()
            } else {
                showMain()
            }
        } catch (t: Throwable) {
            showError(t)
        }
    }

    /**
     * v0.4.3: edge-to-edge draws under the status bar, so the top content
     * (buddy row first) got clipped. Push the given view's content down by
     * the system-bar inset, on top of its base top padding.
     */
    private fun applyTopInset(view: View, baseTopDp: Int, bottomDp: Int) {
        view.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(bars.left, dp(baseTopDp) + bars.top, bars.right, dp(bottomDp))
            insets
        }
    }

    /** v0.4.1 diagnostic: a startup failure must be visible, never a blank screen. */
    private fun showError(t: Throwable) {
        val root = FrameLayout(this)
        root.addView(backgroundLayer(), FrameLayout.LayoutParams(-1, -1))
        val tv = TextView(this).apply {
            text = "起動エラーが出たよ。直すから、この画面のまま教えて。\n\n" +
                "${t.javaClass.name}\n${t.message}\n\n" +
                t.stackTraceToString().lineSequence().take(14).joinToString("\n")
            setTextColor(white)
            textSize = 13f
            setPadding(dp(20), dp(48), dp(20), dp(20))
        }
        root.addView(tv, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    /**
     * v0.4.1 diagnostic: after the first layout pass, verify the main column
     * actually measured. If the content area collapsed, draw the measured
     * numbers as an overlay on the root (the root demonstrably paints).
     */
    private fun selfCheck(root: FrameLayout, mode: String) {
        root.post {
            val info = StringBuilder("診断モード: $mode")
            var broken = false
            try {
                info.append(" / ob_done=${prefs.getBoolean("ob_done", false)}")
                info.append(" / root=${root.width}x${root.height}")
                if (mode == "main") {
                    info.append(" / content=${content.width}x${content.height} 子=${content.childCount}")
                    val sv = content.getChildAt(0) as? ScrollView
                    val page = sv?.getChildAt(0) as? LinearLayout
                    info.append(" / sv=${sv?.width}x${sv?.height} page=${page?.width}x${page?.height} page子=${page?.childCount}")
                    if (content.height == 0 || page == null || page.childCount == 0 || page.height == 0) broken = true
                } else {
                    info.append(" / ob子=${obContainer.childCount} ob=${obContainer.width}x${obContainer.height}")
                    if (obContainer.height == 0 || obContainer.childCount == 0) broken = true
                }
            } catch (t: Throwable) {
                info.append(" / 診断中の例外: ${t.javaClass.simpleName}: ${t.message}")
                broken = true
            }
            if (broken) {
                val tv = TextView(this).apply {
                    text = info.toString()
                    setTextColor(white)
                    textSize = 13f
                    setBackgroundColor(Color.argb(200, 0, 0, 0))
                    setPadding(dp(16), dp(40), dp(16), dp(16))
                }
                root.addView(tv, FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
    }

    private fun showMain() {
        // v0.7.0: first run of the challenge system, then settle past days
        // before anything renders, so score/slots are already current.
        initChallengeSystem()
        settlePastDays()
        val root = FrameLayout(this)
        root.addView(backgroundLayer(), FrameLayout.LayoutParams(-1, -1))

        // v0.4.2: no LinearLayout weight for the content area. The v0.4.1
        // on-device diagnostic showed the weight-1 child measuring 0 high
        // (blank screen) on the user's Pixel, so the layout is now pure
        // FrameLayout layering — the mechanism that demonstrably paints
        // (background, overlays). The tab bar overlays the bottom and the
        // content keeps clear of it via bottom padding.
        content = FrameLayout(this)
        content.setPadding(0, 0, 0, dp(92))
        applyTopInset(content, 0, 92)
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        // Tab bar: one reading-glass surface (dark-side tint), floating.
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(26, 255, 255, 255), Color.argb(18, 255, 255, 255)),
            ).apply {
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.argb(89, 0xD4, 0xAF, 0x37))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val barLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM,
        )
        barLp.setMargins(dp(12), 0, dp(12), dp(12))
        val names = listOf("今日", "鏡", "図鑑", "わたし")
        tabButtons = names.mapIndexed { i, name ->
            Button(this).apply {
                text = name
                textSize = 15f
                setOnClickListener { showScreen(i) }
            }
        }
        tabButtons.forEach {
            tabBar.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(tabBar, barLp)
        setContentView(root)
        showScreen(0)
        if (pendingCatalog) {
            // Fresh from onboarding: the first thing is picking his own 3.
            pendingCatalog = false
            showScreen(4)
        }
        selfCheck(root, "main")
    }

    /** Standard base: navy gradient + blurred brand lights (approximation). */
    private fun backgroundLayer(): View {
        val layer = FrameLayout(this)
        layer.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(navyLight, navy),
        )
        fun blob(color: Int, sizeDp: Int, gravity: Int, xOff: Int, yOff: Int): View {
            val v = View(this)
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = sizeDp * resources.displayMetrics.density / 2f
                colors = intArrayOf(color, Color.TRANSPARENT)
            }
            val lp = FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), gravity)
            lp.setMargins(dp(xOff), dp(yOff), dp(xOff), dp(yOff))
            v.layoutParams = lp
            return v
        }
        // Hero light: gold tint, top-left, bleeding off-screen.
        layer.addView(blob(Color.argb(52, 0xD4, 0xAF, 0x37), 340, Gravity.TOP or Gravity.START, -120, -100))
        // Second light: flame orange, bottom-right. (ART_DIRECTION caps
        // brand lights at 2.)
        layer.addView(blob(Color.argb(56, 0xFF, 0x6A, 0x00), 360, Gravity.BOTTOM or Gravity.END, -110, -90))
        return layer
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- storage ----------

    private fun doneSet(): MutableSet<String> =
        prefs.getStringSet("done", emptySet())?.toMutableSet() ?: mutableSetOf()

    private fun isDone(itemId: String, date: LocalDate) = doneSet().contains("$itemId@${date.format(dateFmt)}")

    private fun setDone(itemId: String, date: LocalDate, done: Boolean) {
        val set = doneSet()
        val key = "$itemId@${date.format(dateFmt)}"
        if (done) set.add(key) else set.remove(key)
        prefs.edit().putStringSet("done", set).apply()
    }

    private fun doneDatesFor(itemId: String): List<LocalDate> =
        doneSet().filter { it.startsWith("$itemId@") }
            .mapNotNull { runCatching { LocalDate.parse(it.substringAfter("@"), dateFmt) }.getOrNull() }

    private fun doneCountOn(date: LocalDate): Int =
        doneSet().count { it.endsWith("@${date.format(dateFmt)}") }

    /** Ever-graduated item ids — the permanent badge record （わたし画面）. */
    private fun badges(): Set<String> = prefs.getStringSet("graduated", emptySet()) ?: emptySet()

    /** Most recent completion day before [today]; null if none yet. */
    private fun lastActiveBefore(today: LocalDate): LocalDate? =
        doneSet().mapNotNull {
            runCatching { LocalDate.parse(it.substringAfter("@"), dateFmt) }.getOrNull()
        }.filter { it.isBefore(today) }.maxOrNull()

    // ---------- v0.7.0: チャレンジ＆ポイント ----------

    /** The user's current challenge set, in catalog order. */
    private fun challengeSet(): List<String> =
        (prefs.getStringSet("ch_set", null) ?: emptySet())
            .sortedBy { id -> MigakeruLogic.CATALOG.indexOfFirst { it.id == id } }

    /** Set history: the date a set took effect -> its challenge ids. */
    private fun challengeHistory(): Map<LocalDate, List<String>> =
        (prefs.getStringSet("ch_history", emptySet()) ?: emptySet()).mapNotNull { entry ->
            val datePart = entry.substringBefore(":", "")
            if (datePart.isEmpty()) return@mapNotNull null
            val ids = entry.substringAfter(":", "").split(",").filter { it.isNotEmpty() }
            runCatching { LocalDate.parse(datePart, dateFmt) to ids }.getOrNull()
        }.toMap()

    private fun saveChallengeSet(ids: List<String>) {
        val today = LocalDate.now()
        val history = challengeHistory() + (today to ids)
        val editor = prefs.edit()
            .putStringSet("ch_set", ids.toSet())
            .putStringSet(
                "ch_history",
                history.map { "${it.key.format(dateFmt)}:${it.value.joinToString(",")}" }.toSet(),
            )
        // Filling the last free slot ends the unlock celebration.
        if (ids.size >= slotCount()) editor.putBoolean("ch_unlock_pending", false)
        editor.apply()
    }

    /** The challenge set that was in effect on [date] (empty before v0.7.0). */
    private fun setForDate(date: LocalDate): List<String> =
        challengeHistory().filterKeys { !it.isAfter(date) }.maxByOrNull { it.key }?.value
            ?: emptyList()

    private fun slotCount(): Int = prefs.getInt("ch_slots", MigakeruLogic.INITIAL_SLOTS)
    private fun unlockStreak(): Int = prefs.getInt("ch_streak", 0)
    private fun scoreTotal(): Int = prefs.getInt("score_total", 0)

    /**
     * One-time v0.7.0 setup. Upgraders keep the 3 items the old system
     * served today as their first set (continuity, no cold start);
     * brand-new users (ch_fresh, set by onboarding) start empty and pick
     * their own 3 in the catalog. Settlement starts from the install
     * day — past days are never judged by rules that did not exist.
     */
    private fun initChallengeSystem() {
        if (prefs.getBoolean("ch_init", false)) return
        val today = LocalDate.now()
        val editor = prefs.edit().putBoolean("ch_init", true)
        if (!prefs.contains("ch_set") && !prefs.getBoolean("ch_fresh", false)) {
            val defaults = legacyTodayPicks(today).map { it.id }
            editor.putStringSet("ch_set", defaults.toSet())
            editor.putStringSet(
                "ch_history",
                setOf("${today.format(dateFmt)}:${defaults.joinToString(",")}"),
            )
        }
        if (!prefs.contains("score_settled_until")) {
            editor.putString("score_settled_until", today.minusDays(1).format(dateFmt))
        }
        editor.apply()
    }

    /**
     * Settle every unsettled day up to yesterday (user's rule): per set
     * challenge, done = +its points, missed = −1 flat. All-done days feed
     * the slot-unlock streak. Idempotent — settled_until only moves on.
     */
    private fun settlePastDays() {
        val yesterday = LocalDate.now().minusDays(1)
        var cursor = runCatching {
            LocalDate.parse(prefs.getString("score_settled_until", "") ?: "", dateFmt)
        }.getOrNull() ?: return
        if (!cursor.isBefore(yesterday)) return
        var score = scoreTotal()
        var state = MigakeruLogic.UnlockState(
            slotCount(), unlockStreak(), prefs.getBoolean("ch_unlock_pending", false),
        )
        var day = cursor.plusDays(1)
        var guard = 0
        while (!day.isAfter(yesterday) && guard < 400) {
            val set = setForDate(day)
            if (set.isNotEmpty()) {
                val doneIds = set.filter { isDone(it, day) }.toSet()
                score += MigakeruLogic.dayDelta(set, doneIds)
                state = MigakeruLogic.applySettledDay(state, doneIds.size == set.size)
            }
            cursor = day
            day = day.plusDays(1)
            guard++
        }
        prefs.edit()
            .putInt("score_total", score)
            .putInt("ch_slots", state.slots)
            .putInt("ch_streak", state.streak)
            .putBoolean("ch_unlock_pending", state.pendingUnlock)
            .putString("score_settled_until", cursor.format(dateFmt))
            .apply()
    }

    /** v0.7.0: the old auto-picks survive only as an upgrader's first set. */
    private fun legacyTodayPicks(date: LocalDate): List<Item> {
        val saved = prefs.getString("pick_${date.format(dateFmt)}", null)
        if (saved != null) {
            val items = saved.split(",").mapNotNull { MigakeruLogic.itemById(it) }
            if (items.size == 3) return items
        }
        val focus = prefs.getString("ob_focus", null)?.takeIf { it.isNotEmpty() }
        return MigakeruLogic.pickToday(date, emptyMap(), emptySet(), focus, emptySet())
    }

    // ---------- shared UI helpers ----------

    /** Reading glass, dark side (v0.5.0): white 14% -> 10%, gold edge 40%. */
    private fun card(done: Boolean = false): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (done) intArrayOf(Color.argb(48, 0xFF, 0xED, 0xD9), Color.argb(36, 0xFF, 0xE2, 0xC4))
        else intArrayOf(Color.argb(36, 255, 255, 255), Color.argb(26, 255, 255, 255)),
    ).apply {
        cornerRadius = dp(24).toFloat()
        setStroke(dp(1), Color.argb(if (done) 150 else 102, 0xD4, 0xAF, 0x37))
    }

    /** Input surface, dark glass; focus edge is set by the caller. */
    private fun inputSurface(focused: Boolean): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.argb(36, 255, 255, 255), Color.argb(26, 255, 255, 255)),
    ).apply {
        cornerRadius = dp(24).toFloat()
        if (focused) setStroke(dp(2), orange) else setStroke(dp(1), Color.argb(90, 255, 255, 255))
    }

    /** Press state: dip alpha while touched (quality.md state completeness). */
    private fun pressable(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> view.alpha = 0.82f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.alpha = 1f
            }
            false
        }
    }

    private fun primaryButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(white)
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(orangeHi, orangeLo),
        ).apply { cornerRadius = dp(24).toFloat() }
        pressable(this)
        setOnClickListener { onClick() }
    }

    private fun ghostButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(white)
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(20, 255, 255, 255), Color.argb(14, 255, 255, 255)),
        ).apply {
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), Color.argb(110, 255, 255, 255))
        }
        pressable(this)
        setOnClickListener { onClick() }
    }

    /** Small gold-tinted button for use ON a glass card (the 戻す action). */
    private fun lightButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(goldText)
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(46, 0xD4, 0xAF, 0x37), Color.argb(34, 0xD4, 0xAF, 0x37)),
        ).apply {
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), Color.argb(120, 0xD4, 0xAF, 0x37))
        }
        pressable(this)
        setOnClickListener { onClick() }
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.6f)
    }

    private fun pillarChip(pillar: String) = TextView(this).apply {
        text = pillar
        textSize = 13f
        setTextColor(goldText)
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply {
            setColor(Color.argb(41, 0xD4, 0xAF, 0x37))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.argb(110, 0xD4, 0xAF, 0x37))
        }
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }

    /**
     * v0.8.0: screen headings get an icon medallion, so every screen
     * opens with a bit of colour and shape instead of bare text.
     */
    private fun headingWithIcon(text: String, iconId: String, rgb: Int, size: Float = 26f): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(ChallengeIconView(this, iconId, rgb), LinearLayout.LayoutParams(dp(34), dp(34)))
        val t = label(text, size, white, bold = true)
        t.setPadding(dp(10), 0, 0, 0)
        row.addView(t)
        return row
    }

    private fun scrollPage(build: (LinearLayout) -> Unit): ScrollView {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        build(page)
        val tab = currentTab
        return ScrollView(this).apply {
            addView(page)
            setOnScrollChangeListener { _, _, y, _, _ -> this@MainActivity.scrollY[tab] = y }
            post { scrollTo(0, this@MainActivity.scrollY[tab] ?: 0) }
        }
    }

    private fun showScreen(index: Int) {
        currentTab = index
        tabButtons.forEachIndexed { i, b ->
            // The catalog (4) belongs to the 今日 tab visually.
            val selected = i == index || (index == 4 && i == 0)
            b.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            // Selected = gold gradient pill + near-black bold text (design A).
            b.setTextColor(if (selected) goldDark else Color.rgb(0xBD, 0xBD, 0xC6))
            b.background = if (selected) GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(goldHi, gold),
            ).apply { cornerRadius = dp(24).toFloat() } else GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(24).toFloat()
            }
        }
        content.removeAllViews()
        content.addView(
            when (index) {
                0 -> todayScreen()
                1 -> mirrorScreen()
                2 -> guideScreen()
                4 -> catalogScreen()
                else -> meScreen()
            },
            FrameLayout.LayoutParams(-1, -1),
        )
    }

    private fun fullLp(margin: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(margin)
        return lp
    }

    // ---------- オンボーディング (design v0.5 §5.5, mobile_onboarding skill) ----------
    // 11 pages, one claim each: hook / choice / match / identity / value /
    // step x3 / trial (real first item) / expectation / connect.
    // Choice and its result live on separate pages; reactions play on the
    // current page first, then a pause, then the page changes.

    private companion object {
        const val OB_TOTAL = 11
    }

    private fun showOnboarding() {
        val root = FrameLayout(this)
        root.addView(backgroundLayer(), FrameLayout.LayoutParams(-1, -1))
        // v0.4.2: same FrameLayout layering as showMain (no weight child) —
        // the onboarding container was the one measured 0 high on-device.
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), 0)
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(dp(12), dp(12) + bars.top, dp(12), 0)
                insets
            }
        }
        fun topBtn(text: String) = Button(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(paleBlue)
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
        }
        obBackBtn = topBtn("‹ もどる").apply {
            setOnClickListener { if (obPage > 0) renderObPage(obPage - 1) }
        }
        top.addView(obBackBtn)
        top.addView(View(this), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        obSkipBtn = topBtn("スキップ").apply {
            setOnClickListener { finishOnboarding() }
        }
        top.addView(obSkipBtn)
        obContainer = FrameLayout(this)
        obContainer.setPadding(0, dp(60), 0, dp(56))
        applyTopInset(obContainer, 60, 56)
        root.addView(obContainer, FrameLayout.LayoutParams(-1, -1))
        root.addView(top, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        obDots = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(20))
        }
        root.addView(obDots, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        setContentView(root)
        renderObPage(0)
        selfCheck(root, "ob")
    }

    private fun renderObPage(idx: Int) {
        cancelPendingAdvance()
        obPage = idx
        obBackBtn.visibility = if (idx == 0) View.INVISIBLE else View.VISIBLE
        obSkipBtn.visibility = if (idx == OB_TOTAL - 1) View.INVISIBLE else View.VISIBLE
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }
        buildObPage(page, idx)
        val scroll = ScrollView(this).apply { addView(page) }
        scroll.setOnClickListener {
            // A tap during the praise pause cuts straight to the next page.
            if (pendingAdvance != null) firePendingAdvance()
        }
        obContainer.removeAllViews()
        obContainer.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        // Staggered entry: headline -> visual -> the rest.
        for (i in 0 until page.childCount) {
            val child = page.getChildAt(i)
            child.alpha = 0f
            child.animate().alpha(1f).setStartDelay(i * 140L).setDuration(300).start()
        }
        renderObDots()
    }

    private fun renderObDots() {
        obDots.removeAllViews()
        val start = MigakeruLogic.dotWindowStart(OB_TOTAL, obPage)
        val end = minOf(start + 5, OB_TOTAL)
        for (i in start until end) {
            val isEdge = (i == start && start > 0) || (i == end - 1 && end < OB_TOTAL)
            val size = when {
                i == obPage -> 10
                isEdge -> 5
                else -> 7
            }
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (i == obPage) goldText else Color.argb(80, 0xD4, 0xAF, 0x37))
                }
            }
            val lp = LinearLayout.LayoutParams(dp(size), dp(size))
            lp.setMargins(dp(4), 0, dp(4), 0)
            obDots.addView(dot, lp)
        }
    }

    private fun bounce(v: View) {
        v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150)
            .withEndAction { v.animate().scaleX(1f).scaleY(1f).setDuration(180).start() }
            .start()
    }

    private fun haptic(v: View, success: Boolean) {
        v.performHapticFeedback(
            if (success && Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.VIRTUAL_KEY,
        )
    }

    private fun praiseFrom(set: List<String>): String {
        val i = MigakeruLogic.pickPraiseIndex(lastPraise, set.size, (System.nanoTime() % 997).toInt())
        lastPraise = i
        return set[i]
    }

    private fun scheduleAdvance(next: Int, delayMs: Long = 1050) {
        cancelPendingAdvance()
        val r = Runnable {
            pendingAdvance = null
            renderObPage(next)
        }
        pendingAdvance = r
        obHandler.postDelayed(r, delayMs)
    }

    private fun cancelPendingAdvance() {
        pendingAdvance?.let { obHandler.removeCallbacks(it) }
        pendingAdvance = null
    }

    private fun firePendingAdvance() {
        val r = pendingAdvance ?: return
        obHandler.removeCallbacks(r)
        pendingAdvance = null
        r.run()
    }

    private fun finishOnboarding(quiet: Boolean = false) {
        cancelPendingAdvance()
        if (!quiet) obContainer.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.VIRTUAL_KEY,
        )
        prefs.edit()
            .putBoolean("ob_done", true)
            .putInt("ob_version", 1)
            .putBoolean("intro_done", true)
            .putString("ob_focus", obFocus ?: "")
            // v0.7.0: a brand-new user picks his own first set (no defaults).
            .putBoolean("ch_fresh", true)
            .apply()
        if (quiet) Toast.makeText(this, "あとで、本体で案内するね", Toast.LENGTH_SHORT).show()
        pendingCatalog = true
        showMain()
    }

    private fun choiceButton(text: String, onClick: (View) -> Unit) = Button(this).apply {
        this.text = text
        textSize = 17f
        setTextColor(ink)
        typeface = Typeface.DEFAULT_BOLD
        background = card()
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setOnClickListener { v -> onClick(v) }
    }

    private fun miniRow(pillar: String, sample: String, doneMark: Boolean = false): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = card(doneMark)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val mark = TextView(this).apply {
            text = if (doneMark) "✓" else ""
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(white)
            if (doneMark) typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (doneMark) setColor(orange) else setStroke(dp(2), Color.argb(170, 0xD4, 0xAF, 0x37))
            }
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        }
        row.addView(mark)
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chipRow.addView(pillarChip(pillar))
        texts.addView(chipRow)
        texts.addView(label(sample, 15f, ink, bold = true))
        row.addView(texts)
        return row
    }

    private fun buildObPage(page: LinearLayout, idx: Int) {
        when (idx) {
            0 -> { // フック
                page.addView(label("男磨きは、モテるためじゃない。", 26f, white, bold = true))
                page.addView(label("鏡の自分に、納得するため。", 15f, paleBlue), fullLp(8))
                val ring = TextView(this).apply {
                    text = "鏡"
                    textSize = 40f
                    setTextColor(white)
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setStroke(dp(2), Color.argb(170, 0xD4, 0xAF, 0x37))
                        setColor(Color.argb(26, 0xD4, 0xAF, 0x37))
                    }
                }
                val lp = LinearLayout.LayoutParams(dp(140), dp(140))
                lp.gravity = Gravity.CENTER_HORIZONTAL
                lp.topMargin = dp(32)
                page.addView(ring, lp)
                // v0.8.0: a teaser row of challenge medallions under the ring.
                val teaser = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
                for (id in listOf("kintore", "skin", "meditation")) {
                    val iv = ChallengeIconView(this, id, MigakeruLogic.colorOf(id))
                    val ilp = LinearLayout.LayoutParams(dp(44), dp(44))
                    ilp.leftMargin = dp(6)
                    ilp.rightMargin = dp(6)
                    iv.layoutParams = ilp
                    teaser.addView(iv)
                }
                page.addView(teaser, fullLp(24))
                page.addView(primaryButton("次へ") { renderObPage(1) }, fullLp(32))
            }
            1 -> { // 選択（1問だけ・3択・3つ目は逃げ道）
                page.addView(label("今いちばん気になるのは？", 26f, white, bold = true))
                page.addView(label("選んだ内容で、最初の提案が少し変わる。", 15f, paleBlue), fullLp(8))
                val praise = label("", 15f, white, bold = true).apply { alpha = 0f }
                var chosen = false
                fun choose(v: View, answer: String, pillar: String?) {
                    if (chosen) return
                    chosen = true
                    obAnswerLabel = answer
                    obFocus = pillar
                    bounce(v)
                    haptic(v, true)
                    praise.text = praiseFrom(choicePraises)
                    praise.animate().alpha(1f).setDuration(200).start()
                    scheduleAdvance(2)
                }
                page.addView(choiceButton("からだを整えたい") { v -> choose(v, "からだを整えたい", Item.KARADA) }, fullLp(24))
                page.addView(choiceButton("見た目を整えたい") { v -> choose(v, "見た目を整えたい", Item.MITAME) }, fullLp(12))
                page.addView(choiceButton("まだわからない") { v -> choose(v, "まだわからない", null) }, fullLp(12))
                page.addView(praise, fullLp(24))
            }
            2 -> { // マッチ宣言（結果は別ページ）
                when (obFocus) {
                    Item.KARADA -> {
                        page.addView(label("「からだを整えたい」、あなたには。", 17f, paleBlue))
                        page.addView(label("ミガケルが、ぴったり。", 26f, white, bold = true), fullLp(8))
                        page.addView(label("チャレンジを自分で3つ選んで、毎日こなしていく。気になる柱のチャレンジは、カタログで先に並べておく。", 15f, paleBlue), fullLp(8))
                    }
                    Item.MITAME -> {
                        page.addView(label("「見た目を整えたい」、あなたには。", 17f, paleBlue))
                        page.addView(label("ミガケルが、ぴったり。", 26f, white, bold = true), fullLp(8))
                        page.addView(label("チャレンジを自分で3つ選んで、毎日こなしていく。気になる柱のチャレンジは、カタログで先に並べておく。", 15f, paleBlue), fullLp(8))
                    }
                    else -> {
                        page.addView(label("まだ決めなくて、大丈夫。", 17f, paleBlue))
                        page.addView(label("様子見からで、ぴったり。", 26f, white, bold = true), fullLp(8))
                        page.addView(label("チャレンジを自分で3つ選ぶところから。気になったものを課せばいい。", 15f, paleBlue), fullLp(8))
                    }
                }
                page.addView(primaryButton("次へ") { renderObPage(3) }, fullLp(32))
            }
            3 -> { // アイデンティティ
                page.addView(label("ミガケルは、自分のチャレンジを毎日こなすアプリ。", 26f, white, bold = true))
                page.addView(label("選ぶのは最初だけ。あとは毎日こなすだけ。", 15f, paleBlue), fullLp(8))
                page.addView(miniRow(Item.KARADA, "筋トレに行く"), fullLp(24))
                page.addView(miniRow(Item.MITAME, "爪を整える"), fullLp(12))
                page.addView(miniRow(Item.KOKORO, "瞑想 10分"), fullLp(12))
                page.addView(primaryButton("次へ") { renderObPage(4) }, fullLp(32))
            }
            4 -> { // 価値：持ち越さない
                page.addView(label("できなかった日は、持ち越さない。", 26f, white, bold = true))
                page.addView(label("昨日の分が、今日に積まれることはない。", 15f, paleBlue), fullLp(8))
                page.addView(label("昨日", 13f, paleBlue), fullLp(24))
                page.addView(label("○ ○ ○", 17f, Color.argb(140, 0xD4, 0xAF, 0x37)))
                page.addView(label("今日", 13f, paleBlue), fullLp(16))
                page.addView(label("● ○ ○", 17f, orange))
                page.addView(primaryButton("次へ") { renderObPage(5) }, fullLp(32))
            }
            5 -> { // ステップ1
                page.addView(label("開く。自分のチャレンジが出ている。", 26f, white, bold = true))
                page.addView(label("ステップ 1 / 3", 13f, paleBlue), fullLp(8))
                page.addView(miniRow(Item.KARADA, "歩く（目安 8,000歩）"), fullLp(24))
                page.addView(miniRow(Item.MITAME, "肌を保湿する"), fullLp(12))
                page.addView(miniRow(Item.KOKORO, "ジャーナルを1行書く"), fullLp(12))
                page.addView(primaryButton("次へ") { renderObPage(6) }, fullLp(32))
            }
            6 -> { // ステップ2
                page.addView(label("できたら、タップ。", 26f, white, bold = true))
                page.addView(label("ステップ 2 / 3", 13f, paleBlue), fullLp(8))
                page.addView(miniRow(Item.KARADA, "筋トレに行く", doneMark = true), fullLp(24))
                page.addView(label("それだけ。時刻も時間も、入力しない。", 15f, paleBlue), fullLp(16))
                page.addView(primaryButton("次へ") { renderObPage(7) }, fullLp(32))
            }
            7 -> { // ステップ3
                page.addView(label("週に1回、鏡で量を見る。", 26f, white, bold = true))
                page.addView(label("ステップ 3 / 3", 13f, paleBlue), fullLp(8))
                val week = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = card()
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                }
                val counts = listOf(2, 3, 1, 3, 2, 0, 0)
                for (d in 0..6) {
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, dp(4), 0, dp(4))
                    }
                    row.addView(label("月火水木金土日"[d].toString(), 15f, ink, bold = true))
                    row.addView(View(this), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(label("●".repeat(counts[d]) + "○".repeat(3 - counts[d]), 15f, orange))
                    week.addView(row)
                }
                page.addView(week, fullLp(24))
                page.addView(label("連続じゃなく、量で見える。", 15f, paleBlue), fullLp(16))
                page.addView(primaryButton("次へ") { renderObPage(8) }, fullLp(32))
            }
            8 -> { // 試し操作（本物の記録になる試しタップ）
                val today = LocalDate.now()
                // v0.7.0: the trial is the easiest challenge of the focus
                // pillar (no focus -> からだ） — a gentle first completion.
                val trialPillar = obFocus ?: Item.KARADA
                val item = MigakeruLogic.CATALOG.filter { it.pillar == trialPillar }
                    .minByOrNull { MigakeruLogic.pointsOf(it.id) } ?: MigakeruLogic.CATALOG.first()
                page.addView(label("試しに、タップしてみて。", 26f, white, bold = true))
                page.addView(label("これは本物の記録になる。タップで完了を試せる。", 15f, paleBlue), fullLp(8))
                val praise = label("", 15f, white, bold = true).apply { alpha = 0f }
                if (isDone(item.id, today)) trialDoneNow = true
                // Built inline (not miniRow) so the mark can flip in place.
                val mark = TextView(this).apply {
                    text = if (trialDoneNow) "✓" else ""
                    textSize = 15f
                    gravity = Gravity.CENTER
                    setTextColor(white)
                    if (trialDoneNow) typeface = Typeface.DEFAULT_BOLD
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        if (trialDoneNow) setColor(orange) else setStroke(dp(2), Color.argb(170, 0xD4, 0xAF, 0x37))
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
                }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = card(trialDoneNow)
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                    contentDescription = item.label
                }
                row.addView(mark)
                val texts = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, 0, 0)
                }
                val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                chipRow.addView(pillarChip(item.pillar))
                texts.addView(chipRow)
                texts.addView(label(item.label, 17f, ink, bold = true))
                row.addView(texts)
                pressable(row)
                row.setOnClickListener { v ->
                    if (trialDoneNow) return@setOnClickListener
                    trialDoneNow = true
                    setDone(item.id, today, true)
                    prefs.edit().putBoolean("first_success", true).apply()
                    mark.text = "✓"
                    mark.setTextColor(white)
                    mark.typeface = Typeface.DEFAULT_BOLD
                    mark.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(orange)
                    }
                    row.background = card(true)
                    bounce(v)
                    haptic(v, true)
                    praise.text = praiseFrom(trialPraises)
                    praise.animate().alpha(1f).setDuration(200).start()
                    scheduleAdvance(9, 1150)
                }
                page.addView(row, fullLp(24))
                page.addView(praise, fullLp(16))
                page.addView(ghostButton("あとで試す") { finishOnboarding(quiet = true) }, fullLp(24))
            }
            9 -> { // 期待：枠が増える
                page.addView(label("全部続けられたら、枠が増える。", 26f, white, bold = true))
                page.addView(label("課せるチャレンジが、少しずつ増えていく。", 15f, paleBlue), fullLp(8))
                val badge = TextView(this).apply {
                    text = "+1"
                    textSize = 26f
                    setTextColor(white)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(orange)
                        setStroke(dp(3), Color.argb(190, 0xF5, 0xD6, 0x7B))
                    }
                }
                val lp = LinearLayout.LayoutParams(dp(120), dp(120))
                lp.gravity = Gravity.CENTER_HORIZONTAL
                lp.topMargin = dp(32)
                page.addView(badge, lp)
                page.addView(primaryButton("次へ") { renderObPage(10) }, fullLp(32))
            }
            else -> { // 接続
                page.addView(label("最初のチャレンジ選びが、待っている。", 26f, white, bold = true))
                page.addView(
                    label(
                        if (prefs.getBoolean("first_success", false)) "さっきの1つは、もう今日の記録に入っている。"
                        else "まずは3つ選ぶところから。",
                        15f, paleBlue,
                    ),
                    fullLp(8),
                )
                // v0.8.0: a sparkle to start on.
                val sparkLp = LinearLayout.LayoutParams(dp(64), dp(64))
                sparkLp.gravity = Gravity.CENTER_HORIZONTAL
                sparkLp.topMargin = dp(24)
                page.addView(ChallengeIconView(this, "ui_sparkle", 0xFFCA28), sparkLp)
                page.addView(primaryButton("始める") { finishOnboarding() }, fullLp(32))
            }
        }
    }

    // ---------- 今日 ----------

    private fun todayScreen(): ScrollView {
        val today = LocalDate.now()
        // v0.7.0: the day is no longer served — it is the user's own set.
        val setIds = challengeSet()
        val items = setIds.mapNotNull { MigakeruLogic.itemById(it) }
        val doneToday = items.count { isDone(it.id, today) }
        return scrollPage { page ->
            // v0.4.0: hot-blooded buddy at the top-right; bubble on its left.
            // v0.6.0 (U4): the bubble reacts to the day's state; v0.7.0: a
            // slot unlock one all-done day away joins the reactions.
            val greeting = MigakeruLogic.buddyLine(
                java.time.LocalTime.now().hour,
                today,
                doneToday,
                items.size,
                lastActiveBefore(today),
                unlockStreak() == MigakeruLogic.UNLOCK_STREAK_DAYS - 1 &&
                    slotCount() < MigakeruLogic.MAX_SLOTS && items.isNotEmpty(),
            )
            val buddyRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                contentDescription = "応援キャラの挨拶：$greeting"
            }
            val bubble = label(greeting, 15f, ink, bold = true).apply {
                background = card()
                setPadding(dp(14), dp(10), dp(14), dp(10))
            }
            buddyRow.addView(bubble, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            buddyRow.addView(BubbleTailView(this), LinearLayout.LayoutParams(dp(12), dp(24)))
            buddyRow.addView(FlameFaceView(this), LinearLayout.LayoutParams(dp(76), dp(76)))
            page.addView(buddyRow, fullLp(0))

            val dow = "月火水木金土日"[today.dayOfWeek.value - 1]
            page.addView(label("${today.monthValue}月${today.dayOfMonth}日（$dow）・昨日より少し好きになる", 13f, paleBlue), fullLp(8))
            page.addView(headingWithIcon("マイチャレンジ", "ui_target", 0xFF7043))

            // v0.7.0: the score is the day's headline number — total so
            // far, plus what today has earned up to now.
            val earnedToday = MigakeruLogic.dayEarned(
                setIds, items.filter { isDone(it.id, today) }.map { it.id }.toSet(),
            )
            val scoreCard = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = card()
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            scoreCard.addView(
                ChallengeIconView(this, "ui_coin", 0xFFC107),
                LinearLayout.LayoutParams(dp(38), dp(38)),
            )
            val scoreLabel = label("ポイント", 15f, ink, bold = true)
            scoreLabel.setPadding(dp(10), 0, 0, 0)
            scoreCard.addView(scoreLabel)
            scoreCard.addView(View(this), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            scoreCard.addView(label("${scoreTotal()} pt", 20f, ink, bold = true))
            scoreCard.addView(label("　今日 +$earnedToday pt", 15f, subOnGlass))
            page.addView(scoreCard, fullLp(12))

            // K4: minimal first-run intro (one card, dismissed once, no wizard).
            if (!prefs.getBoolean("intro_done", false)) {
                val intro = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = card()
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                }
                intro.addView(label("はじめに", 17f, ink, bold = true))
                for (line in listOf(
                    "チャレンジを自分で選んで、毎日こなしていく",
                    "できた分だけポイント。難しいものほど高い",
                    "全部続けられたら枠が増えて、もっと課せる",
                )) {
                    val lineRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, dp(3), 0, dp(3))
                    }
                    lineRow.addView(
                        ChallengeIconView(this, "ui_sparkle", 0xFFCA28),
                        LinearLayout.LayoutParams(dp(18), dp(18)),
                    )
                    val lt = label(line, 15f, subOnGlass)
                    lt.setPadding(dp(8), 0, 0, 0)
                    lineRow.addView(lt)
                    intro.addView(lineRow)
                }
                intro.addView(primaryButton("はじめる") {
                    prefs.edit().putBoolean("intro_done", true).apply()
                    showScreen(0)
                }, fullLp(12))
                page.addView(intro, fullLp(16))
            }

            items.forEach { item ->
                val done = isDone(item.id, today)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = card(done)
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                    contentDescription = if (done) "完了：${item.label}" else "未完了：${item.label}"
                }
                val mark = TextView(this).apply {
                    text = if (done) "✓" else ""
                    textSize = 15f
                    gravity = Gravity.CENTER
                    setTextColor(white)
                    if (done) typeface = Typeface.DEFAULT_BOLD
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        if (done) setColor(orange) else setStroke(dp(2), Color.argb(170, 0xD4, 0xAF, 0x37))
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
                }
                row.addView(mark)
                // v0.7.1: the challenge's own icon rides along on 今日 too.
                val icon = ChallengeIconView(this, item.id, MigakeruLogic.colorOf(item.id))
                icon.layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                    setMargins(dp(10), 0, 0, 0)
                }
                row.addView(icon)
                val texts = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, 0, 0)
                }
                val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                chipRow.addView(pillarChip(item.pillar))
                chipRow.addView(label("　${MigakeruLogic.pointsOf(item.id)} pt", 13f, goldText, bold = true))
                texts.addView(chipRow)
                texts.addView(label(item.label, 17f, if (done) subOnGlass else ink, bold = true))
                row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                pressable(row)
                row.setOnClickListener {
                    val wasDone = isDone(item.id, today)
                    setDone(item.id, today, !wasDone)
                    if (wasDone) {
                        Toast.makeText(this, "「${item.label}」の完了を取り消したよ", Toast.LENGTH_SHORT).show()
                    } else {
                        // v0.6.0 (U5): a completion must answer back — the
                        // haptic lands here and the buddy's line changes to
                        // a reaction on the re-render; clearing the whole
                        // set gets its own small celebration.
                        haptic(row, true)
                        if (items.isNotEmpty() && items.all { isDone(it.id, today) }) {
                            Toast.makeText(this, "全部できたよ", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showScreen(0)
                }
                page.addView(row, fullLp(16))
            }

            // v0.7.0: a freshly unlocked slot is celebrated here, with the
            // way into the catalog one tap away.
            if (prefs.getBoolean("ch_unlock_pending", false)) {
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = card()
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                }
                val flagLp = LinearLayout.LayoutParams(dp(46), dp(46))
                flagLp.gravity = Gravity.CENTER_HORIZONTAL
                box.addView(ChallengeIconView(this, "decide", 0xFF5252), flagLp)
                val unlockText = label("枠が増えたよ。チャレンジをもう1つ、自分に課せる。", 15f, ink, bold = true)
                unlockText.gravity = Gravity.CENTER
                box.addView(unlockText, fullLp(8))
                box.addView(primaryButton("チャレンジを選ぶ") { showScreen(4) }, fullLp(12))
                page.addView(box, fullLp(16))
            } else {
                page.addView(ghostButton("チャレンジを選ぶ・変える") { showScreen(4) }, fullLp(16))
            }

            val streak = unlockStreak()
            val footer = when {
                items.isEmpty() -> "まずはチャレンジを選ぼう。"
                doneToday == items.size -> "今日はもう十分。よく整えたね。"
                slotCount() >= MigakeruLogic.MAX_SLOTS -> "枠は最大。あとは回すだけ。"
                streak == MigakeruLogic.UNLOCK_STREAK_DAYS - 1 -> "あと1日、全部できたら枠が増える。"
                streak > 0 -> "全部できた日が ${streak} 日つづいてる。"
                doneToday == 0 -> "タップで完了。それだけ。"
                else -> "あと ${items.size - doneToday} つ。急がなくていい。"
            }
            val footerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            footerRow.addView(FlameFaceView(this), LinearLayout.LayoutParams(dp(26), dp(26)))
            val footerLabel = label(footer, 15f, white)
            footerLabel.setPadding(dp(8), 0, 0, 0)
            footerRow.addView(footerLabel)
            page.addView(footerRow, fullLp(24))
        }
    }

    // ---------- チャレンジ（カタログ） ----------

    /**
     * v0.7.0: the catalog. All 24 challenges grouped by pillar (the
     * onboarding focus pillar first), each with its points; set/unset
     * within the slot limit. Reached from 今日， not a tab of its own.
     * v0.7.1: a two-column wall of colourful cards — icon medallion,
     * points pill, and a cheer line per challenge (user's request).
     */
    private fun catalogScreen(): ScrollView {
        val setIds = challengeSet()
        val slots = slotCount()
        return scrollPage { page ->
            page.addView(ghostButton("‹ 今日にもどる") { showScreen(0) })
            page.addView(headingWithIcon("チャレンジ", "ui_sparkle", 0xFFCA28), fullLp(16))
            page.addView(label("セット中 ${setIds.size} / 枠 $slots", 17f, white, bold = true), fullLp(8))
            page.addView(
                label("難しいチャレンジほど高ポイント。できなかった日は、1つにつき −1 pt。全部続けられたら枠が増える。", 13f, paleBlue),
                fullLp(4),
            )
            val focus = prefs.getString("ob_focus", null)?.takeIf { it.isNotEmpty() }
            val pillars = listOf(Item.KARADA, Item.MITAME, Item.KOKORO)
                .sortedBy { if (it == focus) 0 else 1 }
            for (pillar in pillars) {
                // v0.8.0: each pillar heading carries a representative icon.
                val pillarIcon = when (pillar) {
                    Item.KARADA -> "kintore"
                    Item.MITAME -> "hair"
                    else -> "thanks"
                }
                val pillarRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                pillarRow.addView(
                    ChallengeIconView(this, pillarIcon, MigakeruLogic.colorOf(pillarIcon)),
                    LinearLayout.LayoutParams(dp(24), dp(24)),
                )
                val pillarLabel = label(pillar, 17f, white, bold = true)
                pillarLabel.setPadding(dp(8), 0, 0, 0)
                pillarRow.addView(pillarLabel)
                page.addView(pillarRow, fullLp(24))
                val pillarItems = MigakeruLogic.CATALOG.filter { it.pillar == pillar }
                for (pair in pillarItems.chunked(2)) {
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    pair.forEachIndexed { idx, item ->
                        val inSet = item.id in setIds
                        val cardView = challengeCard(item, inSet, setIds.size < slots) {
                            if (inSet) {
                                saveChallengeSet(setIds - item.id)
                            } else {
                                saveChallengeSet(setIds + item.id)
                                Toast.makeText(
                                    this, "『${item.label}』をセットしたよ",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            showScreen(4)
                        }
                        val lp = LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f,
                        )
                        if (idx == 0) lp.rightMargin = dp(6) else lp.leftMargin = dp(6)
                        row.addView(cardView, lp)
                    }
                    page.addView(row, fullLp(12))
                }
            }
        }
    }

    /** One catalog card: icon medallion + points pill, name, cheer line, action. */
    private fun challengeCard(
        item: Item,
        inSet: Boolean,
        canSet: Boolean,
        onToggle: () -> Unit,
    ): LinearLayout {
        val rgb = MigakeruLogic.colorOf(item.id)
        val colorInt = Color.rgb((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
        val lum = (0.299 * ((rgb shr 16) and 0xFF) + 0.587 * ((rgb shr 8) and 0xFF) +
            0.114 * (rgb and 0xFF)) / 255.0
        val cardView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = card(inSet)
            if (inSet) (background as GradientDrawable).setStroke(dp(2), gold)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            contentDescription = "${item.label}、${MigakeruLogic.pointsOf(item.id)}ポイント" +
                if (inSet) "、セット中" else ""
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(
            ChallengeIconView(this, item.id, rgb),
            LinearLayout.LayoutParams(dp(52), dp(52)),
        )
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val pill = TextView(this).apply {
            text = "${MigakeruLogic.pointsOf(item.id)} pt"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (lum > 0.62) Color.rgb(0x24, 0x1D, 0x08) else Color.WHITE)
            background = GradientDrawable().apply {
                setColor(colorInt)
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        top.addView(pill)
        cardView.addView(top)
        // v0.9.0 (real-device report): fixed dp heights clipped the text
        // mid-glyph on devices with a larger font size — text is sp and
        // grows, dp heights do not. Minimum heights keep the tidy look
        // at the standard font size and grow when the text needs room.
        val name = label(item.label, 16f, ink, bold = true)
        name.gravity = Gravity.CENTER_VERTICAL
        name.minimumHeight = dp(58)
        cardView.addView(name, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        val cheer = label(MigakeruLogic.taglineOf(item.id), 13f, subOnGlass)
        cheer.minimumHeight = dp(46)
        cardView.addView(cheer, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        when {
            inSet -> cardView.addView(lightButton("外す") { onToggle() }, fullLp(8))
            canSet -> cardView.addView(lightButton("セットする") { onToggle() }, fullLp(8))
            else -> {
                val full = label("枠いっぱい", 13f, subOnGlass)
                full.gravity = Gravity.CENTER
                cardView.addView(full, fullLp(8))
            }
        }
        return cardView
    }

    // ---------- 鏡 ----------

    private fun mirrorScreen(): ScrollView {
        val today = LocalDate.now()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks(weekOffset.toLong())
        return scrollPage { page ->
            page.addView(headingWithIcon("鏡タイム", "ui_mirror", 0x4FC3F7))
            page.addView(label("積み重ねは、連続じゃなく「量」で見える。", 13f, paleBlue))

            // K3: week navigation (past weeks stay reachable, future locked).
            val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            nav.addView(ghostButton("‹ 前の週") {
                weekOffset--
                journalOpen = false
                showScreen(1)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (weekOffset < 0) {
                val spacer = View(this)
                nav.addView(spacer, LinearLayout.LayoutParams(dp(12), 1))
                nav.addView(ghostButton("今週へ ›") {
                    weekOffset = 0
                    journalOpen = false
                    showScreen(1)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            page.addView(nav, fullLp(12))
            val weekTitle = if (weekOffset == 0) "今週" else "${monday.monthValue}月${monday.dayOfMonth}日の週"
            page.addView(label(weekTitle, 17f, white, bold = true), fullLp(12))

            val weekCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = card()
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            // v0.7.0: days under the challenge system show their own set
            // (dots per challenge) and the day's points; older days keep
            // the plain completion count they were recorded with.
            fun signedPt(v: Int) = (if (v >= 0) "+$v" else "−${-v}") + " pt"
            var weekPoints = 0
            var weekHasPoints = false
            var weekCount = 0
            for (off in 0..6) {
                val d = monday.plusDays(off.toLong())
                val future = d.isAfter(today)
                val dow = "月火水木金土日"[off]
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(4))
                }
                row.addView(label("$dow　${d.monthValue}/${d.dayOfMonth}", 15f, ink, bold = true))
                row.addView(View(this), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (future) {
                    // Future days are not "missed" days: different mark entirely.
                    row.addView(label("これから", 13f, subOnGlass))
                } else {
                    val set = setForDate(d)
                    if (set.isNotEmpty()) {
                        val doneIds = set.filter { isDone(it, d) }.toSet()
                        // v0.8.0: the day's set as mini icons — bright when
                        // done, faded when not — instead of ●○ characters.
                        val strip = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            contentDescription = "できた ${doneIds.size} / ${set.size}"
                        }
                        for (id in set) {
                            val iv = ChallengeIconView(this, id, MigakeruLogic.colorOf(id))
                            iv.alpha = if (id in doneIds) 1f else 0.25f
                            val ilp = LinearLayout.LayoutParams(dp(24), dp(24))
                            ilp.leftMargin = dp(3)
                            iv.layoutParams = ilp
                            strip.addView(iv)
                        }
                        row.addView(strip)
                        val dayPt = if (d == today) {
                            MigakeruLogic.dayEarned(set, doneIds)
                        } else {
                            MigakeruLogic.dayDelta(set, doneIds)
                        }
                        weekPoints += dayPt
                        weekHasPoints = true
                        row.addView(label("　${signedPt(dayPt)}", 13f, subOnGlass))
                    } else {
                        val count = doneCountOn(d)
                        weekCount += count
                        // Legacy days: a row of small orange dots, one per completion.
                        val dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                        repeat(count) {
                            val dot = View(this).apply {
                                background = GradientDrawable().apply {
                                    shape = GradientDrawable.OVAL
                                    setColor(orange)
                                }
                            }
                            val dlp = LinearLayout.LayoutParams(dp(10), dp(10))
                            dlp.leftMargin = dp(3)
                            dot.layoutParams = dlp
                            dots.addView(dot)
                        }
                        row.addView(dots)
                    }
                }
                weekCard.addView(row)
            }
            val totalRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
            }
            if (weekHasPoints) {
                totalRow.addView(
                    ChallengeIconView(this, "ui_coin", 0xFFC107),
                    LinearLayout.LayoutParams(dp(22), dp(22)),
                )
                val totalLabel = label(if (weekOffset == 0) "今週のポイント" else "この週のポイント", 13f, subOnGlass)
                totalLabel.setPadding(dp(6), 0, 0, 0)
                totalRow.addView(totalLabel)
                totalRow.addView(View(this), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                totalRow.addView(label(signedPt(weekPoints), 17f, ink, bold = true))
            } else {
                totalRow.addView(label(if (weekOffset == 0) "今週の積み重ね" else "この週の積み重ね", 13f, subOnGlass))
                totalRow.addView(View(this), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                totalRow.addView(label("$weekCount 個", 17f, ink, bold = true))
            }
            weekCard.addView(totalRow)
            if (!weekHasPoints && weekCount == 0 && weekOffset == 0 && doneSet().isEmpty()) {
                weekCard.addView(label("ここに、できた日が並んでいく。", 13f, subOnGlass))
            }
            page.addView(weekCard, fullLp(16))

            val jKey = "journal_${monday.format(dateFmt)}"
            val savedJournal = prefs.getString(jKey, "") ?: ""
            if (weekOffset != 0) {
                // K3: past weeks' journals stay readable (view only).
                if (savedJournal.isNotEmpty()) {
                    val past = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = card()
                        setPadding(dp(16), dp(12), dp(16), dp(12))
                    }
                    past.addView(label("この週の一言", 13f, subOnGlass))
                    past.addView(label(savedJournal, 15f, ink, bold = true))
                    page.addView(past, fullLp(16))
                }
            } else if (!journalOpen) {
                val text = if (savedJournal.isEmpty()) "今週の一言を書く" else "今週の一言：$savedJournal"
                page.addView(ghostButton(text) {
                    journalOpen = true
                    showScreen(1)
                }, fullLp(16))
            } else {
                val edit = EditText(this).apply {
                    hint = "今週の一言（1行でいい）"
                    textSize = 15f
                    setText(savedJournal)
                    setTextColor(ink)
                    setHintTextColor(subOnGlass)
                    background = inputSurface(false)
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    setOnFocusChangeListener { v, hasFocus -> v.background = inputSurface(hasFocus) }
                }
                page.addView(edit, fullLp(16))
                page.addView(primaryButton("一言を残す") {
                    prefs.edit().putString(jKey, edit.text.toString()).apply()
                    journalOpen = false
                    Toast.makeText(this, "残したよ", Toast.LENGTH_SHORT).show()
                    showScreen(1)
                }, fullLp(12))
            }
        }
    }

    // ---------- 図鑑 ----------

    /** v0.8.0: each guide borrows the icon of its closest challenge. */
    private data class Guide(val title: String, val body: String, val icon: String)

    private val guides = listOf(
        Guide("爪と手", "爪は短く清潔に。手は顔と同じくらい見られている。保湿までが1セット。", "nails"),
        Guide("肌の保湿", "洗顔のあと1分以内に保湿。量より「毎日同じ手順」が効く。", "skin"),
        Guide("髪の整え方", "寝ぐせは濡らして乾かし直すのが最短。整髪料は米粒ほどから。", "hair"),
        Guide("香りは控えめに", "香水は1プッシュ、近くでだけ分かるくらいが「整っている」。", "scent"),
        Guide("姿勢", "耳と肩が縦に並ぶ位置。壁に背中をつけて30秒で感覚を戻す。", "posture"),
        Guide("歩き方", "視線は少し遠く、歩幅は普段より少しだけ大きく。", "walk"),
        Guide("声と目線", "話すときは相手の目を見て、語尾まではっきり。速さはゆっくりめ。", "brow"),
        Guide("寝る前1時間", "画面を置いて、照明を落とす。眠りの質は夜の過ごし方で決まる。", "sleep_prep"),
        Guide("朝の光", "起きたらカーテンを開けて光を浴びる。体内時計のリセットボタン。", "sky"),
        Guide("冷水の使い方", "シャワーの最後30秒だけ冷水に。無理のない範囲で目を覚ます。", "bath"),
        Guide("スマホとの距離", "通知はまとめて見る。手の届かない場所に置く時間が集中を作る。", "phone_off"),
        Guide("決めたことを守る", "小さく決めて、必ず守る。守れた回数が自信の正体。", "decide"),
    )

    private fun guideScreen(): ScrollView = scrollPage { page ->
        page.addView(headingWithIcon("整え図鑑", "journal", 0x66BB6A))
        page.addView(label("題名をタップすると、その1枚だけ開く。", 13f, paleBlue))
        guides.forEachIndexed { i, g ->
            val open = openGuide == i
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = card()
                setPadding(dp(16), dp(12), dp(16), dp(12))
                contentDescription = g.title
            }
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(
                ChallengeIconView(this, g.icon, MigakeruLogic.colorOf(g.icon)),
                LinearLayout.LayoutParams(dp(30), dp(30)),
            )
            val guideTitle = label(g.title, 17f, ink, bold = true)
            guideTitle.setPadding(dp(10), 0, 0, 0)
            head.addView(guideTitle)
            head.addView(View(this), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            head.addView(label(if (open) "−" else "＋", 17f, subOnGlass, bold = true))
            box.addView(head)
            if (open) box.addView(label(g.body, 15f, subOnGlass))
            pressable(box)
            box.setOnClickListener {
                openGuide = if (open) -1 else i
                showScreen(2)
            }
            page.addView(box, fullLp(12))
        }
    }

    // ---------- わたし ----------

    private fun meScreen(): ScrollView = scrollPage { page ->
        page.addView(headingWithIcon("わたし", "posture", 0xEC407A))
        // v0.7.0: points headline the page; the counts sit under it.
        // v0.8.0: the three stats become icon rows in one card.
        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = card()
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        fun statRow(iconId: String, rgb: Int, text: String, bold: Boolean) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(5), 0, dp(5))
            }
            r.addView(ChallengeIconView(this, iconId, rgb), LinearLayout.LayoutParams(dp(30), dp(30)))
            val t = label(text, 15f, ink, bold = bold)
            t.setPadding(dp(10), 0, 0, 0)
            r.addView(t)
            stats.addView(r)
        }
        statRow("ui_coin", 0xFFC107, "ポイント：${scoreTotal()} pt", bold = true)
        statRow("stairs", MigakeruLogic.colorOf("stairs"), "これまでの積み重ね：${doneSet().size} 個", bold = false)
        statRow("decide", MigakeruLogic.colorOf("decide"), "チャレンジ枠：${slotCount()}（セット中 ${challengeSet().size}）", bold = false)
        page.addView(stats, fullLp(12))

        page.addView(label("卒業した習慣", 17f, white, bold = true), fullLp(24))
        val grad = badges()
        if (grad.isEmpty()) {
            page.addView(label("まだなし。", 13f, paleBlue))
        } else {
            grad.forEach { id ->
                val item = MigakeruLogic.itemById(id) ?: return@forEach
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = card()
                    setPadding(dp(16), dp(12), dp(12), dp(12))
                }
                // v0.8.0: the graduated habit shows its own icon, not a dot.
                box.addView(
                    ChallengeIconView(this, item.id, MigakeruLogic.colorOf(item.id)),
                    LinearLayout.LayoutParams(dp(30), dp(30)),
                )
                val gradLabel = label("卒業　${item.label}", 15f, ink, bold = true)
                gradLabel.setPadding(dp(10), 0, 0, 0)
                box.addView(gradLabel)
                page.addView(box, fullLp(12))
            }
            page.addView(
                label("前の方式で身につけた習慣の記録。これからもここに残る。", 13f, paleBlue),
                fullLp(8),
            )
        }

        page.addView(
            label("Health Connect は任意・読み取りだけ（歩数／運動／睡眠／体重）。この版では未接続で、タップ記録だけで動く。", 13f, paleBlue),
            fullLp(24),
        )

        page.addView(ghostButton("記録をすべて消す") {
            AlertDialog.Builder(this)
                .setTitle("記録をすべて消しますか？")
                .setMessage("完了の記録・卒業・一言メモが全部消えます。戻せません。")
                .setNegativeButton("やめる", null)
                .setPositiveButton("消す") { _, _ ->
                    prefs.edit().clear().apply()
                    journalOpen = false
                    weekOffset = 0
                    openGuide = -1
                    Toast.makeText(this, "全部消したよ", Toast.LENGTH_SHORT).show()
                    showScreen(3)
                }
                .show()
        }, fullLp(24))
        // Version comes from the package itself so this label never goes stale.
        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: ""
        page.addView(label("ミガケル v$versionName", 13f, paleBlue), fullLp(12))
    }
}
