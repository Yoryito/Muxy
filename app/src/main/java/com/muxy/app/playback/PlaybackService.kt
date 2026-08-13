package com.muxy.app.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.muxy.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

/**
 * Mantiene la reproducción viva cuando la app pasa a segundo plano y publica
 * la sesión al sistema, que es lo que hace aparecer los controles en la
 * notificación y en la pantalla de bloqueo.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    /** Para leer carátulas sin bloquear el hilo principal del servicio. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * El servicio no tiene inyección de dependencias (lo construye el
     * framework), así que crea su propia instancia — ver el comentario de la
     * clase para por qué eso es seguro.
     */
    private val preferences by lazy { PlaybackPreferences(applicationContext) }

    private val artworkListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            attachArtwork()
            applyGain(mediaItem)
            // Solo al terminar sola una canción: un salto a mano ("siguiente")
            // no debe frenarse por este ajuste, igual que ya pasa con el
            // temporizador de apagado en modo "fin de canción".
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && !preferences.autoplay.value) {
                mediaSession?.player?.pause()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                // Deja que ExoPlayer gestione el foco de audio: baja el volumen
                // con las notificaciones y pausa si otra app toma el audio.
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(artworkListener)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .build()

        // Si se toca el interruptor de ajustes a media canción, se nota ya:
        // sin esto habría que esperar a la siguiente pista para que se aplicara.
        scope.launch {
            preferences.normalizeVolume.collect { applyGain(mediaSession?.player?.currentMediaItem) }
        }
    }

    /**
     * Le pega los bytes de la carátula a la canción que está sonando.
     *
     * Se hace aquí y solo con la actual, en vez de al montar la cola, porque la
     * cola es la librería entera: leer y arrastrar una imagen por canción serían
     * decenas de megas de `ByteArray` viajando por Binder y un tirón en el hilo
     * principal justo al dar a reproducir.
     *
     * El `replaceMediaItem` no corta el audio: el `MediaItem` que se pone en su
     * sitio conserva la misma URI, así que ExoPlayer solo actualiza los metadatos
     * en vez de volver a preparar la fuente.
     */
    private fun attachArtwork() {
        val player = mediaSession?.player ?: return
        val item = player.currentMediaItem ?: return

        // Corta el bucle: al reemplazar el elemento vuelven a saltar eventos, y
        // sin esto se estaría releyendo la misma imagen para siempre.
        if (item.mediaMetadata.artworkData != null) return

        val path = item.mediaMetadata.extras?.getString(EXTRA_COVER_PATH) ?: return
        val index = player.currentMediaItemIndex

        scope.launch {
            val bytes = withContext(Dispatchers.IO) { CoverBytes.load(path) } ?: return@launch

            // Leer del disco tarda, y para cuando vuelve la canción puede haber
            // cambiado; reemplazar a ciegas le pondría la carátula a otra.
            val current = mediaSession?.player ?: return@launch
            if (current.currentMediaItemIndex != index) return@launch
            if (current.currentMediaItem?.mediaId != item.mediaId) return@launch

            current.replaceMediaItem(
                index,
                item.buildUpon()
                    .setMediaMetadata(
                        item.mediaMetadata.buildUpon()
                            .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            .build(),
                    )
                    .build(),
            )
        }
    }

    /**
     * Aplica la atenuación de [EXTRA_GAIN_DB], que solo puede bajar el volumen:
     * el campo se calcula al descargar para que ninguna canción destaque sobre
     * las demás, y [Player.volume] no deja subir por encima de 1.0 lo que suena
     * flojo — solo hay margen para bajar lo que suena fuerte.
     *
     * Con la normalización desactivada se ignora el campo y suena tal cual se
     * descargó.
     */
    private fun applyGain(mediaItem: MediaItem?) {
        val gainDb = if (preferences.normalizeVolume.value) {
            mediaItem?.mediaMetadata?.extras?.getFloat(EXTRA_GAIN_DB) ?: 0f
        } else {
            0f
        }
        mediaSession?.player?.volume = 10f.pow(gainDb / 20f)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Si el usuario descarta la app y no hay nada sonando, no tiene sentido
        // dejar el servicio (ni su notificación) por ahí.
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.run {
            player.removeListener(artworkListener)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
