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
import androidx.compose.ui.geometry.CornerRadius
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
 * Pochi, la ranita de Muxy, con su caracol.
 *
 * Estilo de ilustración suave: cuerpo pastel con degradado, contorno fino y
 * cara mínima (dos puntos y una boca pequeña). La expresividad viene del
 * movimiento, no del detalle — por eso se dibuja en Canvas y no como vector
 * estático: respira, parpadea y flota con ritmos distintos que nunca coinciden.
 */
enum class PochiPose {
    /** En su nenúfar, tranquila. Librería vacía. */
    Resting,

    /** Atenta, mirando hacia arriba junto a un junco. Búsqueda sin empezar. */
    Curious,
}

// Espacio de diseño; todo se define aquí y luego se escala al tamaño real.
private const val DW = 200f
private const val DH = 200f

// Cuerpo: un rectángulo con radios enormes, que da la silueta de blob
// regordete mejor que una elipse (más ancho abajo, hombros suaves).
private const val BODY_L = 48f
private const val BODY_T = 74f
private const val BODY_R = 152f
private const val BODY_B = 176f
private const val BODY_RADIUS = 50f

private const val EYE_Y = 100f
private const val EYE_L_X = 80f
private const val EYE_R_X = 120f
private const val EYE_RX = 4.8f
private const val EYE_RY = 5.6f

// La boca acaba en y=120 y la tripa empieza en y=128: ese aire es lo que evita
// que la cara se pise con el vientre.
private const val MOUTH_Y = 114f
private const val MOUTH_DIP = 120f
private const val BELLY_CY = 150f
private const val BELLY_RY = 22f

private val BodyTop = Color(0xFF9BC47E)
private val BodyBottom = Color(0xFFDCEFC2)
private val Outline = Color(0xFF7BA05F)
private val Belly = Color(0xFFEDF7DA)
/** Algo más saturado que el bajo del cuerpo, o las patas se pierden. */
private val Feet = Color(0xFFA6CD87)
private val Ink = Color(0xFF3E5136)
private val Blush = Color(0xFFE8A9A0)
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
    // El caracol se balancea con su propio ritmo, más lento que todo lo demás.
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
        PochiPose.Resting -> "Pochi descansando en su nenúfar con un caracol en la cabeza"
        PochiPose.Curious -> "Pochi mirando hacia arriba con un caracol en la cabeza"
    }

    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        val unit = min(size.width / DW, size.height / DH)
        val originX = (size.width - DW * unit) / 2f
        val originY = (size.height - DH * unit) / 2f

        translate(originX, originY + float * unit) {
            scale(unit, unit, pivot = Offset.Zero) {
                drawPochi(pose, breath, blink.value, snailSway)
            }
        }
    }
}

private fun DrawScope.drawPochi(
    pose: PochiPose,
    breath: Float,
    blink: Float,
    snailSway: Float,
) {
    // La sombra no respira con el cuerpo: quedarse quieta es lo que la hace
    // leer como suelo y no como parte de la rana.
    drawOval(
        color = WaterShadow.copy(alpha = 0.11f),
        topLeft = Offset(48f, 172f),
        size = Size(104f, 14f),
    )

    when (pose) {
        PochiPose.Resting -> drawLilyPad()
        PochiPose.Curious -> drawReed()
    }

    scale(
        scaleX = 1f + 0.010f * breath,
        scaleY = 1f + 0.022f * breath,
        pivot = Offset((BODY_L + BODY_R) / 2f, BODY_B),
    ) {
        drawLimbs()
        drawBody()
        drawBelly()
        drawCheeks()
        drawFace(pose, blink)
        drawSnail(snailSway)
    }
}

private fun DrawScope.drawLimbs() {
    // Solo las patitas de abajo. Unos bracitos a media altura se leían como
    // orejas pegadas a los lados, no como brazos.
    drawOval(Feet, Offset(50f, 162f), Size(34f, 18f))
    drawOval(Feet, Offset(116f, 162f), Size(34f, 18f))
    drawOval(Outline.copy(alpha = 0.35f), Offset(50f, 162f), Size(34f, 18f), style = Stroke(1.4f))
    drawOval(Outline.copy(alpha = 0.35f), Offset(116f, 162f), Size(34f, 18f), style = Stroke(1.4f))
}

