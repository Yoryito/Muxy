package com.muxy.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.muxy.app.R
import com.muxy.app.data.PlaylistSummary
import com.muxy.app.data.Song
import com.muxy.app.ui.components.LilyPadFrame
import com.muxy.app.ui.components.PochiEmptyState
import com.muxy.app.ui.components.PochiPose
import com.muxy.app.ui.components.SongCover
import java.io.File

/**
 * El inicio: una rejilla de accesos rápidos arriba y carruseles temáticos
 * debajo.
 *
 * La rejilla es para volver a lo de siempre en un toque; los carruseles son para
 * reencontrarte con lo que ya tienes. Ninguna sección se pinta vacía — una fila
 * con un título y nada debajo hace que la app parezca rota, no en construcción.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    greeting: Greeting,
    playingSongId: Long?,
    onOpenAllDownloads: () -> Unit,
    onOpenMyTop: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    if (state.libraryIsEmpty) {
        PochiEmptyState(
            pose = PochiPose.Resting,
            title = stringResource(R.string.library_empty_title),
            body = stringResource(R.string.library_empty_body),
            modifier = modifier.padding(contentPadding),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item {
            Text(
                text = stringResource(greeting.labelRes()),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
            )
        }

        // La rejilla va a mano en filas de dos y no con LazyVerticalGrid: una
        // rejilla perezosa dentro de una columna perezosa no tiene alto que
        // medir y revienta en tiempo de ejecución.
        items(state.quickTiles.chunked(2)) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { tile ->
                    QuickTileCard(
                        tile = tile,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (tile) {
                                QuickTile.AllDownloads -> onOpenAllDownloads()
                                QuickTile.MyTop -> onOpenMyTop()
                                is QuickTile.OfPlaylist -> onOpenPlaylist(tile.playlist.id)
                            }
                        },
                    )
                }
                // Una fila impar deja hueco a la derecha en vez de estirar la
                // última tarjeta al doble de ancho que las demás.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (state.recents.isNotEmpty()) {
            item {
                CarouselSection(title = stringResource(R.string.home_recent_title)) {
                    items(state.recents, key = { it.key() }) { card ->
                        when (card) {
                            is HomeCard.OfSong -> SongCard(
                                song = card.song,
                                isPlaying = card.song.id == playingSongId,
                                onClick = { onPlaySong(card.song, state.recents.songs()) },
                            )

                            is HomeCard.OfPlaylist -> PlaylistCard(
                                playlist = card.playlist,
                                onClick = { onOpenPlaylist(card.playlist.id) },
                            )

                            is HomeCard.OfArtist -> ArtistCard(
                                artist = card.artist,
                                onClick = { onOpenArtist(card.artist.name) },
                            )
                        }
                    }
                }
            }
        }

        if (state.mostPlayed.isNotEmpty()) {
            item {
                CarouselSection(title = stringResource(R.string.home_most_played_title)) {
                    items(state.mostPlayed, key = { it.id }) { song ->
                        SongCard(
                            song = song,
                            isPlaying = song.id == playingSongId,
                            onClick = { onPlaySong(song, state.mostPlayed) },
                        )
                    }
                }
            }
        }

        if (state.artists.isNotEmpty()) {
            item {
                CarouselSection(title = stringResource(R.string.home_artists_title)) {
                    items(state.artists, key = { it.name }) { artist ->
                        ArtistCard(artist = artist, onClick = { onOpenArtist(artist.name) })
                    }
                }
            }
        }

        if (state.recentlyAdded.isNotEmpty()) {
            item {
                CarouselSection(title = stringResource(R.string.home_recently_added_title)) {
                    items(state.recentlyAdded, key = { it.id }) { song ->
                        SongCard(
                            song = song,
                            isPlaying = song.id == playingSongId,
                            onClick = { onPlaySong(song, state.recentlyAdded) },
                        )
                    }
                }
            }
        }
    }
}

/** Cabecera + fila que se desplaza. El patrón se repite en todas las secciones. */
@Composable
private fun CarouselSection(title: String, content: LazyListScope.() -> Unit) {
    Column(Modifier.padding(top = 22.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/**
 * Tarjeta ancha de la rejilla: carátula cuadrada pegada al borde izquierdo y el
 * nombre al lado. Es deliberadamente compacta — son atajos, no contenido.
 */
@Composable
private fun QuickTileCard(tile: QuickTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (tile) {
            QuickTile.AllDownloads -> TileIcon(
                icon = Icons.Rounded.LibraryMusic,
                background = MaterialTheme.colorScheme.primaryContainer,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            QuickTile.MyTop -> TileIcon(
                icon = Icons.AutoMirrored.Rounded.TrendingUp,
                background = MaterialTheme.colorScheme.tertiaryContainer,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )

            is QuickTile.OfPlaylist -> TileCover(tile.playlist)
        }

        Text(
            text = when (tile) {
                QuickTile.AllDownloads -> stringResource(R.string.home_all_downloads)
                QuickTile.MyTop -> stringResource(R.string.home_my_top)
                is QuickTile.OfPlaylist -> tile.playlist.name
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

/** El cuadrado de la izquierda de un atajo fijo. Cuadrado y no nenúfar: es un icono, no una carátula. */
@Composable
private fun TileIcon(icon: ImageVector, background: Color, tint: Color) {
    Column(
        modifier = Modifier
            .size(56.dp)
            .background(background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = tint)
    }
}

@Composable
private fun TileCover(playlist: PlaylistSummary) {
    Column(
        modifier = Modifier
            .size(56.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
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
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** El ancho de las tarjetas de carrusel. Todas iguales, para que las filas rimen. */
private val CARD_WIDTH: Dp = 132.dp

@Composable
private fun SongCard(song: Song, isPlaying: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(CARD_WIDTH)
            .clickable(onClick = onClick),
    ) {
        // La muesca se mide en grados, así que a 132 dp la de por defecto abriría
        // un pedazo de tarta: se estrecha como en la carátula del reproductor.
        SongCover(song = song, isPlaying = isPlaying, size = CARD_WIDTH, notchWidth = 7f)
        CardLabels(
            title = song.title,
            subtitle = song.artist ?: stringResource(R.string.unknown_artist),
            highlighted = isPlaying,
        )
    }
}

@Composable
private fun PlaylistCard(playlist: PlaylistSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(CARD_WIDTH)
            .clickable(onClick = onClick),
    ) {
        LilyPadFrame(
            modifier = Modifier.size(CARD_WIDTH),
            notchAngle = 300f + (playlist.id % 5) * 24f,
            notchWidth = 7f,
            background = MaterialTheme.colorScheme.secondaryContainer,
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
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        CardLabels(
            title = playlist.name,
            subtitle = pluralStringResource(
                R.plurals.playlist_song_count,
                playlist.songCount,
                playlist.songCount,
            ),
        )
    }
}

@Composable
private fun ArtistCard(artist: ArtistSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(CARD_WIDTH)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Los artistas van en círculo y no en nenúfar, como en cualquier
        // reproductor: distingue de un vistazo "quién" de "qué" sin leer nada.
        Column(
            modifier = Modifier
                .size(CARD_WIDTH)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (artist.coverArtPath != null) {
                AsyncImage(
                    model = File(artist.coverArtPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        CardLabels(
            title = artist.name,
            subtitle = pluralStringResource(
                R.plurals.playlist_song_count,
                artist.songCount,
                artist.songCount,
            ),
            centered = true,
        )
    }
}

@Composable
private fun CardLabels(
    title: String,
    subtitle: String,
    highlighted: Boolean = false,
    centered: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Clave estable de una tarjeta. Lleva el tipo delante porque una canción y una
 * lista pueden compartir id: son tablas distintas, y sin prefijo el carrusel
 * mezclado tendría claves repetidas.
 */
private fun HomeCard.key(): String = when (this) {
    is HomeCard.OfSong -> "song-${song.id}"
    is HomeCard.OfPlaylist -> "playlist-${playlist.id}"
    is HomeCard.OfArtist -> "artist-${artist.name}"
}

/** Las canciones de un carrusel mezclado, para armar la cola al tocar una. */
private fun List<HomeCard>.songs(): List<Song> = filterIsInstance<HomeCard.OfSong>().map { it.song }

private fun Greeting.labelRes(): Int = when (this) {
    Greeting.Morning -> R.string.home_greeting_morning
    Greeting.Afternoon -> R.string.home_greeting_afternoon
    Greeting.Evening -> R.string.home_greeting_evening
}
