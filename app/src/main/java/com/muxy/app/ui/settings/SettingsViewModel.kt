package com.muxy.app.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.muxy.app.BuildConfig
import com.muxy.app.data.PlaylistBackup
import com.muxy.app.update.AvailableUpdate
import com.muxy.app.update.UpdateCheck
import com.muxy.app.update.UpdateChecker
import com.muxy.app.update.UpdateInstaller
import com.muxy.app.update.UpdatePreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "Muxy"

/** Dónde está el asunto de la actualización ahora mismo. */
sealed interface UpdateState {
    /** Recién abierta la app, o comprobación aún sin hacer. */
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object CheckFailed : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState

    /** [progress] es null mientras el servidor no diga cuánto pesa. */
    data class Downloading(val update: AvailableUpdate, val progress: Float?) : UpdateState
    data class DownloadFailed(val update: AvailableUpdate) : UpdateState

    /** El APK está bajado y el instalador del sistema ya tiene la palabra. */
    data class Installing(val update: AvailableUpdate) : UpdateState
}

/**
 * La versión de la que va el estado, si es que va de alguna. Sirve tanto para
 * saber qué descargar como para saber si el diálogo tiene algo que enseñar.
 */
fun UpdateState.updateOrNull(): AvailableUpdate? = when (this) {
    is UpdateState.Available -> update
    is UpdateState.Downloading -> update
    is UpdateState.DownloadFailed -> update
    is UpdateState.Installing -> update
    else -> null
}

/** Cómo fue la última exportación o importación de playlists. */
sealed interface BackupNotice {
    data object Exported : BackupNotice
    data class Imported(val playlistsCreated: Int, val songsAdded: Int, val songsSkipped: Int) : BackupNotice
    data object ExportFailed : BackupNotice
    data object ImportFailed : BackupNotice
}

data class SettingsUiState(
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val autoCheck: Boolean = true,
    val update: UpdateState = UpdateState.Idle,
    /** El aviso emergente, que sale solo cuando la comprobación fue automática. */
    val promptVisible: Boolean = false,
    val backupNotice: BackupNotice? = null,
)

