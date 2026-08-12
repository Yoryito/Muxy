package com.muxy.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pochi, la ranita de Muxy, con su caracol al lado.
 *
 * Ilustración plana de contorno cerrado: relleno pastel, trazo oscuro y
 * continuo, cara mínima. La expresividad viene del movimiento, no del detalle
 * — por eso se dibuja en Canvas y no como vector estático: respira, parpadea y
 * flota con ritmos distintos que nunca coinciden.
 *
 * Lo que hace que se lea como rana y no como una bola son cuatro cosas: los
 * ojos montados como bultos que sobresalen por encima de la cabeza, el cuerpo
 * claramente más ancho que alto, la boca ondulada y pequeña arriba del todo, y
 * la tripa clara que ocupa casi toda la mitad inferior.
 */
enum class PochiPose {
    /** En su nenúfar, tranquila. Librería vacía. */
    Resting,

    /** Atenta, mirando hacia arriba junto a un junco. Búsqueda sin empezar. */
    Curious,
}

// --- Espacio de diseño ---------------------------------------------------
// Todo se define en estas coordenadas y luego se escala al tamaño real. El
// encuadre no es (0,0)-(DW,DH) sino el rectángulo VIEW_*, que ciñe el dibujo:
// así el aire sobrante no encoge la escena al centrarla.
private const val VIEW_L = 2f
private const val VIEW_T = 28f
private const val VIEW_W = 270f
private const val VIEW_H = 172f

/** Línea de suelo: sobre ella se apoyan la rana, el caracol y las sombras. */
private const val GROUND = 176f

// --- Rana ---
private const val CX = 160f
private const val BODY_L = 84f
private const val BODY_R = 236f
private const val BODY_T = 44f

/** Punto más ancho del cuerpo: por debajo del centro, como en la referencia. */
private const val BODY_WAIST = 116f

/** Medio ancho del fondo plano. Un fondo estrecho convierte la silueta en huevo. */
private const val BODY_FLAT = 40f

// Bultos de los ojos: sobresalen por encima de la cabeza. Son la silueta de rana.
// El centro va un poco por debajo de la coronilla para que el bulto se solape
// con ella un tercio de su diámetro: con menos, la muesca se abre y los ojos
// se leen como dos círculos posados encima en vez de montados.
private const val BUMP_R = 16f
private const val BUMP_CY = 48f
private const val BUMP_L_CX = 107f
private const val BUMP_R_CX = 213f

// Patas: lóbulos que asoman por el borde de abajo, no óvalos pegados a los lados.
private const val FOOT_L_CX = 128f
private const val FOOT_R_CX = 192f
private const val FOOT_CY = 172f
private const val FOOT_RX = 18f
private const val FOOT_RY = 8f

private const val EYE_R = 14.4f
private const val PUPIL_R = 8.4f

// La boca va arriba, justo debajo de los ojos, y es pequeña: bajarla o
// ensancharla la acerca a la tripa y la cara pierde el aire que la sostiene.
private const val MOUTH_CY = 54f
private const val MOUTH_HW = 13f

// La tripa es ancha por abajo y su borde inferior casi roza el contorno: si se
// remata en punta de huevo, la masa clara acaba demasiado arriba y a la rana le
// sobra verde en la mitad de abajo.
private const val BELLY_T = 86f
private const val BELLY_B = 172f
private const val BELLY_HW = 38f

/** Trazo del contorno. Es un trazo marcado a propósito: es lo que da el estilo. */
private const val OUTLINE_W = 2.6f

// --- Caracol (a la izquierda, mirando hacia Pochi) ---
private const val SNAIL_SHELL_CX = 34f
private const val SNAIL_SHELL_CY = 148f
private const val SNAIL_SHELL_R = 15.5f

// El degradado es casi imperceptible a propósito: la referencia es plana y un
// degradado marcado devuelve el aire de "bola con luz" que se quiere evitar.
private val BodyTop = Color(0xFFBFDE96)
private val BodyBottom = Color(0xFFCCE7A8)
private val Outline = Color(0xFF4C7F84)
private val Belly = Color(0xFFE9F2C4)
private val EyeRing = Color(0xFFF1E9A6)
private val Pupil = Color(0xFF2A5257)
private val PadBase = Color(0xFF7FA468)
private val PadTop = Color(0xFFA3C489)
private val WaterShadow = Color(0xFF4F8B87)
private val ShellBase = Color(0xFFE3C08E)
private val ShellLine = Color(0xFFB8935F)
private val SnailBody = Color(0xFFF5E7D0)
private val ReedHead = Color(0xFFC2A176)

