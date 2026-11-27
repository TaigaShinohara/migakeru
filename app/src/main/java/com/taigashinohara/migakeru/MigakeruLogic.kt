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

    /**
     * v0.9.0: the catalog doubles (user's request) — 24 more challenges,
     * 8 per pillar, appended after the original 24 so existing order
     * (and any set built from it) is untouched.
     */
    val EXTRA: List<Item> = listOf(
        Item("squat", Item.KARADA, "スクワット10回"),
        Item("pushup", Item.KARADA, "腕立て5回"),
        Item("plank", Item.KARADA, "プランク30秒"),
        Item("radio", Item.KARADA, "ラジオ体操をする"),
        Item("water", Item.KARADA, "水をコップ1杯飲む"),
        Item("abs", Item.KARADA, "腹筋10回"),
        Item("weigh", Item.KARADA, "体重をはかる"),
        Item("eyes", Item.KARADA, "目を休ませる"),
        Item("teeth", Item.MITAME, "歯を丁寧にみがく"),
        Item("smile", Item.MITAME, "笑顔を3回つくる"),
        Item("sunscreen", Item.MITAME, "日焼け止めを塗る"),
        Item("lips", Item.MITAME, "唇をケアする"),
        Item("mirror_check", Item.MITAME, "鏡で全身をチェック"),
        Item("deodorant", Item.MITAME, "汗・においケアをする"),
        Item("accessory", Item.MITAME, "アクセサリーを1つ着ける"),
        Item("iron", Item.MITAME, "服のしわを伸ばす"),
        Item("reading", Item.KOKORO, "本を10分読む"),
        Item("tidy", Item.KOKORO, "5分だけ片づける"),
        Item("message", Item.KOKORO, "誰かにひと言送る"),
        Item("laugh", Item.KOKORO, "声を出して笑う"),
        Item("sing", Item.KOKORO, "歌を1曲歌う"),
        Item("tea", Item.KOKORO, "お茶をゆっくり飲む"),
        Item("declare", Item.KOKORO, "目標を声に出す"),
        Item("self_praise", Item.KOKORO, "自分を1つ褒める"),
    )

    fun itemById(id: String): Item? =
        CATALOG.firstOrNull { it.id == id }

    /** Items the user can currently be served: base pool + unlocked reserves. */
    fun activeItems(unlocked: Set<String>): List<Item> =
        POOL + RESERVE.filter { it.id in unlocked }

    // ---------- v0.7.0: チャレンジ＆ポイント (user's new system) ----------
    // The user picks their own challenges from the whole catalog, does
    // them daily, and earns each challenge's points. A missed challenge
    // costs a flat −1 regardless of its points (user's rule, 2026-11-25).
    // Keeping every set challenge going unlocks more slots.

    /** The full selectable catalog: base pool + former reserves + v0.9.0 extras. */
    val CATALOG: List<Item> = POOL + RESERVE + EXTRA

    /**
     * Points per challenge, by how hard it is to sustain daily
     * (user's rule: harder to keep up = more points). 1..5.
     */
    val POINTS: Map<String, Int> = mapOf(
        "kintore" to 5, "walk" to 4, "protein" to 2, "bath" to 2, "sleep_prep" to 3,
        "stretch" to 2, "stairs" to 2, "neckroll" to 1,
        "nails" to 2, "skin" to 1, "hair" to 1, "posture" to 3, "clothes" to 2,
        "brow" to 2, "shoes" to 3, "scent" to 1,
        "meditation" to 3, "journal" to 2, "phone_off" to 4, "decide" to 4, "music" to 1,
        "breath" to 1, "sky" to 1, "thanks" to 2,
        // v0.9.0 extras.
        "squat" to 2, "pushup" to 3, "plank" to 3, "radio" to 2, "water" to 1,
        "abs" to 2, "weigh" to 1, "eyes" to 1,
        "teeth" to 1, "smile" to 2, "sunscreen" to 2, "lips" to 1, "mirror_check" to 2,
        "deodorant" to 2, "accessory" to 1, "iron" to 2,
        "reading" to 2, "tidy" to 2, "message" to 2, "laugh" to 1, "sing" to 2,
        "tea" to 1, "declare" to 3, "self_praise" to 2,
    )

    fun pointsOf(id: String): Int = POINTS[id] ?: 1

    /**
     * v0.7.1: each challenge gets its own colour (0xRRGGBB) and a short
     * cheer / benefit line, so picking from the catalog feels fun instead
     * of administrative. Colours are per challenge, not per pillar — the
     * catalog is meant to be colourful.
     */
    val COLORS: Map<String, Int> = mapOf(
        "kintore" to 0xEF5350, "walk" to 0x66BB6A, "protein" to 0xFFCA28, "bath" to 0x42A5F5,
        "sleep_prep" to 0x7E57C2, "stretch" to 0x26A69A, "stairs" to 0xFF7043, "neckroll" to 0x5C6BC0,
        "nails" to 0xEC407A, "skin" to 0x4FC3F7, "hair" to 0xAB47BC, "posture" to 0x9CCC65,
        "clothes" to 0xFFA726, "brow" to 0xBCAAA4, "shoes" to 0xFF8A65, "scent" to 0xBA68C8,
        "meditation" to 0x26C6DA, "journal" to 0xFFEE58, "phone_off" to 0x7C4DFF, "decide" to 0xFF5252,
        "music" to 0xEA80FC, "breath" to 0x80DEEA, "sky" to 0x29B6F6, "thanks" to 0xF06292,
        // v0.9.0 extras — all distinct from the first 24 and each other.
        "squat" to 0x8D6E63, "pushup" to 0x78909C, "plank" to 0xD4E157, "radio" to 0xFFD740,
        "water" to 0x40C4FF, "abs" to 0xFFAB40, "weigh" to 0xB2FF59, "eyes" to 0xA7FFEB,
        "teeth" to 0xECEFF1, "smile" to 0xFFCC80, "sunscreen" to 0xFFE082, "lips" to 0xF48FB1,
        "mirror_check" to 0x90CAF9, "deodorant" to 0xA5D6A7, "accessory" to 0xCE93D8, "iron" to 0xB39DDB,
        "reading" to 0xFFECB3, "tidy" to 0xC5E1A5, "message" to 0x81D4FA, "laugh" to 0xFFF59D,
        "sing" to 0xE1BEE7, "tea" to 0xA1887F, "declare" to 0xEF9A9A, "self_praise" to 0xF8BBD0,
    )

    fun colorOf(id: String): Int = COLORS[id] ?: 0xD4AF37

    /** One cheer / benefit line per challenge, shown on its catalog card. */
    val TAGLINES: Map<String, String> = mapOf(
        "kintore" to "体が変わると、鏡が楽しくなる",
        "walk" to "歩いた日は、夜ぐっすり",
        "protein" to "筋肉の材料を、毎日少し",
        "bath" to "一日の疲れを、湯で流す",
        "sleep_prep" to "いい明日は、夜の支度から",
        "stretch" to "ほぐれた体は、動きが軽い",
        "stairs" to "日常がそのまま運動になる",
        "neckroll" to "こりが取れると、顔もすっきり",
        "nails" to "手元が整うと、所作が綺麗に",
        "skin" to "触りたくなる肌は、毎日の1分",
        "hair" to "髪が決まると、一日が決まる",
        "posture" to "背筋ひとつで、見え方が変わる",
        "clothes" to "朝の迷いが、ゼロになる",
        "brow" to "眉で顔の印象は、大きく変わる",
        "shoes" to "足元が綺麗な人は、信頼される",
        "scent" to "近づいた時に、ふわっと好印象",
        "meditation" to "頭のざわつきが、静かになる",
        "journal" to "書くと、気持ちが整う",
        "phone_off" to "自分の時間が、取り戻せる",
        "decide" to "「できた」が、自信に変わる",
        "music" to "1曲で、気分は切り替わる",
        "breath" to "3回で、心が落ち着く",
        "sky" to "見上げるだけで、視界が広がる",
        "thanks" to "伝えた分だけ、関係が温まる",
        // v0.9.0 extras.
        "squat" to "小さな負荷が、脚を作る",
        "pushup" to "胸と腕に、効く実感",
        "plank" to "体幹が、姿勢を支える",
        "radio" to "全身が、目覚める",
        "water" to "うるおいは、内側から",
        "abs" to "お腹まわりが、引き締まる",
        "weigh" to "数字が見えると、整えやすい",
        "eyes" to "遠くを見て、目をリセット",
        "teeth" to "口元が清潔だと、笑顔に自信",
        "smile" to "笑顔は、練習で育つ",
        "sunscreen" to "未来の肌を、今日守る",
        "lips" to "唇がうるおうと、顔が明るい",
        "mirror_check" to "出かける前の、30秒",
        "deodorant" to "近くても、安心な自分に",
        "accessory" to "1つの小物で、印象が締まる",
        "iron" to "しわのない服は、それだけで清潔感",
        "reading" to "10分で、頭に新しい景色",
        "tidy" to "空間が整うと、心も整う",
        "message" to "ひと言が、つながりを温める",
        "laugh" to "笑った分だけ、軽くなる",
        "sing" to "歌うと、呼吸も気分も深くなる",
        "tea" to "温かい一杯が、ひと息になる",
        "declare" to "口に出すと、体が動き出す",
        "self_praise" to "褒めた分だけ、自分が味方になる",
    )

    fun taglineOf(id: String): String = TAGLINES[id] ?: ""

    /**
     * v0.10.0: genres — cross-cutting shelves over the catalog so the
     * user can browse by angle (quick wins, movement, what health data
     * could auto-check, mental care, grooming, morning, night) instead
     * of only by pillar (user's request, 2026-11-27). A challenge may
     * sit on several shelves; together they cover the whole catalog.
     */
    data class Genre(val id: String, val title: String, val caption: String, val itemIds: List<String>)

    val GENRES: List<Genre> = listOf(
        Genre(
            "quick", "すぐできる系", "1ポイントの軽いものだけ。1分で終わる日にも",
            listOf(
                "neckroll", "skin", "hair", "scent", "music", "breath", "sky",
                "water", "weigh", "eyes", "teeth", "lips", "accessory", "laugh", "tea",
            ),
        ),
        Genre(
            "morning", "朝にいい系", "朝のひと手間で、一日の滑り出しをよくする",
            listOf("sky", "water", "radio", "teeth", "mirror_check", "protein", "stretch", "breath"),
        ),
        Genre(
            "move", "うごかす系", "体を動かすもの。運動不足の解消に",
            listOf(
                "kintore", "walk", "stretch", "stairs", "posture", "neckroll",
                "squat", "pushup", "plank", "radio", "abs",
            ),
        ),
        Genre(
            "health", "ヘルスケアで自動チェックできる系",
            "歩数・体重・睡眠など、ヘルスケアの記録で測れるもの。わたし画面から連携すると自動チェック",
            listOf(
                "walk", "stairs", "weigh", "sleep_prep", "meditation",
                "kintore", "squat", "pushup", "plank", "radio", "abs",
            ),
        ),
        Genre(
            "groom", "みためを整える系", "身だしなみと清潔感を上げるもの",
            listOf(
                "nails", "skin", "hair", "posture", "clothes", "brow", "shoes", "scent",
                "teeth", "smile", "sunscreen", "lips", "mirror_check", "deodorant", "accessory", "iron",
            ),
        ),
        Genre(
            "mental", "メンタル系", "気持ちを整えるもの。「ありがとうを伝える」もここ",
            listOf(
                "meditation", "journal", "phone_off", "decide", "music", "breath", "sky",
                "thanks", "reading", "tidy", "message", "laugh", "sing", "tea",
                "self_praise", "declare", "bath", "smile",
            ),
        ),
        Genre(
            "night", "夜の仕上げ系", "寝る前のひと手間で、明日を軽くする",
            listOf("sleep_prep", "bath", "phone_off", "clothes", "journal", "stretch", "skin", "lips", "tea"),
        ),
    )

    fun genreById(id: String): Genre? = GENRES.firstOrNull { it.id == id }

    // ---------- v0.11.0: Health Connect auto-check ----------

    /** One day of health data, as read from Health Connect. */
    data class HealthFacts(
        val steps: Int = 0,
        val floors: Double = 0.0,
        val hasWeight: Boolean = false,
        val hasExercise: Boolean = false,
        val hasMindfulness: Boolean = false,
        /** Fell asleep on that date (before midnight), per the sleep record. */
        val sleptBeforeMidnight: Boolean = false,
    )

    /** The walk challenge's own yardstick (its label says 目安 8,000歩）. */
    const val AUTO_STEP_GOAL = 8000

    /**
     * Which challenges the day's health data proves done. Deliberately
     * conservative: Health Connect cannot tell a squat from a plank, so
     * the bodyweight set stays manual — an exercise session only proves
     * 筋トレに行く. Sleep uses the near approximation from the design:
     * a sleep session that started on the date (before midnight).
     */
    fun autoDoneIds(facts: HealthFacts): Set<String> {
        val out = mutableSetOf<String>()
        if (facts.steps >= AUTO_STEP_GOAL) out += "walk"
        if (facts.floors >= 1.0) out += "stairs"
        if (facts.hasWeight) out += "weigh"
        if (facts.hasExercise) out += "kintore"
        if (facts.hasMindfulness) out += "meditation"
        if (facts.sleptBeforeMidnight) out += "sleep_prep"
        return out
    }

    /** Points actually earned on a day: completed challenges pay their points. */
    // ---- v0.12.0: accumulation series for the pride graph ----

    private fun doneDate(entry: String): LocalDate? =
        runCatching { LocalDate.parse(entry.substringAfterLast('@')) }.getOrNull()

    /** Completions per day over the window ending at [end], oldest first. */
    fun dailyCounts(done: Set<String>, end: LocalDate, days: Int): List<Int> {
        val byDay = HashMap<LocalDate, Int>()
        for (e in done) {
            val d = doneDate(e) ?: continue
            byDay[d] = (byDay[d] ?: 0) + 1
        }
        return (days - 1 downTo 0).map { off -> byDay[end.minusDays(off.toLong())] ?: 0 }
    }

    /** Cumulative completions up to each day of the window (all earlier
     *  history is folded into the first day, so the mountain never resets). */
    fun cumulativeSeries(done: Set<String>, end: LocalDate, days: Int): List<Int> {
        val start = end.minusDays((days - 1).toLong())
        var run = done.count { e -> doneDate(e)?.isBefore(start) == true }
        return dailyCounts(done, end, days).map { c -> run += c; run }
    }

    /** Consecutive days with at least one completion, ending today — or
     *  yesterday, so a fresh morning does not read as a broken streak. */
    fun currentStreak(done: Set<String>, today: LocalDate): Int {
        val days = done.mapNotNull { doneDate(it) }.toSet()
        var cursor = if (today in days) today else today.minusDays(1)
        var streak = 0
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** Points earned from completions alone (settlement misses are not
     *  subtracted — this is the pride number, not the balance). */
    fun earnedPoints(done: Set<String>): Int =
        done.sumOf { e ->
            val id = e.substringBeforeLast('@')
            if (doneDate(e) != null && itemById(id) != null) pointsOf(id) else 0
        }

    fun dayEarned(setIds: List<String>, doneIds: Set<String>): Int =
        setIds.filter { it in doneIds }.sumOf { pointsOf(it) }

    /**
     * The day's settlement delta: done = +points, missed = a flat −1 each,
     * whatever the challenge (user's rule).
     */
    fun dayDelta(setIds: List<String>, doneIds: Set<String>): Int =
        setIds.sumOf { if (it in doneIds) pointsOf(it) else -1 }

    const val INITIAL_SLOTS = 3
    const val MAX_SLOTS = 6

    /** Consecutive all-done days needed to unlock one more slot. */
    const val UNLOCK_STREAK_DAYS = 3

    /** Slot-unlock state, advanced once per settled day. */
    data class UnlockState(val slots: Int, val streak: Int, val pendingUnlock: Boolean)

    /**
     * Advance the unlock state by one settled day. An all-done day grows
     * the streak; any miss resets it. Reaching the streak target with a
     * free slot available unlocks +1 slot and flags the celebration.
     */
    fun applySettledDay(state: UnlockState, allDone: Boolean): UnlockState {
        if (!allDone) return state.copy(streak = 0)
        val streak = state.streak + 1
        return when {
            streak >= UNLOCK_STREAK_DAYS && state.slots < MAX_SLOTS ->
                UnlockState(state.slots + 1, 0, pendingUnlock = true)
            streak >= UNLOCK_STREAK_DAYS -> state.copy(streak = 0)
            else -> state.copy(streak = streak)
        }
    }

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
        "全部完了。よく整えたね！",
        "完璧だ。その調子でいこう。",
    )
    private val LINES_RETURN = listOf(
        "おかえり。待ってたよ。",
        "久しぶり。今日もここからでいい。",
        "戻ってきてくれて嬉しいよ。",
    )
    private val LINES_UNLOCK_NEAR = listOf(
        "あと1日、全部できたら枠が増えるよ。",
        "枠まであと少し。その調子でいこう。",
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
     * What the buddy says on the today screen. Priority: finishing the
     * whole set beats everything; progress beats a plain greeting; a user
     * returning after 3+ days without a completion is welcomed back,
     * never blamed; a slot unlock one day away is pointed at. Lines
     * rotate by date so they change daily but stay stable within a day.
     */
    fun buddyLine(
        hour: Int,
        date: LocalDate,
        doneToday: Int,
        totalToday: Int,
        lastActive: LocalDate?,
        unlockNear: Boolean,
    ): String {
        val seed = date.dayOfYear
        return when {
            totalToday > 0 && doneToday >= totalToday -> LINES_ALL_DONE[seed % LINES_ALL_DONE.size]
            doneToday > 0 -> LINES_SOME_DONE[(seed + doneToday) % LINES_SOME_DONE.size]
            lastActive != null && !lastActive.isAfter(date.minusDays(3)) ->
                LINES_RETURN[seed % LINES_RETURN.size]
            unlockNear -> LINES_UNLOCK_NEAR[seed % LINES_UNLOCK_NEAR.size]
            else -> greetingPoolForHour(hour)[seed % greetingPoolForHour(hour).size]
        }
    }
}
