package com.example.mytracker.smoke

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun session(
    minuteOfDay: Int = 12 * 60,
    puffs: Int? = null,
    cbd: Boolean = false,
    ratingDuring: Int? = null,
    ratingAfter: Int? = null,
) = SmokeSession(
    id = "session-$minuteOfDay-$puffs",
    epochDay = 20_000L,
    minuteOfDay = minuteOfDay,
    puffs = puffs,
    cbd = cbd,
    ratingDuring = ratingDuring,
    ratingAfter = ratingAfter,
    createdAt = Instant.EPOCH,
)

/**
 * These limits run the opposite way to every other goal in the app: met means *under*, and the bar
 * fills as the allowance is used up. Both directions are pinned here, along with the two cases that
 * are easy to get quietly wrong — a session whose Züge were never counted, and a limit of zero.
 */
class SmokeGoalsTest {
    @Test
    fun aLimitIsMetWhileItIsNotExceeded() {
        val sessions = List(3) { session() }
        val statuses = smokeGoalStatuses(sessions, sessions, SmokeGoals(maxSessionsPerDay = 3))

        assertEquals(1, statuses.size)
        assertTrue("Genau auf dem Limit ist noch im Limit", statuses.single().isMet)
        assertEquals("3 / höchstens 3", statuses.single().valueText)
        assertEquals(1f, statuses.single().fraction, 0.001f)

        val overshot = smokeGoalStatuses(sessions + session(), sessions, SmokeGoals(maxSessionsPerDay = 3))
        assertFalse(overshot.single().isMet)
        // The bar cannot run past its end, so being over shows as full rather than as more than full.
        assertEquals(1f, overshot.single().fraction, 0.001f)
    }

    @Test
    fun anEmptyDayKeepsEveryLimit() {
        val statuses = smokeGoalStatuses(
            daySessions = emptyList(),
            weekSessions = emptyList(),
            goals = SmokeGoals(maxSessionsPerDay = 5, maxPuffsPerDay = 40),
        )

        assertTrue(statuses.all { it.isMet })
        assertTrue(statuses.all { it.fraction == 0f })
    }

    @Test
    fun onlyTheLimitsThatAreSetProduceRows() {
        assertTrue(smokeGoalStatuses(listOf(session()), listOf(session()), SmokeGoals()).isEmpty())
        assertTrue(SmokeGoals().isEmpty)
        assertFalse(SmokeGoals(maxPuffsPerWeek = 100).isEmpty)

        val labels = smokeGoalStatuses(
            daySessions = emptyList(),
            weekSessions = emptyList(),
            goals = SmokeGoals(
                maxSessionsPerDay = 3,
                maxPuffsPerDay = 30,
                maxSessionsPerWeek = 15,
                maxPuffsPerWeek = 150,
            ),
        ).map { it.label }

        // Day before week, sessions before Züge — the order the Ziele rows are read in.
        assertEquals(
            listOf("Sessions am Tag", "Züge am Tag", "Sessions in der Woche", "Züge in der Woche"),
            labels,
        )
    }

    @Test
    fun unzaehlteZuegeCountAsUnknownRatherThanAsZero() {
        val sessions = listOf(session(puffs = 8), session(puffs = null), session(puffs = null))
        val status = smokeGoalStatuses(sessions, sessions, SmokeGoals(maxPuffsPerDay = 20)).single()

        assertEquals(8, sessions.puffTotal())
        assertEquals(2, sessions.sessionsWithoutPuffCount())
        // Said out loud, because 8 of 20 would otherwise read as "well within" when two thirds of
        // the sessions simply were not counted.
        assertEquals("8 / höchstens 20 · 2 Sessions ohne Zugzahl", status.valueText)
        assertTrue("Was nicht gezählt wurde, kann das Limit nicht sprengen", status.isMet)
    }

    @Test
    fun aLimitOfZeroMeansNoneAtAll() {
        val none = smokeGoalStatuses(emptyList(), emptyList(), SmokeGoals(maxSessionsPerDay = 0)).single()
        assertTrue(none.isMet)
        assertEquals(0f, none.fraction, 0.001f)

        val one = smokeGoalStatuses(listOf(session()), emptyList(), SmokeGoals(maxSessionsPerDay = 0)).single()
        assertFalse(one.isMet)
        // No division by zero, and no empty bar next to a broken limit.
        assertEquals(1f, one.fraction, 0.001f)
    }

    @Test
    fun theWeekIsCountedSeparatelyFromTheDay() {
        val day = List(2) { session() }
        val week = List(9) { session() }
        val statuses = smokeGoalStatuses(
            daySessions = day,
            weekSessions = week,
            goals = SmokeGoals(maxSessionsPerDay = 3, maxSessionsPerWeek = 8),
        )

        assertTrue("Der Tag ist im Limit", statuses.first().isMet)
        assertFalse("Die Woche ist es nicht", statuses.last().isMet)
    }

    /**
     * The − and + beside the Züge field. The pair has to be able to reach every state the field can
     * be in, "nicht gezählt" included — otherwise the buttons are a one-way door into a count.
     */
    @Test
    fun steppingTheZuegeReachesEmptyInBothDirections() {
        assertEquals("1", steppedPuffs("", 1))
        assertEquals("7", steppedPuffs("6", 1))
        assertEquals("5", steppedPuffs("6", -1))
        // Down from one is back to "nicht gezählt", not to a zero nobody logs.
        assertEquals("", steppedPuffs("1", -1))
        // And down from there stays there rather than going negative.
        assertEquals("", steppedPuffs("", -1))
    }

    @Test
    fun aSessionSaysOnlyWhatWasActuallyRecorded() {
        assertEquals("12:00", session().summaryLabel())
        assertEquals("07:05 · 1 Zug", session(minuteOfDay = 7 * 60 + 5, puffs = 1).summaryLabel())
        assertEquals(
            "12:00 · 6 Züge · CBD · dabei 8/10 · danach 4/10",
            session(puffs = 6, cbd = true, ratingDuring = 8, ratingAfter = 4).summaryLabel(),
        )
    }
}
