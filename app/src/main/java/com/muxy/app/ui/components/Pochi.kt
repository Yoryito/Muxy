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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
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
 * cara mínima. La expresividad viene del movimiento, no del detalle — por eso
 * se dibuja en Canvas y no como vector estático: respira, parpadea y flota con
 * ritmos distintos que nunca coinciden.
 *
 * Lo que hace que se lea como rana y no como una bola son tres cosas: los ojos
 * montados como bultos que sobresalen por encima de la cabeza, el cuerpo más
 * ancho que alto, y la boca ancha. Sin los bultos vuelve a parecer una pelota.
 */
enum class PochiPose {
    /** En su nenúfar, tranquila. Librería vacía. */
    Resting,

    /** Atenta, mirando hacia arriba junto a un junco. Búsqueda sin empezar. */
    Curious,
}

// Espacio de diseño; todo se define aquí y luego se escala al tamaño real.
private const val DW = 220f
private const val DH = 200f
private const val CX = 110f

// Cuerpo: claramente más ancho que alto.
private const val BODY_L = 44f
private const val BODY_T = 86f
private const val BODY_R = 176f
private const val BODY_B = 180f
private const val BODY_RADIUS = 46f

// Bultos de los ojos: sobresalen por encima del cuerpo. Son la silueta de rana.
private const val BUMP_R = 18f
private const val BUMP_CY = 86f
private const val BUMP_L_CX = 82f
private const val BUMP_R_CX = 138f

private const val EYE_RX = 5.2f
private const val EYE_RY = 6f

// La boca acaba en y=122 y la tripa empieza en y=127: ese aire es lo que evita
// que la cara se pise con el vientre.
private const val MOUTH_Y = 110f
private const val MOUTH_DIP = 122f
private const val BELLY_CY = 152f
private const val BELLY_RY = 25f

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
        topLeft = Offset(BODY_L - 4f, 174f),
        size = Size(BODY_R - BODY_L + 8f, 14f),
    )

    when (pose) {
        PochiPose.Resting -> drawLilyPad()
        PochiPose.Curious -> drawReed()
    }

    scale(
        scaleX = 1f + 0.010f * breath,
        scaleY = 1f + 0.022f * breath,
        pivot = Offset(CX, BODY_B),
    ) {
        drawLegs()
        drawSilhouette()
        drawBelly()
        drawCheeks()
        drawFace(pose, blink)
        drawSnail(snailSway)
    }
}

private fun DrawScope.drawLegs() {
    // Abiertas hacia fuera y asomando por los lados del cuerpo: unas patas
    // metidas dentro del contorno no aportan nada a la silueta.
    listOf(Offset(30f, 162f), Offset(142f, 162f)).forEach { at ->
        val size = Size(48f, 22f)
        drawOval(Feet, at, size)
        drawOval(Outline.copy(alpha = 0.35f), at, size, style = Stroke(1.4f))
    }
}

/**
 * Cuerpo y bultos de los ojos como una sola silueta.
 *
 * Se unen con [PathOperation.Union] antes de pintar: dibujarlos por separado
 * dejaría el contorno de cada bulto cruzando la cabeza.
 */
private fun DrawScope.bodySilhouette(): Path {
    val body = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(BODY_L, BODY_T, BODY_R, BODY_B),
                cornerRadius = CornerRadius(BODY_RADIUS, BODY_RADIUS),
            )
        )
    }
    val bumps = Path().apply {
        addOval(Rect(Offset(BUMP_L_CX, BUMP_CY), BUMP_R))
        addOval(Rect(Offset(BUMP_R_CX, BUMP_CY), BUMP_R))
    }
    return Path().apply { op(body, bumps, PathOperation.Union) }
}

private fun DrawScope.drawSilhouette() {
    val path = bodySilhouette()
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(BodyTop, BodyBottom),
            startY = BUMP_CY - BUMP_R,
            endY = BODY_B,
        ),
    )
    // Contorno fino, no un trazo marcado: define la silueta sin endurecerla.
    drawPath(path, Outline.copy(alpha = 0.5f), style = Stroke(width = 1.8f))
}

