package com.muxy.app.ui.search

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.muxy.app.R
import com.muxy.app.ui.components.LilyPadFrame
import com.muxy.app.ui.components.PochiEmptyState
import com.muxy.app.ui.components.PochiPose
import com.muxy.app.ui.components.PondButton
import com.muxy.app.ui.library.formatDuration
import com.muxy.app.youtube.YoutubeResult

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onDownload: (YoutubeResult) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            placeholder = {
                Text(
                    stringResource(R.string.search_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null)
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.search_clear),
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(percent = 50),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
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
                        ResultRow(result = result, onDownload = { onDownload(result) })
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
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDownload)
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
            Text(
                text = buildString {
                    append(result.uploader ?: stringResource(R.string.unknown_artist))
                    if (result.durationSeconds > 0) {
                        append(" · ")
                        append(formatDuration(result.durationSeconds * 1000))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = onDownload) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = stringResource(R.string.action_download),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
