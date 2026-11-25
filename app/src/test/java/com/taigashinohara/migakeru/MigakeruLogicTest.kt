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

    // ---- v0.6.0: UX review v0.5.1 fixes (U1/U2/U3/U4) ----

    /**
     * U1+U2 regression guard, rest model (v0.6.0 re-review fix): simulate
     * a full year of a maximally diligent user — completes everything,
     * accepts every graduation proposal (recomputed after each acceptance,
     * exactly like the app) — the path that crashed the old build on day
     * 21 and, with permanent graduation, converged to 2 alternating items
     * per pillar. The app must keep serving 3 items, never throw, never
     * let a pillar drop below MIN_ACTIVE_PER_PILLAR active items, and —
     * the metric the re-review demanded — every pillar must keep serving
     * at least 4 distinct items in any rolling 30-day window.
     */
    @Test
    fun aYearOfAcceptingEveryGraduationKeepsRotationWidth() {
        val start = LocalDate.of(2026, 11, 16) // Monday
        val recent = mutableMapOf<String, LocalDate>()
        val doneDates = mutableMapOf<String, MutableList<LocalDate>>()
        val restUntil = mutableMapOf<String, LocalDate>()
        var unlocked = emptySet<String>()
        var graduations = 0
        val served = mutableMapOf<String, MutableList<Pair<LocalDate, String>>>()
        fun restingNow(date: LocalDate): Set<String> =
            restUntil.filterValues { date.isBefore(it) }.keys
        for (day in 0 until 365) {
            val date = start.plusDays(day.toLong())
            val picks = MigakeruLogic.pickToday(date, recent, restingNow(date), null, unlocked)
            assertEquals("day $day must serve 3 items", 3, picks.size)
            picks.forEach {
                recent[it.id] = date
                doneDates.getOrPut(it.id) { mutableListOf() }.add(date)
                served.getOrPut(it.pillar) { mutableListOf() }.add(date to it.id)
            }
            val completions = doneDates.mapValues { it.value.toList() }
            var candidates = MigakeruLogic.graduationCandidates(completions, date, restingNow(date), unlocked)
            while (candidates.isNotEmpty()) {
                val c = candidates.first()
                restUntil[c.id] = date.plusDays(MigakeruLogic.GRADUATION_REST_DAYS)
                graduations++
                MigakeruLogic.nextReserve(c.pillar, unlocked)?.let { unlocked = unlocked + it.id }
                candidates = MigakeruLogic.graduationCandidates(completions, date, restingNow(date), unlocked)
            }
            for (pillar in listOf(Item.KARADA, Item.MITAME, Item.KOKORO)) {
                val active = MigakeruLogic.activeItems(unlocked)
                    .count { it.pillar == pillar && it.id !in restingNow(date) }
                assertTrue(
                    "day $day: $pillar dropped to $active active items",
                    active >= MigakeruLogic.MIN_ACTIVE_PER_PILLAR,
                )
            }
            if (day >= 60) {
                val from = date.minusDays(29)
                for (pillar in listOf(Item.KARADA, Item.MITAME, Item.KOKORO)) {
                    val distinct = served[pillar].orEmpty()
                        .filter { !it.first.isBefore(from) }
                        .map { it.second }.toSet().size
                    assertTrue(
                        "day $day: $pillar served only $distinct distinct items in 30 days",
                        distinct >= 4,
                    )
                }
            }
        }
        assertTrue("expected the diligent user to graduate often", graduations >= 10)
        assertTrue("expected reserves to unlock", unlocked.isNotEmpty())
    }

    /** v0.6.0: all three pillars carry the same width (こころ joined at 5). */
    @Test
    fun everyPillarHasFiveBaseItems() {
        for (pillar in listOf(Item.KARADA, Item.MITAME, Item.KOKORO)) {
            assertEquals(5, MigakeruLogic.POOL.count { it.pillar == pillar })
        }
    }

    /** U1 belt-and-braces: even with everything graduated, no throw. */
    @Test
    fun pickTodayNeverThrowsWhenEverythingIsGraduated() {
        val all = (MigakeruLogic.POOL + MigakeruLogic.RESERVE).map { it.id }.toSet()
        val picks = MigakeruLogic.pickToday(wed, emptyMap(), all, null, all)
        assertEquals(3, picks.size)
    }

    /**
     * U3 regression guard: over 180 days with no graduations, every item
     * of the rotating pillars must actually show up in proportion — the
     * old date-modulo rotation served 髪を整える only 3 times in 180 days.
     */
    @Test
    fun rotationServesEveryItemEvenlyOver180Days() {
        val start = LocalDate.of(2026, 11, 16)
        val recent = mutableMapOf<String, LocalDate>()
        val counts = mutableMapOf<String, Int>()
        for (day in 0 until 180) {
            val date = start.plusDays(day.toLong())
            MigakeruLogic.pickToday(date, recent, emptySet()).forEach {
                recent[it.id] = date
                counts[it.id] = (counts[it.id] ?: 0) + 1
            }
        }
        for (pillar in listOf(Item.MITAME, Item.KOKORO)) {
            val items = MigakeruLogic.POOL.filter { it.pillar == pillar }
            val max = items.maxOf { counts[it.id] ?: 0 }
            for (item in items) {
                val c = counts[item.id] ?: 0
                assertTrue("$pillar/${item.id} served only $c times (max $max)", c * 3 >= max)
            }
        }
        assertTrue("髪を整える starved: ${counts["hair"]}", (counts["hair"] ?: 0) >= 20)
        // からだ on non-training days rotates too (kintore owns Mon/Wed/Fri).
        for (item in MigakeruLogic.POOL.filter { it.pillar == Item.KARADA && it.id != "kintore" }) {
            assertTrue("からだ/${item.id} served only ${counts[item.id]}", (counts[item.id] ?: 0) >= 8)
        }
    }

    /** U2: reserves unlock in order, per pillar, and run out cleanly. */
    @Test
    fun reservesUnlockInOrderPerPillar() {
        var unlocked = emptySet<String>()
        val order = mutableListOf<String>()
        while (true) {
            val next = MigakeruLogic.nextReserve(Item.KOKORO, unlocked) ?: break
            order.add(next.id)
            unlocked = unlocked + next.id
        }
        assertEquals(listOf("breath", "sky", "thanks"), order)
        assertEquals(null, MigakeruLogic.nextReserve(Item.KOKORO, unlocked))
        // Other pillars are unaffected by こころ unlocks.
        assertEquals("stretch", MigakeruLogic.nextReserve(Item.KARADA, unlocked)?.id)
    }

    /** U1 guard at the proposal level: the last 2 of a pillar are never proposed. */
    @Test
    fun graduationProposalStopsBeforePillarRunsDry() {
        // こころ has 5 base items; rest 3 away, make a 4th ready:
        // only 1 active would remain after, so no proposal may come out.
        val resting = setOf("meditation", "journal", "decide")
        val completions = mapOf("phone_off" to (0..4).map { wed.minusDays(it.toLong()) })
        val candidates = MigakeruLogic.graduationCandidates(completions, wed, resting, emptySet())
        assertTrue(candidates.none { it.pillar == Item.KOKORO })
        // With 3 active remaining it IS proposed.
        val candidates2 = MigakeruLogic.graduationCandidates(completions, wed, setOf("meditation", "journal"), emptySet())
        assertTrue(candidates2.any { it.id == "phone_off" })
    }

    /** U4: the buddy reacts to state instead of only greeting. */
    @Test
    fun buddyLineReactsToState() {
        val day = LocalDate.of(2026, 11, 24)
        // All done beats everything.
        val allDone = MigakeruLogic.buddyLine(9, day, 3, day.minusDays(10), graduationReady = true)
        assertTrue(allDone.contains("十分") || allDone.contains("完了") || allDone.contains("完璧"))
        // Some progress beats a plain greeting.
        val some = MigakeruLogic.buddyLine(9, day, 1, day.minusDays(1), graduationReady = false)
        assertFalse(some.startsWith("おはよう"))
        // Returning after a gap: welcomed, never blamed.
        val back = MigakeruLogic.buddyLine(9, day, 0, day.minusDays(5), graduationReady = false)
        assertTrue(back.contains("おかえり") || back.contains("久しぶり") || back.contains("戻って"))
        // A 2-day gap is not a "return" yet: plain greeting family.
        val plain = MigakeruLogic.buddyLine(9, day, 0, day.minusDays(2), graduationReady = false)
        assertTrue(plain.startsWith("おはよう"))
        // Graduation pending is pointed at when nothing else applies.
        val grad = MigakeruLogic.buddyLine(9, day, 0, day.minusDays(1), graduationReady = true)
        assertTrue(grad.contains("卒業") || grad.contains("身について"))
        // Greetings rotate across days instead of one fixed sentence.
        val lines = (0..5).map {
            MigakeruLogic.buddyLine(9, day.plusDays(it.toLong()), 0, null, graduationReady = false)
        }
        assertTrue("greetings did not rotate: $lines", lines.toSet().size >= 2)
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
