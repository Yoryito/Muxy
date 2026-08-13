package com.muxy.app.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.muxy.app.data.MusicLibrary
import com.muxy.app.download.DownloadQueue
import com.muxy.app.download.DownloadStatus
import com.muxy.app.youtube.ResolveError
import com.muxy.app.youtube.YoutubeAudioResolver
import com.muxy.app.youtube.YoutubeResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "Muxy"

/** Cualquier URL de YouTube con `list=` — de playlist o de un vídeo dentro de una. */
private val PLAYLIST_LIST_ID = Regex("[?&]list=([a-zA-Z0-9_-]+)")

enum class SearchFailure {
    /** YouTube pide captcha. Esperar es lo único que ayuda. */
    RateLimited,

    /** Sin conexión o la petición no llegó. */
    Network,

    /** El extractor no supo leer la respuesta: probablemente toca actualizarlo. */
    Extraction,
}

data class SearchUiState(
    val query: String = "",
    val results: List<YoutubeResult> = emptyList(),
    val isSearching: Boolean = false,
    val failure: SearchFailure? = null,
    /** Cierto cuando una búsqueda terminó sin resultados. */
    val emptyResult: Boolean = false,
    /** Estado de las descargas en marcha, por id de vídeo. */
    val downloads: Map<String, DownloadStatus> = emptyMap(),
    /** Vídeos que ya están guardados: no tiene sentido volver a bajarlos. */
    val inLibrary: Set<String> = emptySet(),
    /** Si no es nulo, [results] es una playlist resuelta y no una búsqueda. */
    val playlistName: String? = null,
)

class SearchViewModel(
    private val resolver: YoutubeAudioResolver,
    private val downloads: DownloadQueue,
    library: MusicLibrary,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Las descargas viven en WorkManager, no aquí: esto solo las mira.
        viewModelScope.launch {
            downloads.statuses().collect { statuses ->
                _state.update { it.copy(downloads = statuses) }
            }
        }
        viewModelScope.launch {
            library.observeDownloadedIds().collect { ids ->
                _state.update { it.copy(inLibrary = ids) }
            }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        // Sin debounce cada pulsación lanzaría una petición a YouTube, que es
        // justo lo que dispara el captcha.
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update {
                it.copy(results = emptyList(), emptyResult = false, failure = null, playlistName = null)
            }
            return
        }

        // Pegar una URL de playlist es un gesto deliberado, no una tecla más:
        // no hace falta esperar el debounce como con una búsqueda de texto.
        val listId = PLAYLIST_LIST_ID.find(query)?.groupValues?.get(1)
        searchJob = viewModelScope.launch {
            if (listId != null) {
                runPlaylistResolve(query, listId)
            } else {
                delay(450)
                runSearch(query)
            }
        }
    }

    fun retry() {
        searchJob?.cancel()
        val query = _state.value.query
        val listId = PLAYLIST_LIST_ID.find(query)?.groupValues?.get(1)
        searchJob = viewModelScope.launch {
            if (listId != null) runPlaylistResolve(query, listId) else runSearch(query)
        }
    }

    /** El botón "Descargar todas" de una playlist resuelta. */
    fun onDownloadAllRequested() {
        _state.value.results.forEach(::onDownloadRequested)
    }

    /**
     * Encola la descarga y se desentiende. Todo el trabajo —resolver, bajar,
     * convertir, etiquetar— ocurre en el worker, para que siga aunque el usuario
     * cierre la app.
     */
    fun onDownloadRequested(result: YoutubeResult) {
        val current = _state.value
        if (result.videoId in current.inLibrary) return
        when (current.downloads[result.videoId]) {
            DownloadStatus.Queued, is DownloadStatus.Running, DownloadStatus.Done -> return
            else -> downloads.enqueue(result)
        }
    }

    fun onCancelRequested(result: YoutubeResult) {
        downloads.cancel(result.videoId)
    }

    private suspend fun runSearch(query: String) {
        if (query.isBlank()) return
        _state.update { it.copy(isSearching = true, failure = null, emptyResult = false, playlistName = null) }

        val outcome = runCatching { resolver.search(query) }

        // La consulta puede haber cambiado mientras la petición estaba en vuelo.
        if (_state.value.query != query) return

        outcome
            .onSuccess { results ->
                _state.update {
                    it.copy(
                        isSearching = false,
                        results = results,
                        emptyResult = results.isEmpty(),
                    )
                }
            }
            .onFailure { error ->
                Log.w(TAG, "Búsqueda fallida: $query", error)
                _state.update {
                    it.copy(
                        isSearching = false,
                        results = emptyList(),
                        failure = error.toFailure(),
                    )
                }
            }
    }

    private suspend fun runPlaylistResolve(query: String, listId: String) {
        _state.update { it.copy(isSearching = true, failure = null, emptyResult = false, playlistName = null) }

        val outcome = runCatching {
            resolver.resolvePlaylist("https://www.youtube.com/playlist?list=$listId")
        }

        if (_state.value.query != query) return

        outcome
            .onSuccess { playlist ->
                _state.update {
                    it.copy(
                        isSearching = false,
                        results = playlist.items,
                        emptyResult = playlist.items.isEmpty(),
                        playlistName = playlist.title,
                    )
                }
            }
            .onFailure { error ->
                Log.w(TAG, "No se pudo resolver la playlist: $query", error)
                _state.update {
                    it.copy(
                        isSearching = false,
                        results = emptyList(),
                        failure = error.toFailure(),
                        playlistName = null,
                    )
                }
            }
    }

    private fun Throwable.toFailure(): SearchFailure = when (this) {
        is ResolveError.RateLimited -> SearchFailure.RateLimited
        is ResolveError.Network -> SearchFailure.Network
        else -> SearchFailure.Extraction
    }

    class Factory(
        private val resolver: YoutubeAudioResolver,
        private val downloads: DownloadQueue,
        private val library: MusicLibrary,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SearchViewModel(resolver, downloads, library) as T
    }
}
