package com.example.mytracker.smoke

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mytracker.core.util.formatMinuteOfDay
import java.time.Instant

/**
 * One session — a single time of smoking, not a day's worth of it.
 *
 * Unlike [com.example.mytracker.sleep.SleepEntry] or the Blutdruck slots there is no deterministic
 * id here and no unique index: a day holds as many sessions as there were, and two of them at the
 * same minute is a thing that happens rather than a duplicate to be folded away. That is also the
 * whole point of the category — how *often*, not whether.
 *
 * [minuteOfDay] is the clock time, minutes since midnight, the same convention as everywhere else
 * (see [com.example.mytracker.core.util.TimeOfDay]); [epochDay] beside it says which day that clock
 * reading belongs to.
 *
 * Everything but the time is optional, because it is filled in at a moment when nobody wants to
 * fill in a form: [puffs] because they are not always counted, [ratingDuring] and [ratingAfter]
 * because a rating one didn't actually make would be worse than no rating at all. [cbd] is the one
 * exception — it is a yes/no about what was smoked, and "not stated" and "no" are the same answer
 * for every purpose this screen has.
 */
@Entity(tableName = "smoke_sessions", indices = [Index("epochDay")])
data class SmokeSession(
    @PrimaryKey val id: String,
    val epochDay: Long,
    /** Minutes since midnight — when it happened. */
    val minuteOfDay: Int,
    /** How many Züge, or null when they weren't counted. */
    val puffs: Int?,
    val cbd: Boolean = false,
    /** How it was while smoking, 1–10. Null when it wasn't rated. */
    val ratingDuring: Int?,
    /** How the time afterwards was, 1–10. Null when it wasn't rated. */
    val ratingAfter: Int?,
    val createdAt: Instant,
)

/** The ends of both rating scales. Same 1–10 as the Schlaf screen's Fitness, for the same reason. */
const val MIN_SMOKE_RATING = 1
const val MAX_SMOKE_RATING = 10

/**
 * One line saying what a session was: time first, then only the parts that were actually recorded.
 * Shared by the list row and its delete confirmation, so the dialog names exactly the row that was
 * tapped rather than a shorter description of it.
 */
fun SmokeSession.summaryLabel(): String = buildList {
    add(formatMinuteOfDay(minuteOfDay))
    puffs?.let { add(if (it == 1) "1 Zug" else "$it Züge") }
    if (cbd) add("CBD")
    ratingDuring?.let { add("dabei $it/$MAX_SMOKE_RATING") }
    ratingAfter?.let { add("danach $it/$MAX_SMOKE_RATING") }
}.joinToString(" · ")
