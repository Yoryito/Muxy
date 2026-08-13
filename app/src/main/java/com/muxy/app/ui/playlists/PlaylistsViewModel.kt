package com.muxy.app.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.muxy.app.data.Playlist
import com.muxy.app.data.PlaylistRepository
import com.muxy.app.data.PlaylistSummary
import com.muxy.app.data.Song
import com.muxy.app.playback.PlayerConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsViewModel(
    private val playlists: PlaylistRepository,
    private val player: PlayerConnection,
) : ViewModel() {

    val summaries: StateFlow<List<PlaylistSummary>> = playlists.observeSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _openId = MutableStateFlow<Long?>(null)
    val openId: StateFlow<Long?> = _openId.asStateFlow()

    /**
     * La lista abierta y sus canciones salen de la base y no de una copia en
     * memoria, así que renombrarla o quitarle una canción se ve solo, sin tener
     * que refrescar nada a mano.
     */
    val openPlaylist: StateFlow<Playlist?> = _openId
        .flatMapLatest { id -> if (id == null) flowOf(null) else playlists.observePlaylist(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val openSongs: StateFlow<List<Song>> = _openId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else playlists.observeSongs(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun open(id: Long) {
        _openId.value = id
    }

    fun closeDetail() {
        _openId.value = null
    }

    /** En qué listas está una canción, para marcarlas al ofrecer añadirla. */
    fun playlistsContaining(songId: Long): Flow<Set<Long>> = playlists.observePlaylistsOf(songId)

    /**
     * Crea una lista y, si viene de "añadir a una playlist", mete dentro la
     * canción (o canciones, desde la selección múltiple) de la que se partió:
     * crear la lista y luego tener que buscarla para añadirlas sería dar un
     * rodeo por nada.
     */
    fun create(name: String, fallbackName: String, songIds: Set<Long> = emptySet()) {
        viewModelScope.launch {
            val id = playlists.create(name, fallbackName)
            if (songIds.isNotEmpty()) playlists.addSongs(id, songIds)
        }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch { playlists.rename(id, name) }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            if (_openId.value == id) _openId.value = null
            playlists.delete(id)
        }
    }

    /** Marca y desmarca desde la hoja de "añadir a una playlist". */
    fun toggleSong(playlistId: Long, songId: Long, isMember: Boolean) {
        viewModelScope.launch {
            if (isMember) {
                playlists.removeSong(playlistId, songId)
            } else {
                playlists.addSong(playlistId, songId)
            }
        }
    }

    fun removeSong(playlistId: Long, songId: Long) {
        viewModelScope.launch { playlists.removeSong(playlistId, songId) }
    }

    fun reorder(playlistId: Long, orderedSongIds: List<Long>) {
        viewModelScope.launch { playlists.reorder(playlistId, orderedSongIds) }
    }

    /** El "añadir a una playlist" en bloque de la selección múltiple de la librería. */
    fun addSongs(playlistId: Long, songIds: Set<Long>) {
        viewModelScope.launch { playlists.addSongs(playlistId, songIds) }
    }

    /** Reproduce la lista abierta empezando por [song]. */
    fun play(song: Song) {
        val queue = openSongs.value
        val index = queue.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: return
        player.play(queue, index)
    }

    /** El botón de reproducir de la cabecera: la lista entera desde el principio. */
    fun playAll() {
        val queue = openSongs.value
        if (queue.isNotEmpty()) player.play(queue, 0)
    }

    class Factory(
        private val playlists: PlaylistRepository,
        private val player: PlayerConnection,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlaylistsViewModel(playlists, player) as T
    }
}
