package com.muxy.app.ui.home

import com.muxy.app.data.PlaylistSummary
import com.muxy.app.data.Song
import java.text.Collator

/**
 * Lo que se pinta en el inicio, y cómo se calcula.
 *
 * Todo lo de este archivo es Kotlin puro a propósito — sin `Context`, sin
 * recursos, sin Compose — para que la parte que de verdad tiene reglas (qué
 * entra en cada sección y en qué orden) se pueda probar en la JVM. Las pantallas
 * solo lo pintan.
 */

/**
 * Un artista de la librería. No es una tabla: Muxy no tiene entidad de artista
 * porque las descargas de YouTube no traen más metadatos que título y canal, así
 * que se agrupa al vuelo desde las canciones.
 */
data class ArtistSummary(
    val name: String,
    val songCount: Int,
    val coverArtPath: String?,
)

/**
 * Una tarjeta de la rejilla de accesos rápidos: los dos atajos fijos y las
 * listas del usuario.
 */
sealed interface QuickTile {
    /** Toda la librería, sin filtrar. Siempre la primera. */
    data object AllDownloads : QuickTile

    /** Lo más escuchado. Solo aparece cuando hay historial que enseñar. */
    data object MyTop : QuickTile

    data class OfPlaylist(val playlist: PlaylistSummary) : QuickTile
}

/** Un elemento de un carrusel. Las tres cosas que el inicio sabe enseñar. */
sealed interface HomeCard {
    data class OfSong(val song: Song) : HomeCard
    data class OfPlaylist(val playlist: PlaylistSummary) : HomeCard
    data class OfArtist(val artist: ArtistSummary) : HomeCard
}

/** Los cuatro momentos del día que saluda el inicio. */
enum class Greeting { Morning, Afternoon, Evening }

/**
 * Agrupa la librería por artista.
 *
 * Las canciones sin artista se quedan fuera en vez de juntarse en un cajón de
 * "desconocido": son las importadas a mano, y un montón heterogéneo bajo un
 * nombre falso no es una sugerencia útil.
 *
 * Se ordena por número de canciones porque de eso va la sección: de quién tienes
 * más. El desempate alfabético va con [Collator] y no comparando cadenas, que
 * dejaría la "Ñ" y lo acentuado detrás de la "Z".
 */
fun groupByArtist(songs: List<Song>, limit: Int = 12): List<ArtistSummary> {
    val collator = Collator.getInstance()
    return songs
        .filter { !it.artist.isNullOrBlank() }
        .groupBy { it.artist!!.trim() }
        .map { (name, its) ->
            ArtistSummary(
                name = name,
                songCount = its.size,
                // La portada es la primera carátula que haya, no la de la primera
                // canción: si esa se importó a mano y no tiene, el artista se
                // quedaría con nenúfar liso teniendo carátulas de sobra.
                coverArtPath = its.firstNotNullOfOrNull { it.coverArtPath },
            )
        }
        .sortedWith(
            compareByDescending<ArtistSummary> { it.songCount }
                .thenComparator { a, b -> collator.compare(a.name, b.name) },
        )
        .take(limit)
}

/**
 * Mezcla canciones y listas en un solo carrusel de "escuchado hace poco",
 * ordenado por cuándo sonó cada cosa.
 *
 * Van juntas y no en dos filas separadas porque lo que el usuario recuerda es
 * "lo que estaba oyendo antes", sin distinguir si era una lista o una canción
 * suelta. Lo que no tiene fecha de escucha no entra: nunca ha sonado.
 */
fun mergeRecents(
    songs: List<Song>,
    playlists: List<PlaylistSummary>,
    limit: Int = 12,
): List<HomeCard> {
    val fromSongs = songs.mapNotNull { song ->
        song.lastPlayedAt?.let { it to HomeCard.OfSong(song) }
    }
    val fromPlaylists = playlists.mapNotNull { playlist ->
        playlist.lastPlayedAt?.let { it to HomeCard.OfPlaylist(playlist) }
    }
    return (fromSongs + fromPlaylists)
        .sortedByDescending { (playedAt, _) -> playedAt }
        .take(limit)
        .map { (_, card) -> card }
}

/**
 * Los accesos rápidos de la rejilla.
 *
 * "Mi Top" solo sale cuando ya hay algo escuchado: estrenar la app con un atajo
 * que lleva a una lista vacía es prometer algo que no está.
 */
fun quickTiles(
    playlists: List<PlaylistSummary>,
    hasPlayHistory: Boolean,
    limit: Int = 8,
): List<QuickTile> {
    val fixed = listOfNotNull(
        QuickTile.AllDownloads,
        QuickTile.MyTop.takeIf { hasPlayHistory },
    )
    // Las listas se ordenan por lo último que sonó, y las que nunca han sonado
    // van detrás por orden de creación: la rejilla es para volver a lo de siempre.
    val recentPlaylists = playlists
        .sortedWith(
            compareByDescending<PlaylistSummary> { it.lastPlayedAt ?: Long.MIN_VALUE }
                .thenByDescending { it.createdAt },
        )
        .map { QuickTile.OfPlaylist(it) }

    return (fixed + recentPlaylists).take(limit)
}

/**
 * Qué saludo toca según la hora (0-23).
 *
 * Los tramos son los del castellano hablado, que no coinciden con los del inglés:
 * aquí las tres de la tarde son tarde, no "afternoon" que empieza a las doce.
 */
fun greetingFor(hour: Int): Greeting = when (hour) {
    in 6..13 -> Greeting.Morning
    in 14..20 -> Greeting.Afternoon
    else -> Greeting.Evening
}
