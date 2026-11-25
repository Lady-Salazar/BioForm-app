package com.bioform.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.bioform.ai.ui.theme.BioFormAITheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Pantalla de carga con el logo (debe ir antes de super.onCreate)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Barra de estado con iconos oscuros, porque el fondo de la app es claro
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            )
        )
        setContent {
            BioFormAITheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BioFormApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/** Flujo: Login -> Consentimiento y perfil (solo la primera vez) -> Inicio. */
@Composable
fun BioFormApp(modifier: Modifier = Modifier) {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val currentUser = user
    if (currentUser == null) {
        LoginScreen(auth = auth, modifier = modifier)
    } else {
        LoggedInFlow(user = currentUser, onSignOut = { auth.signOut() }, modifier = modifier)
    }
}

/** Usuario con sesion: revisa el consentimiento y muestra el inicio. */
@Composable
private fun LoggedInFlow(user: FirebaseUser, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var profile by remember(user.uid) { mutableStateOf<UserProfile?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(user.uid) {
        profile = try {
            ProfileRepository.load(user.uid)
        } catch (e: Exception) {
            UserProfile(consentAccepted = false, userType = null)
        }
    }

    val p = profile
    when {
        p == null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        !p.consentAccepted -> ConsentScreen(
            saving = saving,
            error = saveError,
            onAccept = { type ->
                scope.launch {
                    saving = true
                    saveError = null
                    try {
                        ProfileRepository.saveConsent(user.uid, type)
                        profile = UserProfile(consentAccepted = true, userType = type)
                    } catch (e: Exception) {
                        saveError = "No se pudo guardar: ${e.localizedMessage}"
                    }
                    saving = false
                }
            },
            modifier = modifier,
        )

        else -> HomeScreen(
            user = user,
            userType = p.userType,
            onSignOut = onSignOut,
            modifier = modifier,
        )
    }
}
