package com.example.fitapp.feature.photoscan.ui

import android.content.ActivityNotFoundException
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManShapes
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManChip
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.ManTextButton
import com.example.fitapp.core.designsystem.components.ManTextField
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.photoscan.domain.ReviewItem
import com.example.fitapp.feature.photoscan.domain.ReviewMath
import com.example.fitapp.feature.photoscan.domain.ScanError
import com.example.fitapp.feature.photoscan.domain.ScanImage
import com.example.fitapp.feature.photoscan.domain.ScanSource
import com.example.fitapp.feature.workout.ui.WorkoutScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PhotoScanActions(
    val onTakePhoto: () -> Unit,
    val onPickPhoto: () -> Unit,
    val onHintChange: (String) -> Unit,
    val onMealType: (MealType) -> Unit,
    val onToggle: (Int) -> Unit,
    val onGrams: (Int, String) -> Unit,
    val onStep: (Int, Int) -> Unit,
    val onSave: () -> Unit,
    val onRetake: () -> Unit,
    val onRetry: () -> Unit,
    val onUpgrade: () -> Unit,
    val onDismissUpgrade: () -> Unit,
    val onDone: () -> Unit,
)

@Composable
fun PhotoScanScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: PhotoScanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Where the camera is writing right now, so the answer can be handed on or the file thrown away.
    var pendingCapture by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            val uri = pendingCapture
            pendingCapture = null
            if (taken && uri != null) {
                viewModel.onPhoto(uri.toString())
            } else if (uri != null) {
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
        }
    val pickPhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) viewModel.onPhoto(uri.toString())
        }

    // A photo left behind by a crash must not stay on the device: clear anything old when the screen opens.
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { clearStaleCaptures(File(context.cacheDir, CAPTURE_DIR)) } }

    val actions =
        PhotoScanActions(
            onTakePhoto = {
                val dir = File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }
                val file = File(dir, "meal_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                pendingCapture = uri
                try {
                    takePicture.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    pendingCapture = null
                    viewModel.onCameraUnavailable()
                }
            },
            onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onHintChange = viewModel::setHint,
            onMealType = viewModel::setMealType,
            onToggle = viewModel::toggleItem,
            onGrams = viewModel::setGrams,
            onStep = viewModel::stepGrams,
            onSave = viewModel::save,
            onRetake = viewModel::retake,
            onRetry = viewModel::retry,
            onUpgrade = viewModel::showUpgradeInfo,
            onDismissUpgrade = viewModel::dismissUpgradeInfo,
            onDone = onDone,
        )

    WorkoutScaffold(title = "Scan a meal", onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            PhotoScanContent(state = state, preview = rememberPreview(state.image), actions = actions)
        }
    }
}

private const val CAPTURE_DIR = "photo_scan"
private const val STALE_AFTER_MS = 60 * 60 * 1000L

private fun clearStaleCaptures(dir: File) {
    val cutoff = System.currentTimeMillis() - STALE_AFTER_MS
    dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { runCatching { it.delete() } }
}

/** The small photo for the review screen, decoded off the main thread. */
@Composable
private fun rememberPreview(image: ScanImage?): ImageBitmap? {
    val preview by produceState<ImageBitmap?>(initialValue = null, image) {
        value =
            if (image == null) {
                null
            } else {
                withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(image.jpeg, 0, image.jpeg.size)?.asImageBitmap()
                }
            }
    }
    return preview
}

@Composable
fun PhotoScanContent(
    state: PhotoScanUiState,
    preview: ImageBitmap?,
    actions: PhotoScanActions,
) {
    when (val stage = state.stage) {
        is PhotoScanStage.Idle -> IdleBody(state, stage, actions)
        is PhotoScanStage.Analyzing -> AnalyzingBody(stage, preview)
        is PhotoScanStage.Review -> ReviewBody(state, stage, preview, actions)
        is PhotoScanStage.Failed -> FailedBody(state, stage, actions)
        is PhotoScanStage.Saved -> SavedBody(stage, actions)
    }
    if (state.showUpgradeInfo) {
        AlertDialog(
            onDismissRequest = actions.onDismissUpgrade,
            title = { Text("More scans with Premium") },
            text = { Text("Premium gives you more photo scans every day. Premium plans are not available to buy yet, so for now the free daily scans reset tomorrow.") },
            confirmButton = { ManTextButton(text = "OK", onClick = actions.onDismissUpgrade) },
        )
    }
}

