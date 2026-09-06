package com.example.mytracker.smoke

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.example.mytracker.core.metrics.MetricPoint
import kotlinx.coroutines.flow.Flow

@Dao
interface SmokeDao {
    /** One day's sessions in the order they happened — the list the screen shows. */
    @Query("SELECT * FROM smoke_sessions WHERE epochDay = :epochDay ORDER BY minuteOfDay")
    fun observeForDay(epochDay: Long): Flow<List<SmokeSession>>

    /** Both ends inclusive. What the Wochenziel counts over. */
    @Query(
        "SELECT * FROM smoke_sessions WHERE epochDay BETWEEN :startInclusive AND :endInclusive " +
            "ORDER BY epochDay, minuteOfDay",
    )
    fun observeInRange(startInclusive: Long, endInclusive: Long): Flow<List<SmokeSession>>

    @Query("SELECT * FROM smoke_sessions ORDER BY epochDay DESC, minuteOfDay DESC")
    fun observeAll(): Flow<List<SmokeSession>>

    /**
     * Sessions per day for the Analyse screen. Only days with at least one session produce a row —
     * a day nobody smoked has no row here rather than a zero, the same as every other series.
     */
    @Query(
        "SELECT epochDay, COUNT(*) AS value FROM smoke_sessions " +
            "WHERE epochDay BETWEEN :startInclusive AND :endInclusive GROUP BY epochDay ORDER BY epochDay",
    )
    fun observeDailySessionCounts(startInclusive: Long, endInclusive: Long): Flow<List<MetricPoint>>

    /**
     * Züge per day. Sessions whose puffs weren't counted are skipped rather than counted as zero:
     * summing them in would make an uncounted day look like a smoke-free one.
     */
    @Query(
        "SELECT epochDay, SUM(puffs) AS value FROM smoke_sessions " +
            "WHERE puffs IS NOT NULL AND epochDay BETWEEN :startInclusive AND :endInclusive " +
            "GROUP BY epochDay ORDER BY epochDay",
    )
    fun observeDailyPuffs(startInclusive: Long, endInclusive: Long): Flow<List<MetricPoint>>

    @Query("SELECT * FROM smoke_sessions WHERE id = :id")
    suspend fun getById(id: String): SmokeSession?

    @Upsert
    suspend fun upsert(session: SmokeSession)

    @Delete
    suspend fun delete(session: SmokeSession)

    @Query("SELECT * FROM smoke_sessions ORDER BY epochDay, minuteOfDay")
    suspend fun getAllOnce(): List<SmokeSession>

    /** Wipes the Sessions for a replacing import. */
    @Query("DELETE FROM smoke_sessions")
    suspend fun deleteAll()
}
