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
 * Migakeru v0.4.0 — hot-blooded flame buddy at the top-right of the today
 * screen, with a speech bubble that shows a time-of-day greeting
 * (BuddyView.kt; greetings only for now).
 * v0.2.0 — strict UX review fixes K1–K5 (docs/UX_REVIEW_v0.1.2.md):
 * graduation threshold 5/21d (reachable), graduation signal on the today
 * screen, mirror week navigation + past journals, first-run intro card,
 * and a swap button that names its target and destination.
 * v0.1.2 — Simple Glass applied (docs/GLASS_REVIEW_v0.1.1.md).
 * Tokens (Simple Glass canonical, ART_DIRECTION roles unchanged):
 * - Base: navy gradient + 3 blurred lights (radial-gradient ovals, plain-View
 *   approximation of the standard template: hero tint top-left, steel bottom-right,
 *   pale centre-bottom).
 * - Reading glass on cards: white 62% -> 54% top-to-bottom, top edge white 75%.
 *   On-card sub text is #2E2A26 (not canonical #554D44): over the navy base the
 *   blended surface luminance only leaves ~3.0:1 for #554D44, and the contrast
 *   gate (4.5:1) outranks the colour token. Recorded in the review doc.
 * - Input surface: white 86% -> 82%, focus edge turns orange.
 * - Type scale: 26 / 17 / 15 / 13 only. Line spacing 1.6.
 * - Spacing scale: 4 / 8 / 12 / 16 / 24 / 32 (screen side margin 20 per quality.md).
 * - Press feedback: views dip to alpha .82 while touched.
 * Known limits (plain Views): no backdrop blur; the system dialog is not glass.
 */
class MainActivity : Activity() {

    private val navy = Color.rgb(0x0E, 0x22, 0x33)
    private val navyLight = Color.rgb(0x16, 0x32, 0x4A)
    private val orange = Color.rgb(0xE6, 0x51, 0x00)
    private val orangeHi = Color.rgb(0xEB, 0x6A, 0x1F)
    private val orangeLo = Color.rgb(0xDC, 0x4E, 0x00)
    private val ink = Color.rgb(0x1B, 0x2A, 0x36)
    private val subOnGlass = Color.rgb(0x2E, 0x2A, 0x26)
    private val paleBlue = Color.rgb(0xBC, 0xD4, 0xE4)
    private val white = Color.WHITE

