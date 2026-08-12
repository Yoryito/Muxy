package com.muxy.app.ui.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.muxy.app.R
import com.muxy.app.download.DownloadFailure
import com.muxy.app.download.DownloadStatus
import com.muxy.app.download.labelRes
import com.muxy.app.ui.components.LilyPadFrame
import com.muxy.app.ui.components.PochiEmptyState
import com.muxy.app.ui.components.PochiPose
import com.muxy.app.ui.components.PondButton
import com.muxy.app.ui.components.PondSearchField
import com.muxy.app.ui.components.formatDuration
import com.muxy.app.youtube.YoutubeResult

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onDownload: (YoutubeResult) -> Unit,
    onCancelDownload: (YoutubeResult) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
    ) {
        PondSearchField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.search_placeholder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )

        Box(Modifier.fillMaxSize()) {
            when {
                state.isSearching && state.results.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                state.failure != null -> FailureState(state.failure, onRetry)

                state.emptyResult -> PochiEmptyState(
                    pose = PochiPose.Curious,
                    title = stringResource(R.string.search_no_results_title),
                    body = stringResource(R.string.search_no_results_body),
                )

                state.results.isEmpty() -> PochiEmptyState(
                    pose = PochiPose.Curious,
                    title = stringResource(R.string.search_empty_title),
                    body = stringResource(R.string.search_empty_body),
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(state.results, key = { it.videoId }) { result ->
                        // Lo ya guardado se muestra como hecho aunque su descarga
                        // fuera de otra sesión y WorkManager ya la haya olvidado.
                        val status = if (result.videoId in state.inLibrary) {
                            DownloadStatus.Done
                        } else {
                            state.downloads[result.videoId]
                        }
                        ResultRow(
                            result = result,
                            status = status,
                            onDownload = { onDownload(result) },
                            onCancel = { onCancelDownload(result) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FailureState(failure: SearchFailure, onRetry: () -> Unit) {
    val (titleRes, bodyRes) = when (failure) {
        SearchFailure.RateLimited ->
            R.string.search_error_ratelimited_title to R.string.search_error_ratelimited_body

        SearchFailure.Network ->
            R.string.search_error_network_title to R.string.search_error_network_body

        SearchFailure.Extraction ->
            R.string.search_error_extraction_title to R.string.search_error_extraction_body
    }

    PochiEmptyState(
        pose = PochiPose.Curious,
        title = stringResource(titleRes),
        body = stringResource(bodyRes),
        action = {
            PondButton(text = stringResource(R.string.action_retry), onClick = onRetry)
        },
    )
}

@Composable
private fun ResultRow(
    result: YoutubeResult,
    status: DownloadStatus?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    // Tocar la fila descarga, salvo cuando ya no hay nada que descargar: si no,
    // un roce durante la conversión parecería que no hace nada.
    val rowAction: (() -> Unit)? = when (status) {
        null, is DownloadStatus.Failed -> onDownload
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (rowAction != null) Modifier.clickable(onClick = rowAction) else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // La muesca rota según el vídeo para que una lista de miniaturas no
        // parezca estampada con la misma plantilla.
        LilyPadFrame(
            modifier = Modifier.size(56.dp),
            notchAngle = 300f + (result.videoId.hashCode().mod(5)) * 24f,
        ) {
            // Las miniaturas son 16:9 y el marco es casi cuadrado: sin recorte
            // quedan como una franja con bandas a los lados.
            AsyncImage(
                model = result.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            SubtitleLine(result, status)
        }

        Spacer(Modifier.width(6.dp))

        DownloadControl(status = status, onDownload = onDownload, onCancel = onCancel)
    }
}

/**
 * La segunda línea cuenta lo que está pasando: mientras la canción baja, el
 * canal y la duración importan menos que en qué punto va.
 */
@Composable
private fun SubtitleLine(result: YoutubeResult, status: DownloadStatus?) {
    val (text, color) = when (status) {
        null, DownloadStatus.Done -> buildString {
            append(result.uploader ?: stringResource(R.string.unknown_artist))
            if (result.durationSeconds > 0) {
                append(" · ")
                append(formatDuration(result.durationSeconds * 1000))
            }
        } to MaterialTheme.colorScheme.onSurfaceVariant

        DownloadStatus.Queued ->
            stringResource(R.string.download_queued) to MaterialTheme.colorScheme.primary

        is DownloadStatus.Running ->
            stringResource(status.stage.labelRes()) to MaterialTheme.colorScheme.primary

        is DownloadStatus.Failed ->
            stringResource(status.reason.messageRes()) to MaterialTheme.colorScheme.error
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DownloadControl(
    status: DownloadStatus?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    when (status) {
        null -> IconButton(onClick = onDownload) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = stringResource(R.string.action_download),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        DownloadStatus.Done -> Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.download_done),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        is DownloadStatus.Failed -> IconButton(onClick = onDownload) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.download_retry),
                tint = MaterialTheme.colorScheme.error,
            )
        }

        DownloadStatus.Queued -> ProgressRing(progress = null, onCancel = onCancel)

        is DownloadStatus.Running -> ProgressRing(progress = status.progress, onCancel = onCancel)
    }
}

/**
 * Anillo de progreso con la cruz de cancelar dentro. Un anillo suelto no diría
 * que se puede parar, y un botón suelto no diría por dónde va.
 */
@Composable
private fun ProgressRing(progress: Float?, onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        if (progress == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                strokeWidth = 2.5.dp,
            )
        } else {
            // Animado porque el worker informa a saltos: sin esto la barra
            // pegaría tirones en vez de avanzar.
            val animated by animateFloatAsState(targetValue = progress, label = "download")
            CircularProgressIndicator(
                progress = { animated },
                modifier = Modifier.size(30.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeWidth = 2.5.dp,
                gapSize = 0.dp,
            )
        }
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.download_cancel),
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun DownloadFailure.messageRes(): Int = when (this) {
    DownloadFailure.RateLimited -> R.string.download_error_ratelimited
    DownloadFailure.Network -> R.string.download_error_network
    DownloadFailure.Unavailable -> R.string.download_error_unavailable
    DownloadFailure.Extraction -> R.string.download_error_extraction
    DownloadFailure.Conversion -> R.string.download_error_conversion
    DownloadFailure.Storage -> R.string.download_error_storage
}