private fun DrawScope.drawBody() {
    val topLeft = Offset(BODY_L, BODY_T)
    val size = Size(BODY_R - BODY_L, BODY_B - BODY_T)
    val radius = CornerRadius(BODY_RADIUS, BODY_RADIUS)

    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(BodyTop, BodyBottom),
            startY = BODY_T,
            endY = BODY_B,
        ),
        topLeft = topLeft,
        size = size,
        cornerRadius = radius,
    )
    // Contorno fino, no un trazo marcado: define la silueta sin endurecerla.
    drawRoundRect(
        color = Outline.copy(alpha = 0.5f),
        topLeft = topLeft,
        size = size,
        cornerRadius = radius,
        style = Stroke(width = 1.8f),
    )
}

private fun DrawScope.drawBelly() {
    drawOval(
        color = Belly.copy(alpha = 0.85f),
        topLeft = Offset(100f - 36f, BELLY_CY - BELLY_RY),
        size = Size(72f, BELLY_RY * 2),
    )
}

private fun DrawScope.drawCheeks() {
    drawOval(Blush.copy(alpha = 0.45f), Offset(60f, 106f), Size(17f, 10f))
    drawOval(Blush.copy(alpha = 0.45f), Offset(123f, 106f), Size(17f, 10f))
}

private fun DrawScope.drawFace(pose: PochiPose, blink: Float) {
    // Con ojos de punto no hace falta párpado: basta con aplastarlos.
    val ry = EYE_RY * (1f - 0.86f * blink)
    val rise = if (pose == PochiPose.Curious) 3f else 0f

    listOf(EYE_L_X, EYE_R_X).forEach { cx ->
        drawOval(
            color = Ink,
            topLeft = Offset(cx - EYE_RX, EYE_Y - rise - ry),
            size = Size(EYE_RX * 2, ry * 2),
        )
    }

    val mouth = Path().apply {
        moveTo(92f, MOUTH_Y)
        cubicTo(95f, MOUTH_DIP, 105f, MOUTH_DIP, 108f, MOUTH_Y)
    }
    drawPath(
        path = mouth,
        color = Ink.copy(alpha = 0.8f),
        style = Stroke(width = 2.4f, cap = StrokeCap.Round),
    )
}

/** Caracolito sobre la cabeza. Se balancea muy despacio, como si fuera agarrado. */
private fun DrawScope.drawSnail(sway: Float) {
    val shell = Offset(107f, 58f)

    rotate(degrees = sway, pivot = Offset(100f, 76f)) {
        // Cuerpo, con la cabecita asomando por delante del caparazón.
        drawOval(SnailBody, Offset(83f, 62f), Size(34f, 14f))
        drawCircle(SnailBody, 6f, Offset(86f, 64f))

        // Antenas: finas, con la puntita marcada.
        listOf(
            Offset(83f, 61f) to Offset(78f, 50f),
            Offset(89f, 60f) to Offset(88f, 48f),
        ).forEach { (from, to) ->
            drawLine(Ink.copy(alpha = 0.7f), from, to, strokeWidth = 1.4f, cap = StrokeCap.Round)
            drawCircle(Ink.copy(alpha = 0.7f), 1.7f, to)
        }

        drawCircle(ShellBase, 12f, shell)
        drawCircle(ShellLine.copy(alpha = 0.55f), 12f, shell, style = Stroke(width = 1.4f))

        // Espiral real, no dos círculos concéntricos: es lo que hace que se
        // lea como caparazón a este tamaño.
        val spiral = Path()
        var t = 0f
        while (t <= 13.8f) {
            val r = 1.2f + t * 0.76f
            val x = shell.x + r * cos(t + 1.2f)
            val y = shell.y + r * sin(t + 1.2f)
            if (t == 0f) spiral.moveTo(x, y) else spiral.lineTo(x, y)
            t += 0.18f
        }
        drawPath(spiral, ShellLine, style = Stroke(width = 1.5f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawLilyPad() {
    val cx = 100f
    val cy = 178f
    val rx = 66f
    val ry = 14f

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
    // El cuerpo llega hasta x=152 (y los bracitos hasta 162), así que el junco
    // se mantiene por encima de x=170 y no roza a Pochi.
    val stem = Path().apply {
        moveTo(184f, 180f)
        cubicTo(184f, 148f, 180f, 118f, 174f, 92f)
    }
    drawPath(
        path = stem,
        color = PadBase.copy(alpha = 0.7f),
        style = Stroke(width = 4.5f, cap = StrokeCap.Round),
    )
    drawOval(ReedHead.copy(alpha = 0.85f), Offset(168f, 66f), Size(11f, 26f))
    val leaf = Path().apply {
        moveTo(182f, 140f)
        cubicTo(175f, 132f, 170f, 122f, 169f, 114f)
        cubicTo(176f, 118f, 181f, 129f, 182f, 140f)
        close()
    }
    drawPath(leaf, PadBase.copy(alpha = 0.5f))
}
