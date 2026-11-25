package com.bioform.ai

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bioform.ai.ui.theme.BioBlue
import com.bioform.ai.ui.theme.BioMuted
import com.bioform.ai.ui.theme.BioNavy

/** Tipos de usuario: valor guardado en Firestore, emoji y etiqueta. */
private val USER_TYPES = listOf(
    Triple("atleta", "🏃", "Atleta"),
    Triple("gimnasio", "🏋️", "Gimnasio"),
    Triple("salud", "❤️", "Salud general"),
)

/** Consentimiento informado (LOPDP) y tipo de usuario. Se muestra solo la primera vez. */
@Composable
fun ConsentScreen(
    saving: Boolean,
    error: String?,
    onAccept: (userType: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var accepted by remember { mutableStateOf(false) }
    var userType by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            "Consentimiento y perfil",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = BioNavy,
        )
        Spacer(Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "BioForm AI leerá desde Health Connect tu frecuencia cardíaca, pasos, sueño y calorías " +
                        "para generar consejos de bienestar. Tus datos se guardan de forma segura, " +
                        "solo tú puedes verlos y puedes pedir su eliminación en cualquier momento. " +
                        "BioForm AI no es un dispositivo médico y no realiza diagnósticos.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { accepted = !accepted },
                ) {
                    Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                    Text(
                        "Acepto el consentimiento informado y la política de privacidad " +
                            "(Ley Orgánica de Protección de Datos Personales, Ecuador 2021).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Selecciona tu tipo de usuario", style = MaterialTheme.typography.titleMedium, color = BioNavy)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            USER_TYPES.forEach { (value, emoji, label) ->
                val selected = userType == value
                Card(
                    onClick = { userType = value },
                    shape = RoundedCornerShape(16.dp),
                    border = if (selected) BorderStroke(2.dp, BioBlue) else null,
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(emoji, fontSize = 30.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(28.dp))

        if (saving) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        } else {
            Button(
                onClick = { userType?.let(onAccept) },
                enabled = accepted && userType != null,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("Continuar", fontWeight = FontWeight.SemiBold) }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Debes aceptar el consentimiento y elegir un tipo de usuario para continuar.",
            style = MaterialTheme.typography.bodySmall,
            color = BioMuted,
        )
    }
}
