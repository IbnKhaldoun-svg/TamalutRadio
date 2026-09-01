package com.tamalut.radio.core.data

import com.tamalut.radio.core.database.FavoriteStationDao
import com.tamalut.radio.core.database.FavoriteStationEntity
import com.tamalut.radio.core.database.RadioStationDao
import com.tamalut.radio.core.database.RadioStationEntity
import com.tamalut.radio.core.database.RadioStationFallbackEntity
import com.tamalut.radio.core.database.RadioStationWithFallbacks
import com.tamalut.radio.core.database.RecentlyPlayedDao
import com.tamalut.radio.core.database.RecentlyPlayedEntity
import com.tamalut.radio.core.model.MediaId
import com.tamalut.radio.core.model.MediaItemSummary
import com.tamalut.radio.core.model.MediaSourceType
import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.RecentlyPlayedEntry
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.model.StreamEndpoint
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreDataRepositoriesTest {
    @Test
    fun seedInitialCatalog_isIdempotentAndMatchesApprovedCatalog() = runSuspend {
        val stationDao = FakeRadioStationDao()
        val favoriteDao = FakeFavoriteStationDao()
        val repository = RadioStationRepository(stationDao, favoriteDao)

        repository.seedInitialCatalog()
        repository.seedInitialCatalog()

        assertEquals(39, stationDao.getAllStations().size)
        assertEquals(39, stationDao.stationUpsertCount)
        assertEquals(
            InitialRadioCatalog.stations.map { it.id.value }.toSet(),
            stationDao.getAllStations().map { it.station.stationId }.toSet(),
        )
        assertFalse(stationDao.getAllStations().any { it.station.name.contains("Tachlit", ignoreCase = true) })
    }

    @Test
    fun favoritesAreOrderedFirstUsingFavoriteRecency() = runSuspend {
        val stationDao = FakeRadioStationDao()
        val favoriteDao = FakeFavoriteStationDao()
        val stations = RadioStationRepository(stationDao, favoriteDao)
        val favorites = FavoriteStationRepository(favoriteDao)
        stations.seedInitialCatalog()

        favorites.add(StationId("radio-mars"), 10)
        favorites.add(StationId("radio-sportiva"), 20)

        val ordered = stations.getStations(favoritesFirst = true)
        assertEquals("radio-sportiva", ordered[0].id.value)
        assertEquals("radio-mars", ordered[1].id.value)
        assertTrue(favorites.isFavorite(StationId("radio-mars")))
        favorites.remove(StationId("radio-mars"))
        assertFalse(favorites.isFavorite(StationId("radio-mars")))
    }

    @Test
    fun legacyAtbirLabelRepairIsExactIdempotentAndPreservesFallbacks() = runSuspend {
        val stationDao = FakeRadioStationDao()
        val repository = RadioStationRepository(stationDao, FakeFavoriteStationDao())
        stationDao.upsertStation(
            RadioStationEntity(
                stationId = "radio-plus-agadir",
                name = "Radio Plus Agadir 92.4",
                primaryStreamUrl = "https://stream-158.zeno.fm/bqdbb6hd0neuv",
                isCustom = false,
            ),
        )
        stationDao.upsertFallbackStreams(
            listOf(
                RadioStationFallbackEntity(
                    stationId = "radio-plus-agadir",
                    position = 0,
                    url = "https://example.invalid/legacy-fallback.mp3",
                ),
            ),
        )

        repository.seedInitialCatalog()
        val upsertsAfterRepairAndSeed = stationDao.stationUpsertCount
        val repaired = repository.getStation(StationId("radio-plus-agadir"))

        assertEquals("Radio Atbir", repaired?.name)
        assertEquals(
            listOf(
                "https://stream-158.zeno.fm/bqdbb6hd0neuv",
                "https://example.invalid/legacy-fallback.mp3",
            ),
            repaired?.playbackStreams?.map { it.url },
        )
        assertEquals(39, stationDao.getAllStations().size)

        repository.seedInitialCatalog()
        assertEquals(upsertsAfterRepairAndSeed, stationDao.stationUpsertCount)
        assertEquals("Radio Atbir", repository.getStation(StationId("radio-plus-agadir"))?.name)
    }

    @Test
    fun legacyAswatStreamRepairIsExactIdempotentAndPreservesFallbacks() = runSuspend {
        val stationDao = FakeRadioStationDao()
        val repository = RadioStationRepository(stationDao, FakeFavoriteStationDao())
        stationDao.upsertStation(
            RadioStationEntity(
                stationId = "aswat-fm",
                name = "Aswat FM",
                primaryStreamUrl = "https://broadcast.ice.infomaniak.ch/aswat-high.mp3",
                isCustom = false,
            ),
        )
        stationDao.upsertFallbackStreams(
            listOf(
                RadioStationFallbackEntity(
                    stationId = "aswat-fm",
                    position = 0,
                    url = "https://example.invalid/aswat-fallback.mp3",
                ),
            ),
        )

        repository.seedInitialCatalog()
        val upsertsAfterRepairAndSeed = stationDao.stationUpsertCount
        val repaired = repository.getStation(StationId("aswat-fm"))

        assertEquals("https://aswat.ice.infomaniak.ch/aswat-high.mp3", repaired?.primaryStream?.url)
        assertEquals(
            listOf(
                "https://aswat.ice.infomaniak.ch/aswat-high.mp3",
                "https://example.invalid/aswat-fallback.mp3",
            ),
            repaired?.playbackStreams?.map { it.url },
        )
        assertEquals(39, stationDao.getAllStations().size)

        repository.seedInitialCatalog()
        assertEquals(upsertsAfterRepairAndSeed, stationDao.stationUpsertCount)
        assertEquals(
            "https://aswat.ice.infomaniak.ch/aswat-high.mp3",
            repository.getStation(StationId("aswat-fm"))?.primaryStream?.url,
        )
    }

    @Test
    fun aswatRepairDoesNotOverwriteCustomRow() = runSuspend {
        val stationDao = FakeRadioStationDao()
        val repository = RadioStationRepository(stationDao, FakeFavoriteStationDao())
        stationDao.upsertStation(
            RadioStationEntity(
                stationId = "aswat-fm",
                name = "My Aswat",
                primaryStreamUrl = "https://broadcast.ice.infomaniak.ch/aswat-high.mp3",
                isCustom = true,
            ),
        )

        repository.seedInitialCatalog()

        val stored = repository.getStation(StationId("aswat-fm"))
        assertEquals("My Aswat", stored?.name)
        assertEquals(
            "https://broadcast.ice.infomaniak.ch/aswat-high.mp3",
            stored?.primaryStream?.url,
        )
    }

    @Test
    fun seedingDoesNotOverwriteModifiedLegacyOrOtherPreExistingRows() = runSuspend {
        val stationDao = FakeRadioStationDao()
        val repository = RadioStationRepository(stationDao, FakeFavoriteStationDao())
        stationDao.upsertStation(
            RadioStationEntity(
                stationId = "radio-plus-agadir",
                name = "My saved label",
                primaryStreamUrl = "https://stream-158.zeno.fm/bqdbb6hd0neuv",
                isCustom = false,
            ),
        )
        stationDao.upsertStation(
            RadioStationEntity(
                stationId = "radio-azawan",
                name = "My Azawan",
                primaryStreamUrl = "https://example.invalid/my-azawan.mp3",
                isCustom = false,
            ),
        )

        repository.seedInitialCatalog()

        assertEquals("My saved label", repository.getStation(StationId("radio-plus-agadir"))?.name)
        assertEquals("My Azawan", repository.getStation(StationId("radio-azawan"))?.name)
        assertEquals(
            "https://example.invalid/my-azawan.mp3",
            repository.getStation(StationId("radio-azawan"))?.primaryStream?.url,
        )
        assertEquals(39, stationDao.getAllStations().size)
    }

    @Test
    fun repositoryProjectsCatalogOrderThenDeterministicCustomTail() = runSuspend {
        val stationDao = FakeRadioStationDao()
        val repository = RadioStationRepository(stationDao, FakeFavoriteStationDao())
        repository.seedInitialCatalog()
        repository.saveCustomStation(customStation("custom-z", "Zulu Custom"))
        repository.saveCustomStation(customStation("custom-a2", "Alpha Custom"))
        repository.saveCustomStation(customStation("custom-a1", "Alpha Custom"))

        val ordered = repository.getStations(favoritesFirst = false)

        assertEquals(
            InitialRadioCatalog.stations.map { it.id.value },
            ordered.take(39).map { it.id.value },
        )
        assertEquals(
            listOf("custom-a1", "custom-a2", "custom-z"),
            ordered.drop(39).map { it.id.value },
        )
    }

    @Test
    fun customStationRoundTripPreservesFallbackOrderingAndCanBeRemoved() = runSuspend {
        val stationDao = FakeRadioStationDao()
        val repository = RadioStationRepository(stationDao, FakeFavoriteStationDao())
        val custom = RadioStation(
            id = StationId("custom-test"),
            name = "Custom Test",
            primaryStream = StreamEndpoint("https://example.com/primary"),
            fallbackStreams = listOf(
                StreamEndpoint("https://example.com/fallback-1"),
                StreamEndpoint("https://example.com/fallback-2"),
            ),
        )

        repository.saveCustomStation(custom)
        val loaded = repository.getStation(custom.id)

        assertEquals(
            listOf(
                "https://example.com/primary",
                "https://example.com/fallback-1",
                "https://example.com/fallback-2",
            ),
            loaded?.playbackStreams?.map { it.url },
        )
        assertTrue(repository.removeCustomStation(custom.id))
        assertNull(repository.getStation(custom.id))
    }

    @Test
    fun recentlyPlayedIsBoundedMostRecentFirstAndMappedToDomain() = runSuspend {
        val dao = FakeRecentlyPlayedDao()
        val repository = RecentlyPlayedRepository(dao, maxHistorySize = 2)

        repository.record(recent("one", 1))
        repository.record(recent("two", 2))
        repository.record(recent("three", 3))

        assertEquals(listOf("three", "two"), repository.getRecent().map { it.media.id.value })
        repository.clear()
        assertTrue(repository.getRecent().isEmpty())
    }

    private fun customStation(id: String, name: String) = RadioStation(
        id = StationId(id),
        name = name,
        primaryStream = StreamEndpoint("https://example.invalid/$id.mp3"),
    )

    private fun recent(id: String, timestamp: Long) = RecentlyPlayedEntry(
        media = MediaItemSummary(
            id = MediaId(id),
            title = id,
            subtitle = null,
            sourceType = MediaSourceType.RADIO,
        ),
        playedAtEpochMillis = timestamp,
    )
}

