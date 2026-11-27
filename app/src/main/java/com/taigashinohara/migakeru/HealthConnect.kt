package com.taigashinohara.migakeru

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.ZoneId

/**
 * v0.11.0: thin read-only wrapper over Health Connect. Every call is
 * guarded — a missing provider, a revoked permission or a failed query
 * degrades to "no data", never to a crash. The app only ever reads.
 */
class HealthConnectSource(private val context: Context) {

    companion object {
        /** The read permissions the auto-check shelf asks for. */
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(FloorsClimbedRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(MindfulnessSessionRecord::class),
        )
    }

    fun isAvailable(): Boolean = runCatching {
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }.getOrDefault(false)

    fun clientOrNull(): HealthConnectClient? =
        if (isAvailable()) runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() else null

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    suspend fun grantedPermissions(client: HealthConnectClient): Set<String> =
        runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    /** One day of facts. Each metric fails soft on its own. */
    suspend fun factsFor(client: HealthConnectClient, date: LocalDate): MigakeruLogic.HealthFacts {
        val zone = ZoneId.systemDefault()
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        val dayRange = TimeRangeFilter.between(dayStart, dayEnd)

        val steps = runCatching {
            client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), dayRange))
                .get(StepsRecord.COUNT_TOTAL) ?: 0L
        }.getOrDefault(0L)
        val floors = runCatching {
            client.aggregate(AggregateRequest(setOf(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL), dayRange))
                .get(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL) ?: 0.0
        }.getOrDefault(0.0)
        val hasWeight = runCatching {
            client.readRecords(ReadRecordsRequest(WeightRecord::class, dayRange)).records.isNotEmpty()
        }.getOrDefault(false)
        val hasExercise = runCatching {
            client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, dayRange)).records.isNotEmpty()
        }.getOrDefault(false)
        val hasMindfulness = runCatching {
            client.readRecords(ReadRecordsRequest(MindfulnessSessionRecord::class, dayRange)).records.isNotEmpty()
        }.getOrDefault(false)
        // Sleep: a session that started on the date itself (before midnight)
        // proves the evening wind-down. Look at noon-to-noon so the night
        // that belongs to the date is fully inside the window.
        val sleepWindow = TimeRangeFilter.between(
            date.atTime(12, 0).atZone(zone).toInstant(),
            date.plusDays(1).atTime(12, 0).atZone(zone).toInstant(),
        )
        val sleptBeforeMidnight = runCatching {
            client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, sleepWindow))
                .records.any { it.startTime.atZone(zone).toLocalDate() == date }
        }.getOrDefault(false)

        return MigakeruLogic.HealthFacts(
            steps = steps.toInt(),
            floors = floors,
            hasWeight = hasWeight,
            hasExercise = hasExercise,
            hasMindfulness = hasMindfulness,
            sleptBeforeMidnight = sleptBeforeMidnight,
        )
    }
}
