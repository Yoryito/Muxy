package com.muxy.app.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.data.StorageUsage
import com.muxy.app.ui.components.LilyPadCard
import com.muxy.app.ui.components.PondButton
import com.muxy.app.youtube.DownloadQuality

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onToggleAutoCheck: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onUpdate: () -> Unit,
    onExportPlaylists: () -> Unit,
    onImportPlaylists: () -> Unit,
    onDismissBackupNotice: () -> Unit,
    onToggleNormalizeVolume: (Boolean) -> Unit,
    onToggleAutoplay: (Boolean) -> Unit,
    onDownloadQualityChange: (DownloadQuality) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
        )

        AboutCard(version = state.currentVersion)

        StorageCard(storage = state.storage)

        PlaybackCard(
            normalizeVolume = state.normalizeVolume,
            autoplay = state.autoplay,
            onToggleNormalizeVolume = onToggleNormalizeVolume,
            onToggleAutoplay = onToggleAutoplay,
        )

        DownloadQualityCard(
            quality = state.downloadQuality,
            onChange = onDownloadQualityChange,
        )

        BackupCard(
            notice = state.backupNotice,
            onExport = onExportPlaylists,
            onImport = onImportPlaylists,
            onDismissNotice = onDismissBackupNotice,
        )

        // Las actualizaciones van al final: es lo que menos se toca y lo que más
        // ocupa cuando hay algo que contar.
        UpdatesCard(
            state = state,
            onToggleAutoCheck = onToggleAutoCheck,
            onCheckNow = onCheckNow,
            onUpdate = onUpdate,
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AboutCard(version: String) {
    LilyPadCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_version, version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * Con el uso todavía sin calcular no hay nada honesto que enseñar: una barra
 * vacía se leería como "no ocupas nada" en vez de "esto tarda un pelín".
 */
@Composable
private fun StorageCard(storage: StorageUsage?) {
    val context = LocalContext.current

    LilyPadCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.settings_storage_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            if (storage == null) {
                DownloadBar(progress = null)
            } else {
                val fraction = if (storage.deviceTotalBytes > 0) {
                    (storage.deviceUsedBytes.toFloat() / storage.deviceTotalBytes).coerceIn(0f, 1f)
                } else {
                    0f
                }
                DownloadBar(progress = fraction)

                val freeBytes = (storage.deviceTotalBytes - storage.deviceUsedBytes).coerceAtLeast(0)
                Text(
                    text = stringResource(
                        R.string.settings_storage_free,
                        Formatter.formatShortFileSize(context, freeBytes),
                        Formatter.formatShortFileSize(context, storage.deviceTotalBytes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.settings_storage_used,
                        Formatter.formatShortFileSize(context, storage.muxyUsedBytes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlaybackCard(
    normalizeVolume: Boolean,
    autoplay: Boolean,
    onToggleNormalizeVolume: (Boolean) -> Unit,
    onToggleAutoplay: (Boolean) -> Unit,
) {
    LilyPadCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stringResource(R.string.settings_playback_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_normalize_volume),
                body = stringResource(R.string.settings_normalize_volume_body),
                checked = normalizeVolume,
                onCheckedChange = onToggleNormalizeVolume,
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_autoplay),
                body = stringResource(R.string.settings_autoplay_body),
                checked = autoplay,
                onCheckedChange = onToggleAutoplay,
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

@Composable
private fun DownloadQualityCard(quality: DownloadQuality, onChange: (DownloadQuality) -> Unit) {
    LilyPadCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.settings_download_quality_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.settings_download_quality_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DownloadQuality.entries.forEach { option ->
                    FilterChip(
                        selected = quality == option,
                        onClick = { onChange(option) },
                        label = {
                            Text(
                                text = stringResource(option.labelRes()),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        shape = MaterialTheme.shapes.small,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}

private fun DownloadQuality.labelRes(): Int = when (this) {
    DownloadQuality.High -> R.string.settings_download_quality_high
    DownloadQuality.Medium -> R.string.settings_download_quality_medium
    DownloadQuality.Low -> R.string.settings_download_quality_low
}

/**
 * Exportar e importar playlists en JSON, porque todo lo demás vive solo en
 * Room: si se pierde el móvil o hay que reinstalar, las playlists se van con
 * él aunque los archivos de música sigan a salvo.
 */
@Composable
private fun BackupCard(
    notice: BackupNotice?,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    LilyPadCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.settings_backup_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.settings_backup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (notice != null) {
                val failed = notice is BackupNotice.ExportFailed || notice is BackupNotice.ImportFailed
                Text(
                    text = notice.text(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    // Se lee y se toca para quitarla; no hace falta un botón aparte.
                    modifier = Modifier.clickable(onClick = onDismissNotice),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PondButton(
                    text = stringResource(R.string.settings_backup_export),
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                )
                PondButton(
                    text = stringResource(R.string.settings_backup_import),
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BackupNotice.text(): String = when (this) {
    BackupNotice.Exported -> stringResource(R.string.settings_backup_export_done)
    BackupNotice.ExportFailed -> stringResource(R.string.settings_backup_export_failed)
    BackupNotice.ImportFailed -> stringResource(R.string.settings_backup_import_failed)
    is BackupNotice.Imported -> stringResource(
        R.string.settings_backup_import_done,
        playlistsCreated,
        songsAdded,
        songsSkipped,
    )
}

@Composable
private fun UpdatesCard(
    state: SettingsUiState,
    onToggleAutoCheck: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onUpdate: () -> Unit,
) {
    val busy = state.update is UpdateState.Checking || state.update is UpdateState.Downloading

    LilyPadCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.settings_updates),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_auto_check),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.settings_auto_check_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = state.autoCheck,
                    onCheckedChange = onToggleAutoCheck,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            }

            UpdateStatus(state.update)

            // Mientras se descarga, el botón de "buscar" no tiene nada que hacer:
            // lo que toca es esperar o mirar la barra.
            if (state.update is UpdateState.Available || state.update is UpdateState.DownloadFailed) {
                PondButton(
                    text = stringResource(R.string.settings_install_update),
                    onClick = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                PondButton(
                    text = stringResource(R.string.settings_check_now),
                    onClick = onCheckNow,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun UpdateStatus(update: UpdateState) {
    val message = when (update) {
        UpdateState.Idle -> null
        UpdateState.Checking -> stringResource(R.string.settings_checking)
        UpdateState.UpToDate -> stringResource(R.string.settings_up_to_date)
        UpdateState.CheckFailed -> stringResource(R.string.settings_check_failed)
        is UpdateState.Available -> stringResource(
            R.string.settings_update_available,
            update.update.versionName,
        )
        is UpdateState.Downloading -> stringResource(R.string.settings_downloading)
        is UpdateState.DownloadFailed -> stringResource(R.string.settings_download_failed)
        is UpdateState.Installing -> stringResource(R.string.settings_installing)
    } ?: return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (update is UpdateState.CheckFailed || update is UpdateState.DownloadFailed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (update is UpdateState.Downloading) {
            DownloadBar(update.progress)
        }
    }
}

/**
 * Barra de la descarga. Con progreso desconocido va indeterminada: fingir un
 * porcentaje que no se sabe es peor que decir "está pasando algo".
 */
@Composable
fun DownloadBar(progress: Float?, modifier: Modifier = Modifier) {
    val barModifier = modifier
        .fillMaxWidth()
        .height(8.dp)

    if (progress == null) {
        LinearProgressIndicator(
            modifier = barModifier,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    } else {
        LinearProgressIndicator(
            progress = { progress },
            modifier = barModifier,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
}
