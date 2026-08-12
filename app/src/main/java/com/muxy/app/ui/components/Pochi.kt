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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random

/**
 * Pochi, la ranita de Muxy.
 *
 * Se dibuja en Canvas y no como vector estático para poder animar las partes
 * por separado: respira, parpadea y flota con ritmos distintos, de modo que
 * los tres ciclos nunca se sincronizan y el conjunto no parece un bucle.
 */
enum class PochiPose {
    /** Sentada en su nenúfar, tranquila. Librería vacía. */
    Resting,

    /** Atenta y mirando hacia arriba, junto a un junco. Búsqueda sin empezar. */
    Curious,
}

// Espacio de diseño. Todo se define aquí y luego se escala al tamaño real.
private const val DW = 200f
private const val DH = 170f

// Cuerpo: deliberadamente más ancho que alto — Pochi es regordeta.
private const val BODY_CX = 100f
private const val BODY_CY = 96f
private const val BODY_RX = 52f
private const val BODY_RY = 42f

private const val EYE_CY = 48f
private const val EYE_L_CX = 74f
private const val EYE_R_CX = 126f
private const val MOUND_R = 18f
private const val WHITE_R = 13f
private const val PUPIL_R = 6.5f

// La boca termina en y=81 y la tripa empieza en y=90: quedan 9 unidades de aire
// entre ambas para que ningún detalle de la cara se solape con el vientre.
private const val MOUTH_TOP = 72f
private const val MOUTH_DIP = 81f
private const val BELLY_CY = 112f
private const val BELLY_RY = 22f

private val BodyGreen = Color(0xFF6E9159)
private val BodyShade = Color(0xFF5E7F4F)
private val BellyCream = Color(0xFFC9DDB4)
private val EyeWhite = Color(0xFFFFFCF4)
private val Pupil = Color(0xFF1F2C1C)
private val Blush = Color(0xFFDE8B85)
private val PadTop = Color(0xFF87A876)
private val PadBase = Color(0xFF5E7F4F)
private val WaterShadow = Color(0xFF4F8B87)
private val ReedHead = Color(0xFF8A6A4A)

@Composable
fun Pochi(
    pose: PochiPose,
    modifier: Modifier = Modifier,
) {
    val idle = rememberInfiniteTransition(label = "pochi")

    // Tres ciclos con periodos primos entre sí para que no caigan en fase.
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

    // El parpadeo no es un bucle continuo: espera un rato variable y entonces
    // cierra rápido y abre algo más despacio, como un parpadeo de verdad.
    val blink = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2800, 6200))
            blink.animateTo(1f, tween(90))
            blink.animateTo(0f, tween(130))
        }
    }

    val description = when (pose) {
        PochiPose.Resting -> "Pochi descansando en su nenúfar"
        PochiPose.Curious -> "Pochi mirando hacia arriba, atenta"
    }

    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        val unit = min(size.width / DW, size.height / DH)
        val originX = (size.width - DW * unit) / 2f
        val originY = (size.height - DH * unit) / 2f

        translate(originX, originY + float * unit) {
            scale(unit, unit, pivot = Offset.Zero) {
                drawPochi(pose = pose, breath = breath, blink = blink.value)
            }
        }
    }
}

private fun DrawScope.drawPochi(pose: PochiPose, breath: Float, blink: Float) {
    // Sombra en el suelo. No respira con el cuerpo: se queda quieta, que es lo
    // que la hace leer como suelo y no como parte de la rana.
    drawOval(
        color = WaterShadow.copy(alpha = 0.13f),
        topLeft = Offset(BODY_CX - 54f, 137f),
        size = Size(108f, 14f),
    )

    when (pose) {
        PochiPose.Resting -> drawLilyPad()
        PochiPose.Curious -> drawReed()
    }

    // La respiración escala solo 2,5% y pivota en la base del cuerpo, para que
    // Pochi se hinche hacia arriba en vez de flotar.
    val bodyBottom = BODY_CY + BODY_RY
    scale(
        scaleX = 1f + 0.012f * breath,
        scaleY = 1f + 0.025f * breath,
        pivot = Offset(BODY_CX, bodyBottom),
    ) {
        drawFeet()
        drawBody()
        drawBelly()
        drawCheeks()
        drawMouth()
        drawEyes(pose = pose, blink = blink)
    }
}

private fun DrawScope.drawFeet() {
    // Asoman por los lados del cuerpo y refuerzan la silueta rechoncha.
    drawOval(BodyShade, Offset(42f, 125f), Size(30f, 18f))
    drawOval(BodyShade, Offset(128f, 125f), Size(30f, 18f))
}

private fun DrawScope.drawBody() {
    drawOval(
        color = BodyGreen,
        topLeft = Offset(BODY_CX - BODY_RX, BODY_CY - BODY_RY),
        size = Size(BODY_RX * 2, BODY_RY * 2),
    )
}

