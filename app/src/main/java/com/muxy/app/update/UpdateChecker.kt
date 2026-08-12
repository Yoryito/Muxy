package com.muxy.app.update

import android.util.Log
import com.muxy.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "Muxy"

/** Una versión publicada en GitHub que es más nueva que la instalada. */
data class AvailableUpdate(
    val versionName: String,
    /** Las notas de la release, ya recortadas para caber en un diálogo. */
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck
    data class Available(val update: AvailableUpdate) : UpdateCheck

    /** Sin conexión, GitHub caído o una release sin APK: se trata igual. */
    data object Failed : UpdateCheck
}

/**
 * Mira si hay una versión nueva en las releases de GitHub.
 *
 * Sin Play Store no hay actualización automática, así que la app se la busca
 * sola. El repositorio es público, así que la API va sin token: eso limita a 60
 * peticiones por hora y por IP, de sobra para una comprobación al abrir.
 *
 * `releases/latest` deja fuera los borradores y las prereleases, que es justo lo
 * que se quiere: publicar un borrador no debe avisar a nadie.
 */
class UpdateChecker(
    private val client: OkHttpClient,
    private val repo: String = BuildConfig.UPDATE_REPO,
    private val currentVersion: String = BuildConfig.VERSION_NAME,
) {

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        val release = runCatching { fetchLatest() }
            .onFailure { Log.w(TAG, "No se pudo comprobar si hay actualización", it) }
            .getOrNull()
            ?: return@withContext UpdateCheck.Failed

        val version = release.tagName.removePrefix("v").trim()
        if (!isNewer(version, currentVersion)) return@withContext UpdateCheck.UpToDate

        // Una release sin APK no sirve de nada: mejor callarse que ofrecer una
        // actualización que al tocarla no tiene qué descargar.
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: return@withContext UpdateCheck.Failed.also {
                Log.w(TAG, "La release $version no trae ningún APK")
            }

        UpdateCheck.Available(
            AvailableUpdate(
                versionName = version,
                notes = release.body.orEmpty().trim().take(MAX_NOTES_CHARS),
                apkUrl = apk.url,
                sizeBytes = apk.size,
            ),
        )
    }

    private fun fetchLatest(): GithubRelease {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()

        return client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code} al pedir la última release" }
            json.decodeFromString<GithubRelease>(response.body.string())
        }
    }

    private companion object {
        const val MAX_NOTES_CHARS = 600

        /** La respuesta de GitHub trae decenas de campos que aquí no se usan. */
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Compara dos versiones tramo a tramo y **como números**.
 *
 * Comparar las cadenas tal cual pondría "0.1.9" por delante de "0.1.10", que es
 * exactamente el salto que va a ocurrir en cuanto haya diez versiones.
 */
internal fun isNewer(candidate: String, current: String): Boolean {
    val a = candidate.toVersionParts() ?: return false
    val b = current.toVersionParts() ?: return false

    for (i in 0 until maxOf(a.size, b.size)) {
        val left = a.getOrElse(i) { 0 }
        val right = b.getOrElse(i) { 0 }
        if (left != right) return left > right
    }
    return false
}

private fun String.toVersionParts(): List<Int>? {
    val parts = takeWhile { it.isDigit() || it == '.' }
        .split(".")
        .filter { it.isNotEmpty() }
        .mapNotNull { it.toIntOrNull() }
    return parts.ifEmpty { null }
}

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
    val size: Long = 0,
)
