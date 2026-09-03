package com.tamalut.radio.feature.radio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

fun interface RadioStreamValidator {
    suspend fun validate(url: String)
}

internal data class RadioProbeResponse(
    val statusCode: Int,
    val contentType: String? = null,
    val location: String? = null,
    val bodyPrefix: String = "",
)

internal fun interface RadioHttpProbe {
    suspend fun execute(url: String): RadioProbeResponse
}

internal class HttpsRadioStreamValidator(
    private val probe: RadioHttpProbe = UrlConnectionRadioHttpProbe(),
    private val maxRedirects: Int = DEFAULT_MAX_REDIRECTS,
) : RadioStreamValidator {
    override suspend fun validate(url: String) {
        var currentUrl = normalizeHttpsStreamUrl(url)
        val visited = linkedSetOf<String>()

        repeat(maxRedirects + 1) { hop ->
            if (!visited.add(currentUrl)) {
                throw IllegalArgumentException("Il redirect dello stream forma un ciclo")
            }

            val response = try {
                probe.execute(currentUrl)
            } catch (error: Exception) {
                throw IllegalArgumentException(
                    "Impossibile raggiungere lo stream HTTPS: ${error.message ?: "errore di rete"}",
                    error,
                )
            }

            if (response.statusCode in REDIRECT_STATUS_CODES) {
                if (hop >= maxRedirects) {
                    throw IllegalArgumentException("Troppi redirect durante la verifica dello stream")
                }
                val location = response.location?.trim().orEmpty()
                require(location.isNotEmpty()) { "Redirect stream senza destinazione" }
                currentUrl = normalizeHttpsStreamUrl(URI(currentUrl).resolve(location).toString())
                return@repeat
            }

            require(response.statusCode in 200..299) {
                "Lo stream ha risposto con HTTP ${response.statusCode}"
            }

            if (isHls(currentUrl, response.contentType, response.bodyPrefix)) {
                require(!ABSOLUTE_CLEARTEXT_REFERENCE.containsMatchIn(response.bodyPrefix)) {
                    "La playlist HLS contiene un riferimento HTTP non sicuro"
                }
            }
            return
        }

        throw IllegalArgumentException("Impossibile completare la verifica dello stream")
    }

    private fun isHls(url: String, contentType: String?, bodyPrefix: String): Boolean {
        val normalizedType = contentType.orEmpty().lowercase(Locale.ROOT)
        return URI(url).path.orEmpty().lowercase(Locale.ROOT).endsWith(".m3u8") ||
            "mpegurl" in normalizedType ||
            bodyPrefix.trimStart().startsWith("#EXTM3U", ignoreCase = true)
    }

    private companion object {
        const val DEFAULT_MAX_REDIRECTS = 5
        val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
        val ABSOLUTE_CLEARTEXT_REFERENCE = Regex("(?i)http://")
    }
}

fun normalizeHttpsStreamUrl(input: String): String {
    val trimmed = input.trim()
    require(trimmed.isNotEmpty()) { "Inserisci una URL stream HTTPS" }

    val uri = try {
        URI(trimmed)
    } catch (error: Exception) {
        throw IllegalArgumentException("URL stream non valida", error)
    }

    require(uri.isAbsolute && uri.scheme.equals("https", ignoreCase = true)) {
        "La URL stream deve usare HTTPS"
    }
    require(!uri.host.isNullOrBlank()) { "La URL stream deve contenere un host valido" }

    val normalizedPort = if (uri.port == 443) -1 else uri.port
    return URI(
        "https",
        uri.userInfo,
        uri.host.lowercase(Locale.ROOT),
        normalizedPort,
        uri.path,
        uri.query,
        null,
    ).normalize().toASCIIString()
}

internal class UrlConnectionRadioHttpProbe(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectTimeoutMillis: Int = 6_000,
    private val readTimeoutMillis: Int = 6_000,
) : RadioHttpProbe {
    override suspend fun execute(url: String): RadioProbeResponse = withContext(dispatcher) {
        val connection = URL(url).openConnection() as? HttpsURLConnection
            ?: throw IllegalArgumentException("La verifica richiede una connessione HTTPS")
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.useCaches = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "TamalutRadio/1.0")
            connection.setRequestProperty(
                "Accept",
                "audio/*,application/vnd.apple.mpegurl,application/x-mpegURL,*/*;q=0.5",
            )

            val status = connection.responseCode
            val contentType = connection.contentType
            RadioProbeResponse(
                statusCode = status,
                contentType = contentType,
                location = connection.getHeaderField("Location"),
                bodyPrefix = if (status in 200..299 && shouldInspectBody(url, contentType)) {
                    readBoundedPrefix(connection)
                } else {
                    ""
                },
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun shouldInspectBody(url: String, contentType: String?): Boolean {
        val type = contentType.orEmpty().lowercase(Locale.ROOT)
        if (URI(url).path.orEmpty().lowercase(Locale.ROOT).endsWith(".m3u8")) return true
        if ("mpegurl" in type) return true
        return !type.startsWith("audio/")
    }

    private fun readBoundedPrefix(connection: HttpsURLConnection): String {
        val output = ByteArray(MAX_BODY_PREFIX_BYTES)
        var total = 0
        connection.inputStream.buffered().use { input ->
            while (total < output.size) {
                val read = input.read(output, total, output.size - total)
                if (read <= 0) break
                total += read
            }
        }
        return output.decodeToString(endIndex = total)
    }

    private companion object {
        const val MAX_BODY_PREFIX_BYTES = 32 * 1024
    }
}
