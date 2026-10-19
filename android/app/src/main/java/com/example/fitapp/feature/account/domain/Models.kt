package com.example.fitapp.feature.account.domain

import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.body.domain.UnitFormat
import com.example.fitapp.feature.body.domain.WeightEntry
import com.example.fitapp.feature.nutrition.domain.DayTotals
import com.example.fitapp.feature.workout.domain.SessionSummary
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

enum class ReportFormat(val label: String, val mimeType: String, val extension: String) {
    PDF("PDF", "application/pdf", "pdf"),
    CSV("CSV", "text/csv", "csv"),
}

/** How far back a report goes. */
enum class ReportPeriod(val label: String, val days: Int) {
    MONTH("30 days", 30),
    QUARTER("90 days", 90),
    YEAR("1 year", 365),
}

/** A file that is ready to be handed to another app. [uri] is a content address from the app's file provider. */
data class ShareableFile(
    val uri: String,
    val mimeType: String,
    val fileName: String,
)

/** One day of the report. A value is null when nothing was logged for it that day. */
data class DailyRow(
    val date: LocalDate,
    val weightKg: Double?,
    val calories: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val waterGlasses: Int?,
    val workouts: Int,
    val sets: Int,
    val volumeKg: Double,
)

/** Everything a report says, worked out from what is on the phone. */
data class ProgressReport(
    val name: String,
    val generatedOn: LocalDate,
    val from: LocalDate,
    val to: LocalDate,
    val imperial: Boolean,
    val rows: List<DailyRow>,
) {
    val workouts: Int get() = rows.sumOf { it.workouts }
    val sets: Int get() = rows.sumOf { it.sets }
    val volumeKg: Double get() = rows.sumOf { it.volumeKg }
    val weights: List<Pair<LocalDate, Double>> get() = rows.mapNotNull { r -> r.weightKg?.let { r.date to it } }
    val loggedFoodDays: Int get() = rows.count { it.calories != null }
    val averageCalories: Double? get() = rows.mapNotNull { it.calories }.takeIf { it.isNotEmpty() }?.average()
    val averageProtein: Double? get() = rows.mapNotNull { it.proteinG }.takeIf { it.isNotEmpty() }?.average()

    /** First to last weigh-in in the period, or null with fewer than two. */
    val weightChangeKg: Double?
        get() = weights.takeIf { it.size >= 2 }?.let { it.last().second - it.first().second }
}

/** Builds the daily rows. Pure, so the arithmetic can be tested. */
object ReportBuilder {
    fun daily(
        from: LocalDate,
        to: LocalDate,
        weights: List<WeightEntry>,
        nutrition: List<DayTotals>,
        workouts: List<SessionSummary>,
        zone: ZoneId,
    ): List<DailyRow> {
        val weightByDay = weights.filter { it.date in from..to }.associateBy { it.date }
        val nutritionByDay = nutrition.filter { it.date in from..to }.associateBy { it.date }
        val workoutsByDay = workouts.groupBy { it.startedAt.atZone(zone).toLocalDate() }.filterKeys { it in from..to }
        val dates = (weightByDay.keys + nutritionByDay.keys + workoutsByDay.keys).sorted()
        return dates.map { date ->
            val food = nutritionByDay[date]
            val sessions = workoutsByDay[date].orEmpty()
            DailyRow(
                date = date,
                weightKg = weightByDay[date]?.weightKg,
                calories = food?.macros?.calories,
                proteinG = food?.macros?.proteinG,
                carbsG = food?.macros?.carbsG,
                fatG = food?.macros?.fatG,
                waterGlasses = food?.waterGlasses,
                workouts = sessions.size,
                sets = sessions.sumOf { it.completedSets },
                volumeKg = sessions.sumOf { it.totalVolumeKg },
            )
        }
    }
}

/** The CSV file: one row per day, metric units in the column names, ready for a spreadsheet. */
object ReportCsv {
    const val HEADER = "date,weight_kg,calories,protein_g,carbs_g,fat_g,water_glasses,workouts,sets,volume_kg"

    fun build(rows: List<DailyRow>): String =
        buildString {
            append(HEADER).append("\r\n")
            rows.forEach { r ->
                append(
                    listOf(
                        r.date.toString(),
                        number(r.weightKg, 1),
                        number(r.calories, 0),
                        number(r.proteinG, 1),
                        number(r.carbsG, 1),
                        number(r.fatG, 1),
                        r.waterGlasses?.toString().orEmpty(),
                        r.workouts.toString(),
                        r.sets.toString(),
                        number(r.volumeKg, 1),
                    ).joinToString(","),
                ).append("\r\n")
            }
        }

    /** A plain decimal with a point and no thousands separator, or empty for a missing value. */
    internal fun number(
        value: Double?,
        decimals: Int,
    ): String = if (value == null) "" else String.format(Locale.US, "%.${decimals}f", value)
}

/** Words for the PDF and the sharing sheet. */
object ReportText {
    fun title(period: ReportPeriod): String = "Progress report, last ${period.label}"

    fun fileName(
        generatedOn: LocalDate,
        format: ReportFormat,
    ): String = "progress-report-$generatedOn.${format.extension}"

    fun weight(
        kg: Double,
        imperial: Boolean,
    ): String = UnitFormat.weight(kg, imperial)


    val TABLE_HEADER = listOf("Date", "Weight", "Calories", "Protein", "Workouts", "Sets", "Volume")

    /** One row of the PDF table. Blank where nothing was logged. */
    fun cells(
        row: DailyRow,
        imperial: Boolean,
    ): List<String> =
        listOf(
            row.date.toString(),
            row.weightKg?.let { UnitFormat.weight(it, imperial) }.orEmpty(),
            row.calories?.let { Math.round(it).toString() }.orEmpty(),
            row.proteinG?.let { "${Math.round(it)} g" }.orEmpty(),
            if (row.workouts == 0) "" else row.workouts.toString(),
            if (row.sets == 0) "" else row.sets.toString(),
            if (row.volumeKg <= 0.0) "" else volume(row.volumeKg, imperial),
        )

    fun volume(
        kg: Double,
        imperial: Boolean,
    ): String {
        val value = if (imperial) UnitFormat.kgToLb(kg) else kg
        return Math.round(value).toString() + if (imperial) " lb" else " kg"
    }
    /** "-1.2 kg" or "No change data yet". */
    fun change(
        kg: Double?,
        imperial: Boolean,
    ): String = kg?.let { UnitFormat.signedWeight(it, imperial) } ?: "Log two weigh-ins to see a change"
}

interface AccountRepository {
    /** Downloads everything the server holds for the account into a JSON file kept in the app's cache. */
    suspend fun exportData(): AppResult<ShareableFile>

    /** Permanently deletes the account on the server (it needs the password), then clears everything on this phone. */
    suspend fun deleteAccount(password: String): AppResult<Unit>
}

interface ReportRepository {
    /** Builds the report from what is on the phone, so it works offline. */
    suspend fun build(
        period: ReportPeriod,
        format: ReportFormat,
    ): AppResult<ShareableFile>
}