private fun DrawScope.drawBelly() {
    drawOval(
        color = Belly.copy(alpha = 0.85f),
        topLeft = Offset(CX - 44f, BELLY_CY - BELLY_RY),
        size = Size(88f, BELLY_RY * 2),
    )
}

private fun DrawScope.drawCheeks() {
    drawOval(Blush.copy(alpha = 0.45f), Offset(58f, 112f), Size(19f, 11f))
    drawOval(Blush.copy(alpha = 0.45f), Offset(143f, 112f), Size(19f, 11f))
}

private fun DrawScope.drawFace(pose: PochiPose, blink: Float) {
    // Con ojos de punto no hace falta párpado: basta con aplastarlos.
    val ry = EYE_RY * (1f - 0.86f * blink)
    val rise = if (pose == PochiPose.Curious) 3f else 0f

    listOf(BUMP_L_CX, BUMP_R_CX).forEach { cx ->
        drawOval(
            color = Ink,
            topLeft = Offset(cx - EYE_RX, BUMP_CY - rise - ry),
            size = Size(EYE_RX * 2, ry * 2),
        )
    }

    // Boca ancha: junto con los bultos, es lo que dice "rana".
    val mouth = Path().apply {
        moveTo(CX - 22f, MOUTH_Y)
        cubicTo(CX - 14f, MOUTH_DIP, CX + 14f, MOUTH_DIP, CX + 22f, MOUTH_Y)
    }
    drawPath(
        path = mouth,
        color = Ink.copy(alpha = 0.8f),
        style = Stroke(width = 2.6f, cap = StrokeCap.Round),
    )
}

/** Caracolito en el hueco entre los dos bultos. Se balancea muy despacio. */
private fun DrawScope.drawSnail(sway: Float) {
    val shell = Offset(CX + 8f, 54f)

    rotate(degrees = sway, pivot = Offset(CX, BUMP_CY)) {
        // Cuerpo, con la cabecita asomando por delante del caparazón.
        drawOval(SnailBody, Offset(CX - 22f, 58f), Size(34f, 14f))
        drawCircle(SnailBody, 6f, Offset(CX - 19f, 60f))

        listOf(
            Offset(CX - 22f, 57f) to Offset(CX - 27f, 46f),
            Offset(CX - 16f, 56f) to Offset(CX - 17f, 44f),
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
    val cy = 182f
    val rx = 80f
    val ry = 15f

    // La muesca se recorta solo de la capa clara. Recortarla también de la base
    // dejaría ver el fondo de la pantalla por el hueco.
    drawOval(PadBase, Offset(CX - rx, cy - ry), Size(rx * 2, ry * 2))

    val innerRx = rx - 5f
    val innerRy = ry - 3.5f
    val inner = Path().apply {
        addOval(Rect(CX - innerRx, cy - innerRy, CX + innerRx, cy + innerRy))
    }
    val wedge = Path().apply {
        moveTo(CX, cy)
        lineTo(CX + rx + 6f, cy - 8f)
        lineTo(CX + rx + 6f, cy + 8f)
        close()
    }
    drawPath(Path().apply { op(inner, wedge, PathOperation.Difference) }, PadTop)
}

private fun DrawScope.drawReed() {
    // Las patas llegan hasta x=190, así que el junco se mantiene más a la
    // derecha para no rozar a Pochi.
    val stem = Path().apply {
        moveTo(206f, 184f)
        cubicTo(206f, 152f, 202f, 122f, 196f, 96f)
    }
    drawPath(
        path = stem,
        color = PadBase.copy(alpha = 0.7f),
        style = Stroke(width = 4.5f, cap = StrokeCap.Round),
    )
    drawOval(ReedHead.copy(alpha = 0.85f), Offset(190f, 70f), Size(11f, 26f))
    val leaf = Path().apply {
        moveTo(204f, 144f)
        cubicTo(197f, 136f, 192f, 126f, 191f, 118f)
        cubicTo(198f, 122f, 203f, 133f, 204f, 144f)
        close()
    }
    drawPath(leaf, PadBase.copy(alpha = 0.5f))
}
