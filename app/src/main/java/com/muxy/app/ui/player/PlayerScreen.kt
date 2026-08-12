package com.muxy.app.ui.player

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.muxy.app.R
import com.muxy.app.playback.PlaybackState
import com.muxy.app.playback.RepeatMode
import com.muxy.app.ui.components.LilyPadFrame
import com.muxy.app.ui.components.formatDuration
import com.muxy.app.ui.theme.LilyPadShape
import java.io.File

/**
 * Reproductor a pantalla completa: se abre desde el mini-reproductor y se cierra
 * con el chevrón o con el botón atrás.
 *
 * Va montado como capa encima del [Scaffold] en vez de como destino de
 * navegación, para que tape también la barra inferior: mientras suena una
 * canción, esta pantalla *es* la app.
 */
@Composable
fun PlayerScreen(
    state: PlaybackState,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Opaca a propósito: es una capa sobre el resto de la app y cualquier
    // transparencia dejaría ver la lista moviéndose por detrás.
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopRow(onCollapse = onCollapse)

            Spacer(Modifier.weight(1f))

            FloatingCover(
                coverArtPath = state.coverArtPath,
                isPlaying = state.isPlaying,
            )

            Spacer(Modifier.weight(0.6f))

            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = state.artist ?: stringResource(R.string.unknown_artist),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )

            Spacer(Modifier.weight(0.5f))

            WaterSeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = onSeek,
            )

            Spacer(Modifier.weight(0.5f))

            Controls(
                state = state,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
            )

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TopRow(onCollapse: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onCollapse,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.player_collapse),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.player_now_playing),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * La carátula, montada en un nenúfar que flota.
 *
 * El vaivén se apaga casi del todo al pausar en vez de cortarse en seco: es la
 * misma idea que el mini-reproductor meciéndose, pero aquí, con la carátula a
 * este tamaño, un movimiento que siguiera igual con la música parada chirriaría.
 */
@Composable
private fun FloatingCover(coverArtPath: String?, isPlaying: Boolean) {
    val pond = rememberInfiniteTransition(label = "cover")
    // Periodos distintos y sin múltiplo común aparente: así el balanceo y la
    // inclinación nunca coinciden y el bucle no se nota.
    val lift by pond.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(5200), AnimationRepeatMode.Reverse),
        label = "lift",
    )
    val tilt by pond.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(8300), AnimationRepeatMode.Reverse),
        label = "tilt",
    )
    val amplitude by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.15f,
        animationSpec = tween(1400),
        label = "amplitude",
    )

    LilyPadFrame(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .aspectRatio(1f)
            .graphicsLayer {
                translationY = lift * amplitude
                rotationZ = tilt * amplitude
            },
        notchAngle = 315f,
        // A este tamaño los 15° de siempre abren una cuña enorme y la carátula
        // pasa de nenúfar a Pac-Man: se estrecha hasta dejar la misma hendidura
        // fina que se ve en las filas de la librería.
        notchWidth = 5f,
        background = MaterialTheme.colorScheme.primaryContainer,
    ) {
        if (coverArtPath != null) {
            AsyncImage(
                model = File(coverArtPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Las canciones importadas a mano no traen carátula: el nenúfar liso
            // ya es la ilustración, la nota solo evita que parezca un hueco.
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * Barra de posición como nivel de agua, con un nenúfar por tirador.
 *
 * Se apoya en [Slider] en lugar de resolver el arrastre a mano para no perder
 * el manejo del gesto ni la accesibilidad; lo único propio es cómo se pinta.
 * Pintar la barra y el tirador exige la variante experimental del Slider — la
 * estable no deja sustituirlos, y sería una barra genérica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaterSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    // Mientras se arrastra manda el dedo, no el reloj del reproductor: si no,
    // el refresco periódico devolvería el tirador a la posición real.
    var dragMs by remember { mutableStateOf<Long?>(null) }
    val shown = (dragMs ?: positionMs).coerceIn(0L, durationMs.coerceAtLeast(0L))

    val fraction = if (durationMs > 0) shown.toFloat() / durationMs else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        // Al arrastrar, sin animación: cualquier suavizado se siente como retraso.
        // Al reproducir, el tramo cubre justo lo que tarda el siguiente refresco,
        // así el agua sube de continuo en vez de a saltos.
        animationSpec = if (dragMs != null) snap() else tween(500),
        label = "waterLevel",
    )

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = shown.toFloat(),
            onValueChange = { dragMs = it.toLong() },
            onValueChangeFinished = {
                dragMs?.let(onSeek)
                dragMs = null
            },
            // Un rango vacío haría reventar al Slider, así que con duración
            // desconocida se queda con un rango de pega y deshabilitado.
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            enabled = durationMs > 0,
            track = { WaterTrack(animatedFraction) },
            thumb = { LilyPadThumb() },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(shown),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WaterTrack(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.tertiary),
        )
    }
}

@Composable
private fun LilyPadThumb() {
    LilyPadFrame(
        modifier = Modifier.size(20.dp),
        notchAngle = 315f,
        background = MaterialTheme.colorScheme.tertiary,
    )
}

@Composable
private fun Controls(
    state: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeButton(
            icon = Icons.Rounded.Shuffle,
            contentDescription = stringResource(R.string.action_shuffle),
            active = state.shuffleEnabled,
            onClick = onToggleShuffle,
        )

        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.action_previous),
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        PlayButton(isPlaying = state.isPlaying, onClick = onTogglePlayPause)

        IconButton(onClick = onNext, enabled = state.hasNext) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.action_next),
                modifier = Modifier.size(34.dp),
                tint = if (state.hasNext) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        }

        ModeButton(
            icon = if (state.repeatMode == RepeatMode.One) {
                Icons.Rounded.RepeatOne
            } else {
                Icons.Rounded.Repeat
            },
            contentDescription = stringResource(R.string.action_repeat),
            active = state.repeatMode != RepeatMode.Off,
            onClick = onCycleRepeat,
        )
    }
}

/**
 * El botón grande es un nenúfar, no un círculo: es la forma recurrente de la app
 * y aquí es donde más se ve.
 */
@Composable
private fun PlayButton(isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(76.dp)
            // Recortado antes del clickable para que la onda del toque siga la
            // muesca en vez de desbordar en cuadrado.
            .clip(LilyPadShape(notchAngleDegrees = 315f, notchWidthDegrees = 10f))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = stringResource(
                if (isPlaying) R.string.action_pause else R.string.action_play
            ),
            modifier = Modifier.size(38.dp),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** Aleatorio y repetición: encendidos se tiñen del acento, apagados se apagan. */
@Composable
private fun ModeButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
