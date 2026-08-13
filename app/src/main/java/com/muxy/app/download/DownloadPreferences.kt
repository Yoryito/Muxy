package com.muxy.app.download

import android.content.Context
import com.muxy.app.youtube.DownloadQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** La calidad de audio que se pide al descargar, guardada por su nombre. */
class DownloadPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("muxy_settings", Context.MODE_PRIVATE)

    private val _quality = MutableStateFlow(
        prefs.getString(KEY_QUALITY, null)
            ?.let { name -> DownloadQuality.entries.firstOrNull { it.name == name } }
            ?: DownloadQuality.High,
    )
    val quality: StateFlow<DownloadQuality> = _quality.asStateFlow()

    fun setQuality(quality: DownloadQuality) {
        prefs.edit().putString(KEY_QUALITY, quality.name).apply()
        _quality.value = quality
    }

    private companion object {
        const val KEY_QUALITY = "download_quality"
    }
}
