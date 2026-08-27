package com.tamalut.radio.core.data

import com.tamalut.radio.core.database.RecentlyPlayedDao
import com.tamalut.radio.core.database.toDomainOrNull
import com.tamalut.radio.core.database.toEntity
import com.tamalut.radio.core.model.MediaId
import com.tamalut.radio.core.model.RecentlyPlayedEntry
import com.tamalut.radio.core.model.StationId

class RecentlyPlayedRepository(
    private val recentlyPlayedDao: RecentlyPlayedDao,
    private val maxHistorySize: Int = 50,
) {
    init {
        require(maxHistorySize > 0)
    }

    suspend fun record(entry: RecentlyPlayedEntry, stationId: StationId? = null) {
        recentlyPlayedDao.upsert(entry.toEntity(stationId))
        recentlyPlayedDao.trimToLimit(maxHistorySize)
    }

    suspend fun getRecent(limit: Int = maxHistorySize): List<RecentlyPlayedEntry> {
        require(limit > 0)
        return recentlyPlayedDao.getRecent(limit.coerceAtMost(maxHistorySize))
            .mapNotNull { it.toDomainOrNull() }
    }

    suspend fun remove(mediaId: MediaId) {
        recentlyPlayedDao.delete(mediaId.value)
    }

    suspend fun clear() {
        recentlyPlayedDao.clear()
    }
}
