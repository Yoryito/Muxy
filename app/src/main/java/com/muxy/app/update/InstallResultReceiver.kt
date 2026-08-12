package com.muxy.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

private const val TAG = "Muxy"

/**
 * Lo que responde el sistema a la sesión de instalación.
 *
 * La parte que importa es `STATUS_PENDING_USER_ACTION`: `PackageInstaller` no
 * enseña nada por su cuenta, avisa por aquí de que hace falta el visto bueno del
 * usuario y manda dentro el intent de esa pantalla. Sin abrirlo, la
 * actualización se queda esperando para siempre sin decir nada.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                confirmationIntent(intent)?.let { confirmation ->
                    // Sale de un receiver, que no es una Activity y no tiene tarea propia.
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmation)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> Log.i(TAG, "Actualización instalada")

            // Cancelar en la pantalla del sistema también cae aquí; no es un fallo
            // que merezca molestar al usuario, pero conviene verlo en logcat.
            else -> Log.w(
                TAG,
                "Instalación no completada (estado $status): " +
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
            )
        }
    }

    private fun confirmationIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
}
