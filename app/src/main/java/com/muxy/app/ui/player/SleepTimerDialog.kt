package com.muxy.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.playback.SleepTimerState

private val MINUTE_OPTIONS = listOf(15, 30, 45, 60)
private val HOUR_OPTIONS = listOf(2, 3, 4, 5)

/**
 * Las opciones de apagado y, si ya hay uno puesto, cuánto queda y cómo
 * cancelarlo. Es la misma hoja para ponerlo y para cancelarlo: no hace falta
 * distinguir "editar" de "crear" con solo un temporizador posible a la vez.
 */
@Composable
fun SleepTimerDialog(
    state: SleepTimerState,
    onSelectDuration: (Long) -> Unit,
    onSelectEndOfSong: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.sleep_timer_title), style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column {
                if (state !is SleepTimerState.Off) {
                    Text(
                        text = when (state) {
                            is SleepTimerState.Counting ->
                                stringResource(R.string.sleep_timer_remaining, formatRemaining(state.remainingMs))
                            SleepTimerState.EndOfSong -> stringResource(R.string.sleep_timer_active_end_of_song)
                            SleepTimerState.Off -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }

                MINUTE_OPTIONS.forEach { minutes ->
                    TimerOptionRow(
                        text = stringResource(R.string.sleep_timer_minutes, minutes),
                        onClick = { onSelectDuration(minutes * 60_000L) },
                    )
                }
                HOUR_OPTIONS.forEach { hours ->
                    TimerOptionRow(
                        text = stringResource(R.string.sleep_timer_hours, hours),
                        onClick = { onSelectDuration(hours * 3_600_000L) },
                    )
                }
                TimerOptionRow(
                    text = stringResource(R.string.sleep_timer_end_of_song),
                    onClick = onSelectEndOfSong,
                )
            }
        },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        confirmButton = {
            if (state !is SleepTimerState.Off) {
                TextButton(onClick = onCancel) {
                    Text(
                        text = stringResource(R.string.sleep_timer_cancel),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun TimerOptionRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

/** mm:ss para menos de una hora, h:mm:ss a partir de ahí: hasta 5 horas conviene ver las tres unidades. */
private fun formatRemaining(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
