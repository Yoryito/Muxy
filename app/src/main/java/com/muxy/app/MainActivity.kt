package com.muxy.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muxy.app.ui.library.LibraryScreen
import com.muxy.app.ui.library.LibraryViewModel
import com.muxy.app.ui.player.MiniPlayer
import com.muxy.app.ui.player.PlayerScreen
import com.muxy.app.ui.playlists.PlaylistDetailScreen
import com.muxy.app.ui.playlists.PlaylistsScreen
import com.muxy.app.ui.playlists.PlaylistsViewModel
import com.muxy.app.ui.search.SearchScreen
import com.muxy.app.ui.search.SearchViewModel
import com.muxy.app.ui.settings.SettingsScreen
import com.muxy.app.ui.settings.SettingsViewModel
import com.muxy.app.ui.settings.UpdateDialog
import com.muxy.app.ui.settings.updateOrNull
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
                    playlistsViewModel = viewModel(
                        factory = PlaylistsViewModel.Factory(container.playlists, container.player),
                    ),
                    searchViewModel = viewModel(
                        factory = SearchViewModel.Factory(
                            container.youtube,
                            container.downloads,
                            container.library,
                        ),
                    ),
                    settingsViewModel = viewModel(
                        factory = SettingsViewModel.Factory(
                            container.updateChecker,
                            container.updateInstaller,
                            container.updatePreferences,
                            container.playlistBackup,
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

/**
 * Lleva al usuario al ajuste de "instalar apps desconocidas" cuando el
 * ViewModel avisa de que falta, y le devuelve la respuesta al volver.
 *
 * Está aquí y no en la pantalla de ajustes porque el aviso de actualización
 * puede saltar desde cualquier pestaña, y lanzar un intent necesita el
 * `ActivityResultRegistry` de la Activity.
 */
@Composable
private fun UpdatePermissionBridge(settingsViewModel: SettingsViewModel) {
    val context = LocalContext.current
    val hint = stringResource(R.string.update_permission_needed)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { settingsViewModel.onInstallPermissionResult() }

    LaunchedEffect(Unit) {
        settingsViewModel.installPermissionRequests.collect {
            // Aterrizar de golpe en los ajustes de Android sin saber por qué es
            // desconcertante; el aviso va antes de salir de la app.
            Toast.makeText(context, hint, Toast.LENGTH_LONG).show()
            launcher.launch(settingsViewModel.installPermissionIntent())
        }
    }
}

/**
 * Los selectores de "guardar en" y "abrir archivo" del backup de playlists.
 *
 * Van en la Activity y no en el ViewModel porque escribir o leer en la ruta
 * que elige el usuario necesita un `ContentResolver`, y solo la Activity lo
 * tiene. El JSON a exportar se guarda aparte porque `exportReady` avisa de que
 * ya está listo, pero el selector solo devuelve la ruta más tarde, cuando el
 * usuario termina de elegirla.
 */
@Composable
private fun BackupBridge(settingsViewModel: SettingsViewModel) {
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val content = pendingExport
        pendingExport = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        val success = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                ?: error("Sin flujo de escritura")
        }.isSuccess
        settingsViewModel.onExportWritten(success)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
        settingsViewModel.importPlaylists(content)
    }

    LaunchedEffect(Unit) {
        settingsViewModel.exportReady.collect { content ->
            pendingExport = content
            exportLauncher.launch("muxy-playlists.json")
        }
    }

    // El botón "Importar" de ajustes llama a esto directamente; se expone como
    // efecto para no tener que colar el launcher fuera de esta función.
    LaunchedEffect(Unit) {
        settingsViewModel.importRequests.collect {
            importLauncher.launch(arrayOf("application/json"))
        }
    }
}

private enum class Destination(val labelRes: Int) {
    Library(R.string.nav_library),
    Playlists(R.string.nav_playlists),
    Search(R.string.nav_search),
    Settings(R.string.nav_settings),
}

