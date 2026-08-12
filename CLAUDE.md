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
- Pochi aparece solo donde cumple una función (estados vacíos, error, éxito), nunca como adorno suelto. Ver la sección "Pochi" más abajo.
- La muesca del nenúfar (`LilyPadShape`) tiene que ser **estrecha**: una cuña ancha convierte el nenúfar en un Pac-Man. Y donde se recorta sobre un fondo, recortarla de todas las capas deja ver el fondo de la pantalla por el hueco.
- Los contenedores de acento del modo oscuro tienen que ser tonos oscuros de verdad. Reutilizar un tono medio como fondo sobre el estanque nocturno queda chillón.

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

Hay que exportar `JAVA_HOME` antes de cualquier comando de Gradle, y `adb` no está en el PATH por defecto:

```bash
export JAVA_HOME="F:/Java Open JDK/Hotspot"
export PATH="$PATH:/f/03_IA/Tools/Android/Sdk/platform-tools"
./gradlew installDebug           # compilar e instalar en el móvil conectado
adb logcat -s Muxy               # ver los logs de la app
```

El id de aplicación en debug lleva sufijo: `com.muxy.app.debug`.

```bash
adb shell am start -n com.muxy.app.debug/com.muxy.app.MainActivity
adb exec-out screencap -p > captura.png
```

Para capturas usar `adb exec-out`, no `adb shell screencap` con una ruta: Git Bash convierte `/sdcard/...` a una ruta de Windows y el comando falla de forma confusa. Lo mismo con cualquier ruta del dispositivo — hay que envolverla con `MSYS_NO_PATHCONV=1`, o usar PowerShell para los `adb push`.

## Puntos frágiles conocidos

- **La extracción de YouTube se romperá periódicamente.** Es esperado, no un bug puntual. La señal es `ResolveError.ExtractionFailed` apareciendo de golpe en todas las búsquedas (se registra con un aviso explícito en logcat). Cuando pase: subir `newpipeExtractor` en `gradle/libs.versions.toml`; si eso no basta, la interfaz `YoutubeAudioResolver` permite cambiar de backend (por ejemplo a yt-dlp) tocando un solo archivo.
- **El captcha de YouTube (HTTP 429) no es un fallo de red.** Se distingue a propósito como `ResolveError.RateLimited`, porque reintentar solo empeora el bloqueo. Por eso la búsqueda lleva debounce: sin él, cada pulsación sería una petición.
- Salida solo en M4A. Si algún día hiciera falta MP3, implicaría reintroducir FFmpeg y su carga de mantenimiento.

## Estado actual

Fases: 0 memoria ✅ · 1 andamiaje + diseño ✅ · 2 reproducción ✅ · 3 búsqueda YouTube ✅ · **4 pipeline de descarga (siguiente)** · 5 pulido · 6 auto-actualización · 7 (opcional) Spotify.

Cada fase termina con prueba manual en el móvil real por USB antes de pasar a la siguiente. Móvil de pruebas: Samsung Galaxy A55 (`SM_A556B`).

Lo que ya funciona, verificado en dispositivo: librería con Room, reproducción con Media3 en segundo plano con controles en notificación y pantalla de bloqueo, mini-reproductor, y búsqueda real en YouTube con resolución de la pista de audio.

**Repositorio remoto:** https://github.com/Yoryito/Muxy — **público**, rama por defecto `master`. Se eligió público a propósito (el plan original asumía privado). La fase 6 distribuirá el APK desde sus GitHub Releases, que al ser públicas se pueden descargar sin token.

Al ser público, todo lo que se commitee es visible: este mismo CLAUDE.md incluido. No meter nunca el keystore de release ni `local.properties` (ya están en `.gitignore`).

### Qué queda por hacer en la fase 4

El botón de descarga de la pantalla de búsqueda **todavía no descarga**: `SearchViewModel.onDownloadRequested` solo resuelve la pista y la escribe en el log, para haber podido verificar la extracción por separado. La fase 4 sustituye eso por el `DownloadWorker` real (descargar → transcodificar a M4A con Media3 Transformer → etiquetar con eAlvaTag → insertar en Room).

**Truco de pruebas:** `MusicLibrary.sync()` da de alta cualquier audio que aparezca en la carpeta de música de la app, así que se pueden meter canciones sin pasar por la descarga:

