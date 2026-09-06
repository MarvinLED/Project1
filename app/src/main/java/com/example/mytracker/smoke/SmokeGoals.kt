package com.example.mytracker.smoke

import com.example.mytracker.core.datastore.UserPreferences

/**
 * The four limits: how many Sessions and how many Züge there may be at most, per day and per week.
 *
 * All four are **upper** bounds, unlike most goals in this app — this is the one category where the
 * point is to stay under a number rather than to reach one. That is also why they are plain
 * nullable numbers rather than a [com.example.mytracker.core.datastore.NutrientGoal]: a lower bound
 * on smoking would be a goal to smoke more, which is not a thing to offer a field for.
 *
 * Null means "kein Ziel" and produces no row anywhere.
 */
data class SmokeGoals(
    val maxSessionsPerDay: Int? = null,
    val maxPuffsPerDay: Int? = null,
    val maxSessionsPerWeek: Int? = null,
    val maxPuffsPerWeek: Int? = null,
) {
    val isEmpty: Boolean
        get() = maxSessionsPerDay == null && maxPuffsPerDay == null &&
            maxSessionsPerWeek == null && maxPuffsPerWeek == null
}

/**
 * The four limits as stored, put back together — see [UserPreferences.maxSmokeSessionsPerDay] on why
 * they are kept apart there.
 */
val UserPreferences.smokeGoals: SmokeGoals
    get() = SmokeGoals(
        maxSessionsPerDay = maxSmokeSessionsPerDay,
        maxPuffsPerDay = maxSmokePuffsPerDay,
        maxSessionsPerWeek = maxSmokeSessionsPerWeek,
        maxPuffsPerWeek = maxSmokePuffsPerWeek,
    )

/**
 * One limit and where it stands. Same four fields the Tagesziele screen draws a row from and the
 * Schlaf goals already use ([com.example.mytracker.sleep.SleepGoalStatus]), so both screens can
 * render this without knowing what it is about.
 *
 * [isMet] is "still within the limit". A day with nothing logged is met, and correctly so: not
 * having smoked is the goal being kept, not a gap in the data.
 */
data class SmokeGoalStatus(
    val label: String,
    val valueText: String,
    val isMet: Boolean,
    /** How full the allowance is, 0..1 — a limit is a matter of degree, so it always has a bar. */
    val fraction: Float,
)

/** Züge across [sessions], counting only the ones that were actually counted. */
fun List<SmokeSession>.puffTotal(): Int = sumOf { it.puffs ?: 0 }

/** How many of [sessions] carry no Zugzahl — what makes [puffTotal] a lower bound rather than a total. */
fun List<SmokeSession>.sessionsWithoutPuffCount(): Int = count { it.puffs == null }

/**
 * The set limits with the day's and the week's counts against them, in a fixed order: the day
 * before the week, sessions before Züge. Limits that aren't set produce nothing.
 *
 * The labels say "am Tag" and "in der Woche" rather than "heute": this same list is drawn on the
 * Smoken screen, which can be scrolled back to any day, and a row headed "heute" would then be
 * about a day that isn't.
 *
 * [weekSessions] is expected to already be the week [daySessions]'s day falls in — the caller owns
 * the calendar (see [com.example.mytracker.core.util.DateUtils.startOfWeekEpochDay]), so this stays
 * a pure function of the two lists.
 */
fun smokeGoalStatuses(
    daySessions: List<SmokeSession>,
    weekSessions: List<SmokeSession>,
    goals: SmokeGoals,
): List<SmokeGoalStatus> = buildList {
    goals.maxSessionsPerDay?.let { add(sessionStatus("Sessions am Tag", daySessions, it)) }
    goals.maxPuffsPerDay?.let { add(puffStatus("Züge am Tag", daySessions, it)) }
    goals.maxSessionsPerWeek?.let { add(sessionStatus("Sessions in der Woche", weekSessions, it)) }
    goals.maxPuffsPerWeek?.let { add(puffStatus("Züge in der Woche", weekSessions, it)) }
}

private fun sessionStatus(label: String, sessions: List<SmokeSession>, limit: Int) = SmokeGoalStatus(
    label = label,
    valueText = "${sessions.size} / höchstens $limit",
    isMet = sessions.size <= limit,
    fraction = fractionOf(sessions.size, limit),
)

/**
 * Sessions without a Zugzahl are named rather than quietly left out of the sum: without that note a
 * count of 12 out of 20 could equally mean "well within" and "nobody counted half of them".
 */
private fun puffStatus(label: String, sessions: List<SmokeSession>, limit: Int): SmokeGoalStatus {
    val counted = sessions.puffTotal()
    val uncounted = sessions.sessionsWithoutPuffCount()
    val note = when (uncounted) {
        0 -> ""
        1 -> " · 1 Session ohne Zugzahl"
        else -> " · $uncounted Sessions ohne Zugzahl"
    }
    return SmokeGoalStatus(
        label = label,
        valueText = "$counted / höchstens $limit$note",
        // Only what was counted can be judged: an uncounted session cannot put the limit over on
        // its own, and claiming it did would make the red mean nothing.
        isMet = counted <= limit,
        fraction = fractionOf(counted, limit),
    )
}

/** A limit of zero is "gar nicht" — anything at all fills that bar completely. */
private fun fractionOf(value: Int, limit: Int): Float =
    if (limit <= 0) {
        if (value > 0) 1f else 0f
    } else {
        (value.toFloat() / limit).coerceIn(0f, 1f)
    }
