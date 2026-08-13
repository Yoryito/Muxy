package com.muxy.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.muxy.app.data.MusicLibrary
import com.muxy.app.data.Song
import com.muxy.app.playback.PlaybackState
import com.muxy.app.playback.PlayerConnection
import com.muxy.app.playback.SleepTimerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Collator
import java.text.Normalizer

/** Cómo se ordena la librería. */
enum class LibrarySort {
    /** Lo último que ha traído Pochi, arriba. */
    Recent,

    /** Lo primero que se descargó, arriba. */
    Oldest,
    Title,
    Artist,
    Duration,
}

class LibraryViewModel(
    private val library: MusicLibrary,
    private val player: PlayerConnection,
) : ViewModel() {

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(LibrarySort.Recent)
    val sort: StateFlow<LibrarySort> = _sort.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    /** Todo lo que hay en la librería, sin filtrar. */
    private val allSongs: StateFlow<List<Song>> = library.observeSongs()
        .onEach { _loading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Lo que se está viendo: es también lo que se pone en la cola al reproducir. */
    val songs: StateFlow<List<Song>> =
        combine(allSongs, _query, _sort) { songs, query, sort ->
            songs.filterBy(query).sortedWith(sort.comparator())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Si la librería está vacía de verdad, o solo lo parece por el filtro. */
    val libraryIsEmpty: StateFlow<Boolean> = allSongs
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Lo último escuchado, para la sección de recientes al principio de la pantalla. */
    val recentlyPlayed: StateFlow<List<Song>> = library.observeRecentlyPlayed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playback: StateFlow<PlaybackState> = player.state
    val sleepTimer: StateFlow<SleepTimerState> = player.sleepTimer

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

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onSortChange(value: LibrarySort) {
        _sort.value = value
    }

    /** La cola es lo que se está viendo, no la librería entera. */
    fun play(song: Song) {
        val current = songs.value
        val index = current.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: return
        player.play(current, index)
    }

    /** Tocar una canción de "recientes" reproduce esa lista, no la librería filtrada. */
    fun playRecent(song: Song) {
        val current = recentlyPlayed.value
        val index = current.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: return
        player.play(current, index)
    }

    fun togglePlayPause() = player.togglePlayPause()

    fun next() = player.next()

    fun previous() = player.previous()

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun toggleShuffle() = player.toggleShuffle()

    fun cycleRepeat() = player.cycleRepeat()

    fun cyclePlaybackSpeed() = player.cyclePlaybackSpeed()

    fun setSleepTimer(durationMs: Long) = player.setSleepTimer(durationMs)

    fun setSleepTimerEndOfSong() = player.setSleepTimerEndOfSong()

    fun cancelSleepTimer() = player.cancelSleepTimer()

    /**
     * Borra la canción del disco y de la base. Sale primero de la cola: si se
     * quedara, el reproductor acabaría llegando a un archivo que ya no existe.
     * Sus filas en las playlists se van solas, en cascada.
     */
    fun delete(song: Song) {
        viewModelScope.launch {
            player.removeSong(song.id)
            library.remove(song)
        }
    }

    /** Entra o sale del modo selección. Salir vacía lo marcado. */
    fun toggleSelectionMode() {
        _selectionMode.update { !it }
        _selectedIds.value = emptySet()
    }

    fun exitSelectionMode() {
        _selectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleSelected(id: Long) {
        _selectedIds.update { if (id in it) it - id else it + id }
    }

    /** Borra todo lo marcado y sale del modo selección. */
    fun deleteSelected() {
        val ids = _selectedIds.value
        songs.value.filter { it.id in ids }.forEach(::delete)
        exitSelectionMode()
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

/**
 * Filtra por título y artista ignorando mayúsculas y tildes: quien busca
 * "cancion" espera encontrar "Canción", y en castellano eso pasa constantemente.
 */
private fun List<Song>.filterBy(query: String): List<Song> {
    val needle = query.foldForSearch()
    if (needle.isBlank()) return this
    return filter { song ->
        song.title.foldForSearch().contains(needle) ||
            song.artist?.foldForSearch()?.contains(needle) == true
    }
}

private val DIACRITICS = "\\p{Mn}+".toRegex()

private fun String.foldForSearch(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

/**
 * El orden alfabético va con [Collator] y no con `compareBy`: comparar cadenas
 * por code point deja la "Ñ" y todo lo acentuado detrás de la "Z".
 */
private fun LibrarySort.comparator(): Comparator<Song> {
    val collator = Collator.getInstance()
    return when (this) {
        LibrarySort.Recent -> compareByDescending<Song> { it.addedAt }
        LibrarySort.Oldest -> compareBy<Song> { it.addedAt }
        LibrarySort.Title -> Comparator { a, b -> collator.compare(a.title, b.title) }
        // Sin artista se va al final, no al principio: son las importadas a mano
        // y no aportan nada arriba del todo.
        LibrarySort.Artist -> Comparator { a, b ->
            when {
                a.artist == null && b.artist == null -> collator.compare(a.title, b.title)
                a.artist == null -> 1
                b.artist == null -> -1
                else -> collator.compare(a.artist, b.artist)
                    .takeIf { it != 0 } ?: collator.compare(a.title, b.title)
            }
        }
        LibrarySort.Duration -> compareBy<Song> { it.durationMs }
    }
}
