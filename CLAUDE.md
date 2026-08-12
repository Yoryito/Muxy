# Muxy

Reproductor y librería de música local para Android. El usuario busca una canción, la app la localiza en YouTube, extrae solo el audio, lo convierte y lo guarda en una librería local con reproductor propio.

## Contexto y restricciones

- **Uso personal.** Para el propietario y una amiga. Se distribuye como APK por sideload, **no** va a Google Play. Las políticas de la tienda sobre apps que descargan de YouTube no aplican; descargar de YouTube incumple sus ToS y es un compromiso aceptado conscientemente para uso personal.
- **Spotify no se puede descargar.** Su audio está protegido con DRM. Cualquier integración con Spotify es *solo metadatos*: buscar en su catálogo para identificar título/artista y luego resolver y descargar el audio equivalente desde YouTube. No intentar extraer audio de Spotify.
- **El diseño es un requisito de primera clase**, no decoración. Ver "Dirección visual".

## Stack y por qué

| Área | Elección | Razón (no deshacer sin motivo) |
|---|---|---|
| Extracción YouTube | **NewPipeExtractor** tras la interfaz `YoutubeAudioResolver` | Alternativa era yt-dlp embebido (vía Chaquopy), que parchea más rápido pero arrastra un runtime de Python entero. La interfaz existe precisamente para poder cambiar de backend si NewPipeExtractor se queda roto mucho tiempo. |
| Conversión de audio | **Media3 Transformer** → salida **M4A/AAC** | `ffmpeg-kit` fue archivado y retirado de Maven Central en 2025 y no hay sucesor consolidado. Transformer usa MediaCodec nativo, cero binarios de terceros. Por eso la salida es M4A y **no MP3**: MP3 obligaría a volver a FFmpeg. |
| Etiquetado | **eAlvaTag** | Escribe metadatos y carátula embebida en M4A. |
| Reproducción | **androidx.media3** (ExoPlayer + media3-session) con `MediaSessionService` | Estándar actual de Google para audio en segundo plano con controles de notificación. |
| Almacenamiento | **Room** + archivos en almacenamiento específico de la app | App de 1-2 usuarios, sin nube ni sincronización. No hace falta nada más. |
| Descargas | **WorkManager** con worker en primer plano | Sobreviven a que la app pase a segundo plano. |
| DI | Contenedor manual (`AppContainer`), **sin Hilt** | La app es pequeña; Hilt añadiría procesamiento de anotaciones sin beneficio real. |
| Actualizaciones | `UpdateChecker` contra **GitHub Releases** | Sin Play Store no hay auto-update. Compara `versionCode` al arrancar y ofrece instalar. Android no permite auto-instalación silenciosa sin root/device-owner, así que siempre requiere confirmación del usuario. |

## Dirección visual

Tema de **ranas y nenúfares**, estilo "estanque acogedor" — ilustración cálida tipo cuento, paleta botánica apagada, espacio en blanco real.

Lo que hay que **evitar** activamente (look de "AI slop"): degradados morados/azules, glassmorphism, neón, emojis como decoración, sombras en todo, mascota tipo clipart.

- La fuente de verdad de la paleta y tipografía es `app/src/main/java/.../ui/theme/`. No hardcodear colores en las pantallas.
- **Dynamic color de Android está desactivado a propósito** para que el tema sea idéntico en todos los dispositivos. No reactivarlo.
- La ranita mascota aparece solo donde cumple una función (estados vacíos, error, éxito), nunca como adorno suelto.

## Entorno de desarrollo

Esta máquina no tiene Android Studio, solo el SDK por línea de comandos:

- SDK: `F:\03_IA\Tools\Android\Sdk` (platform android-36, build-tools 36.0.0, platform-tools 37, cmdline-tools 22.0)
- JDK: `F:\Java Open JDK\Hotspot` (Temurin 21) — hay que exportar `JAVA_HOME` a mano, no está en el entorno
- Gradle: `F:\03_IA\Tools\Gradle\gradle-8.14.5` (el proyecto usa su propio wrapper; esto solo se usó para generarlo)