private fun DrawScope.drawBelly() {
    drawOval(
        color = BellyCream,
        topLeft = Offset(BODY_CX - 33f, BELLY_CY - BELLY_RY),
        size = Size(66f, BELLY_RY * 2),
    )
}

private fun DrawScope.drawCheeks() {
    // Colocados por dentro del borde del cuerpo a esta altura, para que no
    // sobresalgan por los lados.
    // A menos opacidad el rosa se enturbia contra el verde y tira a marrón.
    drawOval(Blush.copy(alpha = 0.68f), Offset(53f, 74f), Size(19f, 12f))
    drawOval(Blush.copy(alpha = 0.68f), Offset(128f, 74f), Size(19f, 12f))
}

private fun DrawScope.drawMouth() {
    val mouth = Path().apply {
        moveTo(87f, MOUTH_TOP)
        cubicTo(92f, MOUTH_DIP, 108f, MOUTH_DIP, 113f, MOUTH_TOP)
    }
    drawPath(
        path = mouth,
        color = Pupil.copy(alpha = 0.75f),
        style = Stroke(width = 3f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
    )
}

private fun DrawScope.drawEyes(pose: PochiPose, blink: Float) {
    // Cuando busca, mira hacia arriba.
    val pupilRise = if (pose == PochiPose.Curious) 4f else 1.5f

    listOf(EYE_L_CX, EYE_R_CX).forEach { cx ->
        // El montículo es del color del cuerpo, así que al cerrarse el párpado
        // el ojo simplemente desaparece dentro de la cabeza.
        drawCircle(BodyGreen, MOUND_R, Offset(cx, EYE_CY))

        val lidY = EYE_CY - WHITE_R + blink * (WHITE_R * 2)
        clipRect(
            left = cx - MOUND_R,
            top = lidY,
            right = cx + MOUND_R,
            bottom = EYE_CY + MOUND_R,
        ) {
            drawCircle(EyeWhite, WHITE_R, Offset(cx, EYE_CY))
            drawCircle(Pupil, PUPIL_R, Offset(cx + 1f, EYE_CY + 1.5f - pupilRise))
            drawCircle(
                color = Color.White,
                radius = 2.4f,
                center = Offset(cx - 1.5f, EYE_CY - 1.5f - pupilRise),
            )
        }

        // Al cerrarse del todo queda la línea del párpado, que es lo que hace
        // que el parpadeo se lea como tal y no como que el ojo se apaga.
        if (blink > 0.55f) {
            val lidPath = Path().apply {
                moveTo(cx - 9f, EYE_CY + 1f)
                cubicTo(cx - 4f, EYE_CY + 5f, cx + 4f, EYE_CY + 5f, cx + 9f, EYE_CY + 1f)
            }
            drawPath(
                path = lidPath,
                color = Pupil.copy(alpha = (blink - 0.55f) / 0.45f * 0.7f),
                style = Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
    }
}

private fun DrawScope.drawLilyPad() {
    val cx = BODY_CX
    val cy = 141f
    val rx = 64f
    val ry = 15f

    // El nenúfar es una elipse maciza. La muesca se recorta solo de la capa
    // clara, dejando ver la base oscura: si se recortara también la base,
    // asomaría el fondo de la pantalla por el hueco y se leería como un fallo.
    drawOval(PadBase, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))

    val innerRx = rx - 5f
    val innerRy = ry - 3.5f
    val inner = Path().apply {
        addOval(Rect(cx - innerRx, cy - innerRy, cx + innerRx, cy + innerRy))
    }
    // Cuña horizontal desde el centro hacia el borde derecho.
    val wedge = Path().apply {
        moveTo(cx, cy)
        lineTo(cx + rx + 6f, cy - 9f)
        lineTo(cx + rx + 6f, cy + 9f)
        close()
    }
    drawPath(Path().apply { op(inner, wedge, PathOperation.Difference) }, PadTop)
}

private fun DrawScope.drawReed() {
    // El cuerpo llega hasta x=152, así que el junco entero se mantiene por
    // encima de x=160 y no roza a Pochi.
    val stem = Path().apply {
        moveTo(178f, 148f)
        cubicTo(178f, 120f, 174f, 96f, 168f, 72f)
    }
    drawPath(
        path = stem,
        color = BodyShade.copy(alpha = 0.6f),
        style = Stroke(width = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
    )
    drawOval(
        color = ReedHead.copy(alpha = 0.7f),
        topLeft = Offset(162f, 46f),
        size = Size(12f, 26f),
    )
    val leaf = Path().apply {
        moveTo(176f, 120f)
        cubicTo(169f, 113f, 163f, 104f, 162f, 96f)
        cubicTo(169f, 99f, 175f, 109f, 176f, 120f)
        close()
    }
    drawPath(leaf, BodyShade.copy(alpha = 0.45f))
}