private class FakeRadioStationDao : RadioStationDao {
    private val stations = linkedMapOf<String, RadioStationEntity>()
    private val fallbacks = mutableMapOf<String, MutableList<RadioStationFallbackEntity>>()
    var stationUpsertCount: Int = 0
        private set

    override suspend fun upsertStation(station: RadioStationEntity) {
        stations[station.stationId] = station
        stationUpsertCount++
    }

    override suspend fun upsertFallbackStreams(fallbackStreams: List<RadioStationFallbackEntity>) {
        fallbackStreams.groupBy { it.stationId }.forEach { (stationId, entries) ->
            fallbacks.getOrPut(stationId) { mutableListOf() }.apply {
                entries.forEach { incoming ->
                    removeAll { it.position == incoming.position }
                    add(incoming)
                }
            }
        }
    }

    override suspend fun deleteFallbackStreams(stationId: String) {
        fallbacks.remove(stationId)
    }

    override suspend fun deleteStation(stationId: String) {
        stations.remove(stationId)
        fallbacks.remove(stationId)
    }

    override suspend fun getAllStations(): List<RadioStationWithFallbacks> =
        stations.values.sortedBy { it.name.lowercase() }.map(::aggregate)

    override suspend fun getStationsByCustomState(isCustom: Boolean): List<RadioStationWithFallbacks> =
        stations.values.filter { it.isCustom == isCustom }.sortedBy { it.name.lowercase() }.map(::aggregate)

