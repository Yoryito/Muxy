package com.muxy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
 *
 * [notchWidth] va en grados, así que la muesca crece con el radio: el valor por
 * defecto es una hendidura fina en los tamaños de lista, pero en una carátula
 * grande abre un pedazo de tarta. Los elementos grandes tienen que estrecharlo.
 */
@Composable
fun LilyPadFrame(
    modifier: Modifier = Modifier,
    notchAngle: Float = 315f,
    notchWidth: Float = 15f,
    background: Color = MaterialTheme.colorScheme.primaryContainer,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(LilyPadShape(notchAngleDegrees = notchAngle, notchWidthDegrees = notchWidth))
            .background(background),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Estado vacío con Pochi. Se usa solo donde cumple una función:
 * librería vacía, búsqueda sin empezar, error. Nunca como adorno.
 */
@Composable
fun PochiEmptyState(
    pose: PochiPose,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Ancho completo y alto fijo: la escena es apaisada (rana + caracol) y
        // el propio Canvas la centra, así que en pantallas estrechas encoge
        // sola en vez de desbordar.
        Pochi(
            pose = pose,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
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