@Composable
fun Pochi(
    pose: PochiPose,
    modifier: Modifier = Modifier,
) {
    val idle = rememberInfiniteTransition(label = "pochi")

    val float by idle.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "float",
    )
    val breath by idle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3400), RepeatMode.Reverse),
        label = "breath",
    )
    // El caracol se mueve con su propio ritmo, más lento que todo lo demás.
    val snailSway by idle.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(tween(4300), RepeatMode.Reverse),
        label = "snailSway",
    )

    val blink = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2800, 6200))
            blink.animateTo(1f, tween(90))
            blink.animateTo(0f, tween(130))
        }
    }

    val description = when (pose) {
        PochiPose.Resting -> "Pochi descansando en su nenúfar con un caracol a su lado"
        PochiPose.Curious -> "Pochi mirando hacia arriba con un caracol a su lado"
    }

    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        val unit = min(size.width / VIEW_W, size.height / VIEW_H)
        val originX = (size.width - VIEW_W * unit) / 2f - VIEW_L * unit
        val originY = (size.height - VIEW_H * unit) / 2f - VIEW_T * unit

        translate(originX, originY) {
            scale(unit, unit, pivot = Offset.Zero) {
                drawScene(pose, float, breath, blink.value, snailSway)
            }
        }
    }
}

private fun DrawScope.drawScene(
    pose: PochiPose,
    float: Float,
    breath: Float,
    blink: Float,
    snailSway: Float,
) {
    when (pose) {
        PochiPose.Resting -> drawLilyPad()
        PochiPose.Curious -> drawReed()
    }

    // Las sombras se quedan quietas: es lo que las hace leer como suelo y no
    // como parte de la rana ni del caracol.
    drawOval(
        color = WaterShadow.copy(alpha = 0.12f),
        topLeft = Offset(BODY_L + 14f, GROUND + 2f),
        size = Size(BODY_R - BODY_L - 28f, 12f),
    )
    drawOval(
        color = WaterShadow.copy(alpha = 0.10f),
        topLeft = Offset(12f, GROUND - 1f),
        size = Size(58f, 9f),
    )

    drawSnail(snailSway)

    translate(top = float) {
        scale(
            scaleX = 1f + 0.010f * breath,
            scaleY = 1f + 0.022f * breath,
            pivot = Offset(CX, GROUND),
        ) {
            drawSilhouette()
            drawBelly()
            drawLegCreases()
            drawFace(pose, blink)
        }
    }
}

/**
 * Degradado del cuerpo. Se comparte con el párpado para que al parpadear no se
 * vea un escalón de color dentro del bulto.
 */
private fun bodyBrush() = Brush.verticalGradient(
    colors = listOf(BodyTop, BodyBottom),
    startY = BUMP_CY - BUMP_R,
    endY = GROUND,
)

/**
 * Cuerpo, bultos de los ojos y patas como una sola silueta.
 *
 * Se unen con [PathOperation.Union] antes de pintar: dibujarlos por separado
 * dejaría el contorno de cada bulto cruzando la cabeza y el de cada pata
 * cruzando la tripa.
 */
private fun bodySilhouette(): Path {
    // Curvas a mano y no un rectángulo redondeado: hace falta que los costados
    // vayan abombados y que el fondo sea ancho y plano, y un radio único no da
    // las dos cosas a la vez.
    val body = Path().apply {
        moveTo(CX, BODY_T)
        cubicTo(CX + 54f, BODY_T, BODY_R, BODY_T + 14f, BODY_R, BODY_WAIST)
        cubicTo(BODY_R, GROUND - 24f, BODY_R - 12f, GROUND, CX + BODY_FLAT, GROUND)
        lineTo(CX - BODY_FLAT, GROUND)
        cubicTo(BODY_L + 12f, GROUND, BODY_L, GROUND - 24f, BODY_L, BODY_WAIST)
        cubicTo(BODY_L, BODY_T + 14f, CX - 54f, BODY_T, CX, BODY_T)
        close()
    }
    val parts = Path().apply {
        addOval(Rect(Offset(BUMP_L_CX, BUMP_CY), BUMP_R))
        addOval(Rect(Offset(BUMP_R_CX, BUMP_CY), BUMP_R))
        addOval(footRect(FOOT_L_CX))
        addOval(footRect(FOOT_R_CX))
    }
    return Path().apply { op(body, parts, PathOperation.Union) }
}