class SettingsViewModel(
    private val checker: UpdateChecker,
    private val installer: UpdateInstaller,
    private val preferences: UpdatePreferences,
    private val playlistBackup: PlaylistBackup,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(autoCheck = preferences.autoCheck.value))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /**
     * Avisa a la interfaz de que hace falta el permiso de "instalar apps
     * desconocidas": abrir ajustes del sistema necesita una Activity, y el
     * ViewModel no tiene ninguna.
     */
    private val _installPermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val installPermissionRequests: SharedFlow<Unit> = _installPermissionRequests.asSharedFlow()

    /**
     * El JSON ya generado, listo para que la Activity abra el selector de
     * dónde guardarlo — escribir en la ruta que elija el usuario necesita un
     * `ContentResolver`, que el ViewModel no tiene.
     */
    private val _exportReady = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val exportReady: SharedFlow<String> = _exportReady.asSharedFlow()

    /** El botón "Importar": solo pide a la Activity que abra el selector de archivo. */
    private val _importRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val importRequests: SharedFlow<Unit> = _importRequests.asSharedFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var checkedThisLaunch = false

    init {
        viewModelScope.launch {
            preferences.autoCheck.collect { enabled ->
                _state.update { it.copy(autoCheck = enabled) }
            }
        }
    }

    /**
     * Comprobación al abrir la app. Se hace una sola vez por arranque: la llamada
     * vive en un `LaunchedEffect`, que vuelve a correr con cada cambio de
     * configuración (girar la pantalla, cambiar a modo oscuro).
     */
    fun checkOnStart() {
        if (checkedThisLaunch || !preferences.autoCheck.value) return
        checkedThisLaunch = true
        runCheck(announce = true)
    }

    /** Comprobación a mano desde ajustes: el resultado se enseña ahí, sin diálogo. */
    fun checkNow() {
        checkedThisLaunch = true
        runCheck(announce = false)
    }

    fun setAutoCheck(enabled: Boolean) = preferences.setAutoCheck(enabled)

    /** Los ajustes del sistema donde se concede el permiso de instalar. */
    fun installPermissionIntent() = installer.permissionIntent()

    /**
     * Al volver de esos ajustes. Solo sigue si el permiso está: reintentar a
     * ciegas volvería a pedirlo y dejaría al usuario rebotando entre pantallas.
     */
    fun onInstallPermissionResult() {
        if (installer.canInstall()) startUpdate()
    }

    fun dismissPrompt() {
        _state.update { it.copy(promptVisible = false) }
    }

    /**
     * Descarga e instala. Si falta el permiso del sistema no se baja nada: se
     * pide primero y la interfaz vuelve a llamar aquí al volver de los ajustes.
     */
    fun startUpdate() {
        val update = _state.value.update.updateOrNull() ?: return
        if (downloadJob?.isActive == true) return

        if (!installer.canInstall()) {
            _installPermissionRequests.tryEmit(Unit)
            return
        }

        downloadJob = viewModelScope.launch {
            _state.update { it.copy(update = UpdateState.Downloading(update, 0f)) }

            runCatching {
                installer.download(update) { progress ->
                    _state.update { it.copy(update = UpdateState.Downloading(update, progress)) }
                }
            }
                .onSuccess { apk ->
                    // A partir de aquí manda la pantalla del sistema. El diálogo se
                    // cierra para no quedarse detrás pidiendo lo que ya está hecho.
                    _state.update {
                        it.copy(update = UpdateState.Installing(update), promptVisible = false)
                    }
                    installer.install(apk)
                }
                .onFailure { error ->
                    Log.w(TAG, "No se pudo descargar la actualización", error)
                    _state.update { it.copy(update = UpdateState.DownloadFailed(update)) }
                }
        }
    }

    fun exportPlaylists() {
        viewModelScope.launch {
            runCatching { playlistBackup.export() }
                .onSuccess { _exportReady.tryEmit(it) }
                .onFailure {
                    Log.w(TAG, "No se pudo preparar la copia de seguridad", it)
                    _state.update { s -> s.copy(backupNotice = BackupNotice.ExportFailed) }
                }
        }
    }

    /** La Activity llama aquí tras escribir (o intentar escribir) en la ruta elegida. */
    fun onExportWritten(success: Boolean) {
        _state.update {
            it.copy(backupNotice = if (success) BackupNotice.Exported else BackupNotice.ExportFailed)
        }
    }

    fun requestImport() {
        _importRequests.tryEmit(Unit)
    }

    /** [content] llega nulo cuando la Activity no pudo ni leer el archivo elegido. */
    fun importPlaylists(content: String?) {
        viewModelScope.launch {
            if (content == null) {
                _state.update { it.copy(backupNotice = BackupNotice.ImportFailed) }
                return@launch
            }
            runCatching { playlistBackup.import(content) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            backupNotice = BackupNotice.Imported(
                                playlistsCreated = result.playlistsCreated,
                                songsAdded = result.songsAdded,
                                songsSkipped = result.songsSkipped,
                            ),
                        )
                    }
                }
                .onFailure {
                    Log.w(TAG, "No se pudo importar la copia de seguridad", it)
                    _state.update { s -> s.copy(backupNotice = BackupNotice.ImportFailed) }
                }
        }
    }

    fun dismissBackupNotice() {
        _state.update { it.copy(backupNotice = null) }
    }

    private fun runCheck(announce: Boolean) {
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            _state.update { it.copy(update = UpdateState.Checking) }
            val result = checker.check()
            _state.update {
                it.copy(
                    update = when (result) {
                        is UpdateCheck.Available -> UpdateState.Available(result.update)
                        UpdateCheck.UpToDate -> UpdateState.UpToDate
                        UpdateCheck.Failed -> UpdateState.CheckFailed
                    },
                    promptVisible = announce && result is UpdateCheck.Available,
                )
            }
        }
    }

    class Factory(
        private val checker: UpdateChecker,
        private val installer: UpdateInstaller,
        private val preferences: UpdatePreferences,
        private val playlistBackup: PlaylistBackup,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(checker, installer, preferences, playlistBackup) as T
    }
}
