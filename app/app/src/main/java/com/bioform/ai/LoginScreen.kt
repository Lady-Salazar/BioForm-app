package com.bioform.ai

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bioform.ai.ui.theme.BioMuted
import com.bioform.ai.ui.theme.BioNavy
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

/** Pantalla de inicio de sesión y registro con correo y contraseña (Firebase Auth). */
@Composable
fun LoginScreen(auth: FirebaseAuth, modifier: Modifier = Modifier) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun isValid(): Boolean {
        error = when {
            !email.contains("@") -> "Escribe un correo electrónico válido."
            password.length < 6 -> "La contraseña debe tener al menos 6 caracteres."
            else -> null
        }
        return error == null
    }

    val fieldShape = RoundedCornerShape(14.dp)
    val buttonShape = RoundedCornerShape(26.dp)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Image(
            painter = painterResource(R.drawable.bioform_logo),
            contentDescription = "Logo de BioForm AI",
            modifier = Modifier.size(150.dp),
        )
        Text(
            "BioForm AI",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = BioNavy,
        )
        Text("Tu salud, potenciada por IA", style = MaterialTheme.typography.bodyMedium, color = BioMuted)

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Correo electrónico") },
            singleLine = true,
            shape = fieldShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            singleLine = true,
            shape = fieldShape,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))

        if (loading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    if (isValid()) {
                        loading = true
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener { result ->
                                loading = false
                                if (!result.isSuccessful) error = authErrorMessage(result.exception)
                            }
                    }
                },
                shape = buttonShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("Iniciar Sesión", fontWeight = FontWeight.SemiBold) }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f))
                Text("  o  ", color = BioMuted)
                HorizontalDivider(Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    if (isValid()) {
                        loading = true
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener { result ->
                                loading = false
                                if (!result.isSuccessful) error = authErrorMessage(result.exception)
                            }
                    }
                },
                shape = buttonShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("Crear cuenta nueva") }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "BioForm AI · No es un dispositivo médico",
            style = MaterialTheme.typography.bodySmall,
            color = BioMuted,
        )
    }
}

/** Traduce los errores de Firebase a mensajes claros en español. */
fun authErrorMessage(e: Exception?): String = when (e) {
    is FirebaseAuthWeakPasswordException -> "La contraseña es muy débil. Usa al menos 6 caracteres."
    is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con este correo. Inicia sesión."
    is FirebaseAuthInvalidUserException -> "No existe una cuenta con este correo."
    is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos."
    is FirebaseNetworkException -> "Sin conexión a internet. Inténtalo de nuevo."
    else -> "No se pudo completar la operación: ${e?.localizedMessage ?: "error desconocido"}"
}