// ---------------------------------------------------------------- idle

@Composable
private fun IdleBody(
    state: PhotoScanUiState,
    stage: PhotoScanStage.Idle,
    actions: PhotoScanActions,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp).testTag("scan_idle"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Take a photo of your meal",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Get the whole plate in the frame, in good light. We find the foods and estimate the portions, and you check everything before it is logged.",
            style = MaterialTheme.typography.bodyMedium,
            color = ManTheme.colors.muted,
        )
        stage.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = ManTheme.colors.danger,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("scan_message"),
            )
        }
        ManTextField(
            value = state.hint,
            onValueChange = actions.onHintChange,
            label = "What is it? (optional)",
            modifier = Modifier.fillMaxWidth().testTag("scan_hint"),
        )
        ManButton(text = "Take photo", onClick = actions.onTakePhoto, modifier = Modifier.fillMaxWidth().testTag("scan_take"))
        ManOutlinedButton(text = "Choose from gallery", onClick = actions.onPickPhoto, modifier = Modifier.fillMaxWidth().testTag("scan_pick"))
        Text(
            "The photo is shrunk on your phone and sent to be analysed. It is never stored.",
            style = MaterialTheme.typography.bodySmall,
            color = ManTheme.colors.muted,
        )
    }
}

// ---------------------------------------------------------------- analysing

@Composable
private fun AnalyzingBody(
    stage: PhotoScanStage.Analyzing,
    preview: ImageBitmap?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp).testTag("scan_analyzing"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (preview != null) MealPhoto(preview)
        CircularProgressIndicator(color = ManTheme.colors.accent)
        Text(
            text = stage.step.label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("scan_step"),
        )
    }
}

// ---------------------------------------------------------------- review

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewBody(
    state: PhotoScanUiState,
    stage: PhotoScanStage.Review,
    preview: ImageBitmap?,
    actions: PhotoScanActions,
) {
    val total = ReviewMath.total(stage.items)
    val loggable = ReviewMath.loggable(stage.items)
    val kcal = Math.round(total.calories).toInt()
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp).testTag("scan_review"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (preview != null) MealPhoto(preview)

        ManCard(modifier = Modifier.fillMaxWidth()) {
            Text("$kcal kcal", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("scan_total_kcal"))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MacroText("Protein", total.proteinG, ManTheme.colors.protein)
                MacroText("Carbs", total.carbsG, ManTheme.colors.carbs)
                MacroText("Fat", total.fatG, ManTheme.colors.fat)
            }
        }

        SectionTitle("Meal")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MealType.entries.forEach { type ->
                ManChip(label = type.label, selected = state.mealType == type, onClick = { actions.onMealType(type) })
            }
        }

        SectionTitle("What we found")
        stage.items.forEachIndexed { index, row -> ReviewItemCard(index, row, actions) }

        Text(
            "These are estimates and can be off. Change the portions to match what you really ate.",
            style = MaterialTheme.typography.bodySmall,
            color = ManTheme.colors.muted,
        )
        stage.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted) }
        Text(
            text = if (stage.scansLeftToday == 1) "1 scan left today" else "${stage.scansLeftToday} scans left today",
            style = MaterialTheme.typography.bodySmall,
            color = ManTheme.colors.muted,
        )
        stage.error?.let {
            Text(
                it,
                color = ManTheme.colors.danger,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("scan_save_error"),
            )
        }
        ManButton(
            text = "Log $kcal kcal",
            onClick = actions.onSave,
            enabled = loggable.isNotEmpty() && !stage.saving,
            loading = stage.saving,
            modifier = Modifier.fillMaxWidth().testTag("scan_log"),
        )
        ManOutlinedButton(
            text = "Retake photo",
            onClick = actions.onRetake,
            enabled = !stage.saving,
            modifier = Modifier.fillMaxWidth().testTag("scan_retake"),
        )
    }
}

