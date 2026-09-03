package com.tamalut.radio

import com.tamalut.radio.core.data.RadioStationRepository
import com.tamalut.radio.core.database.BackupRestoreDao
import com.tamalut.radio.core.database.FavoriteStationEntity
import com.tamalut.radio.core.database.LEGACY_CUSTOM_RADIO_CATEGORY
import com.tamalut.radio.core.database.RadioStationEntity
import com.tamalut.radio.core.preferences.OverlayEdge
import com.tamalut.radio.core.preferences.PortableUserPreferences
import com.tamalut.radio.core.preferences.ThemePreference
import com.tamalut.radio.core.preferences.UserPreferencesRepository
import com.tamalut.radio.feature.radio.RadioCategoryRules
import com.tamalut.radio.feature.radio.normalizeHttpsStreamUrl
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal const val BACKUP_FORMAT = "tamalutradio-backup"
internal const val BACKUP_FORMAT_VERSION = 1
internal const val MAX_BACKUP_BYTES = 5 * 1024 * 1024
private const val CUSTOM_STATION_ID_PREFIX = "custom-"

internal data class BackupCustomStation(
    val stationId: String,
    val name: String,
    val primaryStreamUrl: String,
    val category: String,
)

internal data class BackupFavorite(
    val stationId: String,
    val favoritedAtEpochMillis: Long,
)

internal data class BackupEnvelope(
    val format: String = BACKUP_FORMAT,
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val createdAtEpochMillis: Long,
    val customStations: List<BackupCustomStation>,
    val favorites: List<BackupFavorite>,
    val portablePreferences: PortableUserPreferences,
)

internal data class BackupPreview(
    val customStationCount: Int,
    val favoriteCount: Int,
    val skippedUnknownBuiltInFavoriteCount: Int,
)

internal data class PreparedBackup(
    val customStations: List<RadioStationEntity>,
    val favorites: List<FavoriteStationEntity>,
    val portablePreferences: PortableUserPreferences,
    val preview: BackupPreview,
)

internal data class BackupRestoreResult(
    val restoredCustomStationCount: Int,
    val restoredFavoriteCount: Int,
    val skippedUnknownBuiltInFavoriteCount: Int,
    val overlayPermissionRequired: Boolean,
)

internal interface BackupDataStore {
    suspend fun ensureReady()
    suspend fun customStations(): List<RadioStationEntity>
    suspend fun builtInStations(): List<RadioStationEntity>
    suspend fun favorites(): List<FavoriteStationEntity>
    suspend fun replaceBackupManagedData(
        customStations: List<RadioStationEntity>,
        favorites: List<FavoriteStationEntity>,
    )
}

internal class RoomBackupDataStore(
    private val dao: BackupRestoreDao,
    private val stationRepository: RadioStationRepository,
) : BackupDataStore {
    override suspend fun ensureReady() = stationRepository.seedInitialCatalog()
    override suspend fun customStations(): List<RadioStationEntity> = dao.getCustomStations()
    override suspend fun builtInStations(): List<RadioStationEntity> = dao.getBuiltInStations()
    override suspend fun favorites(): List<FavoriteStationEntity> = dao.getFavorites()

    override suspend fun replaceBackupManagedData(
        customStations: List<RadioStationEntity>,
        favorites: List<FavoriteStationEntity>,
    ) = dao.replaceBackupManagedData(customStations, favorites)
}

