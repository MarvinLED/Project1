package com.example.mytracker.smoke

import com.example.mytracker.core.backup.BackupExportProvider
import com.example.mytracker.core.backup.BackupScope
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class SmokeSessionDto(
    val id: String,
    val epochDay: Long,
    val minuteOfDay: Int,
    val puffs: Int? = null,
    val cbd: Boolean = false,
    val ratingDuring: Int? = null,
    val ratingAfter: Int? = null,
    val createdAtEpochMillis: Long,
)

private fun SmokeSession.toDto() = SmokeSessionDto(
    id = id,
    epochDay = epochDay,
    minuteOfDay = minuteOfDay,
    puffs = puffs,
    cbd = cbd,
    ratingDuring = ratingDuring,
    ratingAfter = ratingAfter,
    createdAtEpochMillis = createdAt.toEpochMilli(),
)

private fun SmokeSessionDto.toEntity() = SmokeSession(
    id = id,
    epochDay = epochDay,
    minuteOfDay = minuteOfDay,
    puffs = puffs,
    cbd = cbd,
    ratingDuring = ratingDuring,
    ratingAfter = ratingAfter,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
)

/**
 * The Sessions. Matched on the id and never overwritten, like the Flüssigkeits-Einträge and for the
 * same reason: a day may legitimately hold two sessions at the same minute, so there is no natural
 * key to merge on — an id already present is the same session, and anything else is a new one.
 *
 * The limits themselves are not here. They are Ziele, and they travel with the rest of them in
 * `goals/GoalsExportProvider` — see [BackupScope.LIBRARY].
 */
class SmokeSessionsExportProvider @Inject constructor(
    private val smokeDao: SmokeDao,
) : BackupExportProvider {
    override val key = "smokeSessions"
    override val scope = BackupScope.DAILY_ENTRIES
    override val importPriority = BackupExportProvider.DAILY_ENTRIES_PRIORITY

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun export(): JsonElement =
        json.encodeToJsonElement(smokeDao.getAllOnce().map { it.toDto() })

    override suspend fun import(json: JsonElement) {
        val dtos = this.json.decodeFromJsonElement<List<SmokeSessionDto>>(json)
        dtos.forEach { dto ->
            if (smokeDao.getById(dto.id) == null) {
                smokeDao.upsert(dto.toEntity())
            }
        }
    }

    override suspend fun clear() {
        smokeDao.deleteAll()
    }
}
