package com.tamalut.radio

import com.tamalut.radio.core.data.InitialRadioCatalog
import com.tamalut.radio.core.database.BackupRestoreDao
import com.tamalut.radio.core.database.FavoriteStationEntity
import com.tamalut.radio.core.database.LEGACY_CUSTOM_RADIO_CATEGORY
import com.tamalut.radio.core.database.RadioStationEntity
import com.tamalut.radio.core.model.RadioStation
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
    suspend fun customStations(): List<RadioStationEntity>
    suspend fun favorites(): List<FavoriteStationEntity>
    suspend fun replaceBackupManagedData(
        customStations: List<RadioStationEntity>,
        favorites: List<FavoriteStationEntity>,
    )
}

internal class RoomBackupDataStore(
    private val dao: BackupRestoreDao,
) : BackupDataStore {
    override suspend fun customStations(): List<RadioStationEntity> = dao.getCustomStations()
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
    private val currentBuiltInCatalog: List<RadioStation> = InitialRadioCatalog.stations,
) {
    suspend fun exportBytes(): ByteArray {
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
        val encoded = BackupJsonCodec.encode(
            BackupEnvelope(
                createdAtEpochMillis = nowEpochMillis(),
                customStations = customStations,
                favorites = favorites,
                portablePreferences = preferences,
            ),
        )
        require(encoded.size <= MAX_BACKUP_BYTES) {
            "I dati da esportare superano il limite di 5 MiB"
        }
        return encoded
    }

    suspend fun prepareImport(bytes: ByteArray): PreparedBackup {
        require(bytes.size <= MAX_BACKUP_BYTES) {
            "Il backup supera il limite di 5 MiB"
        }
        val envelope = BackupJsonCodec.decode(bytes)
        require(envelope.format == BACKUP_FORMAT) { "Il file non è un backup TamalutRadio" }
        require(envelope.formatVersion <= BACKUP_FORMAT_VERSION) {
            "Questa versione del backup richiede una versione più recente di TamalutRadio"
        }
        require(envelope.formatVersion == BACKUP_FORMAT_VERSION) {
            "Versione backup non supportata"
        }
        require(envelope.createdAtEpochMillis >= 0L) { "Data di creazione backup non valida" }

        val builtInIds = currentBuiltInCatalog.mapTo(linkedSetOf()) { it.id.value }
        val builtInUrls = currentBuiltInCatalog.mapTo(linkedSetOf()) {
            normalizeHttpsStreamUrl(it.primaryStream.url)
        }
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
            require(stationId !in builtInIds) {
                "Una radio personale collide con una radio integrata: $stationId"
            }

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
    private val json = Json

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
            json.parseToJsonElement(text) as? JsonObject
                ?: throw IllegalArgumentException("La radice del backup deve essere un oggetto JSON")
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("JSON backup non valido", error)
        }
        return BackupEnvelope(
            format = root.requiredString("format"),
            formatVersion = root.requiredInt("formatVersion"),
            createdAtEpochMillis = root.requiredLong("createdAtEpochMillis"),
            customStations = root.requiredArray("customStations").mapIndexed { index, element ->
                decodeCustomStation(element.requiredObject("customStations[$index]"))
            },
            favorites = root.requiredArray("favorites").mapIndexed { index, element ->
                decodeFavorite(element.requiredObject("favorites[$index]"))
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

private fun JsonObject.requiredPrimitive(name: String): JsonPrimitive =
    this[name] as? JsonPrimitive
        ?: throw IllegalArgumentException("Campo backup mancante o non valido: $name")

private fun JsonObject.requiredString(name: String): String {
    val primitive = requiredPrimitive(name)
    require(primitive.isString) { "Campo backup non valido: $name" }
    return primitive.content
}

private fun JsonObject.optionalString(name: String): String? {
    val element = this[name] ?: return null
    if (element is JsonNull) return null
    val primitive = element as? JsonPrimitive
        ?: throw IllegalArgumentException("Campo backup non valido: $name")
    require(primitive.isString) { "Campo backup non valido: $name" }
    return primitive.content
}

private fun JsonObject.requiredInt(name: String): Int {
    val primitive = requiredPrimitive(name)
    require(!primitive.isString) { "Campo backup non valido: $name" }
    return primitive.intOrNull
        ?: throw IllegalArgumentException("Campo backup mancante o non valido: $name")
}

private fun JsonObject.requiredLong(name: String): Long {
    val primitive = requiredPrimitive(name)
    require(!primitive.isString) { "Campo backup non valido: $name" }
    return primitive.longOrNull
        ?: throw IllegalArgumentException("Campo backup mancante o non valido: $name")
}

private fun JsonObject.requiredDouble(name: String): Double {
    val primitive = requiredPrimitive(name)
    require(!primitive.isString) { "Campo backup non valido: $name" }
    return primitive.doubleOrNull
        ?: throw IllegalArgumentException("Campo backup mancante o non valido: $name")
}

private fun JsonObject.requiredBoolean(name: String): Boolean {
    val primitive = requiredPrimitive(name)
    require(!primitive.isString) { "Campo backup non valido: $name" }
    return primitive.booleanOrNull
        ?: throw IllegalArgumentException("Campo backup mancante o non valido: $name")
}

private fun JsonObject.requiredArray(name: String): JsonArray =
    this[name] as? JsonArray
        ?: throw IllegalArgumentException("Campo backup mancante o non valido: $name")

private fun JsonObject.requiredObject(name: String): JsonObject =
    this[name] as? JsonObject
        ?: throw IllegalArgumentException("Campo backup mancante o non valido: $name")

private fun JsonElement.requiredObject(context: String): JsonObject =
    this as? JsonObject
        ?: throw IllegalArgumentException("Elemento backup non valido: $context")

internal fun suggestedBackupFileName(epochMillis: Long = System.currentTimeMillis()): String {
    val instant = java.time.Instant.ofEpochMilli(epochMillis)
    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
        .withZone(java.time.ZoneId.systemDefault())
    return "TamalutRadio-backup-${formatter.format(instant)}.json"
}
