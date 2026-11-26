package com.bioform.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.bioform.ai.ui.theme.BioAlert
import com.bioform.ai.ui.theme.BioBlue
import com.bioform.ai.ui.theme.BioGreen
import com.bioform.ai.ui.theme.BioMuted
import com.bioform.ai.ui.theme.BioNavy
import com.bioform.ai.ui.theme.BioOrange
import com.bioform.ai.ui.theme.BioSky
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val STEPS_GOAL = 10_000
private const val SLEEP_GOAL_H = 8.0
private const val CALORIES_GOAL = 2_500

/** Pantalla principal: métricas del día, AI Coach & Nutricionista y avisos de bienestar. */
@Composable
fun HomeScreen(
    user: FirebaseUser,
    userType: String?,
    onSignOut: () -> Unit,
    onOpenHar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repo = remember { HealthRepository(context) }
    val api = remember { BackendApi() }
    val scope = rememberCoroutineScope()

    val hcStatus = remember { repo.sdkStatus() }
    var hasPermissions by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<DailySummary?>(null) }
    var loading by remember { mutableStateOf(false) }
    var lastSync by remember { mutableStateOf<String?>(null) }
    var advice by remember { mutableStateOf<AdviceResult?>(null) }
    var adviceLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    var showAlert by remember { mutableStateOf(false) }
    var showRoutine by remember { mutableStateOf(false) }
    var showNutrition by remember { mutableStateOf(false) }

    val greetingName = when (userType) {
        "atleta" -> "Atleta"
        "gimnasio" -> "Deportista"
        else -> null
    }

    fun loadData() {
        scope.launch {
            loading = true
            message = null
            advice = null
            try {
                summary = repo.readToday()
                lastSync = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            } catch (e: Exception) {
                message = if (e.message?.contains("foreground") == true) {
                    "Abre la app con el teléfono desbloqueado y toca \"Cargar mis datos\"."
                } else {
                    "No se pudieron leer los datos: ${e.localizedMessage}"
                }
            }
            loading = false
        }
    }

    fun requestAdvice(s: DailySummary) {
        scope.launch {
            adviceLoading = true
            message = null
            try {
                val result = api.requestAdvice(user, s)
                advice = result
                if (result.level != "none") showAlert = true
            } catch (e: Exception) {
                message = "No se pudo obtener el consejo: ${e.localizedMessage}"
            }
            adviceLoading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        hasPermissions = granted.containsAll(repo.permissions)
        if (hasPermissions) loadData()
        else message = "Necesitamos todos los permisos para darte un consejo personalizado."
    }

    LaunchedEffect(Unit) {
        if (hcStatus == HealthConnectClient.SDK_AVAILABLE) {
            hasPermissions = repo.hasAllPermissions()
            if (hasPermissions) loadData()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        TopBar(email = user.email ?: "", onSignOut = onSignOut, onOpenHar = onOpenHar)

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (greetingName != null) "¡Hola, $greetingName!" else "¡Hola!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = BioNavy,
                modifier = Modifier.weight(1f),
            )
            lastSync?.let { SyncChip(time = it, onClick = { loadData() }) }
        }

        Spacer(Modifier.height(16.dp))

        when {
            hcStatus != HealthConnectClient.SDK_AVAILABLE -> InfoCard(
                text = "Health Connect no está disponible o necesita actualizarse. Instálalo o actualízalo desde Google Play.",
            )

            !hasPermissions -> InfoCard(
                text = "Conecta tus datos de salud para recibir consejos personalizados.",
                buttonText = "Conectar con Health Connect",
                onClick = { permissionLauncher.launch(repo.permissions) },
            )

            loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            summary == null -> InfoCard(
                text = "Toca el botón para leer tus datos de hoy.",
                buttonText = "Cargar mis datos",
                onClick = { loadData() },
            )

            else -> summary?.let { s ->
                MetricsGrid(s)
                Spacer(Modifier.height(16.dp))
                AiCoachCard(
                    greetingName = greetingName,
                    advice = advice,
                    loading = adviceLoading,
                    onRequest = { requestAdvice(s) },
                    onRoutine = { showRoutine = true },
                    onNutrition = { showNutrition = true },
                    onShowAlert = { showAlert = true },
                )
            }
        }

        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "BioForm AI · Tu salud, potenciada por IA",
            style = MaterialTheme.typography.bodySmall,
            color = BioMuted,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
    }

    // ---- Diálogos ----
    val a = advice
    if (showAlert && a != null) WellnessAlertDialog(a, onDismiss = { showAlert = false })
    if (showRoutine && a?.training != null) RoutineDialog(a.training, onDismiss = { showRoutine = false })
    if (showNutrition && a?.nutrition != null) NutritionDialog(a.nutrition, onDismiss = { showNutrition = false })
}

