package com.muxy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muxy.app.ui.components.PochiEmptyState
import com.muxy.app.ui.components.PochiPose
import com.muxy.app.ui.library.LibraryScreen
import com.muxy.app.ui.library.LibraryViewModel
import com.muxy.app.ui.player.MiniPlayer
import com.muxy.app.ui.search.SearchScreen
import com.muxy.app.ui.search.SearchViewModel
import com.muxy.app.ui.theme.MuxyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MuxyApplication).container

        setContent {
            MuxyTheme {
                MuxyApp(
                    libraryViewModel = viewModel(
                        factory = LibraryViewModel.Factory(container.library, container.player),
                    ),
                    searchViewModel = viewModel(
                        factory = SearchViewModel.Factory(container.youtube),
                    ),
                )
            }
        }
    }
}

private enum class Destination(val labelRes: Int) {
    Library(R.string.nav_library),
    Search(R.string.nav_search),
}

@Composable
private fun MuxyApp(
    libraryViewModel: LibraryViewModel,
    searchViewModel: SearchViewModel,
) {
    var current by rememberSaveable { mutableStateOf(Destination.Library) }

    val songs by libraryViewModel.songs.collectAsStateWithLifecycle()
    val playback by libraryViewModel.playback.collectAsStateWithLifecycle()
    val search by searchViewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniPlayer(
                    state = playback,
                    onTogglePlayPause = libraryViewModel::togglePlayPause,
                    onNext = libraryViewModel::next,
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = current == destination,
                            onClick = { current = destination },
                            icon = {
                                Icon(
                                    imageVector = when (destination) {
                                        Destination.Library -> Icons.Rounded.LibraryMusic
                                        Destination.Search -> Icons.Rounded.Search
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(
                                    stringResource(destination.labelRes),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        when (current) {
            Destination.Library -> LibraryScreen(
                songs = songs,
                playingSongId = playback.songId,
                onPlay = libraryViewModel::play,
                contentPadding = innerPadding,
            )

            Destination.Search -> SearchScreen(
                state = search,
                onQueryChange = searchViewModel::onQueryChange,
                onRetry = searchViewModel::retry,
                onDownload = searchViewModel::onDownloadRequested,
                contentPadding = innerPadding,
            )
        }
    }
}
