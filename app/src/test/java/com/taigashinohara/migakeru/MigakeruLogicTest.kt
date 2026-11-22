package com.taigashinohara.migakeru

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MigakeruLogicTest {

    private val wed = LocalDate.of(2026, 11, 18) // Wednesday

    @Test
    fun picksExactlyOnePerPillar() {
        val picks = MigakeruLogic.pickToday(wed, emptyMap(), emptySet())
        assertEquals(3, picks.size)
        assertEquals(
            listOf(Item.KARADA, Item.MITAME, Item.KOKORO),
            picks.map { it.pillar },
        )
    }

    @Test
    fun trainingDayPrefersKintore() {
        val picks = MigakeruLogic.pickToday(wed, emptyMap(), emptySet())
        assertEquals("kintore", picks.first { it.pillar == Item.KARADA }.id)
    }

    @Test
    fun graduatedItemsAreExcluded() {
        val picks = MigakeruLogic.pickToday(wed, emptyMap(), setOf("kintore"))
        assertFalse(picks.any { it.id == "kintore" })
    }

    @Test
    fun recentPicksAreAvoidedWhenPossible() {
        val recent = mapOf("kintore" to wed.minusDays(1))
        // Thursday is not a training day, so rotation applies.
        val thu = wed.plusDays(1)
        val recentThu = MigakeruLogic.POOL
            .filter { it.pillar == Item.KARADA }
            .associate { it.id to thu.minusDays(1) }
        val picks = MigakeruLogic.pickToday(thu, recentThu - "walk", emptySet())
        assertEquals("walk", picks.first { it.pillar == Item.KARADA }.id)
        assertTrue(recent.isNotEmpty())
    }

    @Test
    fun graduationNeedsFiveInTwentyOneDays() {
        val four = (0..3).map { wed.minusDays(it.toLong()) }
        val five = (0..4).map { wed.minusDays(it.toLong()) }
        assertFalse(MigakeruLogic.isGraduationReady(four, wed))
        assertTrue(MigakeruLogic.isGraduationReady(five, wed))
        val old = (0..4).map { wed.minusDays(30L + it) }
        assertFalse(MigakeruLogic.isGraduationReady(old, wed))
    }

    /**
     * K1 regression guard (UX review v0.1.2): the graduation threshold must be
     * REACHABLE under the normal pick rotation. Simulates 28 days of perfect
     * completion exactly the way the app builds its recent map.
     */
    @Test
    fun graduationIsReachableUnderNormalRotation() {
        val start = LocalDate.of(2026, 11, 16) // Monday
        val picksByDate = mutableMapOf<LocalDate, List<Item>>()
        val doneDates = mutableMapOf<String, MutableList<LocalDate>>()
        var kintoreReadyDay = -1
        var otherReadyDay = -1
        for (day in 0 until 28) {
            val date = start.plusDays(day.toLong())
            val recent = mutableMapOf<String, LocalDate>()
            for (back in 1..7) {
                val past = date.minusDays(back.toLong())
                picksByDate[past]?.forEach { recent.putIfAbsent(it.id, past) }
            }
            val picks = MigakeruLogic.pickToday(date, recent, emptySet())
            picksByDate[date] = picks
            picks.forEach { doneDates.getOrPut(it.id) { mutableListOf() }.add(date) }
            if (kintoreReadyDay < 0 &&
                MigakeruLogic.isGraduationReady(doneDates["kintore"] ?: emptyList(), date)
            ) {
                kintoreReadyDay = day
            }
            if (otherReadyDay < 0 && doneDates.any { (id, dates) ->
                    id != "kintore" && MigakeruLogic.isGraduationReady(dates, date)
                }
            ) {
                otherReadyDay = day
            }
        }
        // Workout: Mon/Wed/Fri picks -> 5th completion lands on day 9.
        assertTrue("kintore never ready", kintoreReadyDay in 0..14)
        // Rotating items must also graduate within about a month.
        assertTrue("no rotating item ready within 28 days", otherReadyDay in 0..27)
    }

    @Test
    fun focusPillarServesMostStaleItemFirst() {
        // Onboarding answer effect: the focus pillar cycles evenly.
        val thu = wed.plusDays(1) // not a training day
        val recent = mapOf(
            "nails" to thu.minusDays(1),
            "skin" to thu.minusDays(9),
            "hair" to thu.minusDays(5),
        )
        val picks = MigakeruLogic.pickToday(thu, recent, emptySet(), focusPillar = Item.MITAME)
        // Never-picked items count as most stale -> posture comes first.
        assertEquals("posture", picks.first { it.pillar == Item.MITAME }.id)
    }

    @Test
    fun dotWindowSlidesAndClamps() {
        assertEquals(0, MigakeruLogic.dotWindowStart(3, 2))
        assertEquals(0, MigakeruLogic.dotWindowStart(11, 0))
        assertEquals(0, MigakeruLogic.dotWindowStart(11, 2))
        assertEquals(3, MigakeruLogic.dotWindowStart(11, 5))
        assertEquals(6, MigakeruLogic.dotWindowStart(11, 10))
    }

    @Test
    fun praiseIndexNeverRepeatsPrevious() {
        for (seed in 0..20) {
            val idx = MigakeruLogic.pickPraiseIndex(1, 3, seed)
            assertTrue(idx in 0..2 && idx != 1)
        }
        assertEquals(0, MigakeruLogic.pickPraiseIndex(0, 1, 5))
    }

    @Test
    fun swapExcludesCurrentItems() {
        val next = MigakeruLogic.swapCandidate(Item.MITAME, setOf("nails"), emptySet(), 0)
        assertTrue(next != null && next.id != "nails" && next.pillar == Item.MITAME)
    }

    @Test
    fun greetingCoversAllHoursByTimeOfDay() {
        assertEquals("おはよう！今日も整えていこう！", MigakeruLogic.greetingForHour(5))
        assertEquals("おはよう！今日も整えていこう！", MigakeruLogic.greetingForHour(10))
        assertEquals("こんにちは！調子はどうだい？", MigakeruLogic.greetingForHour(11))
        assertEquals("こんにちは！調子はどうだい？", MigakeruLogic.greetingForHour(16))
        assertEquals("おつかれさま！今日もよくやってる！", MigakeruLogic.greetingForHour(17))
        assertEquals("おつかれさま！今日もよくやってる！", MigakeruLogic.greetingForHour(22))
        assertEquals("夜だな。無理せずいこう。おつかれさま！", MigakeruLogic.greetingForHour(23))
        assertEquals("夜だな。無理せずいこう。おつかれさま！", MigakeruLogic.greetingForHour(0))
        assertEquals("夜だな。無理せずいこう。おつかれさま！", MigakeruLogic.greetingForHour(4))
    }
}

// v0.1.1: design v0.2 §5.3 pool completeness (mitame must have all 5 items).
class MigakeruPoolTest {
    @org.junit.Test
    fun mitamePoolHasAllFiveDesignItems() {
        val mitame = MigakeruLogic.POOL.filter { it.pillar == Item.MITAME }
        org.junit.Assert.assertEquals(5, mitame.size)
        org.junit.Assert.assertNotNull(MigakeruLogic.itemById("clothes"))
    }
}
