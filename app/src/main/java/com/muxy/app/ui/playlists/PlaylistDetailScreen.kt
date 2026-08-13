package com.muxy.app.ui.playlists

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DragHandle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
    onReorder: (List<Long>) -> Unit,
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
                ReorderableSongList(
                    songs = songs,
                    playingSongId = playingSongId,
                    onPlay = onPlay,
                    onRemoveSong = onRemoveSong,
                    onReorder = onReorder,
                )
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

/**
 * La lista de la playlist, con arrastre a mano por el tirador de cada fila.
 *
 * El orden que se ve mientras se arrastra vive en una copia local: solo así se
 * puede mover una canción de sitio al vuelo sin esperar a que la base confirme
 * el cambio. Se resincroniza con [songs] en cuanto no hay ningún arrastre en
 * marcha, para que un cambio de fuera (quitar una canción, por ejemplo) no se
 * pierda.
 */
@Composable
private fun ReorderableSongList(
    songs: List<Song>,
    playingSongId: Long?,
    onPlay: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onReorder: (List<Long>) -> Unit,
) {
    var orderedSongs by remember { mutableStateOf(songs) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(songs) {
        if (draggingIndex == -1) orderedSongs = songs
    }

    LazyColumn(
        contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(orderedSongs, key = { _, song -> song.id }) { index, song ->
            val isDragging = index == draggingIndex

            Row(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { if (isDragging) translationY = dragOffset }
                    .then(if (isDragging) Modifier else Modifier.animateItem())
                    .onGloballyPositioned { itemHeightPx = it.size.height.toFloat() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = stringResource(R.string.playlist_drag_handle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .pointerInput(song.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    // No se fía del índice capturado al componer: si esta
                                    // fila ya se movió una vez, esa clausura estaría
                                    // apuntando a un puesto viejo.
                                    draggingIndex = orderedSongs.indexOfFirst { it.id == song.id }
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    draggingIndex = -1
                                    dragOffset = 0f
                                    onReorder(orderedSongs.map { it.id })
                                },
                                onDragCancel = {
                                    draggingIndex = -1
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y

                                    val height = itemHeightPx
                                    val current = draggingIndex
                                    if (height <= 0f || current == -1) {
                                        return@detectDragGesturesAfterLongPress
                                    }

                                    if (dragOffset > height / 2 && current < orderedSongs.lastIndex) {
                                        orderedSongs = orderedSongs.toMutableList().apply {
                                            add(current + 1, removeAt(current))
                                        }
                                        draggingIndex = current + 1
                                        dragOffset -= height
                                    } else if (dragOffset < -height / 2 && current > 0) {
                                        orderedSongs = orderedSongs.toMutableList().apply {
                                            add(current - 1, removeAt(current))
                                        }
                                        draggingIndex = current - 1
                                        dragOffset += height
                                    }
                                },
                            )
                        },
                )

                SongRow(
                    song = song,
                    isPlaying = song.id == playingSongId,
                    onClick = { onPlay(song) },
                    modifier = Modifier.weight(1f),
                    trailing = {
                        IconButton(onClick = { onRemoveSong(song) }) {
                            Icon(
                                imageVector = Icons.Rounded.RemoveCircleOutline,
                                contentDescription = stringResource(R.string.playlist_remove_song),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }
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
