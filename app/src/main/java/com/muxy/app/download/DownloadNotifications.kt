package com.muxy.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.muxy.app.MainActivity
import com.muxy.app.R

/**
 * La notificación que mantiene viva la descarga.
 *
 * No es decorativa: un worker en primer plano necesita notificación para que
 * Android no lo mate al salir de la app.
 */
object DownloadNotifications {

    const val CHANNEL_ID = "downloads"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.download_channel_name),
                // Bajo a propósito: es una barra de progreso, no un aviso.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.download_channel_description)
                setShowBadge(false)
            },
        )
    }

    fun build(
        context: Context,
        songTitle: String,
        stage: DownloadStage,
        progress: Float?,
    ): Notification {
        ensureChannel(context)

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(songTitle.ifBlank { context.getString(R.string.download_generic_title) })
            .setContentText(context.getString(stage.labelRes()))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progress == null) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, (progress * 100).toInt().coerceIn(0, 100), false)
        }

        return builder.build()
    }
}

fun DownloadStage.labelRes(): Int = when (this) {
    DownloadStage.Resolving -> R.string.download_stage_resolving
    DownloadStage.Downloading -> R.string.download_stage_downloading
    DownloadStage.Converting -> R.string.download_stage_converting
    DownloadStage.Tagging -> R.string.download_stage_tagging
}
