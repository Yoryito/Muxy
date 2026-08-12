package com.muxy.app.youtube

/** Un resultado de búsqueda, ya reducido a lo que la interfaz necesita. */
data class YoutubeResult(
    val videoId: String,
    val url: String,
    val title: String,
    val uploader: String?,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
)

/** Una pista de audio concreta, lista para descargar. */
data class AudioStream(
    val url: String,
    val format: String,
    val bitrateKbps: Int,
    val mimeType: String?,
)

/** Metadatos del vídeo, para etiquetar el archivo resultante. */
data class YoutubeTrack(
    val videoId: String,
    val title: String,
    val uploader: String?,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
    val audio: AudioStream,
)

sealed class ResolveError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** YouTube pide captcha: han detectado demasiadas peticiones. */
    class RateLimited(cause: Throwable? = null) : ResolveError("reCaptcha", cause)

    /** El vídeo existe pero no se puede reproducir (privado, con edad, geobloqueado). */
    class Unavailable(reason: String, cause: Throwable? = null) : ResolveError(reason, cause)

    /** No hay pista de audio utilizable. */
    class NoAudio : ResolveError("Sin pista de audio")

    /** Fallo de red. */
    class Network(cause: Throwable? = null) : ResolveError("Error de red", cause)

    /**
     * El extractor no supo leer la respuesta de YouTube. Es la señal de que
     * YouTube ha cambiado algo y toca actualizar NewPipeExtractor.
     */
    class ExtractionFailed(cause: Throwable? = null) : ResolveError("Extracción fallida", cause)
}

/**
 * Frontera con YouTube.
 *
 * Existe como interfaz a propósito: la extracción es la parte más frágil de la
 * app y se rompe cada vez que YouTube cambia algo. Tenerla aislada aquí
 * significa que cambiar de backend (por ejemplo a yt-dlp) toca un solo sitio.
 */
interface YoutubeAudioResolver {

    suspend fun search(query: String): List<YoutubeResult>

    /** Resuelve la mejor pista de audio del vídeo. */
    suspend fun resolve(videoId: String): YoutubeTrack
}
