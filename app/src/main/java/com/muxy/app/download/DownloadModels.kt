package com.muxy.app.download

/**
 * En qué punto del pipeline está una descarga.
 *
 * Se expone a la interfaz porque las etapas duran cosas muy distintas: bajar
 * puede tardar segundos y convertir bastante más, así que una sola barra sin
 * etiqueta parecería colgada.
 */
enum class DownloadStage {
    /** Pidiendo a YouTube la pista de audio real. */
    Resolving,

    Downloading,

    /** Transcodificando a M4A con Media3 Transformer. */
    Converting,

    /** Escribiendo metadatos y carátula. */
    Tagging,
}

/** Por qué falló una descarga, en términos que la interfaz sabe traducir. */
enum class DownloadFailure {
    /** YouTube pide captcha. Esperar es lo único que ayuda. */
    RateLimited,

    Network,

    /** El vídeo no se puede reproducir: privado, con edad o geobloqueado. */
    Unavailable,

    /** El extractor no supo leer la respuesta: toca actualizar NewPipeExtractor. */
    Extraction,

    /** El audio bajó bien pero no se pudo convertir a M4A. */
    Conversion,

    /** No se pudo escribir en el almacenamiento. */
    Storage,
}

sealed interface DownloadStatus {

    /** En cola, esperando a que WorkManager la arranque. */
    data object Queued : DownloadStatus

    /** [progress] es nulo cuando la etapa no sabe medirse (resolver, etiquetar). */
    data class Running(val stage: DownloadStage, val progress: Float?) : DownloadStatus

    data object Done : DownloadStatus

    data class Failed(val reason: DownloadFailure) : DownloadStatus
}
