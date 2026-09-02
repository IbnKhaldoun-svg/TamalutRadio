package com.tamalut.radio.feature.radio

import com.tamalut.radio.core.data.FavoriteStationRepository
import com.tamalut.radio.core.data.RadioStationRepository
import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.model.StreamEndpoint
import java.util.UUID

interface RadioDataSource {
    suspend fun seedInitialCatalog()
    suspend fun stations(): List<RadioStation>
    suspend fun favoriteIds(): Set<StationId>
    suspend fun setFavorite(stationId: StationId, favorite: Boolean)
    suspend fun customStationIds(): Set<StationId> = emptySet()
    suspend fun customStationCategories(): Map<StationId, String> =
        customStationIds().associateWith { RadioCategoryRules.LEGACY_CATEGORY }
    suspend fun saveCustomStation(station: RadioStation) {
        throw UnsupportedOperationException("Custom radio persistence is not available")
    }
    suspend fun saveCustomStation(station: RadioStation, category: String) {
        saveCustomStation(station)
    }
    suspend fun removeCustomStation(stationId: StationId): Boolean = false
}

class CoreRadioDataSource(
    private val stationRepository: RadioStationRepository,
    private val favoriteRepository: FavoriteStationRepository,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : RadioDataSource {
    override suspend fun seedInitialCatalog() = stationRepository.seedInitialCatalog()

    override suspend fun stations(): List<RadioStation> =
        stationRepository.getStations(favoritesFirst = false)

    override suspend fun favoriteIds(): Set<StationId> =
        favoriteRepository.getFavoriteStationIds().toSet()

    override suspend fun setFavorite(stationId: StationId, favorite: Boolean) {
        if (favorite) {
            favoriteRepository.add(stationId, nowEpochMillis())
        } else {
            favoriteRepository.remove(stationId)
        }
    }

    override suspend fun customStationIds(): Set<StationId> =
        stationRepository.getCustomStationIds()

    override suspend fun customStationCategories(): Map<StationId, String> =
        stationRepository.getCustomStationCategories()

    override suspend fun saveCustomStation(station: RadioStation, category: String) =
        stationRepository.saveCustomStation(station, category)

    override suspend fun removeCustomStation(stationId: StationId): Boolean =
        stationRepository.removeCustomStation(stationId)
}

data class RadioSnapshot(
    val stations: List<RadioStation>,
    val favoriteIds: Set<StationId>,
    val customStationIds: Set<StationId> = emptySet(),
    val customStationCategories: Map<StationId, String> = emptyMap(),
)

class RadioFeatureController(
    private val dataSource: RadioDataSource,
    private val streamValidator: RadioStreamValidator = HttpsRadioStreamValidator(),
    private val customStationIdFactory: () -> StationId = {
        StationId("custom-${UUID.randomUUID()}")
    },
) {
    suspend fun load(): RadioSnapshot {
        dataSource.seedInitialCatalog()
        return snapshot()
    }

    suspend fun toggleFavorite(stationId: StationId, currentlyFavorite: Boolean): RadioSnapshot {
        dataSource.setFavorite(stationId, favorite = !currentlyFavorite)
        return snapshot()
    }

    suspend fun saveCustomStation(
        stationId: StationId?,
        name: String,
        streamUrl: String,
        category: String,
    ): RadioSnapshot {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Inserisci un nome per la radio" }
        val normalizedUrl = normalizeHttpsStreamUrl(streamUrl)
        val before = snapshot()

        if (stationId != null) {
            require(stationId in before.customStationIds) {
                "Le radio integrate non possono essere modificate"
            }
        }

        val normalizedCategory = RadioCategoryRules.normalize(
            category = category,
            existingCategories = before.customStationCategories.values,
        )
        val duplicate = before.stations.any { station ->
            station.id != stationId && comparableStreamUrl(station.primaryStream.url) == normalizedUrl
        }
        require(!duplicate) { "Questa URL stream è già presente nella libreria" }

        streamValidator.validate(normalizedUrl)

        val resolvedId = stationId ?: newCustomStationId(before.stations.mapTo(mutableSetOf()) { it.id })
        dataSource.saveCustomStation(
            RadioStation(
                id = resolvedId,
                name = normalizedName,
                primaryStream = StreamEndpoint(normalizedUrl),
            ),
            normalizedCategory,
        )
        return snapshot()
    }

    suspend fun deleteCustomStation(stationId: StationId): RadioSnapshot {
        val before = snapshot()
        require(stationId in before.customStationIds) {
            "Le radio integrate non possono essere eliminate"
        }
        check(dataSource.removeCustomStation(stationId)) {
            "Impossibile eliminare la radio personale"
        }
        return snapshot()
    }

    private suspend fun snapshot(): RadioSnapshot {
        val customStationIds = dataSource.customStationIds()
        val categories = dataSource.customStationCategories()
        val effectiveCategories = buildMap {
            customStationIds.forEach { stationId ->
                put(stationId, categories[stationId]?.trim().orEmpty().ifBlank { RadioCategoryRules.LEGACY_CATEGORY })
            }
            categories.forEach { (stationId, category) ->
                put(stationId, category.trim().ifBlank { RadioCategoryRules.LEGACY_CATEGORY })
            }
        }
        return RadioSnapshot(
            stations = dataSource.stations(),
            favoriteIds = dataSource.favoriteIds(),
            customStationIds = customStationIds + effectiveCategories.keys,
            customStationCategories = effectiveCategories,
        )
    }

    private fun newCustomStationId(existingIds: Set<StationId>): StationId {
        repeat(MAX_CUSTOM_ID_ATTEMPTS) {
            val candidate = customStationIdFactory()
            require(candidate.value.startsWith(CUSTOM_ID_PREFIX)) {
                "Custom station IDs must start with $CUSTOM_ID_PREFIX"
            }
            if (candidate !in existingIds) return candidate
        }
        error("Impossibile generare un identificatore univoco per la radio personale")
    }

    private fun comparableStreamUrl(url: String): String =
        runCatching { normalizeHttpsStreamUrl(url) }.getOrElse { url.trim() }

    private companion object {
        const val CUSTOM_ID_PREFIX = "custom-"
        const val MAX_CUSTOM_ID_ATTEMPTS = 16
    }
}
