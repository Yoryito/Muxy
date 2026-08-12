package com.muxy.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
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
import com.muxy.app.ui.player.PlayerScreen
import com.muxy.app.ui.search.SearchScreen
import com.muxy.app.ui.search.SearchViewModel
import com.muxy.app.ui.theme.MuxyTheme

class MainActivity : ComponentActivity() {

    // Sin este permiso la descarga funciona igual, pero pierde su notificación
    // de progreso — y con ella el aviso de que algo sigue pasando en segundo plano.
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()

        val container = (application as MuxyApplication).container

        setContent {
            MuxyTheme {
                MuxyApp(
                    libraryViewModel = viewModel(
                        factory = LibraryViewModel.Factory(container.library, container.player),
                    ),
                    searchViewModel = viewModel(
                        factory = SearchViewModel.Factory(
                            container.youtube,
                            container.downloads,
                            container.library,
                        ),
                    ),
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
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
    var playerOpen by rememberSaveable { mutableStateOf(false) }

    val songs by libraryViewModel.songs.collectAsStateWithLifecycle()
    val playback by libraryViewModel.playback.collectAsStateWithLifecycle()
    val search by searchViewModel.state.collectAsStateWithLifecycle()

    // Si la cola se vacía, el reproductor se queda sin nada que enseñar.
    LaunchedEffect(playback.songId) {
        if (playback.songId == null) playerOpen = false
    }

    BackHandler(enabled = playerOpen) { playerOpen = false }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniPlayer(
                        state = playback,
                        onExpand = { playerOpen = true },
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
                    onCancelDownload = searchViewModel::onCancelRequested,
                    contentPadding = innerPadding,
                )
            }
        }

        // El reproductor completo va encima de todo, barra de navegación
        // incluida: mientras está abierto es la pantalla, no una sección más.
        AnimatedVisibility(
            visible = playerOpen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            PlayerScreen(
                state = playback,
                onCollapse = { playerOpen = false },
                onTogglePlayPause = libraryViewModel::togglePlayPause,
                onNext = libraryViewModel::next,
                onPrevious = libraryViewModel::previous,
                onSeek = libraryViewModel::seekTo,
                onToggleShuffle = libraryViewModel::toggleShuffle,
                onCycleRepeat = libraryViewModel::cycleRepeat,
            )
        }
    }
}