internal class BackupRestoreCoordinator(
    private val dataStore: BackupDataStore,
    private val preferencesRepository: UserPreferencesRepository,
    private val overlayPermissionGranted: () -> Boolean,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun exportBytes(): ByteArray {
        dataStore.ensureReady()
        val customStations = dataStore.customStations().map { entity ->
            check(entity.isCustom) { "Backup export received a built-in station as custom data" }
            BackupCustomStation(
                stationId = entity.stationId,
                name = entity.name.trim(),
                primaryStreamUrl = entity.primaryStreamUrl,
                category = entity.customCategory?.trim().orEmpty().ifBlank { LEGACY_CUSTOM_RADIO_CATEGORY },
            )
        }
        val favorites = dataStore.favorites().map { favorite ->
            BackupFavorite(
                stationId = favorite.stationId,
                favoritedAtEpochMillis = favorite.favoritedAtEpochMillis,
            )
        }
        val preferences = preferencesRepository.userPreferences.first().portableSnapshot()
        return BackupJsonCodec.encode(
            BackupEnvelope(
                createdAtEpochMillis = nowEpochMillis(),
                customStations = customStations,
                favorites = favorites,
                portablePreferences = preferences,
            ),
        )
    }

    suspend fun prepareImport(bytes: ByteArray): PreparedBackup {
        require(bytes.size <= MAX_BACKUP_BYTES) {
            "Il backup supera il limite di 5 MiB"
        }
        dataStore.ensureReady()
        val envelope = BackupJsonCodec.decode(bytes)
        require(envelope.format == BACKUP_FORMAT) { "Il file non è un backup TamalutRadio" }
        require(envelope.formatVersion <= BACKUP_FORMAT_VERSION) {
            "Questa versione del backup richiede una versione più recente di TamalutRadio"
        }
        require(envelope.formatVersion == BACKUP_FORMAT_VERSION) {
            "Versione backup non supportata"
        }
        require(envelope.createdAtEpochMillis >= 0L) { "Data di creazione backup non valida" }

        val builtIns = dataStore.builtInStations()
        val builtInIds = builtIns.mapTo(linkedSetOf()) { it.stationId }
        val builtInUrls = builtIns.mapTo(linkedSetOf()) { normalizeHttpsStreamUrl(it.primaryStreamUrl) }
        val seenIds = linkedSetOf<String>()
        val seenUrls = linkedSetOf<String>()
        val canonicalCategories = mutableListOf<String>()

        val customEntities = envelope.customStations.map { station ->
            val stationId = station.stationId.trim()
            require(stationId.isNotEmpty()) { "Una radio personale ha un ID vuoto" }
            require(stationId.startsWith(CUSTOM_STATION_ID_PREFIX)) {
                "ID radio personale non valido: $stationId"
            }
            require(seenIds.add(stationId)) { "ID radio personale duplicato: $stationId" }
            require(stationId !in builtInIds) { "Una radio personale collide con una radio integrata: $stationId" }

            val name = station.name.trim()
            require(name.isNotEmpty()) { "Una radio personale ha un nome vuoto" }
            val normalizedUrl = normalizeHttpsStreamUrl(station.primaryStreamUrl)
            require(seenUrls.add(normalizedUrl)) { "URL radio personale duplicata: $normalizedUrl" }
            require(normalizedUrl !in builtInUrls) {
                "Una radio personale usa la stessa URL di una radio integrata"
            }
            val category = RadioCategoryRules.normalize(station.category, canonicalCategories)
            if (canonicalCategories.none { it.equals(category, ignoreCase = true) }) {
                canonicalCategories += category
            }
            RadioStationEntity(
                stationId = stationId,
                name = name,
                primaryStreamUrl = normalizedUrl,
                isCustom = true,
                customCategory = category,
            )
        }

        val customIds = customEntities.mapTo(linkedSetOf()) { it.stationId }
        val seenFavoriteIds = linkedSetOf<String>()
        var skippedUnknownBuiltIns = 0
        val favoriteEntities = buildList {
            envelope.favorites.forEach { favorite ->
                val stationId = favorite.stationId.trim()
                require(stationId.isNotEmpty()) { "Un preferito ha un ID vuoto" }
                require(favorite.favoritedAtEpochMillis >= 0L) { "Timestamp preferito non valido" }
                require(seenFavoriteIds.add(stationId)) { "Preferito duplicato: $stationId" }
                when {
                    stationId in customIds || stationId in builtInIds -> add(
                        FavoriteStationEntity(
                            stationId = stationId,
                            favoritedAtEpochMillis = favorite.favoritedAtEpochMillis,
                        ),
                    )
                    stationId.startsWith(CUSTOM_STATION_ID_PREFIX) -> {
                        throw IllegalArgumentException(
                            "Un preferito fa riferimento a una radio personale mancante: $stationId",
                        )
                    }
                    else -> skippedUnknownBuiltIns += 1
                }
            }
        }

        val portable = envelope.portablePreferences
        require(portable.overlayVerticalFraction.isFinite()) { "Posizione overlay non valida" }
        require(portable.overlayVerticalFraction in 0f..1f) { "Posizione overlay fuori intervallo" }

        return PreparedBackup(
            customStations = customEntities,
            favorites = favoriteEntities,
            portablePreferences = portable.copy(
                languageTag = portable.languageTag?.trim()?.takeIf(String::isNotEmpty),
            ),
            preview = BackupPreview(
                customStationCount = customEntities.size,
                favoriteCount = favoriteEntities.size,
                skippedUnknownBuiltInFavoriteCount = skippedUnknownBuiltIns,
            ),
        )
    }

    suspend fun restore(prepared: PreparedBackup): BackupRestoreResult {
        dataStore.replaceBackupManagedData(prepared.customStations, prepared.favorites)
        val requestedOverlay = prepared.portablePreferences.overlayEnabled
        val canEnableOverlay = !requestedOverlay || overlayPermissionGranted()
        preferencesRepository.applyPortablePreferences(
            prepared.portablePreferences.copy(
                overlayEnabled = requestedOverlay && canEnableOverlay,
            ),
        )
        return BackupRestoreResult(
            restoredCustomStationCount = prepared.customStations.size,
            restoredFavoriteCount = prepared.favorites.size,
            skippedUnknownBuiltInFavoriteCount = prepared.preview.skippedUnknownBuiltInFavoriteCount,
            overlayPermissionRequired = requestedOverlay && !canEnableOverlay,
        )
    }
}

