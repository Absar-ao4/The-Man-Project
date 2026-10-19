package com.example.fitapp.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.ThemeMode
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.feature.gamification.ui.ProgressCard
import com.example.fitapp.feature.settings.ui.AppearanceSection
import com.example.fitapp.feature.settings.ui.SyncSection

@Composable
fun ProfileScreen(
    onOpenReminders: () -> Unit = {},
    onOpenProgress: () -> Unit = {},
    onOpenArticles: () -> Unit = {},
    onOpenData: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileContent(
        state = state,
        onThemeModeChange = viewModel::setThemeMode,
        onReduceMotionChange = viewModel::setReduceMotion,
        onLogout = viewModel::logout,
        onSyncNow = viewModel::syncNow,
        onRestoreFromCloud = viewModel::restoreFromCloud,
        onOpenReminders = onOpenReminders,
        onOpenProgress = onOpenProgress,
        onOpenArticles = onOpenArticles,
        onOpenData = onOpenData,
    )
}

@Composable
fun ProfileContent(
    state: ProfileUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onReduceMotionChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onSyncNow: () -> Unit = {},
    onRestoreFromCloud: () -> Unit = {},
    onOpenReminders: () -> Unit = {},
    onOpenProgress: () -> Unit = {},
    onOpenArticles: () -> Unit = {},
    onOpenData: () -> Unit = {},
) {
    when (state) {
        ProfileUiState.Loading -> LoadingState()
        is ProfileUiState.Content ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = state.profile.name,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() },
                )

                ManCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle("You")
                    InfoRow("Goal", state.profile.goal.label)
                    InfoRow("Level", state.profile.fitnessLevel.label)
                    InfoRow("Activity", state.profile.activityLevel.label)
                    state.profile.heightCm?.let { InfoRow("Height", "${trim(it)} cm") }
                    state.profile.weightKg?.let { InfoRow("Weight", "${trim(it)} kg") }
                }

                ProgressCard(stats = state.progress, badges = state.badges)

                state.targets?.let { t ->
                    ManCard(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle("Daily targets")
                        InfoRow("Calories", "${t.calories} kcal")
                        InfoRow("Protein", "${t.proteinG} g")
                        InfoRow("Carbs", "${t.carbsG} g")
                        InfoRow("Fat", "${t.fatG} g")
                        InfoRow("Water", "${t.waterGlasses} glasses")
                    }
                }

                ManCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle("More")
                    ManOutlinedButton("Reminders", onClick = onOpenReminders, modifier = Modifier.fillMaxWidth().testTag("profile_reminders"))
                    ManOutlinedButton("Progress and body check", onClick = onOpenProgress, modifier = Modifier.fillMaxWidth().testTag("profile_progress"))
                    ManOutlinedButton("Articles", onClick = onOpenArticles, modifier = Modifier.fillMaxWidth().testTag("profile_articles"))
                    ManOutlinedButton("Reports and data", onClick = onOpenData, modifier = Modifier.fillMaxWidth().testTag("profile_data"))
                }

                AppearanceSection(
                    themeMode = state.themeMode,
                    reduceMotion = state.reduceMotion,
                    onThemeModeChange = onThemeModeChange,
                    onReduceMotionChange = onReduceMotionChange,
                )

                SyncSection(
                    overview = state.sync,
                    onSyncNow = onSyncNow,
                    onRestore = onRestoreFromCloud,
                )

                ManOutlinedButton(
                    text = "Log out",
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth().testTag("profile_logout"),
                )
            }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = ManTheme.colors.muted)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun trim(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
