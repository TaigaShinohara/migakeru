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
        assertNonBlank(activity, "今日の3つ", "からだ")
    }

    @Test
    fun todayScreen_firstRun_showsIntroCard() {
        val activity = launch(obDone = true, introDone = false)
        assertNonBlank(activity, "今日の3つ", "はじめに")
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
        assertNonBlank(activity, "整え図鑑", "爪と手")
        shot(activity, "guide")
    }

    @Test
    fun meScreen_rendersNonBlank() {
        val activity = launch(obDone = true)
        clickTab(activity, "わたし")
        assertNonBlank(activity, "卒業した習慣")
        shot(activity, "me")
    }
}
