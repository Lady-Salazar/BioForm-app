package com.bioform.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bioform.ai.ui.theme.BioBlue
import com.bioform.ai.ui.theme.BioGreen
import com.bioform.ai.ui.theme.BioMuted
import com.bioform.ai.ui.theme.BioNavy
import com.bioform.ai.ui.theme.BioSky

private val PocketColor = Color(0xFF16307A)

private val GUIDE_STEPS = listOf(
    "1. Toma el teléfono con la pantalla hacia tu pierna (ves la cámara).",
    "2. Gíralo: la cámara (parte de arriba) queda hacia ABAJO.",
    "3. Guárdalo vertical en el bolsillo delantero DERECHO.",
    "¡Listo! Colócalo igual en todas las grabaciones.",
)

private fun ease(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

/**
 * Animación de 5 segundos (en bucle) que muestra cómo colocar el teléfono
 * en el bolsillo para grabar los datos de entrenamiento.
 */
@Composable
fun PocketPlacementGuide(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bolsillo")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "progreso",
    )
    val t = progress * 5f  // segundos dentro del clip
    val step = when {
        t < 1.2f -> 0
        t < 2.2f -> 1
        t < 3.9f -> 2
        else -> 3
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            val w = size.width
            val h = size.height

            // Pantalón visto por quien lo lleva puesto: cintura y dos piernas
            drawRect(BioNavy, Offset(0.14f * w, 0.02f * h), Size(0.72f * w, 0.28f * h))
            drawRect(PocketColor, Offset(0.14f * w, 0.02f * h), Size(0.72f * w, 0.05f * h))
            drawRect(BioNavy, Offset(0.14f * w, 0.25f * h), Size(0.34f * w, 0.75f * h))
            drawRect(BioNavy, Offset(0.52f * w, 0.25f * h), Size(0.34f * w, 0.75f * h))

            // Teléfono: primero se gira 180° (cámara hacia abajo) y luego entra al bolsillo
            val pw = 0.15f * w
            val ph = 0.34f * h
            val cx = 0.69f * w
            val angle = 180f * ease((t - 1.2f) / 1.0f)
            val slide = ease((t - 2.2f) / 1.6f)
            val cy = 0.20f * h + (0.50f * h - 0.20f * h) * slide
            rotate(degrees = angle, pivot = Offset(cx, cy)) {
                drawRoundRect(
                    color = BioBlue,
                    topLeft = Offset(cx - pw / 2, cy - ph / 2),
                    size = Size(pw, ph),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
                // Cámara trasera (marca la parte de arriba del teléfono)
                for (k in 0..1) {
                    val c = Offset(cx - pw / 2 + 0.25f * pw, cy - ph / 2 + 0.14f * ph + k * 0.2f * ph)
                    drawCircle(BioNavy, radius = 0.09f * pw, center = c)
                    drawCircle(BioSky, radius = 0.09f * pw, center = c, style = Stroke(2.dp.toPx()))
                }
            }

            // Bolsillo delantero de la pierna derecha (tapa la parte del teléfono que entra)
            drawRoundRect(
                color = PocketColor,
                topLeft = Offset(0.55f * w, 0.40f * h),
                size = Size(0.28f * w, 0.38f * h),
                cornerRadius = CornerRadius(10.dp.toPx()),
            )
            drawLine(BioSky, Offset(0.55f * w, 0.40f * h), Offset(0.83f * w, 0.40f * h), strokeWidth = 3.dp.toPx())

            // Flecha mientras entra
            if (t in 2.0f..3.8f) {
                val ax = 0.93f * w
                drawLine(BioBlue, Offset(ax, 0.12f * h), Offset(ax, 0.34f * h), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                val head = Path().apply {
                    moveTo(ax - 0.025f * w, 0.32f * h)
                    lineTo(ax + 0.025f * w, 0.32f * h)
                    lineTo(ax, 0.40f * h)
                    close()
                }
                drawPath(head, BioBlue)
            }

            // Visto bueno al final
            if (t > 3.9f) {
                val c = Offset(0.93f * w, 0.14f * h)
                val r = 0.09f * h
                drawCircle(BioGreen, radius = r, center = c)
                val check = Path().apply {
                    moveTo(c.x - 0.45f * r, c.y)
                    lineTo(c.x - 0.1f * r, c.y + 0.35f * r)
                    lineTo(c.x + 0.5f * r, c.y - 0.35f * r)
                }
                drawPath(check, Color.White, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
        }

        Row(Modifier.fillMaxWidth()) {
            Text(
                "Pierna izquierda",
                style = MaterialTheme.typography.labelSmall,
                color = BioMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Pierna DERECHA",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = BioNavy,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            GUIDE_STEPS[step],
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = BioNavy,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
        )
    }
}