    private lateinit var content: FrameLayout
    private lateinit var tabButtons: List<Button>
    private var currentTab = 0
    private val scrollY = mutableMapOf<Int, Int>()
    private var journalOpen = false
    private var weekOffset = 0
    private var openGuide = -1

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
                intArrayOf(Color.argb(64, 255, 255, 255), Color.argb(46, 255, 255, 255)),
            ).apply {
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.argb(97, 255, 255, 255))
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
        // Hero light: warm orange tint, top-left, bleeding off-screen.
        layer.addView(blob(Color.argb(58, 0xE6, 0x8C, 0x4A), 340, Gravity.TOP or Gravity.START, -120, -100))
        // Second light: steel blue, bottom-right. (ART_DIRECTION caps brand
        // lights at 2; the v0.1.2 third light was a review miss, removed in v0.2.)
        layer.addView(blob(Color.argb(80, 0x5A, 0x96, 0xBE), 360, Gravity.BOTTOM or Gravity.END, -110, -90))
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

    private fun graduated(): Set<String> = prefs.getStringSet("graduated", emptySet()) ?: emptySet()

    private fun todayPicks(date: LocalDate): List<Item> {
        val key = "pick_${date.format(dateFmt)}"
        val saved = prefs.getString(key, null)
        if (saved != null) {
            val items = saved.split(",").mapNotNull { MigakeruLogic.itemById(it) }
            if (items.size == 3) return items
        }
        val recent = mutableMapOf<String, LocalDate>()
        for (d in 1..7) {
            val past = date.minusDays(d.toLong())
            val pastSaved = prefs.getString("pick_${past.format(dateFmt)}", null) ?: continue
            pastSaved.split(",").forEach { id -> recent.putIfAbsent(id, past) }
        }
        val focus = prefs.getString("ob_focus", null)?.takeIf { it.isNotEmpty() }
        val picks = MigakeruLogic.pickToday(date, recent, graduated(), focus)
        prefs.edit().putString(key, picks.joinToString(",") { it.id }).apply()
        return picks
    }

    // ---------- shared UI helpers ----------

    /** Reading glass: white 62% -> 54%, top edge at 75% white. */
    private fun card(done: Boolean = false): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (done) intArrayOf(Color.argb(205, 0xFF, 0xED, 0xD9), Color.argb(188, 0xFF, 0xE2, 0xC4))
        else intArrayOf(Color.argb(158, 255, 255, 255), Color.argb(138, 255, 255, 255)),
    ).apply {
        cornerRadius = dp(24).toFloat()
        setStroke(dp(1), Color.argb(190, 255, 255, 255))
    }

    /** Input surface: white 86% -> 82%; focus edge is set by the caller. */
    private fun inputSurface(focused: Boolean): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.argb(219, 255, 255, 255), Color.argb(209, 255, 255, 255)),
    ).apply {
        cornerRadius = dp(24).toFloat()
        if (focused) setStroke(dp(2), orange) else setStroke(dp(1), Color.argb(190, 255, 255, 255))
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
            intArrayOf(Color.argb(56, 255, 255, 255), Color.argb(40, 255, 255, 255)),
        ).apply {
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), Color.argb(120, 255, 255, 255))
        }
        pressable(this)
        setOnClickListener { onClick() }
    }

    /** Small light button for use ON a glass card (the 戻す action). */
    private fun lightButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(ink)
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(219, 255, 255, 255), Color.argb(209, 255, 255, 255)),
        ).apply {
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), Color.argb(190, 255, 255, 255))
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
        setTextColor(ink)
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply {
            setColor(Color.rgb(0xE0, 0xF2, 0xFB))
            cornerRadius = dp(12).toFloat()
        }
        setPadding(dp(8), dp(4), dp(8), dp(4))
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
            val selected = i == index
            b.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            // Selected = white pill + ink bold (shape + weight, not colour alone;
            // orange text on white would fall under the 4.5:1 gate).
            b.setTextColor(if (selected) ink else paleBlue)
            b.background = GradientDrawable().apply {
                setColor(if (selected) Color.argb(235, 255, 255, 255) else Color.TRANSPARENT)
                cornerRadius = dp(24).toFloat()
            }
        }
        content.removeAllViews()
        content.addView(
            when (index) {
                0 -> todayScreen()
                1 -> mirrorScreen()
                2 -> guideScreen()
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
                    setColor(if (i == obPage) white else Color.argb(90, 255, 255, 255))
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
            .apply()
        if (quiet) Toast.makeText(this, "あとで、本体で案内するね", Toast.LENGTH_SHORT).show()
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
            text = if (doneMark) "✓" else "●"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(if (doneMark) white else Color.rgb(0x8A, 0x99, 0xA6))
            if (doneMark) typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (doneMark) setColor(orange) else setColor(Color.TRANSPARENT)
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
                        setStroke(dp(2), Color.argb(160, 255, 255, 255))
                        setColor(Color.argb(30, 255, 255, 255))
                    }
                }
                val lp = LinearLayout.LayoutParams(dp(140), dp(140))
                lp.gravity = Gravity.CENTER_HORIZONTAL
                lp.topMargin = dp(32)
                page.addView(ring, lp)
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
                        page.addView(label("毎日、からだ・みため・こころから1つずつ。気になる『からだ』は、ひと通りまんべんなく回るよう厚く出す。", 15f, paleBlue), fullLp(8))
                    }
                    Item.MITAME -> {
                        page.addView(label("「見た目を整えたい」、あなたには。", 17f, paleBlue))
                        page.addView(label("ミガケルが、ぴったり。", 26f, white, bold = true), fullLp(8))
                        page.addView(label("毎日、からだ・みため・こころから1つずつ。気になる『みため』は、ひと通りまんべんなく回るよう厚く出す。", 15f, paleBlue), fullLp(8))
                    }
                    else -> {
                        page.addView(label("まだ決めなくて、大丈夫。", 17f, paleBlue))
                        page.addView(label("様子見からで、ぴったり。", 26f, white, bold = true), fullLp(8))
                        page.addView(label("毎日3つ出るから、気になったものだけやればいい。決まったら、その柱を厚くする。", 15f, paleBlue), fullLp(8))
                    }
                }
                page.addView(primaryButton("次へ") { renderObPage(3) }, fullLp(32))
            }
            3 -> { // アイデンティティ
                page.addView(label("ミガケルは、今日の3つだけを出すアプリ。", 26f, white, bold = true))
                page.addView(label("考えるのはアプリの仕事。あなたは選ばなくていい。", 15f, paleBlue), fullLp(8))
                page.addView(miniRow(Item.KARADA, "筋トレに行く"), fullLp(24))
                page.addView(miniRow(Item.MITAME, "爪を整える"), fullLp(12))
                page.addView(miniRow(Item.KOKORO, "瞑想 10分"), fullLp(12))
                page.addView(primaryButton("次へ") { renderObPage(4) }, fullLp(32))
            }
            4 -> { // 価値：持ち越さない
                page.addView(label("できなかった日は、持ち越さない。", 26f, white, bold = true))
                page.addView(label("昨日の分が、今日に積まれることはない。", 15f, paleBlue), fullLp(8))
                page.addView(label("昨日", 13f, paleBlue), fullLp(24))
                page.addView(label("○ ○ ○", 17f, Color.argb(120, 255, 255, 255)))
                page.addView(label("今日", 13f, paleBlue), fullLp(16))
                page.addView(label("● ○ ○", 17f, orange))
                page.addView(primaryButton("次へ") { renderObPage(5) }, fullLp(32))
            }
            5 -> { // ステップ1
                page.addView(label("開く。今日の3つが出ている。", 26f, white, bold = true))
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
            8 -> { // 試し操作（本物の今日の1つ目）
                val today = LocalDate.now()
                val item = todayPicks(today).first()
                page.addView(label("試しに、タップしてみて。", 26f, white, bold = true))
                page.addView(label("これは本物の今日の1つ目。そのまま記録になる。", 15f, paleBlue), fullLp(8))
                val praise = label("", 15f, white, bold = true).apply { alpha = 0f }
                if (isDone(item.id, today)) trialDoneNow = true
                // Built inline (not miniRow) so the mark can flip in place.
                val mark = TextView(this).apply {
                    text = if (trialDoneNow) "✓" else "●"
                    textSize = 15f
                    gravity = Gravity.CENTER
                    setTextColor(if (trialDoneNow) white else Color.rgb(0x8A, 0x99, 0xA6))
                    if (trialDoneNow) typeface = Typeface.DEFAULT_BOLD
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        if (trialDoneNow) setColor(orange) else setColor(Color.TRANSPARENT)
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
            9 -> { // 期待：卒業
                page.addView(label("続いた習慣は、卒業してバッチになる。", 26f, white, bold = true))
                page.addView(label("身についた証が、静かに残る。", 15f, paleBlue), fullLp(8))
                val badge = TextView(this).apply {
                    text = "卒業"
                    textSize = 26f
                    setTextColor(white)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(orange)
                        setStroke(dp(3), Color.argb(160, 255, 255, 255))
                    }
                }
                val lp = LinearLayout.LayoutParams(dp(120), dp(120))
                lp.gravity = Gravity.CENTER_HORIZONTAL
                lp.topMargin = dp(32)
                page.addView(badge, lp)
                page.addView(primaryButton("次へ") { renderObPage(10) }, fullLp(32))
            }
            else -> { // 接続
                page.addView(label("今日の3つが、待っている。", 26f, white, bold = true))
                page.addView(
                    label(
                        if (prefs.getBoolean("first_success", false)) "さっきの1つは、もう今日の記録に入っている。"
                        else "最初の1つをタップするところから。",
                        15f, paleBlue,
                    ),
                    fullLp(8),
                )
                page.addView(primaryButton("始める") { finishOnboarding() }, fullLp(32))
            }
        }
    }

    // ---------- 今日 ----------

    private fun todayScreen(): ScrollView {
        val today = LocalDate.now()
        val picks = todayPicks(today)
        return scrollPage { page ->
            // v0.4.0: hot-blooded buddy at the top-right; bubble on its left.
            // For now the buddy only greets (time-of-day greeting).
            val greeting = MigakeruLogic.greetingForHour(java.time.LocalTime.now().hour)
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
            page.addView(label("今日の3つ", 26f, white, bold = true))

            // K4: minimal first-run intro (one card, dismissed once, no wizard).
            if (!prefs.getBoolean("intro_done", false)) {
                val intro = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = card()
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                }
                intro.addView(label("はじめに", 17f, ink, bold = true))
                intro.addView(label("・今日やることは3つだけ。タップで完了", 15f, subOnGlass))
                intro.addView(label("・できたら積み重なって、身についたら卒業していく", 15f, subOnGlass))
                intro.addView(label("・できなかった日は、それで終わり。持ち越しなし", 15f, subOnGlass))
                intro.addView(primaryButton("はじめる") {
                    prefs.edit().putBoolean("intro_done", true).apply()
                    showScreen(0)
                }, fullLp(12))
                page.addView(intro, fullLp(16))
            }

            picks.forEach { item ->
                val done = isDone(item.id, today)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = card(done)
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                    contentDescription = if (done) "完了：${item.label}" else "未完了：${item.label}"
                }
                val mark = TextView(this).apply {
                    text = if (done) "✓" else "●"
                    textSize = if (done) 15f else 17f
                    gravity = Gravity.CENTER
                    setTextColor(if (done) white else Color.rgb(0x8A, 0x99, 0xA6))
                    if (done) typeface = Typeface.DEFAULT_BOLD
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        if (done) setColor(orange) else setColor(Color.TRANSPARENT)
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
                }
                row.addView(mark)
                val texts = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, 0, 0)
                }
                val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                chipRow.addView(pillarChip(item.pillar))
                texts.addView(chipRow)
                texts.addView(label(item.label, 17f, if (done) subOnGlass else ink, bold = true))
                row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                pressable(row)
                row.setOnClickListener {
                    val wasDone = isDone(item.id, today)
                    setDone(item.id, today, !wasDone)
                    if (wasDone) {
                        Toast.makeText(this, "「${item.label}」の完了を取り消したよ", Toast.LENGTH_SHORT).show()
                    }
                    showScreen(0)
                }
                page.addView(row, fullLp(16))
            }

            val swapUsed = prefs.getBoolean("swap_${today.format(dateFmt)}", false)
            val doneCount = picks.count { isDone(it.id, today) }
            if (!swapUsed && doneCount < 3) {
                // K5: the button names its target and destination BEFORE the tap.
                val target = picks.first { !isDone(it.id, today) }
                val next = MigakeruLogic.swapCandidate(
                    target.pillar, picks.map { it.id }.toSet(), graduated(), today.dayOfYear,
                )
                if (next != null) {
                    val swap = ghostButton("『${target.label}』を『${next.label}』に変える") {
                        val newIds = picks.map { if (it.id == target.id) next.id else it.id }
                        prefs.edit()
                            .putString("pick_${today.format(dateFmt)}", newIds.joinToString(","))
                            .putBoolean("swap_${today.format(dateFmt)}", true)
                            .apply()
                        Toast.makeText(this, "『${target.label}』を『${next.label}』に変えたよ", Toast.LENGTH_SHORT).show()
                        showScreen(0)
                    }
                    page.addView(swap, fullLp(16))
                }
            }

            // K2: graduation must be visible here, not only in the mirror.
            val readyToday = MigakeruLogic.POOL.filter {
                it.id !in graduated() && MigakeruLogic.isGraduationReady(doneDatesFor(it.id), today)
            }
            readyToday.forEach { item ->
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = card()
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                }
                box.addView(label("「${item.label}」は身についてきたみたい。", 15f, ink, bold = true))
                box.addView(primaryButton("卒業する") {
                    prefs.edit().putStringSet("graduated", graduated() + item.id).apply()
                    Toast.makeText(this, "卒業バッチに入れたよ", Toast.LENGTH_SHORT).show()
                    showScreen(0)
                }, fullLp(12))
                page.addView(box, fullLp(16))
            }

            // K2: near-graduation hint replaces the footer line (aux stays 2).
            val nearest = MigakeruLogic.POOL
                .filter { it.id !in graduated() }
                .map { it to MigakeruLogic.countInWindow(doneDatesFor(it.id), today) }
                .filter { it.second in (MigakeruLogic.GRADUATION_THRESHOLD - 2) until MigakeruLogic.GRADUATION_THRESHOLD }
                .maxByOrNull { it.second }
            val footer = when {
                readyToday.isNotEmpty() -> "卒業の提案が届いてるよ。"
                doneCount == 3 -> "今日はもう十分。よく整えたね。"
                nearest != null -> "「${nearest.first.label}」、あと ${MigakeruLogic.GRADUATION_THRESHOLD - nearest.second} 回で卒業が見えてくる。"
                doneCount == 0 -> "タップで完了。それだけ。"
                else -> "あと ${3 - doneCount} つ。急がなくていい。"
            }
            page.addView(label(footer, 15f, white), fullLp(24))
        }
    }

    // ---------- 鏡 ----------

    private fun mirrorScreen(): ScrollView {
        val today = LocalDate.now()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks(weekOffset.toLong())
        return scrollPage { page ->
            page.addView(label("鏡タイム", 26f, white, bold = true))
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
            var weekTotal = 0
            for (off in 0..6) {
                val d = monday.plusDays(off.toLong())
                val future = d.isAfter(today)
                val count = if (future) 0 else doneCountOn(d)
                weekTotal += count
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
                    row.addView(label("●".repeat(count) + "○".repeat((3 - count).coerceAtLeast(0)), 15f, orange))
                }
                weekCard.addView(row)
            }
            val totalRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
            }
            totalRow.addView(label(if (weekOffset == 0) "今週の積み重ね" else "この週の積み重ね", 13f, subOnGlass))
            totalRow.addView(View(this), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            totalRow.addView(label("$weekTotal 個", 17f, ink, bold = true))
            weekCard.addView(totalRow)
            if (weekTotal == 0 && weekOffset == 0 && doneSet().isEmpty()) {
                weekCard.addView(label("ここに、できた日が並んでいく。", 13f, subOnGlass))
            }
            page.addView(weekCard, fullLp(16))

            val ready = MigakeruLogic.POOL.filter {
                it.id !in graduated() && MigakeruLogic.isGraduationReady(doneDatesFor(it.id), today)
            }
            ready.forEach { item ->
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = card()
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                }
                box.addView(label("「${item.label}」は身についてきたみたい。", 15f, ink, bold = true))
                box.addView(primaryButton("卒業する") {
                    prefs.edit().putStringSet("graduated", graduated() + item.id).apply()
                    Toast.makeText(this, "卒業バッチに入れたよ", Toast.LENGTH_SHORT).show()
                    showScreen(1)
                }, fullLp(12))
                page.addView(box, fullLp(12))
            }

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

    private data class Guide(val title: String, val body: String)

    private val guides = listOf(
        Guide("爪と手", "爪は短く清潔に。手は顔と同じくらい見られている。保湿までが1セット。"),
        Guide("肌の保湿", "洗顔のあと1分以内に保湿。量より「毎日同じ手順」が効く。"),
        Guide("髪の整え方", "寝ぐせは濡らして乾かし直すのが最短。整髪料は米粒ほどから。"),
        Guide("香りは控えめに", "香水は1プッシュ、近くでだけ分かるくらいが「整っている」。"),
        Guide("姿勢", "耳と肩が縦に並ぶ位置。壁に背中をつけて30秒で感覚を戻す。"),
        Guide("歩き方", "視線は少し遠く、歩幅は普段より少しだけ大きく。"),
        Guide("声と目線", "話すときは相手の目を見て、語尾まではっきり。速さはゆっくりめ。"),
        Guide("寝る前1時間", "画面を置いて、照明を落とす。眠りの質は夜の過ごし方で決まる。"),
        Guide("朝の光", "起きたらカーテンを開けて光を浴びる。体内時計のリセットボタン。"),
        Guide("冷水の使い方", "シャワーの最後30秒だけ冷水に。無理のない範囲で目を覚ます。"),
        Guide("スマホとの距離", "通知はまとめて見る。手の届かない場所に置く時間が集中を作る。"),
        Guide("決めたことを守る", "小さく決めて、必ず守る。守れた回数が自信の正体。"),
    )

    private fun guideScreen(): ScrollView = scrollPage { page ->
        page.addView(label("整え図鑑", 26f, white, bold = true))
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
            head.addView(label(g.title, 17f, ink, bold = true))
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
        page.addView(label("わたし", 26f, white, bold = true))
        page.addView(label("これまでの積み重ね：${doneSet().size} 個", 17f, white, bold = true))

        page.addView(label("卒業した習慣", 17f, white, bold = true), fullLp(24))
        val grad = graduated()
        if (grad.isEmpty()) {
            page.addView(label("まだなし。21日で5回できると、ここにバッチが並ぶ。", 13f, paleBlue))
        } else {
            grad.forEach { id ->
                val item = MigakeruLogic.itemById(id) ?: return@forEach
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = card()
                    setPadding(dp(16), dp(12), dp(12), dp(12))
                }
                val dot = View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(orange)
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(8), dp(8))
                }
                box.addView(dot)
                box.addView(label("　卒業　${item.label}", 15f, ink, bold = true))
                box.addView(View(this), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                box.addView(lightButton("戻す") {
                    prefs.edit().putStringSet("graduated", graduated() - id).apply()
                    Toast.makeText(this@MainActivity, "今日の候補に戻したよ", Toast.LENGTH_SHORT).show()
                    showScreen(3)
                })
                page.addView(box, fullLp(12))
            }
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
        page.addView(label("ミガケル v0.3.0", 13f, paleBlue), fullLp(12))
    }
}
