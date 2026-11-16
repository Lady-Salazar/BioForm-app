package com.bioform.ai

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.reflect.KClass

/** Resumen diario que la app enviara al backend. */
data class DailySummary(
    val day: LocalDate,
    val restingHr: Int?,
    val restingHrEstimated: Boolean,  // true si se calculo a partir de la FC continua
    val steps: Long?,
    val sleepHours: Double?,
    val calories: Int?,
)

/** Lee los datos de salud del dia desde Health Connect. */
class HealthRepository(private val context: Context) {

    /** Permisos de lectura que necesita BioForm AI. */
    val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    )

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    /** Indica si Health Connect esta instalado y disponible en el telefono. */
    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    suspend fun hasAllPermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(permissions)

    suspend fun readToday(): DailySummary {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfDay = today.atStartOfDay(zone).toInstant()
        val now = Instant.now()
        val last24h = now.minus(Duration.ofHours(24))

        // Pasos y calorias de hoy (Health Connect suma los datos de todas las apps)
        val totals = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL, TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
            )
        )
        val steps = totals[StepsRecord.COUNT_TOTAL]
        val calories = totals[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.roundToInt()

        // Sueno de anoche: sesiones desde las 18:00 de ayer hasta ahora
        val sleepSessions = readAll(
            SleepSessionRecord::class,
            TimeRangeFilter.between(startOfDay.minus(Duration.ofHours(6)), now),
        )
        val sleepMinutes = sleepSessions.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
        val sleepHours = if (sleepMinutes > 0) sleepMinutes / 60.0 else null

        // FC en reposo: primero el valor que calcula el reloj
        val resting = readAll(RestingHeartRateRecord::class, TimeRangeFilter.between(last24h, now))
            .maxByOrNull { it.time }
            ?.beatsPerMinute?.toInt()

        if (resting != null) {
            return DailySummary(today, resting, false, steps, sleepHours, calories)
        }

        // Si el reloj no la entrega: se estima con el 10% de lecturas mas bajas de las ultimas 24 h
        val samples = readAll(HeartRateRecord::class, TimeRangeFilter.between(last24h, now))
            .flatMap { record -> record.samples.map { it.beatsPerMinute } }
            .sorted()
        val estimated = if (samples.isNotEmpty()) {
            samples.take(maxOf(1, samples.size / 10)).average().roundToInt()
        } else null

        return DailySummary(today, estimated, estimated != null, steps, sleepHours, calories)
    }

    /** Lee todas las paginas de registros de un tipo. */
    private suspend fun <T : Record> readAll(type: KClass<T>, filter: TimeRangeFilter): List<T> {
        val all = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(recordType = type, timeRangeFilter = filter, pageToken = token)
            )
            all += response.records
            token = response.pageToken
        } while (!token.isNullOrEmpty())
        return all
    }
}