internal object BackupJsonCodec {
    private val json = Json {
        isLenient = false
        allowTrailingComma = false
    }

    fun encode(envelope: BackupEnvelope): ByteArray {
        val root = JsonObject(
            linkedMapOf(
                "format" to JsonPrimitive(envelope.format),
                "formatVersion" to JsonPrimitive(envelope.formatVersion),
                "createdAtEpochMillis" to JsonPrimitive(envelope.createdAtEpochMillis),
                "customStations" to JsonArray(envelope.customStations.map(::encodeCustomStation)),
                "favorites" to JsonArray(envelope.favorites.map(::encodeFavorite)),
                "portablePreferences" to encodePreferences(envelope.portablePreferences),
            ),
        )
        return root.toString().encodeToByteArray()
    }

    fun decode(bytes: ByteArray): BackupEnvelope {
        val text = try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (error: Exception) {
            throw IllegalArgumentException("Il backup non è UTF-8 valido", error)
        }
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (error: Exception) {
            throw IllegalArgumentException("JSON backup non valido", error)
        }
        return BackupEnvelope(
            format = root.requiredString("format"),
            formatVersion = root.requiredInt("formatVersion"),
            createdAtEpochMillis = root.requiredLong("createdAtEpochMillis"),
            customStations = root.requiredArray("customStations").map { element ->
                decodeCustomStation(element.jsonObject)
            },
            favorites = root.requiredArray("favorites").map { element ->
                decodeFavorite(element.jsonObject)
            },
            portablePreferences = decodePreferences(root.requiredObject("portablePreferences")),
        )
    }

    private fun encodeCustomStation(station: BackupCustomStation): JsonElement = JsonObject(
        linkedMapOf(
            "stationId" to JsonPrimitive(station.stationId),
            "name" to JsonPrimitive(station.name),
            "primaryStreamUrl" to JsonPrimitive(station.primaryStreamUrl),
            "category" to JsonPrimitive(station.category),
        ),
    )

    private fun decodeCustomStation(json: JsonObject): BackupCustomStation = BackupCustomStation(
        stationId = json.requiredString("stationId"),
        name = json.requiredString("name"),
        primaryStreamUrl = json.requiredString("primaryStreamUrl"),
        category = json.requiredString("category"),
    )