private fun footRect(cx: Float) =
    Rect(cx - FOOT_RX, FOOT_CY - FOOT_RY, cx + FOOT_RX, FOOT_CY + FOOT_RY)

private fun DrawScope.drawSilhouette() {
    val path = bodySilhouette()
    drawPath(path, bodyBrush())
    drawPath(path, Outline, style = Stroke(width = OUTLINE_W))
}

/** Tripa: cúpula estrecha arriba que se ensancha y termina en base ancha. */
private fun DrawScope.drawBelly() {
    val belly = Path().apply {
        moveTo(CX, BELLY_T)
        cubicTo(CX + 20f, BELLY_T, CX + 34f, 104f, CX + 36f, 126f)
        cubicTo(CX + BELLY_HW, 150f, CX + 32f, BELLY_B, CX, BELLY_B)
        cubicTo(CX - 32f, BELLY_B, CX - BELLY_HW, 150f, CX - 36f, 126f)
        cubicTo(CX - 34f, 104f, CX - 20f, BELLY_T, CX, BELLY_T)
        close()
    }
    drawPath(belly, Belly)
}

/**
 * Pliegue que marca cada pata recogida. Arranca del borde de abajo y sube
 * escorándose hacia fuera: sin él las patas son dos bultos sueltos del
 * contorno, y si queda demasiado fino o demasiado largo parece un arañazo.
 */