@Composable
private fun MuxyApp(
    libraryViewModel: LibraryViewModel,
    playlistsViewModel: PlaylistsViewModel,
    searchViewModel: SearchViewModel,
    settingsViewModel: SettingsViewModel,
) {
    var current by rememberSaveable { mutableStateOf(Destination.Library) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }

    val songs by libraryViewModel.songs.collectAsStateWithLifecycle()
    val libraryIsEmpty by libraryViewModel.libraryIsEmpty.collectAsStateWithLifecycle()
    val libraryQuery by libraryViewModel.query.collectAsStateWithLifecycle()
    val librarySort by libraryViewModel.sort.collectAsStateWithLifecycle()
    val librarySelectionMode by libraryViewModel.selectionMode.collectAsStateWithLifecycle()
    val librarySelectedIds by libraryViewModel.selectedIds.collectAsStateWithLifecycle()
    val playback by libraryViewModel.playback.collectAsStateWithLifecycle()
    val sleepTimer by libraryViewModel.sleepTimer.collectAsStateWithLifecycle()
    val search by searchViewModel.state.collectAsStateWithLifecycle()

    val playlists by playlistsViewModel.summaries.collectAsStateWithLifecycle()
    val openPlaylist by playlistsViewModel.openPlaylist.collectAsStateWithLifecycle()
    val openPlaylistSongs by playlistsViewModel.openSongs.collectAsStateWithLifecycle()

    val settings by settingsViewModel.state.collectAsStateWithLifecycle()

    val defaultPlaylistName = stringResource(R.string.playlist_default_name)

    // Comprobar si hay versión nueva es lo primero que hace la app al abrirse; el
    // ViewModel se encarga de que sea una sola vez por arranque.
    LaunchedEffect(Unit) { settingsViewModel.checkOnStart() }

    UpdatePermissionBridge(settingsViewModel)
    BackupBridge(settingsViewModel)

    // Si la cola se vacía, el reproductor se queda sin nada que enseñar.
    LaunchedEffect(playback.songId) {
        if (playback.songId == null) playerOpen = false
    }

    BackHandler(enabled = playerOpen) { playerOpen = false }
    // Con el reproductor abierto manda él: atrás lo cierra antes de tocar la
    // pestaña que hay debajo.
    BackHandler(enabled = !playerOpen && openPlaylist != null) {
        playlistsViewModel.closeDetail()
    }

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
                                            Destination.Playlists -> Icons.AutoMirrored.Rounded.QueueMusic
                                            Destination.Search -> Icons.Rounded.Search
                                            Destination.Settings -> Icons.Rounded.Settings
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
                    libraryIsEmpty = libraryIsEmpty,
                    query = libraryQuery,
                    sort = librarySort,
                    playingSongId = playback.songId,
                    playlists = playlists,
                    playlistsContaining = playlistsViewModel::playlistsContaining,
                    selectionMode = librarySelectionMode,
                    selectedIds = librarySelectedIds,
                    onQueryChange = libraryViewModel::onQueryChange,
                    onSortChange = libraryViewModel::onSortChange,
                    onPlay = libraryViewModel::play,
                    onDelete = libraryViewModel::delete,
                    onTogglePlaylist = playlistsViewModel::toggleSong,
                    onCreatePlaylistWith = { name, songId ->
                        playlistsViewModel.create(name, defaultPlaylistName, setOf(songId))
                    },
                    onToggleSelectionMode = libraryViewModel::toggleSelectionMode,
                    onToggleSelected = libraryViewModel::toggleSelected,
                    onDeleteSelected = libraryViewModel::deleteSelected,
                    onAddSelectedToPlaylist = { playlistId ->
                        playlistsViewModel.addSongs(playlistId, librarySelectedIds)
                        libraryViewModel.exitSelectionMode()
                    },
                    onCreatePlaylistWithSelected = { name ->
                        playlistsViewModel.create(name, defaultPlaylistName, librarySelectedIds)
                        libraryViewModel.exitSelectionMode()
                    },
                    contentPadding = innerPadding,
                )

                // La pestaña enseña la lista o el detalle según haya una abierta:
                // una sola pantalla de profundidad no justifica un NavHost.
                Destination.Playlists -> openPlaylist.let { playlist ->
                    if (playlist == null) {
                        PlaylistsScreen(
                            playlists = playlists,
                            onOpen = playlistsViewModel::open,
                            onCreate = { playlistsViewModel.create(it, defaultPlaylistName) },
                            contentPadding = innerPadding,
                        )
                    } else {
                        PlaylistDetailScreen(
                            playlist = playlist,
                            songs = openPlaylistSongs,
                            playingSongId = playback.songId,
                            onBack = playlistsViewModel::closeDetail,
                            onPlayAll = playlistsViewModel::playAll,
                            onPlay = playlistsViewModel::play,
                            onRemoveSong = { playlistsViewModel.removeSong(playlist.id, it.id) },
                            onReorder = { playlistsViewModel.reorder(playlist.id, it) },
                            onRename = { playlistsViewModel.rename(playlist.id, it) },
                            onDelete = { playlistsViewModel.delete(playlist.id) },
                            contentPadding = innerPadding,
                        )
                    }
                }

                Destination.Search -> SearchScreen(
                    state = search,
                    onQueryChange = searchViewModel::onQueryChange,
                    onRetry = searchViewModel::retry,
                    onDownload = searchViewModel::onDownloadRequested,
                    onCancelDownload = searchViewModel::onCancelRequested,
                    onDownloadAll = searchViewModel::onDownloadAllRequested,
                    contentPadding = innerPadding,
                )

                Destination.Settings -> SettingsScreen(
                    state = settings,
                    onToggleAutoCheck = settingsViewModel::setAutoCheck,
                    onCheckNow = settingsViewModel::checkNow,
                    onUpdate = settingsViewModel::startUpdate,
                    onExportPlaylists = settingsViewModel::exportPlaylists,
                    onImportPlaylists = settingsViewModel::requestImport,
                    onDismissBackupNotice = settingsViewModel::dismissBackupNotice,
                    contentPadding = innerPadding,
                )
            }
        }

        // El aviso de versión nueva no pertenece a ninguna pestaña: sale donde
        // esté el usuario, y por encima del reproductor si lo tiene abierto.
        if (settings.promptVisible) {
            settings.update.updateOrNull()?.let { update ->
                UpdateDialog(
                    update = update,
                    state = settings.update,
                    onUpdate = settingsViewModel::startUpdate,
                    onDismiss = settingsViewModel::dismissPrompt,
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
                sleepTimer = sleepTimer,
                onCollapse = { playerOpen = false },
                onTogglePlayPause = libraryViewModel::togglePlayPause,
                onNext = libraryViewModel::next,
                onPrevious = libraryViewModel::previous,
                onSeek = libraryViewModel::seekTo,
                onToggleShuffle = libraryViewModel::toggleShuffle,
                onCycleRepeat = libraryViewModel::cycleRepeat,
                onCyclePlaybackSpeed = libraryViewModel::cyclePlaybackSpeed,
                onSetSleepTimer = libraryViewModel::setSleepTimer,
                onSetSleepTimerEndOfSong = libraryViewModel::setSleepTimerEndOfSong,
                onCancelSleepTimer = libraryViewModel::cancelSleepTimer,
            )
        }
    }
}
