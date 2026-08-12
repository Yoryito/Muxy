package com.muxy.app.download

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Convierte lo que baje de YouTube (normalmente Opus dentro de WebM) a M4A/AAC.
 *
 * Usa Media3 Transformer, que por dentro es MediaCodec: sin binarios de
 * terceros. Ese es también el motivo de que la salida sea M4A y no MP3 — el
 * único camino a MP3 pasaría por volver a meter FFmpeg.
 *
 * Cuando la entrada ya viene en AAC, Transformer se limita a remuxear en vez de
 * recodificar, así que esos casos salen prácticamente gratis.
 */
class AudioTranscoder(private val context: Context) {

    /**
     * Transcodifica [input] a [output] y devuelve la duración resultante en ms.
     *
     * Todo ocurre en el hilo principal a propósito: Transformer se ata al
     * `Looper` del hilo donde se construye y exige que `start` y `cancel` se
     * llamen desde ahí. El trabajo pesado lo hace en sus propios hilos.
     */
    suspend fun toM4a(
        input: File,
        output: File,
        onProgress: suspend (Float) -> Unit,
    ): Long = withContext(Dispatchers.Main) {
        var transformer: Transformer? = null

        // Transformer no avisa del avance: hay que preguntárselo.
        val poller = launch {
            val holder = ProgressHolder()
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                val current = transformer ?: continue
                if (current.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress / 100f)
                }
            }
        }

        try {
            suspendCancellableCoroutine<Long> { continuation ->
                val built = Transformer.Builder(context)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            continuation.resume(result.approximateDurationMs)
                        }

                        override fun onError(
                            composition: Composition,
                            result: ExportResult,
                            exception: ExportException,
                        ) {
                            continuation.resumeWithException(exception)
                        }
                    })
                    .build()

                transformer = built
                built.start(
                    EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
                        // Los streams de solo audio no traen vídeo, pero algún
                        // formato mete una miniatura como pista de vídeo.
                        .setRemoveVideo(true)
                        .build(),
                    output.absolutePath,
                )
            }
        } catch (cancellation: CancellationException) {
            // Se atrapa aquí y no en invokeOnCancellation porque cancel() solo
            // vale desde el hilo del Looper, y este catch ya está en él.
            transformer?.cancel()
            output.delete()
            throw cancellation
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            poller.cancel()
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 300L
    }
}
