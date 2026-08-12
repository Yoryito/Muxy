package com.muxy.app.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.workDataOf
import com.muxy.app.youtube.YoutubeResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/**
 * Cara visible de las descargas: encolar y mirar cómo van.
 *
 * El estado no se guarda aquí. La verdad la tiene WorkManager, que persiste las
 * suyas en su propia base de datos y por tanto sobrevive a que se cierre la app
 * — un mapa en memoria se perdería justo cuando más falta hace.
 */
class DownloadQueue(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun enqueue(result: YoutubeResult) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_VIDEO_ID to result.videoId,
                    DownloadWorker.KEY_TITLE to result.title,
                ),
            )
            .addTag(DownloadWorker.TAG_ALL)
            .addTag(DownloadWorker.videoTag(result.videoId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        // KEEP no deja duplicar una descarga en marcha, pero sí sustituye a una
        // que ya terminó — que es lo que hace falta para poder reintentar.
        workManager.enqueueUniqueWork(uniqueName(result.videoId), ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(videoId: String) {
        workManager.cancelUniqueWork(uniqueName(videoId))
    }

    /** Estado de todas las descargas, indexado por id de vídeo. */
    fun statuses(): Flow<Map<String, DownloadStatus>> =
        workManager.getWorkInfosFlow(WorkQuery.fromTags(DownloadWorker.TAG_ALL))
            .map { infos ->
                buildMap {
                    for (info in infos) {
                        val videoId = DownloadWorker.videoIdFromTags(info.tags) ?: continue
                        val status = info.toStatus() ?: continue
                        // Puede quedar una entrada vieja del mismo vídeo junto a
                        // la nueva; manda siempre la que está viva.
                        val existing = get(videoId)
                        if (existing == null || rank(status) > rank(existing)) {
                            put(videoId, status)
                        }
                    }
                }
            }

    private fun WorkInfo.toStatus(): DownloadStatus? = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadStatus.Queued

        WorkInfo.State.RUNNING -> DownloadStatus.Running(
            stage = DownloadWorker.stageOf(progress) ?: DownloadStage.Resolving,
            progress = DownloadWorker.progressOf(progress),
        )

        WorkInfo.State.SUCCEEDED -> DownloadStatus.Done
        WorkInfo.State.FAILED -> DownloadStatus.Failed(DownloadWorker.failureOf(outputData))

        // Cancelada: la fila vuelve a ofrecer el botón de descargar.
        WorkInfo.State.CANCELLED -> null
    }

    /** Prioridad al desempatar: lo que está en marcha pesa más que lo terminado. */
    private fun rank(status: DownloadStatus): Int = when (status) {
        is DownloadStatus.Running -> 4
        DownloadStatus.Queued -> 3
        DownloadStatus.Done -> 2
        is DownloadStatus.Failed -> 1
    }

    private fun uniqueName(videoId: String) = "download-$videoId"
}
