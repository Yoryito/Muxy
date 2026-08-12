package com.muxy.app.update

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * El único ajuste que se guarda de momento. Va en un `SharedPreferences` pelado:
 * es un booleano, y montar DataStore para eso sería más andamiaje que ajuste.
 */
class UpdatePreferences(context: Context) {

    private val prefs = context.getSharedPreferences("muxy_settings", Context.MODE_PRIVATE)

    private val _autoCheck = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CHECK, true))
    val autoCheck: StateFlow<Boolean> = _autoCheck.asStateFlow()

    fun setAutoCheck(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
        _autoCheck.value = enabled
    }

    private companion object {
        const val KEY_AUTO_CHECK = "auto_check_updates"
    }
}
