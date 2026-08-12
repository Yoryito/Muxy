package com.muxy.app.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.data.Playlist
import com.muxy.app.data.Song
import com.muxy.app.ui.components.ConfirmDialog
import com.muxy.app.ui.components.PochiEmptyState
import com.muxy.app.ui.components.PochiPose
import com.muxy.app.ui.components.PondButton
import com.muxy.app.ui.components.SongRow
import com.muxy.app.ui.components.TextPromptDialog

/**
 * El contenido de una playlist.
 *
 * No es un destino de navegación: la pestaña cambia entre la lista y el detalle
 * según haya una abierta. Con una sola pantalla de profundidad, montar
 * NavHost aquí sería más andamiaje que navegación.
 */
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    songs: List<Song>,
    playingSongId: Long?,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onPlay: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
    ) {
        DetailHeader(
            playlist = playlist,
            songCount = songs.size,
            onBack = onBack,
            onRename = { renaming = true },
            onDelete = { deleting = true },
        )

        if (songs.isNotEmpty()) {
            PondButton(
                text = stringResource(R.string.playlist_play_all),
                onClick = onPlayAll,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp),
            )
        }

        Box(Modifier.fillMaxSize()) {
            if (songs.isEmpty()) {
                PochiEmptyState(
                    pose = PochiPose.Resting,
                    title = stringResource(R.string.playlist_empty_title),
                    body = stringResource(R.string.playlist_empty_body),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(songs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            isPlaying = song.id == playingSongId,
                            onClick = { onPlay(song) },
                            trailing = {
                                IconButton(onClick = { onRemoveSong(song) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.RemoveCircleOutline,
                                        contentDescription = stringResource(
                                            R.string.playlist_remove_song,
                                        ),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (renaming) {
        TextPromptDialog(
            title = stringResource(R.string.playlist_rename),
            label = stringResource(R.string.playlist_name_label),
            confirmText = stringResource(R.string.action_save),
            initialValue = playlist.name,
            onConfirm = {
                onRename(it)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }

    if (deleting) {
        ConfirmDialog(
            title = stringResource(R.string.playlist_delete_title, playlist.name),
            body = stringResource(R.string.playlist_delete_body),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                deleting = false
                onDelete()
            },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
private fun DetailHeader(
    playlist: Playlist,
    songCount: Int,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.playlist_back),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.playlist_song_count, songCount, songCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.playlist_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_rename)) },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.playlist_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}
