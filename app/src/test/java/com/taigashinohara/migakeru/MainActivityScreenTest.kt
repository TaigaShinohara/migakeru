package com.taigashinohara.migakeru

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pre-delivery UI verification on the JVM (docs/UI_VERIFICATION.md).
 *
 * The build VM has no emulator/KVM, so screens are verified here instead:
 * Robolectric launches the real activity with the real layout pass, the
 * structural assertions catch the v0.4.2 failure shape (a content area
 * measuring 0 high = blank screen), and Roborazzi goldens catch visual
 * regressions of the redesign.
 *
 * Goldens vs structural-only: java.time (LocalDate.now) is NOT
 * shadowed by Robolectric — it reads the real clock — so screens that
 * render the date (today, mirror) cannot have stable goldens. Those
 * are guarded by the structural assertions above (the actual v0.4.2
 * blank-screen shape). Screens with static content (onboarding entry,
 * guide, me) carry Roborazzi goldens for visual regression.
 *
 * Note: no RoborazziRule — in Roborazzi 1.40 the rule is built around
 * Espresso/Compose capture roots and rejects plain View captures
 * ("captureRoot is required for LastImage"). The core captureRoboImage()
 * processes record/verify itself from the roborazzi.test.* properties.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h915dp")
class MainActivityScreenTest {

