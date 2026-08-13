package com.muxy.app.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.media3.transformer.ExportException
import androidx.work.workDataOf
import com.muxy.app.MuxyApplication
import com.muxy.app.data.Song
import com.muxy.app.data.SongSource
import com.muxy.app.youtube.ResolveError
import com.muxy.app.youtube.YoutubeTrack
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException

private const val TAG = "Muxy"

/**
 * El pipeline completo de una descarga: resolver → bajar → convertir a M4A →
 * etiquetar → dar de alta en la librería.
 *
 * Va en un worker de WorkManager en primer plano para que sobreviva a que el
 * usuario salga de la app, que es justo lo que uno hace mientras algo baja.
 *
 * La pista **se resuelve aquí dentro**, no al pulsar el botón: las URLs de
 * googlevideo caducan en unas horas y van firmadas, así que resolver en la
 * pantalla y descargar más tarde daría un 403 difícil de leer.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val container get() = (applicationContext as MuxyApplication).container

    private var lastStage: DownloadStage? = null
    private var lastReportAtMs = 0L

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(inputData.getString(KEY_TITLE).orEmpty(), DownloadStage.Resolving, null)

    override suspend fun doWork(): Result {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return Result.failure()
        val searchTitle = inputData.getString(KEY_TITLE).orEmpty()

        val library = container.library

        // Puede haber caído ya por otra vía (una descarga anterior, un archivo
        // copiado a mano). Volver a bajarla solo dejaría un duplicado.
        if (library.isAlreadyDownloaded(videoId)) return Result.success()

        val temp = File(applicationContext.cacheDir, "downloads").apply { mkdirs() }
        var partial: File? = null
        var staging: File? = null
        var output: File? = null

        return try {
            report(searchTitle, DownloadStage.Resolving, null)
            val track = container.youtube.resolve(videoId)

            val names = TrackNaming.parse(track.title, track.uploader)
            val displayTitle = names.title.ifBlank { searchTitle }

            partial = File(temp, "$videoId.${track.audio.format}")
            report(displayTitle, DownloadStage.Downloading, 0f)
            container.httpFetcher.toFile(track.audio.url, partial) { fraction ->
                report(displayTitle, DownloadStage.Downloading, fraction)
            }

            // Se cocina en la carpeta de staging y solo al final entra en la
            // librería: si arrancara la app a media conversión, `sync()` daría
            // de alta un archivo a medio escribir.
            output = uniqueFile(library.musicDir, TrackNaming.toFileName(displayTitle, names.artist))
            staging = File(library.stagingDir, output.name)

            report(displayTitle, DownloadStage.Converting, 0f)
            val durationMs = container.transcoder.toM4a(partial, staging) { fraction ->
                report(displayTitle, DownloadStage.Converting, fraction)
            }

            // Nunca crítico: si falla o tarda raro, la canción se queda sin
            // normalizar en vez de perder la descarga entera por esto.
            val gainDb = LoudnessAnalyzer.measureRmsDb(staging)?.let(TrackGain::dbFor) ?: 0f

            report(displayTitle, DownloadStage.Tagging, null)
            val cover = saveCover(track, videoId)
            TrackTagger.write(
                file = staging,
                title = displayTitle,
                artist = names.artist,
                album = null,
                cover = cover,
            )

            // El renombrado es atómico: la canción aparece entera o no aparece.
            if (!staging.renameTo(output)) error("No se pudo guardar ${output.name}")

            library.add(
                Song(
                    title = displayTitle,
                    artist = names.artist,
                    filePath = output.absolutePath,
                    coverArtPath = cover?.absolutePath,
                    // Transformer no siempre sabe la duración final; entonces se
                    // usa la que dio YouTube, que para esto es suficiente.
                    durationMs = durationMs.takeIf { it > 0 } ?: (track.durationSeconds * 1000),
                    addedAt = System.currentTimeMillis(),
                    sourceId = videoId,
                    source = SongSource.YouTube,
                    gainDb = gainDb,
                ),
            )

            Log.i(TAG, "Descargada '$displayTitle' → ${output.name}")
            Result.success()
        } catch (cancellation: CancellationException) {
            // Existe solo para que no la atrape el catch de abajo: cancelar no es
            // fallar, y tratarla como fallo dejaría la fila marcada en rojo en
            // vez de volver a ofrecer el botón de descargar.
            throw cancellation
        } catch (error: Throwable) {
            val failure = error.toFailure()
            // Los fallos de red suelen ser el ascensor o el metro, no un error
            // real: se reintenta con backoff. Un captcha o una extracción rota
            // no mejoran reintentando, así que esos se rinden ya.
            if (failure == DownloadFailure.Network && runAttemptCount < MAX_ATTEMPTS) {
                Log.w(TAG, "Descarga de $videoId cortada, se reintentará", error)
                return Result.retry()
            }

            Log.w(TAG, "Descarga de $videoId fallida ($failure)", error)
            Result.failure(workDataOf(KEY_FAILURE to failure.name))
        } finally {
            partial?.delete()
            // Si sigue existiendo es que algo se torció antes del renombrado.
            staging?.delete()
        }
    }

    private suspend fun saveCover(track: YoutubeTrack, videoId: String): File? {
        val url = track.thumbnailUrl ?: return null
        val bytes = container.httpFetcher.bytesOrNull(url) ?: return null
        return CoverArt.save(bytes, File(container.library.coverDir, "$videoId.jpg"))
    }

    /**
     * Publica el avance dos veces: a la interfaz y a la notificación.
     *
     * Con freno de tiempo porque el sistema estrangula las notificaciones que se
     * actualizan demasiado seguido, y una descarga puede emitir cien avances.
     */
    private suspend fun report(title: String, stage: DownloadStage, progress: Float?) {
        val now = System.currentTimeMillis()
        val stageChanged = stage != lastStage
        if (!stageChanged && now - lastReportAtMs < MIN_REPORT_INTERVAL_MS) return
        lastStage = stage
        lastReportAtMs = now

        setProgress(
            workDataOf(
                KEY_STAGE to stage.name,
                KEY_PROGRESS to (progress ?: -1f),
            ),
        )
        // Si el sistema no deja promocionar el worker a primer plano (pasa al
        // arrancar desde segundo plano en Android 12+), la descarga sigue: solo
        // pierde la notificación y el blindaje contra que la maten.
        runCatching { setForeground(foregroundInfo(title, stage, progress)) }
    }

    private fun foregroundInfo(title: String, stage: DownloadStage, progress: Float?): ForegroundInfo {
        val notification = DownloadNotifications.build(applicationContext, title, stage, progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id.hashCode(), notification)
        }
    }

    /** Evita pisar un archivo existente con el mismo nombre. */
    private fun uniqueFile(dir: File, baseName: String): File {
        var candidate = File(dir, "$baseName.m4a")
        var index = 2
        while (candidate.exists()) {
            candidate = File(dir, "$baseName ($index).m4a")
            index++
        }
        return candidate
    }

    private fun Throwable.toFailure(): DownloadFailure = when (this) {
        is ResolveError.RateLimited -> DownloadFailure.RateLimited
        is ResolveError.Network -> DownloadFailure.Network
        is ResolveError.Unavailable -> DownloadFailure.Unavailable
        is ResolveError.NoAudio -> DownloadFailure.Unavailable
        is ResolveError.ExtractionFailed -> DownloadFailure.Extraction
        is ExportException -> DownloadFailure.Conversion
        is IOException -> DownloadFailure.Network
        else -> DownloadFailure.Storage
    }

    companion object {
        /** Etiqueta común: por ella se consultan todas las descargas de golpe. */
        const val TAG_ALL = "muxy-download"

        const val KEY_VIDEO_ID = "videoId"
        const val KEY_TITLE = "title"
        const val KEY_STAGE = "stage"
        const val KEY_PROGRESS = "progress"
        const val KEY_FAILURE = "failure"

        private const val MAX_ATTEMPTS = 3
        private const val MIN_REPORT_INTERVAL_MS = 400L

        private const val VIDEO_TAG_PREFIX = "muxy-video:"

        /** Etiqueta por vídeo: es lo que permite volver de un WorkInfo a su fila. */
        fun videoTag(videoId: String) = "$VIDEO_TAG_PREFIX$videoId"

        fun videoIdFromTags(tags: Set<String>): String? =
            tags.firstOrNull { it.startsWith(VIDEO_TAG_PREFIX) }?.removePrefix(VIDEO_TAG_PREFIX)

        fun stageOf(progress: Data): DownloadStage? =
            progress.getString(KEY_STAGE)?.let { name ->
                DownloadStage.entries.firstOrNull { it.name == name }
            }

        fun progressOf(progress: Data): Float? =
            progress.getFloat(KEY_PROGRESS, -1f).takeIf { it >= 0f }

        fun failureOf(output: Data): DownloadFailure =
            output.getString(KEY_FAILURE)
                ?.let { name -> DownloadFailure.entries.firstOrNull { it.name == name } }
                ?: DownloadFailure.Storage
    }
}
