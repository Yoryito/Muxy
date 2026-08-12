package com.muxy.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.muxy.app.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Lo que la interfaz necesita saber de la reproducción, y nada más. */
data class PlaybackState(
    val songId: Long? = null,
    val title: String = "",
    val artist: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val coverArtPath: String? = null,
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
            coverArtPath = c.mediaMetadata.artworkUri?.path,
        )
    }

    private fun withController(action: MediaController.() -> Unit) {
        val c = controller
        if (c != null) action(c) else pending = action
    }
}

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(File(filePath).toUri())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(coverArtPath?.let { File(it).toUri() })
            .build(),
    )
    .build()

private fun File.toUri(): android.net.Uri = android.net.Uri.fromFile(this)
