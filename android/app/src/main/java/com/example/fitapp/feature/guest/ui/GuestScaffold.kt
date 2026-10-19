package com.example.fitapp.feature.guest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.ThemeMode
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManTextButton
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.feature.guest.domain.ExampleMeter
import com.example.fitapp.feature.guest.domain.GuestCatalog
import com.example.fitapp.feature.guest.domain.GuestFeature
import com.example.fitapp.feature.guest.domain.GuestSection
import com.example.fitapp.feature.settings.ui.AppearanceSection
import com.example.fitapp.navigation.MainTab

/**
 * The app as a guest sees it: the same five tabs, but every feature that needs an account is a locked card. Nothing here
 * reads or writes a database or calls the server. Looking and changing the theme work; everything else asks to sign in.
 */
@Composable
fun GuestScaffold(
    onSignIn: () -> Unit,
    viewModel: GuestViewModel = hiltViewModel(),
) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    GuestShell(
        themeMode = appearance.themeMode,
        reduceMotion = appearance.reduceMotion,
        onThemeModeChange = viewModel::setThemeMode,
        onReduceMotionChange = viewModel::setReduceMotion,
        onSignIn = onSignIn,
    )
}

@Composable
fun GuestShell(
    themeMode: ThemeMode,
    reduceMotion: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onReduceMotionChange: (Boolean) -> Unit,
    onSignIn: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(GuestSection.HOME) }
    var locked by remember { mutableStateOf<GuestFeature?>(null) }
    val colors = ManTheme.colors

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { GuestBanner(onSignIn) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                GuestSection.entries.forEach { section ->
                    val tab = section.tab()
                    NavigationBarItem(
                        selected = selected == section,
                        onClick = { selected = section },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                        modifier = Modifier.testTag("guest_tab_${section.name.lowercase()}"),
                        colors =
                            NavigationBarItemDefaults.colors(
                                indicatorColor = colors.accent,
                                selectedIconColor = colors.onAccent,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = colors.muted,
                                unselectedTextColor = colors.muted,
                            ),
                    )
                }
            }
        },
    ) { padding ->
        GuestSectionScreen(
            section = selected,
            themeMode = themeMode,
            reduceMotion = reduceMotion,
            onThemeModeChange = onThemeModeChange,
            onReduceMotionChange = onReduceMotionChange,
            onLockedFeature = { locked = it },
            modifier = Modifier.padding(padding),
        )
    }

    locked?.let { feature ->
        AlertDialog(
            onDismissRequest = { locked = null },
            title = { Text("Sign in to use ${feature.title}") },
            text = { Text(GuestCatalog.lockedMessage(feature)) },
            confirmButton = {
                ManButton(
                    text = "Sign in or create account",
                    onClick = {
                        locked = null
                        onSignIn()
                    },
                    modifier = Modifier.testTag("guest_dialog_signin"),
                )
            },
            dismissButton = { ManTextButton(text = "Not now", onClick = { locked = null }) },
        )
    }
}

@Composable
private fun GuestBanner(onSignIn: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().testTag("guest_banner")) {
        Row(
            modifier = Modifier.statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Guest mode: preview only",
                style = MaterialTheme.typography.labelLarge,
                color = ManTheme.colors.muted,
                modifier = Modifier.weight(1f),
            )
            ManTextButton(text = "Sign in", onClick = onSignIn, modifier = Modifier.testTag("guest_banner_signin"))
        }
    }
}

@Composable
private fun GuestSectionScreen(
    section: GuestSection,
    themeMode: ThemeMode,
    reduceMotion: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onReduceMotionChange: (Boolean) -> Unit,
    onLockedFeature: (GuestFeature) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = GuestCatalog.content(section)
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = content.headline,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = content.intro, style = MaterialTheme.typography.bodyLarge, color = ManTheme.colors.muted)

        if (section == GuestSection.HOME) ExampleDayCard(GuestCatalog.exampleDay)

        content.features.forEach { feature ->
            FeatureCard(feature = feature, onClick = { onLockedFeature(feature) })
        }

        if (section == GuestSection.PROFILE) {
            AppearanceSection(
                themeMode = themeMode,
                reduceMotion = reduceMotion,
                onThemeModeChange = onThemeModeChange,
                onReduceMotionChange = onReduceMotionChange,
            )
        }
    }
}

@Composable
private fun FeatureCard(
    feature: GuestFeature,
    onClick: () -> Unit,
) {
    ManCard(modifier = Modifier.fillMaxWidth().testTag("guest_feature_${feature.title}"), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = feature.title, style = MaterialTheme.typography.titleMedium)
                Text(text = feature.description, style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
            }
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Needs an account",
                tint = ManTheme.colors.muted,
            )
        }
    }
}

@Composable
private fun ExampleDayCard(meters: List<ExampleMeter>) {
    ManCard(modifier = Modifier.fillMaxWidth().testTag("guest_example_day")) {
        SectionTitle("An example day")
        Text(
            text = "Made up numbers, to show how Home looks once you log.",
            style = MaterialTheme.typography.bodyMedium,
            color = ManTheme.colors.muted,
        )
        meters.forEach { meter ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = meter.label, style = MaterialTheme.typography.bodyLarge)
                    Text(text = meter.text, style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
                }
                LinearProgressIndicator(
                    progress = { meter.fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = ManTheme.colors.accent,
                    trackColor = ManTheme.colors.hairline,
                )
            }
        }
    }
}

private fun GuestSection.tab(): MainTab =
    when (this) {
        GuestSection.HOME -> MainTab.HOME
        GuestSection.WORKOUT -> MainTab.WORKOUT
        GuestSection.FOOD -> MainTab.FOOD
        GuestSection.SOCIAL -> MainTab.SOCIAL
        GuestSection.PROFILE -> MainTab.PROFILE
    }