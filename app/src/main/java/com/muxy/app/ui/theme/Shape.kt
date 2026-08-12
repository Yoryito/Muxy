package com.muxy.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

val MuxyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/**
 * Nenúfar: un círculo con la muesca en forma de cuña que lo caracteriza.
 * Es el lenguaje de formas recurrente de la app — carátulas, avatares, el botón de reproducir.
 *
 * [notchAngleDegrees] sitúa la muesca; variarlo entre elementos evita que una rejilla
 * de nenúfares parezca estampada con plantilla.
 */
class LilyPadShape(
    private val notchAngleDegrees: Float = 315f,
    private val notchWidthDegrees: Float = 34f,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = minOf(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        val pad = Path().apply {
            addOval(Rect(center = center, radius = radius))
        }

        // La muesca es una cuña que sale del centro y se extiende más allá del borde,
        // de modo que al restarla deja el corte limpio hasta el centro del nenúfar.
        val reach = radius * 1.4f
        val half = Math.toRadians((notchWidthDegrees / 2f).toDouble())
        val angle = Math.toRadians(notchAngleDegrees.toDouble())

        val wedge = Path().apply {
            moveTo(center.x, center.y)
            lineTo(
                center.x + (reach * kotlin.math.cos(angle - half)).toFloat(),
                center.y + (reach * kotlin.math.sin(angle - half)).toFloat(),
            )
            lineTo(
                center.x + (reach * kotlin.math.cos(angle + half)).toFloat(),
                center.y + (reach * kotlin.math.sin(angle + half)).toFloat(),
            )
            close()
        }

        return Outline.Generic(
            Path().apply { op(pad, wedge, PathOperation.Difference) }
        )
    }
}