private fun DrawScope.drawLegCreases() {
    listOf(1f, -1f).forEach { dir ->
        val baseX = CX + dir * 33f
        val crease = Path().apply {
            moveTo(baseX, GROUND + 3f)
            cubicTo(
                baseX + dir * 2f, GROUND - 6f,
                baseX + dir * 6f, GROUND - 13f,
                baseX + dir * 7f, GROUND - 20f,
            )
        }
        drawPath(
            path = crease,
            color = Outline.copy(alpha = 0.62f),
            style = Stroke(width = 2.1f, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawFace(pose: PochiPose, blink: Float) {
    val lookUp = if (pose == PochiPose.Curious) 2.6f else 0f
    drawEye(BUMP_L_CX, inward = 0.8f, lookUp = lookUp, blink = blink)
    drawEye(BUMP_R_CX, inward = -0.8f, lookUp = lookUp, blink = blink)

    // Boca ondulada y corta: dos valles y una cresta en medio. Junto con los
    // bultos, es lo que dice "rana contenta" sin dibujar más cara.
    val mouth = Path().apply {
        moveTo(CX - MOUTH_HW, MOUTH_CY - 1.5f)
        cubicTo(CX - 9f, MOUTH_CY + 5f, CX - 4f, MOUTH_CY + 4f, CX, MOUTH_CY - 2f)
        cubicTo(CX + 4f, MOUTH_CY + 4f, CX + 9f, MOUTH_CY + 5f, CX + MOUTH_HW, MOUTH_CY - 1.5f)
    }
    drawPath(mouth, Outline, style = Stroke(width = OUTLINE_W, cap = StrokeCap.Round))
}

/**
 * Ojo: anillo amarillo y pupila grande y plana, sin brillo. El brillo blanco
 * rompe el estilo plano del resto del dibujo.
 */
private fun DrawScope.drawEye(cx: Float, inward: Float, lookUp: Float, blink: Float) {
    drawCircle(EyeRing, EYE_R, Offset(cx, BUMP_CY))
    drawCircle(Pupil, PUPIL_R, Offset(cx + inward, BUMP_CY - lookUp))

    if (blink <= 0.01f) return

    // Párpado: la parte del bulto por encima de lidY, pintada con el mismo
    // degradado del cuerpo. Se recorta un pelo por dentro para no comerse el
    // contorno del bulto.
    val lidY = BUMP_CY - BUMP_R + 2f * BUMP_R * blink
    val inner = Path().apply {
        addOval(Rect(Offset(cx, BUMP_CY), BUMP_R - OUTLINE_W / 2f))
    }
    val above = Path().apply {
        addRect(Rect(cx - BUMP_R, BUMP_CY - BUMP_R - 1f, cx + BUMP_R, lidY))
    }
    drawPath(Path().apply { op(inner, above, PathOperation.Intersect) }, bodyBrush())

    // Ya casi cerrado, la línea del párpado. Aparece tarde para que el
    // parpadeo no se vea como un guiño largo.
    val lash = ((blink - 0.45f) / 0.55f).coerceIn(0f, 1f)
    if (lash > 0f) {
        val line = Path().apply {
            moveTo(cx - 7.5f, BUMP_CY + 2f)
            cubicTo(cx - 3f, BUMP_CY - 3f, cx + 3f, BUMP_CY - 3f, cx + 7.5f, BUMP_CY + 2f)
        }
        drawPath(
            path = line,
            color = Outline.copy(alpha = lash),
            style = Stroke(width = 2.2f, cap = StrokeCap.Round),
        )
    }
}

/**
 * El caracol, apoyado en el suelo a la izquierda y mirando hacia Pochi.
 *
 * Se dibuja fuera del respiro de la rana: si compartieran la escala parecerían
 * la misma pieza en vez de dos bichos distintos.
 */
private fun DrawScope.drawSnail(sway: Float) {
    translate(top = sway * 0.35f) {
        val foot = Path().apply {
            moveTo(10f, GROUND)
            cubicTo(4f, 170f, 8f, 161f, 20f, 159f)
            cubicTo(34f, 156f, 48f, 155f, 54f, 148f)
            cubicTo(58f, 143f, 64f, 140f, 68f, 143f)
            cubicTo(72f, 147f, 70f, 157f, 63f, 164f)
            cubicTo(55f, 172f, 40f, GROUND, 10f, GROUND)
            close()
        }
        drawPath(foot, SnailBody)
        drawPath(foot, Outline, style = Stroke(width = 1.8f))

        // Las antenas se balancean sobre la cabeza; el cuerpo apenas se mueve.
        rotate(degrees = sway, pivot = Offset(64f, 146f)) {
            listOf(
                Offset(65f, 145f) to Offset(72f, 126f),
                Offset(59f, 144f) to Offset(60f, 124f),
            ).forEach { (from, to) ->
                drawLine(Outline, from, to, strokeWidth = 1.8f, cap = StrokeCap.Round)
                drawCircle(Outline, 2.1f, to)
            }
        }

        val shell = Offset(SNAIL_SHELL_CX, SNAIL_SHELL_CY)
        drawCircle(ShellBase, SNAIL_SHELL_R, shell)
        drawCircle(Outline, SNAIL_SHELL_R, shell, style = Stroke(width = 1.8f))

        // Espiral real, no dos círculos concéntricos: es lo que hace que se
        // lea como caparazón a este tamaño.
        val spiral = Path()
        var t = 0f
        while (t <= 13.8f) {
            val r = 1.4f + t * 0.94f
            val x = shell.x + r * cos(t + 1.2f)
            val y = shell.y + r * sin(t + 1.2f)
            if (t == 0f) spiral.moveTo(x, y) else spiral.lineTo(x, y)
            t += 0.18f
        }
        drawPath(spiral, ShellLine, style = Stroke(width = 1.5f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawLilyPad() {
    val cx = 123f
    val cy = 183f
    val rx = 118f
    val ry = 15f

    // La muesca se recorta solo de la capa clara. Recortarla también de la base
    // dejaría ver el fondo de la pantalla por el hueco.
    drawOval(PadBase, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))

    val innerRx = rx - 5f
    val innerRy = ry - 3.5f
    val inner = Path().apply {
        addOval(Rect(cx - innerRx, cy - innerRy, cx + innerRx, cy + innerRy))
    }
    val wedge = Path().apply {
        moveTo(cx, cy)
        lineTo(cx + rx + 6f, cy - 8f)
        lineTo(cx + rx + 6f, cy + 8f)
        close()
    }
    drawPath(Path().apply { op(inner, wedge, PathOperation.Difference) }, PadTop)
}

private fun DrawScope.drawReed() {
    // El cuerpo llega hasta x=236, así que el junco se mantiene más a la
    // derecha para no rozar a Pochi.
    val stem = Path().apply {
        moveTo(264f, 186f)
        cubicTo(264f, 154f, 260f, 122f, 256f, 92f)
    }
    drawPath(
        path = stem,
        color = PadBase.copy(alpha = 0.7f),
        style = Stroke(width = 4.5f, cap = StrokeCap.Round),
    )
    drawOval(ReedHead.copy(alpha = 0.85f), Offset(250f, 66f), Size(11f, 26f))
    val leaf = Path().apply {
        moveTo(262f, 146f)
        cubicTo(255f, 138f, 250f, 128f, 249f, 120f)
        cubicTo(256f, 124f, 261f, 135f, 262f, 146f)
        close()
    }
    drawPath(leaf, PadBase.copy(alpha = 0.5f))
}
