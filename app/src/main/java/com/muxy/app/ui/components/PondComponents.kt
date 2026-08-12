package com.muxy.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muxy.app.ui.theme.LilyPadShape

/**
 * Tarjeta base de la app. Esquinas generosas y sin sombra dura — la separación
 * viene del contraste de superficie, no de sombras apiladas.
 */
@Composable
fun LilyPadCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

/** Botón principal: cápsula, sin elevación, tipografía redonda. */
@Composable
fun PondButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 14.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Contenedor con forma de nenúfar, para carátulas y avatares.
 * [notchAngle] varía la orientación de la muesca para que una lista no parezca estampada.
 */
@Composable
fun LilyPadFrame(
    modifier: Modifier = Modifier,
    notchAngle: Float = 315f,
    background: Color = MaterialTheme.colorScheme.primaryContainer,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(LilyPadShape(notchAngleDegrees = notchAngle))
            .background(background),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Estado vacío con la ranita. Se usa solo donde cumple una función:
 * librería vacía, búsqueda sin empezar, error. Nunca como adorno.
 */
@Composable
fun FrogEmptyState(
    illustration: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    // Flotación muy leve, como si estuviera sobre el agua.
    val transition = rememberInfiniteTransition(label = "float")
    val offset by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "floatOffset",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(illustration),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(190.dp)
                .graphicsLayer { translationY = offset },
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        if (action != null) {
            Box(Modifier.padding(top = 26.dp)) { action() }
        }
    }
}