    override suspend fun getStation(stationId: String): RadioStationWithFallbacks? =
        stations[stationId]?.let(::aggregate)

    private fun aggregate(station: RadioStationEntity) = RadioStationWithFallbacks(
        station = station,
        fallbackStreams = fallbacks[station.stationId].orEmpty().sortedBy { it.position },
    )
}

private class FakeFavoriteStationDao : FavoriteStationDao {
    private val favorites = mutableMapOf<String, FavoriteStationEntity>()

    override suspend fun upsertFavorite(favorite: FavoriteStationEntity) {
        favorites[favorite.stationId] = favorite
    }

    override suspend fun removeFavorite(stationId: String) {
        favorites.remove(stationId)
    }

    override suspend fun isFavorite(stationId: String): Boolean = stationId in favorites

    override suspend fun getFavoriteStationIds(): List<String> = favorites.values
        .sortedWith(compareByDescending<FavoriteStationEntity> { it.favoritedAtEpochMillis }.thenBy { it.stationId })
        .map { it.stationId }
}

private class FakeRecentlyPlayedDao : RecentlyPlayedDao {
    private val entries = mutableMapOf<String, RecentlyPlayedEntity>()

    override suspend fun upsert(entry: RecentlyPlayedEntity) {
        entries[entry.mediaId] = entry
    }

    override suspend fun getRecent(limit: Int): List<RecentlyPlayedEntity> = entries.values
        .sortedWith(compareByDescending<RecentlyPlayedEntity> { it.playedAtEpochMillis }.thenBy { it.mediaId })
        .take(limit)

    override suspend fun delete(mediaId: String) {
        entries.remove(mediaId)
    }

    override suspend fun trimToLimit(limit: Int) {
        val keep = getRecent(limit).map { it.mediaId }.toSet()
        entries.keys.retainAll(keep)
    }

    override suspend fun clear() {
        entries.clear()
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) {
            outcome = result
        }
    })
    return outcome?.getOrThrow() ?: error("Suspend block did not complete synchronously")
}
