package com.muxy.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.muxy.app.download.HttpFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Baja el APK de una release y se lo pasa al instalador de Android.
 *
 * Android no permite instalar en silencio sin ser device-owner o tener root, así
 * que el último paso es siempre la pantalla del sistema con su "¿Instalar?": la
 * app puede traer el archivo, pero quien acepta es el usuario.
 */
class UpdateInstaller(
    private val context: Context,
    private val fetcher: HttpFetcher,
) {

    /**
     * Instalar desde fuera de una tienda exige que el usuario haya dado permiso a
     * *esta* app en concreto ("Instalar apps desconocidas"). Se pregunta antes de
     * descargar: bajar 20 MB para chocar al final con un permiso es tiempo tirado.
     */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Intent que abre el ajuste del sistema donde se concede ese permiso. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))

    suspend fun download(
        update: AvailableUpdate,
        onProgress: suspend (Float?) -> Unit,
    ): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Los intentos anteriores no valen para nada y ocupan lo que ocupa un
        // APK: cada descarga empieza dejando la carpeta con un solo archivo.
        dir.listFiles()?.forEach { it.delete() }

        val target = File(dir, "muxy-${update.versionName}.apk")

        // Un corte de red a mitad de descarga es lo normal en un móvil, y volver
        // a empezar cuesta unos megas: mejor eso que dar la actualización por
        // imposible y que el usuario tenga que buscarla en ajustes.
        repeat(ATTEMPTS) { attempt ->
            val outcome = runCatching { fetcher.toFile(update.apkUrl, target, onProgress) }
            if (outcome.isSuccess) return target

            target.delete()
            if (attempt == ATTEMPTS - 1) throw outcome.exceptionOrNull()!!
            delay(RETRY_DELAY_MS)
        }
        error("inalcanzable")
    }

    /**
     * Instala por `PackageInstaller`, no lanzando un `ACTION_VIEW` con el APK.
     *
     * Aquello abre un "Abrir con" en cuanto hay otra app instalada que diga
     * entender los APK —un explorador de archivos, un editor de documentos—, y
     * elegir a mano entre iconos no es lo que se espera al tocar "Actualizar".
     * Con la sesión, la siguiente pantalla es directamente la del sistema.
     *
     * El archivo se copia dentro de la sesión, así que no hace falta compartirlo
     * por `FileProvider`: el instalador ya no lee la caché de la app.
     */
    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        )

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("muxy", 0, apk.length()).use { output ->
                apk.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }

            // Mutable a propósito: el sistema mete en este intent el resultado y,
            // si hace falta confirmación, la pantalla que hay que abrir.
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )

            session.commit(callback.intentSender)
        }
    }

    private companion object {
        const val ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1_500L
    }
}
