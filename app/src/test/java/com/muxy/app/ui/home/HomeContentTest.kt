package com.muxy.app.ui.home

import com.muxy.app.data.PlaylistSummary
import com.muxy.app.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las reglas de qué entra en cada sección del inicio y en qué orden. Es la parte
 * del inicio que puede equivocarse en silencio: una sección mal ordenada se ve
 * plausible, y solo se nota comparando con lo que de verdad escuchaste.
 */
class HomeContentTest {

    private fun song(
        id: Long,
        title: String = "Cancion $id",
        artist: String? = "Artista",
        cover: String? = null,
        lastPlayedAt: Long? = null,
        playCount: Int = 0,
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        filePath = "/music/$id.m4a",
        coverArtPath = cover,
        addedAt = id,
        lastPlayedAt = lastPlayedAt,
        playCount = playCount,
    )

    private fun playlist(
        id: Long,
        name: String = "Lista $id",
        createdAt: Long = id,
        lastPlayedAt: Long? = null,
    ) = PlaylistSummary(
        id = id,
        name = name,
        createdAt = createdAt,
        songCount = 3,
        coverArtPath = null,
        lastPlayedAt = lastPlayedAt,
    )

    // --- groupByArtist ---

    @Test
    fun `agrupa por artista y cuenta las canciones`() {
        val artists = groupByArtist(
            listOf(
                song(1, artist = "Extremoduro"),
                song(2, artist = "Extremoduro"),
                song(3, artist = "Robe"),
            ),
        )
        assertEquals(listOf("Extremoduro", "Robe"), artists.map { it.name })
        assertEquals(listOf(2, 1), artists.map { it.songCount })
    }

    @Test
    fun `las canciones sin artista se quedan fuera`() {
        // Son las importadas a mano: juntarlas bajo "desconocido" no sugiere nada.
        val artists = groupByArtist(listOf(song(1, artist = null), song(2, artist = "  ")))
        assertTrue(artists.isEmpty())
    }

    @Test
    fun `la portada del artista es la primera caratula que haya`() {
        // Si la primera canción se importó sin carátula, el artista no debe
        // quedarse con el nenúfar liso teniendo carátulas más abajo.
        val artists = groupByArtist(
            listOf(
                song(1, artist = "Robe", cover = null),
                song(2, artist = "Robe", cover = "/covers/2.jpg"),
            ),
        )
        assertEquals("/covers/2.jpg", artists.single().coverArtPath)
    }

    @Test
    fun `empatados van por orden alfabetico respetando las tildes`() {
        // Comparando por code point, "Ñ" cae detrás de "Z" y "Ángel" detrás de todo.
        val artists = groupByArtist(listOf(song(1, artist = "Zoe"), song(2, artist = "Ñu")))
        assertEquals(listOf("Ñu", "Zoe"), artists.map { it.name })
    }

    // --- mergeRecents ---

    @Test
    fun `mezcla canciones y listas por cuando sonaron`() {
        val cards = mergeRecents(
            songs = listOf(song(1, lastPlayedAt = 100), song(2, lastPlayedAt = 300)),
            playlists = listOf(playlist(10, lastPlayedAt = 200)),
        )
        assertEquals(
            listOf("song-2", "playlist-10", "song-1"),
            cards.map {
                when (it) {
                    is HomeCard.OfSong -> "song-${it.song.id}"
                    is HomeCard.OfPlaylist -> "playlist-${it.playlist.id}"
                    is HomeCard.OfArtist -> "artist-${it.artist.name}"
                }
            },
        )
    }

    @Test
    fun `lo que nunca ha sonado no aparece en recientes`() {
        val cards = mergeRecents(
            songs = listOf(song(1, lastPlayedAt = null)),
            playlists = listOf(playlist(10, lastPlayedAt = null)),
        )
        assertTrue(cards.isEmpty())
    }

    @Test
    fun `recientes respeta el limite quedandose con lo mas nuevo`() {
        val songs = (1L..20L).map { song(it, lastPlayedAt = it) }
        val cards = mergeRecents(songs, playlists = emptyList(), limit = 3)
        assertEquals(3, cards.size)
        assertEquals(
            listOf(20L, 19L, 18L),
            cards.map { (it as HomeCard.OfSong).song.id },
        )
    }

    // --- quickTiles ---

    @Test
    fun `sin historial no se ofrece el atajo de Mi Top`() {
        // Llevaría a una lista vacía: es prometer algo que todavía no está.
        val tiles = quickTiles(playlists = emptyList(), hasPlayHistory = false)
        assertEquals(listOf<QuickTile>(QuickTile.AllDownloads), tiles)
    }

    @Test
    fun `con historial Mi Top va detras de todas las descargas`() {
        val tiles = quickTiles(playlists = emptyList(), hasPlayHistory = true)
        assertEquals(listOf(QuickTile.AllDownloads, QuickTile.MyTop), tiles)
    }

    @Test
    fun `las listas sonadas van antes que las que nunca han sonado`() {
        val tiles = quickTiles(
            playlists = listOf(
                playlist(1, createdAt = 500, lastPlayedAt = null),
                playlist(2, createdAt = 100, lastPlayedAt = 900),
            ),
            hasPlayHistory = false,
        )
        assertEquals(
            listOf(2L, 1L),
            tiles.filterIsInstance<QuickTile.OfPlaylist>().map { it.playlist.id },
        )
    }

    @Test
    fun `la rejilla no crece indefinidamente`() {
        val many = (1L..20L).map { playlist(it) }
        assertEquals(8, quickTiles(many, hasPlayHistory = true).size)
    }

    // --- greetingFor ---

    @Test
    fun `los tramos del saludo son los del castellano`() {
        // Las tres de la tarde son tarde, no "afternoon" empezando a las doce.
        assertEquals(Greeting.Morning, greetingFor(9))
        assertEquals(Greeting.Morning, greetingFor(13))
        assertEquals(Greeting.Afternoon, greetingFor(15))
        assertEquals(Greeting.Afternoon, greetingFor(20))
        assertEquals(Greeting.Evening, greetingFor(23))
        assertEquals(Greeting.Evening, greetingFor(3))
    }
}
