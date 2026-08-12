package com.muxy.app.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
)

class SearchViewModel(
    private val resolver: YoutubeAudioResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        // Sin debounce cada pulsación lanzaría una petición a YouTube, que es
        // justo lo que dispara el captcha.
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(results = emptyList(), emptyResult = false, failure = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(450)
            runSearch(query)
        }
    }

    fun retry() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(_state.value.query) }
    }

    /**
     * Fase 3: solo resuelve la pista y la registra en el log, para comprobar
     * que la extracción funciona antes de montar la descarga encima.
     */
    fun onDownloadRequested(result: YoutubeResult) {
        viewModelScope.launch {
            runCatching { resolver.resolve(result.videoId) }
                .onSuccess { track ->
                    Log.i(
                        TAG,
                        "Resuelto '${track.title}' — ${track.audio.format} " +
                            "${track.audio.bitrateKbps}kbps · ${track.audio.url.take(90)}…",
                    )
                }
                .onFailure { Log.w(TAG, "No se pudo resolver ${result.videoId}", it) }
        }
    }

    private suspend fun runSearch(query: String) {
        if (query.isBlank()) return
        _state.update { it.copy(isSearching = true, failure = null, emptyResult = false) }

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

    private fun Throwable.toFailure(): SearchFailure = when (this) {
        is ResolveError.RateLimited -> SearchFailure.RateLimited
        is ResolveError.Network -> SearchFailure.Network
        else -> SearchFailure.Extraction
    }

    class Factory(private val resolver: YoutubeAudioResolver) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SearchViewModel(resolver) as T
    }
}
