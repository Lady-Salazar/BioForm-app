package com.bioform.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

/** Pantalla principal: permisos de Health Connect y datos del día. */
@Composable
fun HomeScreen(user: FirebaseUser, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { HealthRepository(context) }
    val scope = rememberCoroutineScope()

    val hcStatus = remember { repo.sdkStatus() }
    var hasPermissions by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<DailySummary?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun loadData() {
        scope.launch {
            loading = true
            message = null
            try {
                summary = repo.readToday()
            } catch (e: Exception) {
                message = "No se pudieron leer los datos: ${e.localizedMessage}"
            }
            loading = false
        }
    }

    // Abre la ventana de permisos de Health Connect
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        hasPermissions = granted.containsAll(repo.permissions)
        if (hasPermissions) loadData()
        else message = "Necesitamos todos los permisos para darte un consejo personalizado."
    }

    // Al abrir la pantalla: revisa si ya hay permisos y carga los datos
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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Hola", style = MaterialTheme.typography.headlineMedium)
        Text(user.email ?: "", style = MaterialTheme.typography.bodyLarge)

        Spacer(Modifier.height(24.dp))

        when {
            hcStatus != HealthConnectClient.SDK_AVAILABLE -> Text(
                "Health Connect no está disponible o necesita actualizarse. Instálalo o actualízalo desde Google Play.",
                color = MaterialTheme.colorScheme.error,
            )

            !hasPermissions -> {
                Text("Conecta tus datos de salud para recibir consejos personalizados.")
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { permissionLauncher.launch(repo.permissions) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Conectar con Health Connect") }
            }

            loading -> CircularProgressIndicator()

            else -> summary?.let { s ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Tus datos de hoy", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        MetricRow(
                            "FC en reposo",
                            s.restingHr?.let { "$it lpm" + if (s.restingHrEstimated) " (estimada)" else "" },
                        )
                        MetricRow("Pasos", s.steps?.toString())
                        MetricRow("Sueño", s.sleepHours?.let { "%.1f h".format(it) })
                        MetricRow("Calorías", s.calories?.let { "$it kcal" })
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { loadData() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Actualizar datos")
                }
            }
        }

        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(32.dp))

        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text("Cerrar sesión")
        }
    }
}

/** Una fila de la tarjeta: nombre del dato y su valor (o "Sin datos"). */
@Composable
private fun MetricRow(label: String, value: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text(value ?: "Sin datos", style = MaterialTheme.typography.bodyLarge)
    }
}