// ---------------------------------------------------------------- Barra superior

@Composable
private fun TopBar(email: String, onSignOut: () -> Unit, onOpenHar: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(R.drawable.bioform_logo),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = BioNavy)) { append("BioForm ") }
                withStyle(SpanStyle(color = BioBlue)) { append("AI") }
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Box {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BioBlue)
                    .clickable { menuOpen = true },
            ) {
                Text(
                    email.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(email) }, onClick = { menuOpen = false }, enabled = false)
                DropdownMenuItem(
                    text = { Text("Recolectar datos (entrenamiento IA)") },
                    onClick = {
                        menuOpen = false
                        onOpenHar()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Cerrar sesión") },
                    onClick = {
                        menuOpen = false
                        onSignOut()
                    },
                )
            }
        }
    }
}

@Composable
private fun SyncChip(time: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.End) {
            Text("Sincronizado ✓", style = MaterialTheme.typography.labelMedium, color = BioGreen)
            Text("$time · tocar para actualizar", style = MaterialTheme.typography.labelSmall, color = BioMuted)
        }
    }
}

@Composable
private fun InfoCard(text: String, buttonText: String? = null, onClick: (() -> Unit)? = null) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (buttonText != null && onClick != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onClick, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(buttonText)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Métricas

@Composable
private fun MetricsGrid(s: DailySummary) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(236.dp)) {
        HeartRateCard(s, Modifier.weight(1f).fillMaxHeight())
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f).fillMaxHeight()) {
            StepsCard(s.steps, Modifier.fillMaxWidth().weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().weight(1.3f)) {
                RingCard(
                    label = "Sueño",
                    value = s.sleepHours?.let { "${it.toInt()}h ${((it - it.toInt()) * 60).toInt()}m" },
                    progress = ((s.sleepHours ?: 0.0) / SLEEP_GOAL_H).toFloat(),
                    color = BioSky,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                RingCard(
                    label = "Calorías",
                    value = s.calories?.let { "%,d".format(it) },
                    progress = (s.calories ?: 0).toFloat() / CALORIES_GOAL,
                    color = BioOrange,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun HeartRateCard(s: DailySummary, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(BioBlue, BioNavy)))
            .padding(16.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text("Frecuencia Cardíaca", color = Color.White, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            EcgLine(Modifier.fillMaxWidth().height(56.dp))
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    s.restingHr?.toString() ?: "--",
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text("BPM", color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(
                if (s.restingHrEstimated) "En reposo · estimada" else "En reposo",
                color = BioSky,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Línea de electrocardiograma decorativa. */
@Composable
private fun EcgLine(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val mid = h * 0.6f
        val path = Path().apply {
            moveTo(0f, mid)
            lineTo(w * 0.25f, mid)
            lineTo(w * 0.32f, mid - h * 0.25f)
            lineTo(w * 0.38f, mid + h * 0.15f)
            lineTo(w * 0.45f, h * 0.02f)
            lineTo(w * 0.52f, h * 0.95f)
            lineTo(w * 0.58f, mid - h * 0.2f)
            lineTo(w * 0.64f, mid)
            lineTo(w, mid)
        }
        drawPath(path, color = Color.White, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun StepsCard(steps: Long?, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
            Text("Pasos", style = MaterialTheme.typography.labelMedium, color = BioMuted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    steps?.let { "%,d".format(it) } ?: "--",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = BioNavy,
                )
                Text(" / %,d".format(STEPS_GOAL), style = MaterialTheme.typography.bodySmall, color = BioMuted)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { ((steps ?: 0L).toFloat() / STEPS_GOAL).coerceIn(0f, 1f) },
                color = BioSky,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
        }
    }
}

@Composable
private fun RingCard(label: String, value: String?, progress: Float, color: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = BioMuted)
            Spacer(Modifier.height(4.dp))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 5.dp,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.size(62.dp),
                )
                Text(
                    value ?: "--",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = BioNavy,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- AI Coach & Nutricionista

@Composable
private fun AiCoachCard(
    greetingName: String?,
    advice: AdviceResult?,
    loading: Boolean,
    onRequest: () -> Unit,
    onRoutine: () -> Unit,
    onNutrition: () -> Unit,
    onShowAlert: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BioSky),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.bioform_logo),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("AI Coach & Nutricionista", fontWeight = FontWeight.Bold, color = BioNavy)
                    Text("Genkit + Gemini 3.1 Flash-Lite", style = MaterialTheme.typography.bodySmall, color = BioMuted)
                }
            }
            Spacer(Modifier.height(14.dp))

            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Generando tu consejo… puede tardar hasta 1 minuto.")
                }

                advice == null -> {
                    Text("Analizo tu frecuencia cardíaca, actividad y sueño para recomendarte qué hacer hoy.")
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onRequest, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Obtener mi consejo del día")
                    }
                }

                advice.adviceStatus == "blocked" -> {
                    Text("Por tu seguridad, hoy no generamos un consejo de entrenamiento.", color = BioAlert)
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = onShowAlert, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Ver aviso de bienestar")
                    }
                }

                advice.adviceStatus == "unavailable" -> {
                    Text("El asesor de IA no está disponible ahora.")
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onRequest, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Intentar de nuevo")
                    }
                }

                else -> {
                    Text(
                        if (greetingName != null) "¡Gran trabajo, $greetingName!" else "¡Gran trabajo!",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Basado en tu ritmo cardíaco y sueño, hoy te sugiero:", color = BioMuted)
                    Spacer(Modifier.height(6.dp))
                    advice.training?.let { Text("• Entrenamiento de intensidad ${it.intensity}.") }
                    advice.nutrition?.let { Text("• ${it.summary}") }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onRoutine, shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f)) {
                            Text("Ver Rutina de Hoy")
                        }
                        OutlinedButton(onClick = onNutrition, shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f)) {
                            Text("Plan Nutricional")
                        }
                    }
                    if (advice.level != "none") {
                        TextButton(onClick = onShowAlert) { Text("Ver aviso de bienestar", color = BioAlert) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Diálogos

@Composable
private fun WellnessAlertDialog(a: AdviceResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BioAlert,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        icon = { Text("🛡️", fontSize = 32.sp) },
        title = {
            Text(
                "Aviso Preventivo de Bienestar (Reglas Cardíacas)",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(a.ruleMessage)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Esto es un aviso de bienestar, no un diagnóstico médico. Consulta a un especialista.",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, Color.White),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                shape = RoundedCornerShape(24.dp),
            ) { Text("Entendido") }
        },
    )
}

@Composable
private fun RoutineDialog(t: TrainingAdvice, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rutina de hoy · intensidad ${t.intensity}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Antes", fontWeight = FontWeight.Bold, color = BioBlue)
                Text(t.pre)
                Spacer(Modifier.height(10.dp))
                Text("Durante", fontWeight = FontWeight.Bold, color = BioBlue)
                Text(t.during)
                Spacer(Modifier.height(10.dp))
                Text("Después", fontWeight = FontWeight.Bold, color = BioBlue)
                Text(t.post)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun NutritionDialog(n: NutritionAdvice, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plan nutricional de hoy") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(n.summary)
                Spacer(Modifier.height(10.dp))
                n.suggestions.forEach {
                    Text("• $it")
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text("Hidratación", fontWeight = FontWeight.Bold, color = BioBlue)
                Text(n.hydration)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
