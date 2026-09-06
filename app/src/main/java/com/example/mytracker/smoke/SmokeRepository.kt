package com.example.mytracker.smoke

import com.example.mytracker.core.metrics.MetricPoint
import com.example.mytracker.core.util.IdGenerator
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SmokeRepository @Inject constructor(
    private val smokeDao: SmokeDao,
) {
    fun observeForDay(epochDay: Long): Flow<List<SmokeSession>> = smokeDao.observeForDay(epochDay)

    fun observeInRange(startInclusive: Long, endInclusive: Long): Flow<List<SmokeSession>> =
        smokeDao.observeInRange(startInclusive, endInclusive)

    fun observeAll(): Flow<List<SmokeSession>> = smokeDao.observeAll()

    fun observeDailySessionCounts(startInclusive: Long, endInclusive: Long): Flow<List<MetricPoint>> =
        smokeDao.observeDailySessionCounts(startInclusive, endInclusive)

    fun observeDailyPuffs(startInclusive: Long, endInclusive: Long): Flow<List<MetricPoint>> =
        smokeDao.observeDailyPuffs(startInclusive, endInclusive)

    /**
     * Adds a session, or rewrites the one at [id] when a logged session is being corrected. New ids
     * are random rather than derived from the day and time — see [SmokeSession] on why a day may
     * hold two sessions at the same minute.
     *
     * Both ratings are clamped rather than trusted: they come from a slider today, but a value out
     * of range would silently break every average built on them.
     *
     * A typed 0 for the Züge is stored as null — "keinen Zug genommen" is not a session anyone logs,
     * so a zero in that field is someone clearing it, and null is what an uncounted session is.
     */
    suspend fun logSession(
        epochDay: Long,
        minuteOfDay: Int,
        puffs: Int?,
        cbd: Boolean,
        ratingDuring: Int?,
        ratingAfter: Int?,
        id: String? = null,
    ) {
        // Keeps the original createdAt when editing: correcting a time should not make an old
        // session look freshly logged.
        val existing = id?.let { smokeDao.getById(it) }
        smokeDao.upsert(
            SmokeSession(
                id = existing?.id ?: id ?: IdGenerator.newId(),
                epochDay = epochDay,
                minuteOfDay = minuteOfDay,
                puffs = puffs?.takeIf { it > 0 },
                cbd = cbd,
                ratingDuring = ratingDuring?.coerceIn(MIN_SMOKE_RATING, MAX_SMOKE_RATING),
                ratingAfter = ratingAfter?.coerceIn(MIN_SMOKE_RATING, MAX_SMOKE_RATING),
                createdAt = existing?.createdAt ?: Instant.now(),
            ),
        )
    }

    suspend fun delete(session: SmokeSession) {
        smokeDao.delete(session)
    }
}
