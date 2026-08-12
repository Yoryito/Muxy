package com.muxy.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.muxy.app.data.MusicLibrary
import com.muxy.app.data.Song
import com.muxy.app.playback.PlaybackState
import com.muxy.app.playback.PlayerConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val library: MusicLibrary,
    private val player: PlayerConnection,
) : ViewModel() {

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    val songs: StateFlow<List<Song>> = library.observeSongs()
        .onEach { _loading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playback: StateFlow<PlaybackState> = player.state

    init {
        player.connect()
        viewModelScope.launch { library.sync() }

        // El reproductor no emite la posición de forma continua, así que
        // mientras algo suena se refresca a mano para mover la barra.
        viewModelScope.launch {
            while (true) {
                if (player.state.value.isPlaying) player.refresh()
                delay(500)
            }
        }
    }

    fun play(song: Song) {
        val current = songs.value
        val index = current.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: return
        player.play(current, index)
    }

    fun togglePlayPause() = player.togglePlayPause()

    fun next() = player.next()

    fun previous() = player.previous()

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun toggleShuffle() = player.toggleShuffle()

    fun cycleRepeat() = player.cycleRepeat()

    fun delete(song: Song) {
        viewModelScope.launch { library.remove(song) }
    }

    class Factory(
        private val library: MusicLibrary,
        private val player: PlayerConnection,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryViewModel(library, player) as T
    }
}