    /** Goldens live in the source tree so they are committed and verify
     *  runs compare against them. The 2% change threshold absorbs the
     *  flame buddy's animation shimmer; a blank screen is a ~100% diff. */
    private val shotOptions = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.02f),
    )

    private fun shot(activity: MainActivity, name: String) {
        activity.window.decorView.captureRoboImage(
            File("src/test/screenshots/$name.png"),
            shotOptions,
        )
    }

    private fun seedPrefs(obDone: Boolean, introDone: Boolean) {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("migakeru", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("ob_done", obDone)
            .putBoolean("intro_done", introDone)
            .apply()
    }

    private fun launch(obDone: Boolean, introDone: Boolean = true): MainActivity {
        // Pin the sandbox clock BEFORE the activity reads the date: the
        // scheduler starts at real now, and LocalDate.now() follows it.
        // Fixed point: Tuesday 2026-11-24 09:00 JST (morning greeting,
        // non-training weekday picks, mirror week of 11/23-11/29).
        val fixedMillis = java.time.ZonedDateTime.of(
            2026, 11, 24, 9, 0, 0, 0, java.time.ZoneId.of("Asia/Tokyo"),
        ).toInstant().toEpochMilli()
        Robolectric.getForegroundThreadScheduler().advanceTo(fixedMillis)
        seedPrefs(obDone, introDone)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        settle(activity.window.decorView)
        return activity
    }

    /** Run posted layout/animation work, then pin any alpha the staggered
     *  onboarding fade-in may have left mid-flight. */
    private fun settle(root: View) {
        Robolectric.getForegroundThreadScheduler().advanceBy(3, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        fun pin(v: View) {
            v.alpha = 1f
            if (v is ViewGroup) for (i in 0 until v.childCount) pin(v.getChildAt(i))
        }
        pin(root)
    }

    private fun allViews(root: View, out: MutableList<View> = mutableListOf()): List<View> {
        out.add(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) allViews(root.getChildAt(i), out)
        return out
    }

    private fun texts(root: View): List<String> =
        allViews(root).filterIsInstance<TextView>().mapNotNull { it.text?.toString() }

    /** The v0.4.2 blank-screen guard: something must actually be laid out. */
    private fun assertNonBlank(activity: MainActivity, vararg expected: String) {
        val root = activity.window.decorView
        assertTrue("decor view has no height", root.height > 0)
        val scroll = allViews(root).filterIsInstance<ScrollView>().firstOrNull()
        assertTrue("no ScrollView in tree", scroll != null)
        assertTrue("scroll view measured 0 high (blank screen shape)", scroll!!.height > 0)
        val page = scroll.getChildAt(0) as? ViewGroup
        assertTrue("page has no children", page != null && page.childCount > 0)
        assertTrue("page measured 0 high (blank screen shape)", page!!.height > 0)
        val found = texts(root)
        assertTrue("no text rendered at all", found.isNotEmpty())
        for (e in expected) {
            assertTrue("missing text '$e' (found: $found)", found.any { it.contains(e) })
        }
    }

    private fun clickTab(activity: MainActivity, name: String) {
        val root = activity.window.decorView
        val btn = allViews(root).filterIsInstance<Button>().firstOrNull { it.text.toString() == name }
        assertTrue("tab button '$name' not found", btn != null)
        btn!!.performClick()
        settle(root)
    }

    @Test
    fun onboardingEntry_rendersNonBlank() {
        val activity = launch(obDone = false)
        assertNonBlank(activity, "男磨きは、モテるためじゃない。")
        shot(activity, "onboarding_entry")
    }

    @Test
    fun todayScreen_rendersNonBlank() {
        val activity = launch(obDone = true)
        assertNonBlank(activity, "マイチャレンジ", "からだ", "ポイント")
    }

    /** v0.12.0: the accumulation greets with the running total.
     *  v0.14.0: on 今日 it is a one-line pride strip (the mountain
     *  itself lives on わたし). Dates are seeded relative to the real
     *  today: java.time reads the wall clock under Robolectric, not
     *  the pinned scheduler. */
    @Test
    fun todayScreen_showsPrideStrip() {
        val today = java.time.LocalDate.now()
        fun key(id: String, d: java.time.LocalDate) = "$id@$d"
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("migakeru", Context.MODE_PRIVATE)
            .edit()
            .putStringSet(
                "done",
                setOf(
                    key("walk", today),
                    key("skin", today.minusDays(1)),
                    key("nails", today.minusDays(4)),
                ),
            )
            .apply()
        val activity = launch(obDone = true)
        assertNonBlank(activity, "累計 3 回", "できた")
    }

    @Test
    fun todayScreen_firstRun_showsIntroCard() {
        val activity = launch(obDone = true, introDone = false)
        assertNonBlank(activity, "マイチャレンジ", "はじめに")
    }

    /** v0.7.0: the catalog is reached from 今日 and lists the challenges. */
    @Test
    fun catalogScreen_rendersNonBlank() {
        val activity = launch(obDone = true)
        val root = activity.window.decorView
        val btn = allViews(root).filterIsInstance<Button>()
            .firstOrNull { it.text.toString() == "チャレンジを選ぶ・変える" }
        assertTrue("catalog button not found", btn != null)
        btn!!.performClick()
        settle(root)
        assertNonBlank(activity, "チャレンジ", "セット中", "筋トレに行く")
        shot(activity, "catalog")
    }

    /** v0.10.0: a genre chip filters the catalog to that shelf. */
    @Test
    fun catalogGenre_filtersToShelf() {
        val activity = launch(obDone = true)
        val root = activity.window.decorView
        val btn = allViews(root).filterIsInstance<Button>()
            .firstOrNull { it.text.toString() == "チャレンジを選ぶ・変える" }
        assertTrue("catalog button not found", btn != null)
        btn!!.performClick()
        settle(root)
        val chip = allViews(root).filterIsInstance<TextView>()
            .firstOrNull { it.text.toString() == "メンタル系" }
        assertTrue("genre chip not found", chip != null)
        chip!!.performClick()
        settle(root)
        assertNonBlank(activity, "メンタル系", "ありがとうを伝える")
        shot(activity, "catalog_genre")
    }

    @Test
    fun mirrorScreen_rendersNonBlank() {
        val activity = launch(obDone = true)
        clickTab(activity, "鏡")
        assertNonBlank(activity, "鏡タイム")
    }

    @Test
    fun guideScreen_rendersNonBlank() {
        val activity = launch(obDone = true)
        clickTab(activity, "図鑑")
        // v0.11.2: pillar sections organise the shelf.
        assertNonBlank(activity, "整え図鑑", "からだ", "みため", "こころ", "爪と手")
        shot(activity, "guide")
    }

    /** v0.11.2: a guide page can set its challenge on the spot. */
    @Test
    fun guideScreen_canSetChallengeFromGuide() {
        val activity = launch(obDone = true)
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("migakeru", Context.MODE_PRIVATE)
        // One set challenge in three slots: room to add from the guide.
        prefs.edit().putStringSet("ch_set", setOf("walk")).apply()
        clickTab(activity, "図鑑")
        val root = activity.window.decorView
        val box = allViews(root).firstOrNull { it.contentDescription?.toString() == "爪と手" }
        assertTrue("guide box not found", box != null)
        box!!.performClick()
        settle(root)
        val setBtn = allViews(root).filterIsInstance<Button>()
            .firstOrNull { it.text.toString() == "このチャレンジをセットする" }
        assertTrue("set button not found", setBtn != null)
        setBtn!!.performClick()
        settle(root)
        val set = prefs.getStringSet("ch_set", emptySet()) ?: emptySet()
        assertTrue("nails not added to set: $set", "nails" in set)
        assertNonBlank(activity, "セット中")
    }

    @Test
    fun meScreen_rendersNonBlank() {
        val activity = launch(obDone = true)
        clickTab(activity, "わたし")
        assertNonBlank(activity, "わたしの積み重ね", "卒業した習慣", "ポイント")
        shot(activity, "me")
    }

    /** v0.13.1: opening the app must land on the doing — the checklist
     *  comes first in the page's reading order. v0.14.0: the sky card
     *  follows it, then the one-line pride strip (the mountain itself
     *  moved to わたし). */
    @Test
    fun todayScreen_checklistFirstSkyThenStrip() {
        val activity = launch(obDone = true)
        val found = texts(activity.window.decorView)
        val listAt = found.indexOfFirst { it.contains("マイチャレンジ") }
        val skyAt = found.indexOfFirst { it.contains("今日の空") }
        val stripAt = found.indexOfFirst { it.contains("積み重ねは、ここから") || it.contains("累計") }
        assertTrue("checklist heading missing: $found", listAt >= 0)
        assertTrue("sky card missing: $found", skyAt > listAt)
        assertTrue("pride strip must close the page: $found", stripAt > skyAt)
        shot(activity, "today")
    }

    /** v0.13.0: with the permission granted and a rainy cache seeded,
     *  the sky card offers one indoor pick; tapping adds it as a bonus
     *  row outside the user's set. */
    @Test
    fun skyCard_offersPickAndAddsBonus() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        val prefs = app.getSharedPreferences("migakeru", Context.MODE_PRIVATE)
        val today = java.time.LocalDate.now()
        SkyWeather.writeCache(
            prefs, today,
            MigakeruLogic.DaySky(61, 18.0, 10.0, 2.0, 80, 12.0),
        )
        val activity = launch(obDone = true)
        assertNonBlank(activity, "今日の空", "雨・18℃", "室内でできるやつを置いといたよ。")
        val root = activity.window.decorView
        val addBtn = allViews(root).filterIsInstance<Button>()
            .firstOrNull { it.text.toString().startsWith("＋ ") }
        assertTrue("sky add button not found (texts: ${texts(root)})", addBtn != null)
        addBtn!!.performClick()
        settle(root)
        assertNonBlank(activity, "空のおすすめ", "おすすめは、今日の分に入ってるよ。")
        val bonus = prefs.getStringSet("sky_bonus", emptySet()) ?: emptySet()
        assertTrue("bonus not stored: $bonus", bonus.any { it.endsWith("@$today") })
    }

    /** v0.13.0: settlement folds a done sky bonus in as pure gain and a
     *  missed one as exactly zero — the bonus can never cost points. */
    @Test
    fun skyBonus_settlesAsPureGain() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("migakeru", Context.MODE_PRIVATE)
        val today = java.time.LocalDate.now()
        val fmt = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
        fun key(id: String, d: java.time.LocalDate) = "$id@${d.format(fmt)}"
        prefs.edit()
            .putString("score_settled_until", today.minusDays(3).format(fmt))
            .putStringSet(
                "sky_bonus",
                setOf(key("plank", today.minusDays(2)), key("plank", today.minusDays(1))),
            )
            .putStringSet("done", setOf(key("plank", today.minusDays(1))))
            .apply()
        launch(obDone = true)
        // Only yesterday's bonus was done; the missed one adds nothing
        // and subtracts nothing. No set history exists, so the set side
        // contributes zero too.
        assertTrue(
            "score_total should be exactly the done bonus's points",
            prefs.getInt("score_total", -1) == MigakeruLogic.pointsOf("plank"),
        )
    }
}
