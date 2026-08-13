package com.muxy.app.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.muxy.app.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Cómo se encadena la cola al terminar una canción. */
enum class RepeatMode {
    /** Al acabar la última, se para. */
    Off,

    /** Al acabar la última, vuelve a la primera. */
    All,

    /** Repite la canción actual indefinidamente. */
    One,
}

/** El temporizador de apagado, si hay uno puesto. */
sealed interface SleepTimerState {
    data object Off : SleepTimerState
    data class Counting(val remainingMs: Long) : SleepTimerState

    /** Se para al terminar la canción que suena ahora, sin cuenta atrás. */
    data object EndOfSong : SleepTimerState
}

/** Lo que la interfaz necesita saber de la reproducción, y nada más. */
data class PlaybackState(
    val songId: Long? = null,
    val title: String = "",
    val artist: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val coverArtPath: String? = null,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val hasNext: Boolean = false,
)

/**
 * Puente entre la interfaz y [PlaybackService].
 *
 * La conexión al servicio es asíncrona, así que las órdenes que llegan antes de
 * que el controlador exista se guardan y se ejecutan al conectar, en vez de
 * perderse en silencio.
 */
class PlayerConnection(private val context: Context) {

    private var controller: MediaController? = null
    private var pending: (MediaController.() -> Unit)? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    // Vive tanto como la propia conexión: el temporizador tiene que seguir
    // contando aunque la pantalla que lo puso se cierre o gire.
    private val timerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null

    private val _sleepTimer = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val sleepTimer: StateFlow<SleepTimerState> = _sleepTimer.asStateFlow()

    /**
     * Solo se engancha en el modo "al terminar la canción". `AUTO` es la razón
     * que Media3 da cuando una pista se acaba sola y pasa a la siguiente — con
     * un salto manual o un `seek` no se dispara, que es justo lo que se quiere:
     * saltar de canción a mano no debería cortar el temporizador.
     */
    private val endOfSongListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) cancelSleepTimer(pause = true)
        }
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                controller = runCatching { future.get() }.getOrNull()?.also {
                    it.addListener(listener)
                    pending?.invoke(it)
                    pending = null
                    refresh()
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        timerScope.cancel()
    }

    /** Reproduce [songs] empezando por [startIndex]. */
    fun play(songs: List<Song>, startIndex: Int) {
        val items = songs.map { it.toMediaItem() }
        withController {
            setMediaItems(items, startIndex, 0L)
            prepare()
            play()
        }
    }

    fun togglePlayPause() = withController {
        if (isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) = withController { seekTo(positionMs) }

    fun next() = withController { seekToNextMediaItem() }

    fun previous() = withController {
        // Comportamiento habitual: si ya han pasado unos segundos, el botón
        // reinicia la canción en vez de saltar a la anterior.
        if (currentPosition > 3_000 || !hasPreviousMediaItem()) seekTo(0) else seekToPreviousMediaItem()
    }

    fun toggleShuffle() = withController { shuffleModeEnabled = !shuffleModeEnabled }

    /** Recorre los tres modos en el orden habitual: ninguno → toda la cola → una. */
    fun cycleRepeat() = withController {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Pausa dentro de [durationMs]. Sustituye cualquier temporizador que hubiera puesto antes. */
    fun setSleepTimer(durationMs: Long) {
        clearTimer()
        _sleepTimer.value = SleepTimerState.Counting(durationMs)
        timerJob = timerScope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                delay(TIMER_TICK_MS.coerceAtMost(remaining))
                remaining -= TIMER_TICK_MS
                _sleepTimer.value = SleepTimerState.Counting(remaining.coerceAtLeast(0))
            }
            withController { pause() }
            _sleepTimer.value = SleepTimerState.Off
        }
    }

    /** Pausa en cuanto termine sola la canción que suena ahora. */
    fun setSleepTimerEndOfSong() {
        clearTimer()
        _sleepTimer.value = SleepTimerState.EndOfSong
        withController { addListener(endOfSongListener) }
    }

    /**
     * Quita el temporizador sin pausar. [pause] se pone a `true` solo cuando lo
     * llama el propio temporizador al cumplirse — desde fuera (botón "cancelar")
     * nunca se quiere cortar lo que está sonando, solo dejar de esperar a que se
     * pare sola.
     */
    fun cancelSleepTimer(pause: Boolean = false) {
        clearTimer()
        if (pause) withController { pause() }
    }

    private fun clearTimer() {
        timerJob?.cancel()
        timerJob = null
        withController { removeListener(endOfSongListener) }
        _sleepTimer.value = SleepTimerState.Off
    }

    /**
     * Saca una canción de la cola. Se llama al borrarla de la librería: si se
     * quedara, el reproductor llegaría a ella y se encontraría el archivo ya
     * borrado. Se recorre hacia atrás porque quitar un elemento recoloca los
     * índices de los que van detrás.
     */
    fun removeSong(songId: Long) = withController {
        val target = songId.toString()
        for (index in mediaItemCount - 1 downTo 0) {
            if (getMediaItemAt(index).mediaId == target) removeMediaItem(index)
        }
    }

    /** Refresca la posición mientras suena; el reproductor no la emite continuamente. */
    fun refresh() {
        val c = controller ?: return
        _state.value = PlaybackState(
            songId = c.currentMediaItem?.mediaId?.toLongOrNull(),
            title = c.mediaMetadata.title?.toString().orEmpty(),
            artist = c.mediaMetadata.artist?.toString(),
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            coverArtPath = c.mediaMetadata.extras?.getString(EXTRA_COVER_PATH),
            shuffleEnabled = c.shuffleModeEnabled,
            repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.One
                Player.REPEAT_MODE_ALL -> RepeatMode.All
                else -> RepeatMode.Off
            },
            hasNext = c.hasNextMediaItem(),
        )
    }

    private fun withController(action: MediaController.() -> Unit) {
        val c = controller
        if (c != null) action(c) else pending = action
    }

    private companion object {
        const val TIMER_TICK_MS = 1_000L
    }
}

/**
 * Dónde viaja la ruta de la carátula.
 *
 * No va en `artworkUri` a propósito: sería un `file://` de la carpeta privada de
 * la app, y el sistema lo prefiere sobre cualquier otra cosa para pintar la
 * notificación — pero no puede abrirlo desde su proceso, así que la miniatura se
 * quedaba en blanco. Sin URI, el sistema usa los bytes que le pega
 * [PlaybackService], y la ruta sigue aquí para que la interfaz de la propia app
 * (que sí puede leer el archivo) la pinte con Coil.
 */
const val EXTRA_COVER_PATH = "com.muxy.app.COVER_PATH"

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(File(filePath).toUri())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setExtras(
                Bundle().apply {
                    if (coverArtPath != null) putString(EXTRA_COVER_PATH, coverArtPath)
                },
            )
            .build(),
    )
    .build()

private fun File.toUri(): android.net.Uri = android.net.Uri.fromFile(this)
