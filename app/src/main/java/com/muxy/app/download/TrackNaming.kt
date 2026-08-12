package com.muxy.app.download

/** Título y artista ya separados, listos para guardar. */
data class TrackNames(val title: String, val artist: String?)

/**
 * Los títulos de YouTube no son metadatos: llegan como
 * `Artista - Canción (Official Video) [4K]`. Sin limpiarlos, la librería se
 * llena de filas con paréntesis de promoción y sin artista.
 *
 * La limpieza es deliberadamente conservadora: solo se quita lo que está entre
 * paréntesis o corchetes **y** contiene una palabra de la lista de ruido, para
 * no cargarse `(feat. Alguien)`, `(Remix)` ni `(Acústico)`, que sí son parte
 * del nombre de la canción.
 */
object TrackNaming {

    fun parse(videoTitle: String, uploader: String?): TrackNames {
        val cleanTitle = stripNoise(videoTitle)
        val channelArtist = uploader?.let(::stripTopicSuffix)?.takeIf { it.isNotBlank() }

        // "Artista - Canción": solo se parte por el primer separador, porque un
        // título puede llevar más guiones dentro del nombre de la canción.
        val split = SEPARATOR.find(cleanTitle)
        if (split != null) {
            val left = cleanTitle.take(split.range.first).trim()
            val right = cleanTitle.substring(split.range.last + 1).trim()
            if (left.isNotBlank() && right.isNotBlank()) {
                return TrackNames(title = right, artist = left)
            }
        }

        return TrackNames(
            title = cleanTitle.ifBlank { videoTitle },
            artist = channelArtist,
        )
    }

    /**
     * Nombre de archivo seguro. Se recorta porque algunos títulos de YouTube son
     * párrafos enteros y hay sistemas de archivos con límite por componente.
     */
    fun toFileName(title: String, artist: String?): String {
        val base = if (artist.isNullOrBlank()) title else "$artist - $title"
        return base
            .replace(ILLEGAL, " ")
            .replace(WHITESPACE, " ")
            .trim()
            .trimEnd('.')
            .take(80)
            .trim()
            .ifBlank { "cancion" }
    }

    private fun stripNoise(title: String): String =
        title.replace(NOISE, "")
            .replace(WHITESPACE, " ")
            .trim()
            .trim('-', '|', '·')
            .trim()

    /**
     * Los canales autogenerados de YouTube se llaman literalmente
     * `Artista - Topic`, así que son la mejor fuente de artista que hay.
     */
    private fun stripTopicSuffix(uploader: String): String =
        uploader.removeSuffix(" - Topic").trim()

    /** Guion normal, guion largo y raya, siempre con espacios alrededor. */
    private val SEPARATOR = Regex("\\s[-\u2013\u2014]\\s")

    private val WHITESPACE = Regex("\\s+")

    private val NOISE = Regex(
        "\\s*[(\\[][^()\\[\\]]*\\b(" +
            "official|oficial|video|v\u00eddeo|videoclip|audio|lyrics?|letra|" +
            "visualizer|hd|hq|4k|8k|mv|m/v|remaster|remastered|" +
            "clip officiel|con letra|video oficial|audio oficial|official music" +
            ")\\b[^()\\[\\]]*[)\\]]",
        RegexOption.IGNORE_CASE,
    )

    /** Reservados en FAT/exFAT, que es lo que lleva la tarjeta de muchos móviles. */
    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")
}
