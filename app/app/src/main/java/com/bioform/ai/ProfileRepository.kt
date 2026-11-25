package com.bioform.ai

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Perfil guardado en Firestore: users/{uid}. */
data class UserProfile(val consentAccepted: Boolean, val userType: String?)

object ProfileRepository {

    /** Version del texto de consentimiento (cambiarla si se modifica el texto). */
    const val CONSENT_VERSION = "2026-11"

    private val db get() = FirebaseFirestore.getInstance()

    suspend fun load(uid: String): UserProfile = suspendCancellableCoroutine { cont ->
        db.collection("users").document(uid).get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val doc = task.result
                cont.resume(
                    UserProfile(
                        consentAccepted = doc?.getBoolean("consent_accepted") == true,
                        userType = doc?.getString("user_type"),
                    )
                )
            } else {
                cont.resumeWithException(task.exception ?: IllegalStateException("No se pudo leer el perfil."))
            }
        }
    }

    suspend fun saveConsent(uid: String, userType: String): Unit = suspendCancellableCoroutine { cont ->
        val data = mapOf(
            "consent_accepted" to true,
            "consent_version" to CONSENT_VERSION,
            "consent_at" to FieldValue.serverTimestamp(),
            "user_type" to userType,
        )
        db.collection("users").document(uid).set(data, SetOptions.merge()).addOnCompleteListener { task ->
            if (task.isSuccessful) cont.resume(Unit)
            else cont.resumeWithException(task.exception ?: IllegalStateException("No se pudo guardar el perfil."))
        }
    }
}
