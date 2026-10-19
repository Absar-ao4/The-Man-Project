package com.example.fitapp.feature.account.data

import android.content.Context
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.account.domain.ProgressReport
import com.example.fitapp.feature.account.domain.ReportBuilder
import com.example.fitapp.feature.account.domain.ReportCsv
import com.example.fitapp.feature.account.domain.ReportFormat
import com.example.fitapp.feature.account.domain.ReportPeriod
import com.example.fitapp.feature.account.domain.ReportRepository
import com.example.fitapp.feature.account.domain.ReportText
import com.example.fitapp.feature.account.domain.ShareableFile
import com.example.fitapp.feature.body.domain.BodyRepository
import com.example.fitapp.feature.nutrition.domain.MealRepository
import com.example.fitapp.feature.profile.domain.DeviceInfo
import com.example.fitapp.feature.profile.domain.ProfileRepository
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.workout.domain.SessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Builds progress reports from what is on the phone, so they work offline. */
@Singleton
class ReportRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val body: BodyRepository,
        private val meals: MealRepository,
        private val sessions: SessionRepository,
        private val profiles: ProfileRepository,
        private val device: DeviceInfo,
    ) : ReportRepository {
        override suspend fun build(
            period: ReportPeriod,
            format: ReportFormat,
        ): AppResult<ShareableFile> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val profile = profiles.observeProfile().first()
                    val zone = runCatching { ZoneId.of(profile?.timezone ?: "UTC") }.getOrDefault(ZoneId.of("UTC"))
                    val to = device.today()
                    val from = to.minusDays(period.days - 1L)
                    val rows =
                        ReportBuilder.daily(
                            from = from,
                            to = to,
                            weights = body.observeWeights().first(),
                            nutrition = meals.observeHistory(from, to).first(),
                            workouts = sessions.observeHistory().first(),
                            zone = zone,
                        )
                    val report = ProgressReport(profile?.name.orEmpty(), to, from, to, profile?.units == Units.IMPERIAL, rows)
                    FileExports.clear(context)
                    val file = File(FileExports.dir(context), ReportText.fileName(to, format))
                    when (format) {
                        ReportFormat.PDF -> PdfReportWriter.write(report, ReportText.title(period), file)
                        ReportFormat.CSV -> file.writeText(ReportCsv.build(rows))
                    }
                    FileExports.shareable(context, file, format.mimeType)
                }.fold(
                    onSuccess = { AppResult.Success(it) },
                    onFailure = { AppResult.Failure(ApiFailure.Unknown("The report could not be created. Please try again.")) },
                )
            }
    }