package com.bioform.ai

import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Direccion del backend publicado en Render. */
object BackendConfig {
    const val BASE_URL = "https://bioform-api.onrender.com"
}

data class TrainingAdvice(val intensity: String, val pre: String, val during: String, val post: String)
data class NutritionAdvice(val summary: String, val suggestions: List<String>, val hydration: String)

/** Respuesta del backend: reglas de salud + consejo de Gemini. */
data class AdviceResult(
    val level: String,          // "none" | "warning" | "alert"
    val blockTraining: Boolean,
    val ruleMessage: String,
    val adviceStatus: String,   // "ok" | "blocked" | "unavailable"
    val training: TrainingAdvice?,
    val nutrition: NutritionAdvice?,
)

/** Envia el resumen del dia al backend (POST /advice) con el token de Firebase. */
class BackendApi {

    suspend fun requestAdvice(user: FirebaseUser, s: DailySummary): AdviceResult {
        val restingHr = s.restingHr
            ?: throw IllegalStateException("No hay frecuencia cardíaca en reposo para enviar.")
        val token = getToken(user)

        return withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("day", s.day.toString())
                put("resting_hr", restingHr)
                put("at_rest", true)
                s.steps?.let { put("steps", it) }
                s.sleepHours?.let { put("sleep_hours", it) }
                s.calories?.let { put("calories", it) }
            }

            val conn = (URL("${BackendConfig.BASE_URL}/advice").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 90_000  // Render Free puede tardar ~50 s en despertar
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
            }

            try {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (code !in 200..299) throw IllegalStateException("El servidor respondió $code: $text")
                parse(JSONObject(text))
            } finally {
                conn.disconnect()
            }
        }
    }

    /** Obtiene el token de sesion de Firebase que verifica el backend. */
    private suspend fun getToken(user: FirebaseUser): String = suspendCancellableCoroutine { cont ->
        user.getIdToken(false).addOnCompleteListener { task ->
            val token = if (task.isSuccessful) task.result?.token else null
            if (token != null) cont.resume(token)
            else cont.resumeWithException(task.exception ?: IllegalStateException("No se pudo obtener el token."))
        }
    }

    private fun parse(json: JSONObject): AdviceResult {
        val rule = json.getJSONObject("rule")
        val advice = json.optJSONObject("advice")
        val training = advice?.getJSONObject("training")?.let {
            TrainingAdvice(
                intensity = it.getString("intensity"),
                pre = it.getString("pre_workout"),
                during = it.getString("during_workout"),
                post = it.getString("post_workout"),
            )
        }
        val nutrition = advice?.getJSONObject("nutrition")?.let { n ->
            val list = n.getJSONArray("suggestions")
            NutritionAdvice(
                summary = n.getString("summary"),
                suggestions = List(list.length()) { i -> list.getString(i) },
                hydration = n.getString("hydration"),
            )
        }
        return AdviceResult(
            level = rule.getString("level"),
            blockTraining = rule.getBoolean("block_training"),
            ruleMessage = rule.getString("message"),
            adviceStatus = json.getString("advice_status"),
            training = training,
            nutrition = nutrition,
        )
    }
}