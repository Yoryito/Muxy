package com.muxy.app.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muxy.app.R
import com.muxy.app.data.PlaylistSummary
import com.muxy.app.data.Song
import com.muxy.app.ui.components.ConfirmDialog
import com.muxy.app.ui.components.PochiEmptyState
import com.muxy.app.ui.components.PochiPose
import com.muxy.app.ui.components.PondSearchField
import com.muxy.app.ui.components.SongRow
import com.muxy.app.ui.components.TextPromptDialog
import com.muxy.app.ui.components.formatDuration
import kotlinx.coroutines.flow.Flow

@Composable
fun LibraryScreen(
    songs: List<Song>,
    libraryIsEmpty: Boolean,
    query: String,
    sort: LibrarySort,
    playingSongId: Long?,
    playlists: List<PlaylistSummary>,
    playlistsContaining: (Long) -> Flow<Set<Long>>,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onPlay: (Song) -> Unit,
    onDelete: (Song) -> Unit,
    onTogglePlaylist: (playlistId: Long, songId: Long, isMember: Boolean) -> Unit,
    onCreatePlaylistWith: (name: String, songId: Long) -> Unit,
    onToggleSelectionMode: () -> Unit,
    onToggleSelected: (Long) -> Unit,
    onDeleteSelected: () -> Unit,
    onAddSelectedToPlaylist: (Long) -> Unit,
    onCreatePlaylistWithSelected: (name: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    // La librería vacía no enseña ni filtro ni orden: no hay nada que filtrar y
    // los controles solos, sobre el estado vacío, solo hacen ruido.
    if (libraryIsEmpty) {
        PochiEmptyState(
            pose = PochiPose.Resting,
            title = stringResource(R.string.library_empty_title),
            body = stringResource(R.string.library_empty_body),
            modifier = modifier.padding(contentPadding),
        )
        return
    }

    var sheetSong by remember { mutableStateOf<Song?>(null) }
    var deleting by remember { mutableStateOf<Song?>(null) }
    var creatingPlaylistFor by remember { mutableStateOf<Song?>(null) }
    var addingSelectedToPlaylist by remember { mutableStateOf(false) }
    var creatingPlaylistForSelection by remember { mutableStateOf(false) }
    var deletingSelected by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
    ) {
        if (selectionMode) {
            SelectionBar(
                count = selectedIds.size,
                onClose = onToggleSelectionMode,
                onAddToPlaylist = { addingSelectedToPlaylist = true },
                onDelete = { deletingSelected = true },
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PondSearchField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = stringResource(R.string.library_filter_placeholder),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                )
                IconButton(onClick = onToggleSelectionMode, modifier = Modifier.padding(end = 12.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Checklist,
                        contentDescription = stringResource(R.string.library_selection_start),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SortRow(sort = sort, onSortChange = onSortChange)
        }

        Box(Modifier.fillMaxSize()) {
            if (songs.isEmpty()) {
                PochiEmptyState(
                    pose = PochiPose.Curious,
                    title = stringResource(R.string.library_no_matches_title),
                    body = stringResource(R.string.library_no_matches_body),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(songs, key = { it.id }) { song ->
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectionMode) {
                            Checkbox(
                                checked = song.id in selectedIds,
                                onCheckedChange = { onToggleSelected(song.id) },
                            )
                        }
                        SongRow(
                            song = song,
                            isPlaying = song.id == playingSongId,
                            onClick = {
                                if (selectionMode) onToggleSelected(song.id) else onPlay(song)
                            },
                            onLongClick = if (selectionMode) null else { { sheetSong = song } },
                            modifier = Modifier.weight(1f),
                            trailing = if (song.durationMs > 0) {
                                {
                                    Text(
                                        text = formatDuration(song.durationMs),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                      }
                    }
                }
            }
        }
    }

    sheetSong?.let { song ->
        val memberFlow = remember(song.id) { playlistsContaining(song.id) }
        val memberOf by memberFlow.collectAsStateWithLifecycle(emptySet())

        SongActionsSheet(
            song = song,
            playlists = playlists,
            memberOf = memberOf,
            onTogglePlaylist = { playlistId ->
                onTogglePlaylist(playlistId, song.id, playlistId in memberOf)
            },
            onNewPlaylist = {
                sheetSong = null
                creatingPlaylistFor = song
            },
            onDelete = {
                sheetSong = null
                deleting = song
            },
            onDismiss = { sheetSong = null },
        )
    }

    creatingPlaylistFor?.let { song ->
        TextPromptDialog(
            title = stringResource(R.string.playlist_new),
            label = stringResource(R.string.playlist_name_label),
            confirmText = stringResource(R.string.action_create),
            onConfirm = { name ->
                onCreatePlaylistWith(name, song.id)
                creatingPlaylistFor = null
            },
            onDismiss = { creatingPlaylistFor = null },
        )
    }

    deleting?.let { song ->
        ConfirmDialog(
            title = stringResource(R.string.song_delete_title, song.title),
            body = stringResource(R.string.song_delete_body),
            confirmText = stringResource(R.string.action_remove),
            destructive = true,
            onConfirm = {
                onDelete(song)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }

    if (addingSelectedToPlaylist) {
        AddToPlaylistSheet(
            count = selectedIds.size,
            playlists = playlists,
            onSelect = { playlistId ->
                onAddSelectedToPlaylist(playlistId)
                addingSelectedToPlaylist = false
            },
            onNewPlaylist = {
                addingSelectedToPlaylist = false
                creatingPlaylistForSelection = true
            },
            onDismiss = { addingSelectedToPlaylist = false },
        )
    }

    if (creatingPlaylistForSelection) {
        TextPromptDialog(
            title = stringResource(R.string.playlist_new),
            label = stringResource(R.string.playlist_name_label),
            confirmText = stringResource(R.string.action_create),
            onConfirm = { name ->
                onCreatePlaylistWithSelected(name)
                creatingPlaylistForSelection = false
            },
            onDismiss = { creatingPlaylistForSelection = false },
        )
    }

    if (deletingSelected) {
        ConfirmDialog(
            title = pluralStringResource(
                R.plurals.library_selection_delete_title,
                selectedIds.size,
                selectedIds.size,
            ),
            body = stringResource(R.string.library_selection_delete_body),
            confirmText = stringResource(R.string.action_remove),
            destructive = true,
            onConfirm = {
                onDeleteSelected()
                deletingSelected = false
            },
            onDismiss = { deletingSelected = false },
        )
    }
}

/**
 * La barra que sustituye a la búsqueda y el orden mientras hay canciones
 * marcadas: contar, añadir a una playlist o borrar todas de una.
 */
@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.library_selection_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = pluralStringResource(R.plurals.library_selection_count, count, count),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
        IconButton(onClick = onAddToPlaylist, enabled = count > 0) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = stringResource(R.string.library_selection_add_to_playlist_action),
                tint = if (count > 0) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        }
        IconButton(onClick = onDelete, enabled = count > 0) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(R.string.song_delete),
                tint = if (count > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/**
 * Los órdenes, como fichas. Van en una fila que se desplaza porque en pantallas
 * estrechas con el texto grande del sistema no siempre caben todas.
 */
@Composable
private fun SortRow(sort: LibrarySort, onSortChange: (LibrarySort) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibrarySort.entries.forEach { option ->
            FilterChip(
                selected = sort == option,
                onClick = { onSortChange(option) },
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

private fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.Recent -> R.string.library_sort_recent
    LibrarySort.Oldest -> R.string.library_sort_oldest
    LibrarySort.Title -> R.string.library_sort_title
    LibrarySort.Artist -> R.string.library_sort_artist
    LibrarySort.Duration -> R.string.library_sort_duration
}
