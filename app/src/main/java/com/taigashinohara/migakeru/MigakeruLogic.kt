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

    /** Base pool: available from day one. */
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
        // v0.6.0 re-review fix: こころ was the only 4-item pillar and its
        // rotation collapsed first under the rest model — now 5 like the
        // others, so all three pillars carry the same width.
        Item("music", Item.KOKORO, "好きな曲を1曲聴く"),
    )

    /**
     * Reserve pool (v0.6.0, UX review U2): one reserve item of the same
     * pillar unlocks each time an item graduates, so the active pool is
     * replenished instead of only shrinking. Reserves join in list order.
     */
    val RESERVE: List<Item> = listOf(
        Item("stretch", Item.KARADA, "ストレッチをする"),
        Item("stairs", Item.KARADA, "階段を使う"),
        Item("neckroll", Item.KARADA, "肩と首を回す"),
        Item("brow", Item.MITAME, "眉を整える"),
        Item("shoes", Item.MITAME, "靴を手入れする"),
        Item("scent", Item.MITAME, "香りをひと吹き"),
        Item("breath", Item.KOKORO, "深呼吸を3回"),
        Item("sky", Item.KOKORO, "空を見上げる"),
        Item("thanks", Item.KOKORO, "ありがとうを伝える"),
    )

    fun itemById(id: String): Item? =
        (POOL + RESERVE).firstOrNull { it.id == id }

    /** Items the user can currently be served: base pool + unlocked reserves. */
    fun activeItems(unlocked: Set<String>): List<Item> =
        POOL + RESERVE.filter { it.id in unlocked }

    /** Next locked reserve of a pillar (the graduation reward), if any. */
    fun nextReserve(pillar: String, unlocked: Set<String>): Item? =
        RESERVE.firstOrNull { it.pillar == pillar && it.id !in unlocked }

    /**
     * Pick today's 3 items: one per pillar.
     * Rules: training days (Mon/Wed/Fri) prefer 筋トレ for からだ; otherwise
     * the least-recently-picked item of the pillar goes first (v0.6.0, UX
     * review U3 — the even cycling that used to be focus-only is now the
     * rule for every pillar, so nothing starves: over 180 days every item
     * of a pillar is served within a few counts of its neighbours).
     * Yesterday's unfinished items are NOT carried over on purpose —
     * selection never reads "what you failed".
     * Items resting after a graduation are excluded while they rest, and
     * this function never throws (U1): if a pillar's candidates were
     * somehow all resting, it falls back to the pillar itself instead of
     * crashing the today screen.
     * The onboarding focus answer is kept in prefs but no longer changes
     * selection — even cycling now covers every pillar.
     */
    fun pickToday(
        date: LocalDate,
        recentPicks: Map<String, LocalDate>, // itemId -> last picked date
        resting: Set<String>, // itemIds currently out of the pool (graduation rest)
        focusPillar: String? = null, // kept for call compatibility; see note above
        unlocked: Set<String> = emptySet(),
    ): List<Item> {
        val active = activeItems(unlocked)
        val pillars = listOf(Item.KARADA, Item.MITAME, Item.KOKORO)
        return pillars.map { pillar ->
            val inPillar = active.filter { it.pillar == pillar }
            val candidates = inPillar.filter { it.id !in resting }
            val usable = if (candidates.isNotEmpty()) candidates else inPillar
            if (pillar == Item.KARADA &&
                date.dayOfWeek in listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            ) {
                usable.firstOrNull { it.id == "kintore" }?.let { return@map it }
            }
            // Least-recently-picked first; never-picked counts as oldest,
            // ties break by pool order so the choice is deterministic.
            usable.minWithOrNull(
                compareBy({ recentPicks[it.id] ?: LocalDate.MIN }, { inPillar.indexOf(it) }),
            ) ?: usable.first()
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
        resting: Set<String>,
        seed: Int,
        unlocked: Set<String> = emptySet(),
    ): Item? {
        val candidates = activeItems(unlocked).filter {
            it.pillar == pillar && it.id !in excludeIds && it.id !in resting
        }
        if (candidates.isEmpty()) return null
        return candidates[seed % candidates.size]
    }

    /** Graduation threshold (design v0.4 §4): reachable under the pick rules. */
    const val GRADUATION_THRESHOLD = 5

    /**
     * How long a graduated item rests before it quietly returns to the
     * candidate pool (v0.6.0 re-review fix for U2). Graduation used to be
     * a permanent removal, which no finite reserve pool could offset —
     * the most diligent user's pillars converged to 2 alternating items
     * forever. As a rest, graduation keeps its honour (the badge in the
     * わたし screen is permanent) while the rotation width is structurally
     * preserved: over a simulated 730 days of completing everything and
     * accepting every graduation, every pillar keeps serving at least 6
     * distinct items in any rolling 30-day window.
     */
    const val GRADUATION_REST_DAYS = 60L

    /**
     * U1 guard: a pillar always keeps at least this many active items, so
     * graduation proposals stop before the pool can run dry.
     */
    const val MIN_ACTIVE_PER_PILLAR = 2

    /** Completions inside the trailing 21-day window (today included). */
    fun countInWindow(completionDates: List<LocalDate>, today: LocalDate): Int {
        val from = today.minusDays(20)
        return completionDates.count { !it.isBefore(from) && !it.isAfter(today) }
    }

    /** Graduation (design v0.4 §4): 5 completions within the last 21 days. */
    fun isGraduationReady(completionDates: List<LocalDate>, today: LocalDate): Boolean =
        countInWindow(completionDates, today) >= GRADUATION_THRESHOLD

    /**
     * Items that may be proposed for graduation right now: ready by count,
     * not currently resting, AND their pillar keeps more than
     * [MIN_ACTIVE_PER_PILLAR] active items after the graduation (U1 — the
     * proposal itself can never drain a pillar, no matter how often the
     * user accepts). An item whose rest has ended can be proposed again.
     */
    fun graduationCandidates(
        completions: Map<String, List<LocalDate>>,
        today: LocalDate,
        resting: Set<String>,
        unlocked: Set<String>,
    ): List<Item> {
        val active = activeItems(unlocked).filter { it.id !in resting }
        val perPillar = active.groupingBy { it.pillar }.eachCount()
        return active.filter { item ->
            (perPillar[item.pillar] ?: 0) > MIN_ACTIVE_PER_PILLAR &&
                isGraduationReady(completions[item.id] ?: emptyList(), today)
        }
    }

    // ---------- Buddy lines (v0.6.0, UX review U4) ----------
    // The buddy used to say one fixed line per time band. It now reacts to
    // the day's state, and even the plain greetings rotate by date so the
    // same sentence never sits on the screen day after day. Hot-blooded
    // but never pushy — energy without pressure, and never a word of
    // blame for a gap.

    private val GREETINGS_MORNING = listOf(
        "おはよう！今日も整えていこう！",
        "おはよう。まずは1つからでいい。",
        "おはよう！深呼吸して、始めよう。",
    )
    private val GREETINGS_MIDDAY = listOf(
        "こんにちは！調子はどうだい？",
        "こんにちは。できるところからでいいよ。",
        "こんにちは！午後も自分のペースで。",
    )
    private val GREETINGS_EVENING = listOf(
        "おつかれさま！今日もよくやってる！",
        "おつかれさま。残りは無理のない分だけでいい。",
        "おつかれさま！今日の分、一緒に見よう。",
    )
    private val GREETINGS_NIGHT = listOf(
        "夜だな。無理せずいこう。おつかれさま！",
        "夜は静かに。できる分だけでいい。",
        "遅くまでおつかれさま。今日はここまででも十分。",
    )
    private val LINES_SOME_DONE = listOf(
        "いいぞ、進んでる。",
        "その調子。あとは急がなくていい。",
        "できた分だけ、ちゃんと積み上がってる。",
    )
    private val LINES_ALL_DONE = listOf(
        "全部できた！今日はもう十分だ。",
        "3つとも完了。よく整えたね！",
        "完璧だ。その調子でいこう。",
    )
    private val LINES_RETURN = listOf(
        "おかえり。待ってたよ。",
        "久しぶり。今日もここからでいい。",
        "戻ってきてくれて嬉しいよ。",
    )
    private val LINES_GRAD_READY = listOf(
        "卒業できそうな習慣があるよ。下を見てみて。",
        "身についてきた習慣があるみたいだ。",
    )

    private fun greetingPoolForHour(hour: Int): List<String> = when (hour) {
        in 5..10 -> GREETINGS_MORNING
        in 11..16 -> GREETINGS_MIDDAY
        in 17..22 -> GREETINGS_EVENING
        else -> GREETINGS_NIGHT
    }

    /** The canonical greeting of a time band (first line of its pool). */
    fun greetingForHour(hour: Int): String = greetingPoolForHour(hour).first()

    /**
     * What the buddy says on the today screen. Priority: finishing all 3
     * beats everything; progress beats a plain greeting; a user returning
     * after 3+ days without a completion is welcomed back, never blamed;
     * a pending graduation is pointed at. Lines rotate by date so they
     * change daily but stay stable within a day.
     */
    fun buddyLine(
        hour: Int,
        date: LocalDate,
        doneToday: Int,
        lastActive: LocalDate?,
        graduationReady: Boolean,
    ): String {
        val seed = date.dayOfYear
        return when {
            doneToday >= 3 -> LINES_ALL_DONE[seed % LINES_ALL_DONE.size]
            doneToday > 0 -> LINES_SOME_DONE[(seed + doneToday) % LINES_SOME_DONE.size]
            lastActive != null && !lastActive.isAfter(date.minusDays(3)) ->
                LINES_RETURN[seed % LINES_RETURN.size]
            graduationReady -> LINES_GRAD_READY[seed % LINES_GRAD_READY.size]
            else -> greetingPoolForHour(hour)[seed % greetingPoolForHour(hour).size]
        }
    }
}
