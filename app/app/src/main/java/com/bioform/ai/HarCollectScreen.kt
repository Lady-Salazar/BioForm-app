package com.bioform.ai

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bioform.ai.ui.theme.BioBlue
import com.bioform.ai.ui.theme.BioMuted
import com.bioform.ai.ui.theme.BioNavy
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Actividades del modelo (deben coincidir con ACTIVITY_CATEGORY del backend). */
private val HAR_ACTIVITIES = listOf(
    "reposo" to "Reposo (de pie o sentado)",
    "caminar" to "Caminar",
    "trotar" to "Trotar",
    "saltos" to "Saltos (jumping jacks)",
    "sentadillas" to "Sentadillas",
)
private const val POSITION = "bolsillo"
private const val RECORD_SECONDS = 60
private const val TEST_SECONDS = 10

private enum class Phase { IDLE, COUNTDOWN, RECORDING, TESTING }

/** Pantalla para grabar datos de entrenamiento y probar el modelo de actividad. */
@Composable
fun HarCollectScreen(user: FirebaseUser, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val recorder = remember { HarRecorder(context) }
    val api = remember { BackendApi() }

    var subject by remember { mutableStateOf("S01") }
    var activity by remember { mutableStateOf("caminar") }
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var secondsLeft by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var counts by remember { mutableStateOf(HarFiles.countByActivity(context)) }

    // Pantalla siempre encendida mientras graba: Android corta los sensores con la app en segundo plano
    DisposableEffect(phase) {
        view.keepScreenOn = phase != Phase.IDLE
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(Unit) { onDispose { recorder.stop() } }
    BackHandler(enabled = phase == Phase.IDLE) { onBack() }

    val cleanSubject = subject.filter { it.isLetterOrDigit() }.uppercase().ifEmpty { "S01" }

    suspend fun countdown() {
        phase = Phase.COUNTDOWN
        for (i in 5 downTo 1) {
            secondsLeft = i
            delay(1000)
        }
        beep()
    }

    fun recordTraining() {
        scope.launch {
            message = null
            countdown()
            phase = Phase.RECORDING
            val rows = recorder.record(RECORD_SECONDS) { secondsLeft = it }
            beep()
            val file = HarFiles.save(context, cleanSubject, activity, POSITION, rows)
            counts = HarFiles.countByActivity(context)
            message = "Guardado: ${file.name} (${rows.size} muestras)"
            phase = Phase.IDLE
        }
    }

    fun testModel() {
        scope.launch {
            message = null
            countdown()
            phase = Phase.TESTING
            val rows = recorder.record(TEST_SECONDS) { secondsLeft = it }
            beep()
            message = try {
                val r = api.classifyActivity(user, rows)
                "Actividad detectada: ${r.activity} (${r.category}) · confianza ${(r.confidence * 100).toInt()} %"
            } catch (e: Exception) {
                "No se pudo clasificar: ${e.localizedMessage}"
            }
            phase = Phase.IDLE
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        if (phase != Phase.IDLE) {
            // Durante la grabacion no hay botones: el telefono va en el bolsillo
            Spacer(Modifier.height(80.dp))
            Text(
                when (phase) {
                    Phase.COUNTDOWN -> "Guarda el teléfono en el bolsillo"
                    Phase.RECORDING -> "Grabando: $activity"
                    else -> "Probando el modelo"
                },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = BioNavy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "$secondsLeft",
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
                color = BioBlue,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (phase == Phase.COUNTDOWN) "Empieza después del pitido" else "Sigue hasta el siguiente pitido",
                textAlign = TextAlign.Center,
                color = BioMuted,
                modifier = Modifier.fillMaxWidth(),
            )
            if (phase == Phase.COUNTDOWN) {
                Spacer(Modifier.height(24.dp))
                PocketPlacementGuide()
            }
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Volver") }
        }
        Text(
            "Recolectar datos de entrenamiento",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = BioNavy,
        )
        Spacer(Modifier.height(12.dp))

        if (!recorder.hasSensors) {
            Text("Este teléfono no tiene acelerómetro o giroscopio.", color = MaterialTheme.colorScheme.error)
            return@Column
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Cómo colocar el teléfono", fontWeight = FontWeight.Bold, color = BioNavy)
                Spacer(Modifier.height(8.dp))
                PocketPlacementGuide()
                Spacer(Modifier.height(8.dp))
                Text("• Bolsillo delantero DERECHO del pantalón.")
                Text("• Vertical, con la cámara (parte de arriba) hacia ABAJO.")
                Text("• Pantalla mirando hacia tu pierna.")
                Text("• Nunca en la mano, sobre la mesa ni en otro bolsillo.")
                Text("• Reposo: una grabación sentado y otra de pie, con el teléfono en el bolsillo.")
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Cómo grabar", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("1. Escribe tu código de participante (nunca tu nombre).")
                Text("2. Elige la actividad que vas a realizar.")
                Text("3. Toca Grabar y guarda el teléfono como muestra la animación.")
                Text("4. Tras el pitido, realiza la actividad durante $RECORD_SECONDS s, hasta el segundo pitido.")
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = subject,
            onValueChange = { subject = it },
            label = { Text("Código de participante (ej. S01)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        Text("Actividad", style = MaterialTheme.typography.titleMedium, color = BioNavy)
        HAR_ACTIVITIES.forEach { (key, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activity = key },
            ) {
                RadioButton(selected = activity == key, onClick = { activity = key })
                Text(label, modifier = Modifier.weight(1f))
                Text("${counts[key] ?: 0} grab.", color = BioMuted, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { recordTraining() },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) { Text("Grabar $RECORD_SECONDS s de \"$activity\"") }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { testModel() },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Probar el modelo ($TEST_SECONDS s)") }

        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = BioNavy)
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                val shared = try {
                    HarFiles.shareAll(context)
                } catch (e: Exception) {
                    message = "No se pudo compartir: ${e.localizedMessage}"
                    -1
                }
                if (shared == 0) message = "Todavía no hay grabaciones para compartir."
            },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Compartir grabaciones (${counts.values.sum()})") }

        Spacer(Modifier.height(20.dp))
        Text(
            "Las grabaciones se guardan en Android/data/com.bioform.ai/files/har/. " +
                "Se copian al computador con adb pull para entrenar el modelo.",
            style = MaterialTheme.typography.bodySmall,
            color = BioMuted,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Sujeto actual: $cleanSubject · posición: $POSITION · teléfono: ${HarFiles.deviceModel()}",
            style = MaterialTheme.typography.bodySmall,
            color = BioMuted,
        )
    }
}

/** Pitido corto para avisar el inicio y el fin sin mirar la pantalla. */
private fun beep() {
    try {
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 800)
    } catch (_: Exception) {
        // Sin audio disponible: se continua sin pitido
    }
}
