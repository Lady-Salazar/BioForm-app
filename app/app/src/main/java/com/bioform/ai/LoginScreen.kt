package com.bioform.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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

    // Revisa los datos antes de enviarlos a Firebase
    fun isValid(): Boolean {
        error = when {
            !email.contains("@") -> "Escribe un correo electrónico válido."
            password.length < 6 -> "La contraseña debe tener al menos 6 caracteres."
            else -> null
        }
        return error == null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("BioForm AI", style = MaterialTheme.typography.headlineMedium)
        Text("Tu asesor fitness digital", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Correo electrónico") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            singleLine = true,
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
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Iniciar sesión") }

            Spacer(Modifier.height(8.dp))

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
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Crear cuenta") }
        }
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