```bash
adb push "cancion.wav" /sdcard/Android/data/com.muxy.app.debug/files/music/
```

## Pochi

La mascota se llama **Pochi** y va acompañada de un caracol que se apoya en el suelo **a su izquierda** (antes lo llevaba en la cabeza; se movió al lado a petición del propietario). Está dibujada en Canvas (`ui/components/Pochi.kt`), no como vector estático, porque así se animan las partes por separado.

**El dibujo copia una referencia concreta** que dio el propietario: rana kawaii de contorno cerrado. Si algún día hay que retocarlo, el criterio es acercarse más a esa referencia, no reinterpretarla. Sus rasgos, y las proporciones medidas sobre ella (todas relativas al ancho del cuerpo `W`):

| Rasgo | Valor |
|---|---|
| alto del cuerpo | 0,87·W — claramente más ancho que alto |
| radio del bulto del ojo | 0,10·W, centros a ±0,35·W |
| centro del ojo bajo la coronilla | 0,02·W |
| centro de la boca bajo la coronilla | 0,067·W, ancho 0,18·W |
| tripa | ancho 0,50·W, de 0,28·W bajo la coronilla hasta casi el borde de abajo |
| grosor del contorno | 0,016·W |

Estilo: relleno **plano** (el degradado vertical es casi imperceptible a propósito) y **contorno oscuro y marcado** en un teal apagado emparentado con el `Pond` del tema. **La expresividad la lleva el movimiento, no el detalle del dibujo** — por eso no hace falta más definición en la cara.

Reglas que costó descubrir y conviene no romper:

- **Lo que la hace leer como rana y no como una bola** son tres cosas juntas: los ojos montados como bultos que sobresalen por encima de la cabeza, el cuerpo más ancho que alto, y la boca ondulada. Sin los bultos vuelve a parecer una pelota con ojos.
- El bulto tiene que **solaparse con la coronilla en torno a un tercio de su diámetro**. Con menos, la muesca se abre y los ojos parecen dos círculos posados encima en vez de montados.
- Cuerpo, bultos y patas se pintan como **una sola silueta** unida con `PathOperation.Union`. Dibujarlos por separado deja el contorno de cada bulto cruzando la cabeza y el de cada pata cruzando la tripa.
- El cuerpo son **curvas a mano, no un rectángulo redondeado**: hacen falta a la vez costados abombados y un fondo ancho y plano, y un radio único no da las dos cosas.
- La **tripa termina en base ancha**, casi rozando el contorno de abajo. Rematada en punta de huevo, la masa clara acaba demasiado arriba y sobra verde en la mitad inferior. Rematada plana del todo parece un babero.
- El **ojo es anillo amarillo + pupila grande y plana, sin brillo blanco**: el brillo rompe el estilo plano del resto.
- Las patas son **lóbulos que asoman por el borde de abajo**, cada uno con un pliegue que arranca del contorno y sube escorándose hacia fuera. Sin el pliegue son dos bultos sueltos; demasiado fino o largo, parece un arañazo. (Antes eran óvalos abiertos a los lados; la referencia las lleva recogidas.)
- **Sin coloretes**: la referencia no los tiene y añadirlos vuelve a alejar el dibujo de ella.
- El párpado del parpadeo se pinta con **el mismo `Brush` del cuerpo** (`bodyBrush()`), no con un color fijo, para que no se vea un escalón dentro del bulto.
- El caracol se dibuja **fuera de la escala del respiro** de la rana: compartiéndola parecerían la misma pieza en vez de dos bichos.
- Las animaciones (flotar, respirar, parpadear, y el vaivén propio del caracol) usan **periodos distintos a propósito** para que nunca caigan en fase y el bucle no se note.
- El encuadre no es `(0,0)-(DW,DH)` sino el rectángulo `VIEW_*`, que ciñe el dibujo: si no, el aire sobrante encoge la escena al centrarla. La escena es apaisada, así que `PochiEmptyState` la pinta a ancho completo con alto fijo en vez de en una caja cuadrada.
- Las dos poses comparten el mismo cuerpo, para que sea reconociblemente la misma rana. En `Resting` el nenúfar sostiene a los dos; en `Curious` el junco va a la derecha, lejos del caracol.
