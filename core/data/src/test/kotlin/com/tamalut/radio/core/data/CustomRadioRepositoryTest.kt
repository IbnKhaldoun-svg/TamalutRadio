package com.tamalut.radio.core.data

import com.tamalut.radio.core.database.FavoriteStationDao
import com.tamalut.radio.core.database.FavoriteStationEntity
import com.tamalut.radio.core.database.RadioStationDao
import com.tamalut.radio.core.database.RadioStationEntity
import com.tamalut.radio.core.database.RadioStationFallbackEntity
import com.tamalut.radio.core.database.RadioStationWithFallbacks
import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.model.StreamEndpoint
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomRadioRepositoryTest {
    @Test
    fun customSaveUpdateDeleteAndOrderingPreserveBuiltInCatalog() = runSuspendCustom {
        val stationDao = CustomRadioStationDao()
        val repository = RadioStationRepository(stationDao, CustomFavoriteStationDao())
        repository.seedInitialCatalog()

        val builtIn = InitialRadioCatalog.stations.first()
        val overwriteError = captureSuspendFailure {
            repository.saveCustomStation(
                RadioStation(
                    id = builtIn.id,
                    name = "User replacement",
                    primaryStream = StreamEndpoint("https://example.com/replacement"),
                ),
            )
        }
        assertTrue(overwriteError is IllegalArgumentException)
        assertEquals(builtIn, repository.getStation(builtIn.id))

        val zulu = customStation("custom-zulu", "Zulu", "https://example.com/zulu")
        val alpha = customStation("custom-alpha", "Alpha", "https://example.com/alpha")
        repository.saveCustomStation(zulu)
        repository.saveCustomStation(alpha)
        repository.saveCustomStation(alpha.copy(name = "Alpha Updated"))

        assertEquals(setOf(alpha.id, zulu.id), repository.getCustomStationIds())
        val ordered = repository.getStations(favoritesFirst = false)
        assertEquals(
            InitialRadioCatalog.stations.map { it.id },
            ordered.take(54).map { it.id },
        )
        assertEquals(listOf(alpha.id, zulu.id), ordered.drop(54).map { it.id })
        assertEquals("Alpha Updated", ordered.first { it.id == alpha.id }.name)

        assertFalse(repository.removeCustomStation(builtIn.id))
        assertTrue(repository.removeCustomStation(alpha.id))
        assertEquals(setOf(zulu.id), repository.getCustomStationIds())
        assertEquals(54, repository.getStations(favoritesFirst = false).count { it.id in InitialRadioCatalog.stations.map { station -> station.id }.toSet() })
    }

    private fun customStation(id: String, name: String, url: String) = RadioStation(
        id = StationId(id),
        name = name,
        primaryStream = StreamEndpoint(url),
    )

    private suspend fun captureSuspendFailure(block: suspend () -> Unit): Throwable {
        return try {
            block()
            throw AssertionError("Expected failure")
        } catch (error: Throwable) {
            if (error is AssertionError) throw error
            error
        }
    }
}

private class CustomRadioStationDao : RadioStationDao {
    private val stations = linkedMapOf<String, RadioStationEntity>()
    private val fallbacks = mutableMapOf<String, MutableList<RadioStationFallbackEntity>>()

    override suspend fun upsertStation(station: RadioStationEntity) {
        stations[station.stationId] = station
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

private class CustomFavoriteStationDao : FavoriteStationDao {
    private val favorites = linkedMapOf<String, FavoriteStationEntity>()

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

private fun <T> runSuspendCustom(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) {
            outcome = result
        }
    })
    return outcome?.getOrThrow() ?: error("Suspend block did not complete synchronously")
}