    private fun encodeFavorite(favorite: BackupFavorite): JsonElement = JsonObject(
        linkedMapOf(
            "stationId" to JsonPrimitive(favorite.stationId),
            "favoritedAtEpochMillis" to JsonPrimitive(favorite.favoritedAtEpochMillis),
        ),
    )

    private fun decodeFavorite(json: JsonObject): BackupFavorite = BackupFavorite(
        stationId = json.requiredString("stationId"),
        favoritedAtEpochMillis = json.requiredLong("favoritedAtEpochMillis"),
    )

    private fun encodePreferences(preferences: PortableUserPreferences): JsonObject = JsonObject(
        linkedMapOf(
            "themePreference" to JsonPrimitive(preferences.themePreference.name),
            "languageTag" to (preferences.languageTag?.let(::JsonPrimitive) ?: JsonNull),
            "overlayEnabled" to JsonPrimitive(preferences.overlayEnabled),
            "overlayEdge" to JsonPrimitive(preferences.overlayEdge.name),
            "overlayVerticalFraction" to JsonPrimitive(preferences.overlayVerticalFraction),
        ),
    )

    private fun decodePreferences(json: JsonObject): PortableUserPreferences {
        val themeName = json.requiredString("themePreference")
        val edgeName = json.requiredString("overlayEdge")
        val verticalFraction = json.requiredDouble("overlayVerticalFraction")
        require(verticalFraction.isFinite()) { "Posizione overlay non valida" }
        return PortableUserPreferences(
            themePreference = ThemePreference.entries.firstOrNull { it.name == themeName }
                ?: throw IllegalArgumentException("Tema backup non supportato: $themeName"),
            languageTag = json.optionalString("languageTag"),
            overlayEnabled = json.requiredBoolean("overlayEnabled"),
            overlayEdge = OverlayEdge.entries.firstOrNull { it.name == edgeName }
                ?: throw IllegalArgumentException("Lato overlay non supportato: $edgeName"),
            overlayVerticalFraction = verticalFraction.toFloat(),
        )
    }
}

private fun JsonObject.requiredString(name: String): String =
    this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
        ?: throw IllegalArgumentException("Campo backup mancante o non valido: $name")

private fun JsonObject.optionalString(name: String): String? {
    val element = this[name] ?: return null
    if (element is JsonNull) return null
    return runCatching { element.jsonPrimitive.content }.getOrElse {
        throw IllegalArgumentException("Campo backup non valido: $name")
    }
}

private fun JsonObject.requiredInt(name: String): Int =
    this[name]?.jsonPrimitive?.intOrNull
        ?: throw IllegalArgumentException("Campo backup mancante o non valido: $name")

private fun JsonObject.requiredLong(name: String): Long =
    this[name]?.jsonPrimitive?.longOrNull
        ?: throw IllegalArgumentException("Campo backup mancante o non valido: $name")

private fun JsonObject.requiredDouble(name: String): Double =
    this[name]?.jsonPrimitive?.doubleOrNull
        ?: throw IllegalArgumentException("Campo backup mancante o non valido: $name")

private fun JsonObject.requiredBoolean(name: String): Boolean =
    this[name]?.jsonPrimitive?.booleanOrNull
        ?: throw IllegalArgumentException("Campo backup mancante o non valido: $name")

private fun JsonObject.requiredArray(name: String): JsonArray =
    try {
        this[name]?.jsonArray ?: throw IllegalArgumentException("Campo backup mancante: $name")
    } catch (error: IllegalStateException) {
        throw IllegalArgumentException("Campo backup non valido: $name", error)
    }

private fun JsonObject.requiredObject(name: String): JsonObject =
    try {
        this[name]?.jsonObject ?: throw IllegalArgumentException("Campo backup mancante: $name")
    } catch (error: IllegalStateException) {
        throw IllegalArgumentException("Campo backup non valido: $name", error)
    }

internal fun suggestedBackupFileName(epochMillis: Long = System.currentTimeMillis()): String {
    val instant = java.time.Instant.ofEpochMilli(epochMillis)
    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
        .withZone(java.time.ZoneId.systemDefault())
    return "TamalutRadio-backup-${formatter.format(instant)}.json"
}
