package com.bioform.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bioform.ai.ui.theme.BioFormAITheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BioFormAITheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BioFormApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/** Decide que pantalla mostrar segun si hay una sesion iniciada. */
@Composable
fun BioFormApp(modifier: Modifier = Modifier) {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }

    // Escucha los cambios de sesion (login, registro, cierre de sesion)
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val currentUser = user
    if (currentUser == null) {
        LoginScreen(auth = auth, modifier = modifier)
    } else {
        HomeScreen(user = currentUser, onSignOut = { auth.signOut() }, modifier = modifier)
    }
}