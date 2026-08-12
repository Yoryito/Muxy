package com.muxy.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.update.AvailableUpdate
import java.util.Locale

/**
 * El aviso emergente de versión nueva. Solo sale de la comprobación automática:
 * si el usuario ha ido a buscarla a mano, ya está mirando la respuesta en ajustes.
 *
 * Se queda abierto durante la descarga para que la barra tenga dónde vivir, y se
 * cierra solo cuando el instalador del sistema toma el relevo.
 */
@Composable
fun UpdateDialog(
    update: AvailableUpdate,
    state: UpdateState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val downloading = state as? UpdateState.Downloading

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.update_available_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(
                        R.string.update_available_body,
                        update.versionName,
                        formatSize(update.sizeBytes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (update.notes.isNotBlank()) {
                    Text(
                        text = update.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state is UpdateState.DownloadFailed) {
                    Text(
                        text = stringResource(R.string.settings_download_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (downloading != null) DownloadBar(downloading.progress)
            }
        },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        confirmButton = {
            if (downloading == null) {
                TextButton(onClick = onUpdate) {
                    Text(stringResource(R.string.settings_install_update))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = if (downloading == null) {
                        stringResource(R.string.update_later)
                    } else {
                        stringResource(R.string.update_hide)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/** Los tamaños de APK están en megas; más precisión no aporta nada aquí. */
private fun formatSize(bytes: Long): String =
    String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576f)
