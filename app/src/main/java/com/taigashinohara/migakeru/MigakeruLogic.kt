package com.taigashinohara.migakeru

import java.time.DayOfWeek
import java.time.LocalDate

/** One polish item. [pillar] is one of KARADA / MITAME / KOKORO. */
data class Item(val id: String, val pillar: String, val label: String) {
    companion object {
        const val KARADA = "からだ"
        const val MITAME = "みため"
        const val KOKORO = "こころ"
    }
}

object MigakeruLogic {

    val POOL: List<Item> = listOf(
        Item("kintore", Item.KARADA, "筋トレに行く"),
        Item("walk", Item.KARADA, "歩く（目安 8,000歩）"),
        Item("protein", Item.KARADA, "たんぱく質を1品足す"),
        Item("bath", Item.KARADA, "湯船につかる"),
        Item("sleep_prep", Item.KARADA, "早めに寝る支度をする"),
        Item("nails", Item.MITAME, "爪を整える"),
        Item("skin", Item.MITAME, "肌を保湿する"),
        Item("hair", Item.MITAME, "髪を整える"),
        Item("posture", Item.MITAME, "姿勢を正して歩く"),
        Item("clothes", Item.MITAME, "明日の服を1着選んでおく"),
        Item("meditation", Item.KOKORO, "瞑想 10分"),
        Item("journal", Item.KOKORO, "ジャーナルを1行書く"),
        Item("phone_off", Item.KOKORO, "スマホを置いて30分"),
        Item("decide", Item.KOKORO, "決めたことを1つやり切る"),
    )

    fun itemById(id: String): Item? = POOL.firstOrNull { it.id == id }

    /**
     * Pick today's 3 items: one per pillar.
     * Rules (design v0.2 §3): training days (Mon/Wed/Fri) prefer 筋トレ for
     * からだ; an item shown in the last 3 days is avoided when an alternative
     * exists; yesterday's unfinished items are NOT carried over on purpose —
     * selection is purely date + recent picks, never "what you failed".
     * Graduated items are excluded.
     */
    fun pickToday(
        date: LocalDate,
        recentPicks: Map<String, LocalDate>, // itemId -> last picked date
        graduated: Set<String>,
        focusPillar: String? = null, // onboarding answer: cycle this pillar evenly
    ): List<Item> {
        val pillars = listOf(Item.KARADA, Item.MITAME, Item.KOKORO)
        return pillars.map { pillar ->
            val candidates = POOL.filter { it.pillar == pillar && it.id !in graduated }
            require(candidates.isNotEmpty()) { "no candidates for $pillar" }
            if (pillar == Item.KARADA &&
                date.dayOfWeek in listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            ) {
                candidates.firstOrNull { it.id == "kintore" }?.let { return@map it }
            }
            val fresh = candidates.filter { item ->
                val last = recentPicks[item.id]
                last == null || last.isBefore(date.minusDays(3))
            }
            val pool = if (fresh.isNotEmpty()) fresh else candidates
            if (pillar == focusPillar) {
                // The pillar the user said they care about: serve its most
                // long-ago item first, so it cycles through everything evenly.
                return@map pool.minByOrNull { recentPicks[it.id] ?: LocalDate.MIN } ?: pool.first()
            }
            // Deterministic rotation by day-of-year so the choice is stable
            // within a day and varies across days.
            pool[date.dayOfYear % pool.size]
        }
    }

    /** Onboarding window dots: first index of the visible window (skill rule). */
    fun dotWindowStart(total: Int, current: Int, window: Int = 5): Int {
        if (total <= window) return 0
        return (current - window / 2).coerceIn(0, total - window)
    }

    /** Praise rotation: never repeat the previous index. */
    fun pickPraiseIndex(last: Int, size: Int, seed: Int): Int {
        if (size <= 1) return 0
        var i = ((seed % size) + size) % size
        if (i == last) i = (i + 1) % size
        return i
    }

    /** Next candidate for a pillar when the user swaps today's item (once/day). */
    fun swapCandidate(
        pillar: String,
        excludeIds: Set<String>,
        graduated: Set<String>,
        seed: Int,
    ): Item? {
        val candidates = POOL.filter { it.pillar == pillar && it.id !in excludeIds && it.id !in graduated }
        if (candidates.isEmpty()) return null
        return candidates[seed % candidates.size]
    }

    /** Graduation threshold (design v0.4 §4): reachable under the pick rules. */
    const val GRADUATION_THRESHOLD = 5

    /** Completions inside the trailing 21-day window (today included). */
    fun countInWindow(completionDates: List<LocalDate>, today: LocalDate): Int {
        val from = today.minusDays(20)
        return completionDates.count { !it.isBefore(from) && !it.isAfter(today) }
    }

    /** Graduation (design v0.4 §4): 5 completions within the last 21 days. */
    fun isGraduationReady(completionDates: List<LocalDate>, today: LocalDate): Boolean =
        countInWindow(completionDates, today) >= GRADUATION_THRESHOLD
}
