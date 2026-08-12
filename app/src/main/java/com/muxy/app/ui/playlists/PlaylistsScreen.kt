package com.muxy.app.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.muxy.app.R
import com.muxy.app.data.PlaylistSummary
import com.muxy.app.ui.components.LilyPadFrame
import com.muxy.app.ui.components.PochiEmptyState
import com.muxy.app.ui.components.PochiPose
import com.muxy.app.ui.components.PondButton
import com.muxy.app.ui.components.TextPromptDialog
import java.io.File

@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistSummary>,
    onOpen: (Long) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var creating by remember { mutableStateOf(false) }

    if (playlists.isEmpty()) {
        PochiEmptyState(
            pose = PochiPose.Curious,
            title = stringResource(R.string.playlists_empty_title),
            body = stringResource(R.string.playlists_empty_body),
            modifier = modifier.padding(contentPadding),
            action = {
                PondButton(
                    text = stringResource(R.string.playlist_new),
                    onClick = { creating = true },
                )
            },
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Crear va arriba y como una fila más, no en un botón flotante: el
            // flotante taparía la última lista y ya hay mini-reproductor abajo.
            item {
                NewPlaylistRow(onClick = { creating = true })
            }

            items(playlists, key = { it.id }) { playlist ->
                PlaylistRow(playlist = playlist, onClick = { onOpen(playlist.id) })
            }
        }
    }

    if (creating) {
        TextPromptDialog(
            title = stringResource(R.string.playlist_new),
            label = stringResource(R.string.playlist_name_label),
            confirmText = stringResource(R.string.action_create),
            onConfirm = {
                onCreate(it)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun NewPlaylistRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LilyPadFrame(
            modifier = Modifier.size(52.dp),
            notchAngle = 315f,
            background = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = stringResource(R.string.playlist_new),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PlaylistRow(playlist: PlaylistSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // La portada es la carátula de su primera canción; una lista vacía o de
        // canciones sin carátula se queda con el nenúfar liso y su icono.
        LilyPadFrame(
            modifier = Modifier.size(52.dp),
            notchAngle = 300f + (playlist.id % 5) * 24f,
            background = MaterialTheme.colorScheme.primaryContainer,
        ) {
            if (playlist.coverArtPath != null) {
                AsyncImage(
                    model = File(playlist.coverArtPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.playlist_song_count,
                    playlist.songCount,
                    playlist.songCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
