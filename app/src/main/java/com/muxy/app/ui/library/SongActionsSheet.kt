package com.muxy.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.data.PlaylistSummary
import com.muxy.app.data.Song
import com.muxy.app.ui.components.SongCover

/**
 * Lo que se puede hacer con una canción de la librería, al mantenerla pulsada.
 *
 * Las playlists van marcadas en vez de ser un simple "añadir": si no, no habría
 * forma de saber dónde está ya la canción sin ir lista por lista, y añadirla dos
 * veces parecería que no ha hecho nada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(
    song: Song,
    playlists: List<PlaylistSummary>,
    memberOf: Set<Long>,
    onTogglePlaylist: (Long) -> Unit,
    onNewPlaylist: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            SheetHeader(song)

            Text(
                text = stringResource(R.string.song_add_to_playlist),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )

            playlists.forEach { playlist ->
                val isMember = playlist.id in memberOf
                SheetRow(
                    icon = if (isMember) {
                        Icons.Rounded.Check
                    } else {
                        Icons.AutoMirrored.Rounded.QueueMusic
                    },
                    iconTint = if (isMember) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    title = playlist.name,
                    subtitle = pluralStringResource(
                        R.plurals.playlist_song_count,
                        playlist.songCount,
                        playlist.songCount,
                    ),
                    onClick = { onTogglePlaylist(playlist.id) },
                )
            }

            SheetRow(
                icon = Icons.Rounded.Add,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.playlist_new),
                titleColor = MaterialTheme.colorScheme.primary,
                onClick = onNewPlaylist,
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            SheetRow(
                icon = Icons.Rounded.DeleteOutline,
                iconTint = MaterialTheme.colorScheme.error,
                title = stringResource(R.string.song_delete),
                titleColor = MaterialTheme.colorScheme.error,
                onClick = onDelete,
            )
        }
    }
}

/**
 * La misma lista de playlists que [SongActionsSheet], pero para añadir de golpe
 * la selección múltiple de la librería: no hay una sola canción de la que
 * marcar en qué listas ya está, así que aquí toda fila añade sin más.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    count: Int,
    playlists: List<PlaylistSummary>,
    onSelect: (Long) -> Unit,
    onNewPlaylist: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = pluralStringResource(R.plurals.library_selection_add_to_playlist, count, count),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )

            playlists.forEach { playlist ->
                SheetRow(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = playlist.name,
                    subtitle = pluralStringResource(
                        R.plurals.playlist_song_count,
                        playlist.songCount,
                        playlist.songCount,
                    ),
                    onClick = { onSelect(playlist.id) },
                )
            }

            SheetRow(
                icon = Icons.Rounded.Add,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.playlist_new),
                titleColor = MaterialTheme.colorScheme.primary,
                onClick = onNewPlaylist,
            )
        }
    }
}

@Composable
private fun SheetHeader(song: Song) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongCover(song = song, isPlaying = false, size = 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist ?: stringResource(R.string.unknown_artist),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    titleColor: Color = Color.Unspecified,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint)
        }

        Spacer(Modifier.width(18.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (titleColor == Color.Unspecified) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    titleColor
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