@Composable
private fun ReviewItemCard(
    index: Int,
    row: ReviewItem,
    actions: PhotoScanActions,
) {
    val item = row.item
    val grams = row.grams
    val macros = row.macros
    ManCard(modifier = Modifier.fillMaxWidth().testTag("scan_item_$index")) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = row.included, role = Role.Checkbox, onValueChange = { actions.onToggle(index) })
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = row.included, onCheckedChange = null)
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (item.source == ScanSource.USDA_MATCH) "USDA match: ${item.matchedName}" else "AI estimate",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.source == ScanSource.USDA_MATCH) ManTheme.colors.success else ManTheme.colors.muted,
                    modifier = Modifier.testTag("scan_source_$index"),
                )
            }
        }
        if (item.lowConfidence) {
            Text(
                "Not sure about this one. Please check the name and the portion.",
                style = MaterialTheme.typography.bodySmall,
                color = ManTheme.colors.danger,
                modifier = Modifier.testTag("scan_low_$index"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ManOutlinedButton(
                text = "-${ReviewMath.STEP_GRAMS}",
                onClick = { actions.onStep(index, -ReviewMath.STEP_GRAMS) },
                enabled = row.included,
                modifier = Modifier.semantics { contentDescription = "Remove ${ReviewMath.STEP_GRAMS} grams from ${item.name}" }.testTag("scan_minus_$index"),
            )
            ManTextField(
                value = row.gramsText,
                onValueChange = { actions.onGrams(index, it) },
                label = "Portion",
                suffix = "g",
                enabled = row.included,
                error = if (grams == null) "0.1 to 10,000 g" else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("scan_grams_$index"),
            )
            ManOutlinedButton(
                text = "+${ReviewMath.STEP_GRAMS}",
                onClick = { actions.onStep(index, ReviewMath.STEP_GRAMS) },
                enabled = row.included,
                modifier = Modifier.semantics { contentDescription = "Add ${ReviewMath.STEP_GRAMS} grams to ${item.name}" }.testTag("scan_plus_$index"),
            )
        }
        Text(
            text =
                if (macros != null) {
                    "${Math.round(macros.calories)} kcal · P ${Math.round(macros.proteinG)} C ${Math.round(macros.carbsG)} F ${Math.round(macros.fatG)}"
                } else {
                    "Enter a portion to see the nutrition."
                },
            style = MaterialTheme.typography.bodySmall,
            color = ManTheme.colors.muted,
            modifier = Modifier.testTag("scan_macros_$index"),
        )
    }
}

@Composable
private fun MacroText(
    label: String,
    grams: Double,
    color: androidx.compose.ui.graphics.Color,
) {
    Column {
        Text("${Math.round(grams)} g", style = MaterialTheme.typography.titleMedium, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted)
    }
}

@Composable
private fun MealPhoto(preview: ImageBitmap) {
    Image(
        bitmap = preview,
        contentDescription = "Your meal photo",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().height(200.dp).clip(ManShapes.Card).testTag("scan_photo"),
    )
}

// ---------------------------------------------------------------- failed and saved

@Composable
private fun FailedBody(
    state: PhotoScanUiState,
    stage: PhotoScanStage.Failed,
    actions: PhotoScanActions,
) {
    val canRetry = stage.error != ScanError.LIMIT_REACHED && stage.error != ScanError.SIGNED_OUT && stage.error != ScanError.INVALID_IMAGE
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp).testTag("scan_failed"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ManCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "That did not work",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stage.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("scan_error_message"),
            )
        }
        if (canRetry && state.image != null) {
            ManButton(text = "Try again", onClick = actions.onRetry, modifier = Modifier.fillMaxWidth().testTag("scan_retry"))
        }
        if (stage.error == ScanError.LIMIT_REACHED && !state.premium) {
            ManButton(text = "See upgrade options", onClick = actions.onUpgrade, modifier = Modifier.fillMaxWidth().testTag("scan_upgrade"))
        }
        ManOutlinedButton(text = "Take a new photo", onClick = actions.onRetake, modifier = Modifier.fillMaxWidth().testTag("scan_new_photo"))
        ManTextButton(text = "Back to food log", onClick = actions.onDone, modifier = Modifier.testTag("scan_back_to_log"))
    }
}

@Composable
private fun SavedBody(
    stage: PhotoScanStage.Saved,
    actions: PhotoScanActions,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp).testTag("scan_saved"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (stage.count == 1) "Logged 1 food" else "Logged ${stage.count} foods",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
        )
        Text("${stage.calories} kcal added to your food log.", style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
        ManButton(text = "Done", onClick = actions.onDone, modifier = Modifier.fillMaxWidth().testTag("scan_done"))
        ManOutlinedButton(text = "Scan another meal", onClick = actions.onRetake, modifier = Modifier.fillMaxWidth().testTag("scan_another"))
    }
}
