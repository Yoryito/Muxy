package com.muxy.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.muxy.app.data.MusicLibrary
import com.muxy.app.data.PlaylistRepository
import com.muxy.app.data.Song
import com.muxy.app.playback.PlayerConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Todo lo que pinta el inicio, ya calculado. */
data class HomeState(
    val libraryIsEmpty: Boolean = true,
    val quickTiles: List<QuickTile> = emptyList(),
    val recents: List<HomeCard> = emptyList(),
    val mostPlayed: List<Song> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val recentlyAdded: List<Song> = emptyList(),
)

class HomeViewModel(
    library: MusicLibrary,
    playlists: PlaylistRepository,
    private val player: PlayerConnection,
) : ViewModel() {

    /**
     * Un solo estado en vez de un flujo por sección: las secciones se calculan
     * de las mismas dos fuentes (librería y listas), y separarlas obligaría a la
     * pantalla a recomponerse varias veces por cada cambio.
     */
    val state: StateFlow<HomeState> = combine(
        library.observeSongs(),
        library.observeMostPlayed(),
        playlists.observeSummaries(),
    ) { songs, mostPlayed, playlistSummaries ->
        HomeState(
            libraryIsEmpty = songs.isEmpty(),
            quickTiles = quickTiles(
                playlists = playlistSummaries,
                hasPlayHistory = mostPlayed.isNotEmpty(),
            ),
            recents = mergeRecents(songs, playlistSummaries),
            mostPlayed = mostPlayed,
            artists = groupByArtist(songs),
            // `observeSongs` ya llega ordenado por fecha de alta descendente,
            // así que lo último descargado es simplemente lo primero.
            recentlyAdded = songs.take(12),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /**
     * Reproduce [song] poniendo en cola la sección de la que salió, no la
     * librería entera: tocar algo en "Lo que más suenas" debería seguir con lo
     * que más suena, no saltar a la canción siguiente por orden de descarga.
     */
    fun play(song: Song, queue: List<Song>) {
        val index = queue.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: return
        player.play(queue, index)
    }

    class Factory(
        private val library: MusicLibrary,
        private val playlists: PlaylistRepository,
        private val player: PlayerConnection,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(library, playlists, player) as T
    }
}
