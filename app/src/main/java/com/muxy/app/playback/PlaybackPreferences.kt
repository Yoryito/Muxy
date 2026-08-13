package com.muxy.app.playback

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ajustes de reproducción, en el mismo `SharedPreferences` pelado que
 * [com.muxy.app.update.UpdatePreferences]: son booleanos, y no hay tantos
 * como para justificar DataStore.
 *
 * [PlaybackService] no tiene inyección de dependencias (lo construye el
 * framework), así que crea su propia instancia de esta clase. Es seguro:
 * `getSharedPreferences` devuelve el mismo objeto en memoria para el mismo
 * nombre de archivo dentro de un proceso, así que ambas instancias comparten
 * los mismos datos sin necesidad de IPC.
 */
class PlaybackPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("muxy_settings", Context.MODE_PRIVATE)

    private val _normalizeVolume = MutableStateFlow(prefs.getBoolean(KEY_NORMALIZE_VOLUME, true))
    val normalizeVolume: StateFlow<Boolean> = _normalizeVolume.asStateFlow()

    /**
     * Si está desactivado, la reproducción se para sola al terminar cada
     * canción en vez de seguir con la siguiente de la cola.
     */
    private val _autoplay = MutableStateFlow(prefs.getBoolean(KEY_AUTOPLAY, true))
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()

    /**
     * [PlaybackService] tiene su propia instancia de esta clase, separada de la
     * que usan los ajustes — ver la nota de la clase. Sin este listener, tocar
     * el interruptor no se notaría hasta la siguiente vez que `PlaybackService`
     * releyera el `SharedPreferences` por su cuenta, es decir, con la canción
     * siguiente. Se guarda como campo (no como lambda suelta al registrar)
     * porque `registerOnSharedPreferenceChangeListener` solo mantiene una
     * referencia débil: sin nada más sujetándolo, el recolector de basura se lo
     * llevaría y el listener dejaría de sonar en silencio.
     */
    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            KEY_NORMALIZE_VOLUME -> _normalizeVolume.value = p.getBoolean(KEY_NORMALIZE_VOLUME, true)
            KEY_AUTOPLAY -> _autoplay.value = p.getBoolean(KEY_AUTOPLAY, true)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(changeListener)
    }

    fun setNormalizeVolume(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NORMALIZE_VOLUME, enabled).apply()
        _normalizeVolume.value = enabled
    }

    fun setAutoplay(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOPLAY, enabled).apply()
        _autoplay.value = enabled
    }

    companion object {
        const val KEY_NORMALIZE_VOLUME = "normalize_volume"
        const val KEY_AUTOPLAY = "autoplay"
    }
}