**Proxy que intercepta TLS.** Esta máquina tiene un proxy que intercepta TLS y la JVM no confía en su certificado, así que cualquier herramienta basada en JVM falla al descargar dependencias (`sdkmanager`: "IO exception while downloading manifest"; Gradle: "could not resolve plugin artifact"). curl sí funciona, lo que despista. La solución es leer las raíces de confianza del almacén de Windows:

- Gradle: ya resuelto en `~/.gradle/gradle.properties` con `systemProp.javax.net.ssl.trustStoreType=Windows-ROOT` (fuera del repo, es específico de esta máquina).
- sdkmanager: hay que pasarlo a mano cada vez.

```bash
JAVA_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT" sdkmanager "<paquete>"
```

## Versiones fijadas a propósito (no subir sin comprobar)

Las versiones del `libs.versions.toml` **no son las últimas**, y bajarlas fue deliberado:

- **AGP 8.13.2 / compileSdk 36.** Las últimas AndroidX (core-ktx 1.19, lifecycle 2.11, activity 1.13…) exigen AGP 9.1+ y compileSdk 37, pero **la API 37 todavía no existe como plataforma estable** en el repositorio del SDK. Por eso todas las AndroidX están una o dos versiones por debajo de la última.
- **Kotlin 2.2.21.** Es la última con la que KSP publica versión emparejada (`2.2.21-2.0.5`), y Room necesita KSP. KSP cambió a versionado independiente y aún no cubre Kotlin 2.3/2.4.
- **Coil 3.3.0.** La 3.5.0 arrastra `kotlin-stdlib` 2.4.0, que el compilador de Kotlin 2.2.21 no sabe leer.

Cuando salga la plataforma android-37 estable y KSP alcance a Kotlin 2.4, se puede subir todo el bloque de golpe.

## Comandos

```bash
./gradlew assembleDebug          # compilar APK de debug
./gradlew installDebug           # compilar e instalar en el móvil conectado
adb logcat -s Muxy               # ver logs de la app
```

## Puntos frágiles conocidos

- **La extracción de YouTube se romperá periódicamente.** Es esperado, no un bug puntual. Cuando pase: subir la versión de NewPipeExtractor en `gradle/libs.versions.toml`; si eso no basta, la interfaz `YoutubeAudioResolver` permite cambiar de backend tocando un solo sitio.
- Salida solo en M4A. Si algún día hiciera falta MP3, implicaría reintroducir FFmpeg y su carga de mantenimiento.

## Estado actual

Fases: 0 memoria ✅ · 1 andamiaje + diseño ✅ · 2 reproducción ✅ · **3 búsqueda YouTube (siguiente)** · 4 pipeline de descarga · 5 pulido · 6 auto-actualización · 7 (opcional) Spotify.

Cada fase termina con prueba manual en el móvil real por USB antes de pasar a la siguiente. Móvil de pruebas: Samsung Galaxy A55 (`SM_A556B`).

Lo que ya funciona, verificado en dispositivo: librería con Room, reproducción con Media3 en segundo plano, controles en notificación y pantalla de bloqueo, mini-reproductor.

**Truco de pruebas:** `MusicLibrary.sync()` da de alta cualquier audio que aparezca en la carpeta de música de la app, así que se pueden meter canciones sin pasar por la descarga:

```bash
adb push "cancion.wav" /sdcard/Android/data/com.muxy.app.debug/files/music/
```

## Pochi

La mascota se llama **Pochi**. Está dibujada en Canvas (`ui/components/Pochi.kt`), no como vector estático, porque así se animan las partes por separado: flota, respira y parpadea con periodos distintos para que los ciclos no caigan en fase.

Es deliberadamente regordeta —más ancha que alta— y las dos poses comparten el mismo cuerpo para que sea reconociblemente la misma rana. Al tocar la cara hay que respetar el aire entre la boca y la tripa, y mantener los mofletes dentro del contorno del cuerpo: ahí es donde los detalles se solapaban antes.
