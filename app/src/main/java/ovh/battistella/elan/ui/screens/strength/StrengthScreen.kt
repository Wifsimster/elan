package ovh.battistella.elan.ui.screens.strength

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.elan.R
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.TEMPLATES
import ovh.battistella.elan.domain.WorkoutTemplate
import ovh.battistella.elan.domain.difficultyLabel
import ovh.battistella.elan.domain.fmtKg
import ovh.battistella.elan.domain.formatDuration
import ovh.battistella.elan.ui.components.ButtonVariant
import ovh.battistella.elan.ui.components.ExerciseCatalog
import ovh.battistella.elan.ui.components.ExerciseInfo
import ovh.battistella.elan.ui.components.ExerciseInfoSheet
import ovh.battistella.elan.ui.components.PulseButton
import ovh.battistella.elan.ui.components.PulseCard
import ovh.battistella.elan.ui.components.PulseChip
import ovh.battistella.elan.ui.components.RestTimerBar
import ovh.battistella.elan.ui.components.Stepper
import ovh.battistella.elan.ui.components.pressableScale
import ovh.battistella.elan.ui.components.screenContent
import ovh.battistella.elan.ui.haptics.HapticKind
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.screens.common.ConfirmDialog
import ovh.battistella.elan.ui.screens.common.HeaderAction
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.Elevation
import ovh.battistella.elan.ui.theme.PulseType
import ovh.battistella.elan.ui.theme.Radius
import kotlin.math.roundToInt

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Séance de musculation : résumé, sélecteur de programme (séance vide),
 * cartes d'exercices (séries cochables, steppers, ressenti), carte d'ajout
 * (catalogue plein écran, saisie libre, chips rapides), minuteur de repos et
 * barre de contrôle flottants. Écran allumé ; retour matériel = croix.
 */
@Composable
fun StrengthScreen(
    contentPadding: PaddingValues,
    viewModel: StrengthViewModel = hiltViewModel(),
    onExit: () -> Unit = {},
    onSaved: (Long) -> Unit = {},
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = ElanTheme.colors
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onNotificationPermission()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is StrengthEvent.Saved -> onSaved(event.sessionId)
                StrengthEvent.Exit -> onExit()
                StrengthEvent.RequestNotificationPermission -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }

    // Retour matériel = même chemin que la croix.
    BackHandler { viewModel.requestExit() }

    // Brouillon réécrit au passage en arrière-plan (chrono live + FC).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStop() }

    // Écran allumé pendant la séance.
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 200.dp)
                .screenContent(),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                HeaderAction(icon = MdiIcons.Close, label = stringResource(R.string.muscu_quit), onClick = viewModel::requestExit, size = 26.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(MdiIcons.Dumbbell), contentDescription = null, tint = colors.muscu, modifier = Modifier.size(22.dp))
                    Text(stringResource(R.string.muscu_title), style = PulseType.headline, color = colors.text)
                }
                Spacer(Modifier.width(38.dp))
            }

            SummaryCard(ui)

            if (ui.hydrated && ui.exercises.isEmpty()) {
                TemplatePicker(onPick = viewModel::loadTemplate)
            }

            ui.exercises.forEach { ex ->
                key(ex.id) {
                    ExerciseCard(ex = ex, viewModel = viewModel)
                }
            }

            AddExerciseCard(
                onBrowse = { viewModel.setCatalogOpen(true) },
                onAdd = viewModel::addExercise,
            )
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            // Remonté à chaque (re)programmation pour repartir d'un état frais.
            key(ui.restEndsAt) {
                RestTimerBar(
                    endsAt = ui.restEndsAt,
                    onChange = viewModel::onRestChange,
                    onAdjustPreference = viewModel::adjustRestPreference,
                    now = viewModel::nowMs,
                )
            }
            ControlBar(
                paused = ui.paused,
                saving = ui.saving,
                bottomInset = contentPadding.calculateBottomPadding(),
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onFinish = viewModel::requestFinish,
            )
        }
    }

    ExerciseInfoSheet(
        exercise = ui.infoExercise?.let { ExerciseInfo(it.name, it.icon, it.imageKey, it.muscles, it.howTo) },
        onClose = { viewModel.showInfo(null) },
    )

    if (ui.catalogOpen) {
        CatalogDialog(ui = ui, viewModel = viewModel)
    }

    StrengthDialogs(ui.dialog, viewModel)
}

@Composable
private fun StrengthDialogs(dialog: StrengthDialog, viewModel: StrengthViewModel) {
    val colors = ElanTheme.colors
    when (dialog) {
        StrengthDialog.None -> Unit
        StrengthDialog.Finish -> ConfirmDialog(
            title = stringResource(R.string.muscu_finish_title),
            text = stringResource(R.string.muscu_finish_text),
            confirmLabel = stringResource(R.string.muscu_finish),
            confirmColor = colors.accent,
            onConfirm = viewModel::confirmFinish,
            onDismiss = viewModel::dismissDialog,
        )
        StrengthDialog.EmptyFinish -> InfoDialog(R.string.muscu_empty_title, R.string.muscu_empty_text, viewModel::dismissDialog)
        StrengthDialog.SaveFailed -> InfoDialog(R.string.muscu_save_failed_title, R.string.muscu_save_failed_text, viewModel::dismissDialog)
        StrengthDialog.Exit -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(R.string.muscu_exit_title)) },
            text = { Text(stringResource(R.string.muscu_exit_text)) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = viewModel::pauseAndExit) { Text(stringResource(R.string.muscu_exit_pause)) }
                    TextButton(onClick = viewModel::abandon) { Text(stringResource(R.string.muscu_exit_abandon), color = colors.danger) }
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.common_continue)) } },
        )
        is StrengthDialog.RemoveExercise -> ConfirmDialog(
            title = stringResource(R.string.muscu_remove_exercise_title),
            text = stringResource(R.string.muscu_remove_exercise_text, dialog.name),
            confirmLabel = stringResource(R.string.common_delete),
            confirmColor = colors.danger,
            onConfirm = { viewModel.confirmRemoveExercise(dialog.id) },
            onDismiss = viewModel::dismissDialog,
            dismissLabel = stringResource(R.string.common_cancel),
        )
        is StrengthDialog.RemoveSet -> ConfirmDialog(
            title = stringResource(R.string.muscu_remove_set_title),
            text = stringResource(R.string.muscu_remove_set_text),
            confirmLabel = stringResource(R.string.common_delete),
            confirmColor = colors.danger,
            onConfirm = { viewModel.confirmRemoveSet(dialog.id, dialog.index) },
            onDismiss = viewModel::dismissDialog,
            dismissLabel = stringResource(R.string.common_cancel),
        )
    }
}

@Composable
private fun InfoDialog(title: Int, text: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(text)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) } },
    )
}

/** Résumé Durée / Séries done/total / Cardio / Volume. */
@Composable
private fun SummaryCard(ui: StrengthUi) {
    val colors = ElanTheme.colors
    val stats = ui.stats
    PulseCard {
        Row(horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxWidth()) {
            Summary(stringResource(R.string.muscu_duration), formatDuration(ui.elapsedSec))
            Summary(stringResource(R.string.muscu_sets), if (stats.totalSets > 0) "${stats.doneSets}/${stats.totalSets}" else "0")
            Summary(stringResource(R.string.muscu_cardio), ui.bpm?.toString() ?: "—", colors.heart)
            Summary(stringResource(R.string.muscu_volume), "${stats.totalVolume.roundToInt()} kg", colors.muscu)
        }
    }
}

@Composable
private fun Summary(label: String, value: String, color: Color? = null) {
    val colors = ElanTheme.colors
    val density = LocalDensity.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.2f))) {
            Text(value, style = PulseType.metric.copy(fontSize = 20.sp), color = color ?: colors.text, maxLines = 1)
        }
        Text(label, style = PulseType.caption, color = colors.textSecondary)
    }
}

/** Sélecteur de programme (séance vide) : tuiles sur deux colonnes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplatePicker(onPick: (WorkoutTemplate) -> Unit) {
    val colors = ElanTheme.colors
    PulseCard {
        Text(stringResource(R.string.muscu_load_program), style = PulseType.subtitle, color = colors.text)
        Text(stringResource(R.string.muscu_load_program_hint), style = PulseType.caption, color = colors.textSecondary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TEMPLATES.forEach { t ->
                val shape = RoundedCornerShape(Radius.sm)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .pressableScale(haptic = HapticKind.Light, onClick = { onPick(t) })
                        .background(colors.backgroundElement, shape)
                        .border(1.dp, colors.muscu, shape)
                        .clip(shape)
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                ) {
                    Text(t.name, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold), color = colors.muscu, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.day, style = TextStyle(fontSize = 11.sp), color = colors.textSecondary)
                }
            }
        }
    }
}

/** Carte d'un exercice : en-tête, séries, « Ajouter une série », ressenti. */
@Composable
private fun ExerciseCard(ex: StrengthExercise, viewModel: StrengthViewModel) {
    val colors = ElanTheme.colors
    PulseCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(ex.name, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold), color = colors.text)
                if (ex.target != null || ex.lastWeight != null) {
                    val parts = listOfNotNull(
                        ex.target?.let { stringResource(R.string.muscu_target, it) },
                        ex.lastWeight?.let { stringResource(R.string.muscu_last_time, fmtKg(it)) },
                    )
                    Text(parts.joinToString("  ·  "), style = TextStyle(fontSize = 12.sp), color = colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
                }
                if (ex.bump != 0.0) {
                    Text(
                        bumpHint(ex.bump, ex.bumpKind),
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = colors.muscu,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (!ex.howTo.isNullOrEmpty()) {
                HeaderAction(
                    icon = MdiIcons.InformationOutline,
                    label = stringResource(R.string.muscu_how_to_a11y, ex.name),
                    tint = colors.muscu,
                    onClick = { viewModel.showInfo(ex) },
                )
            }
            HeaderAction(
                icon = MdiIcons.TrashCanOutline,
                label = stringResource(R.string.muscu_remove_exercise_a11y, ex.name),
                tint = colors.textSecondary,
                size = 20.dp,
                onClick = { viewModel.requestRemoveExercise(ex.id) },
            )
        }

        ex.sets.forEachIndexed { i, s ->
            SetRowView(
                index = i,
                set = s,
                repUnit = ex.repUnit,
                onToggle = { viewModel.toggleSet(ex.id, i) },
                onReps = { viewModel.setReps(ex.id, i, it) },
                onWeight = { viewModel.setWeight(ex.id, i, it) },
                onRemove = { viewModel.requestRemoveSet(ex.id, i) },
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.pressableScale(onClick = { viewModel.addSet(ex.id) }).padding(vertical = 4.dp),
        ) {
            Icon(painter = painterResource(MdiIcons.PlusCircleOutline), contentDescription = null, tint = colors.muscu, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.muscu_add_set), style = TextStyle(fontWeight = FontWeight.Bold), color = colors.muscu)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.muscu_feeling), style = PulseType.label, color = colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { d ->
                    PulseChip(
                        label = difficultyLabel(d),
                        selected = ex.difficulty == d,
                        color = colors.muscu,
                        onClick = { viewModel.setDifficulty(ex.id, d) },
                    )
                }
            }
        }
    }
}

/** Une série : cercle cochable, steppers reps et charge (atténués si cochée), suppression. */
@Composable
private fun SetRowView(
    index: Int,
    set: SetRow,
    repUnit: String,
    onToggle: () -> Unit,
    onReps: (Int) -> Unit,
    onWeight: (Double) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = ElanTheme.colors
    val toggleLabel = stringResource(R.string.muscu_set_toggle_a11y, index + 1)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .semantics { contentDescription = toggleLabel }
                .pressableScale(scaleTo = 0.85f, haptic = HapticKind.Success, onClick = onToggle)
                .size(26.dp)
                .background(if (set.done) colors.muscu else Color.Transparent, CircleShape)
                .then(if (set.done) Modifier else Modifier.border(1.5.dp, colors.border, CircleShape)),
        ) {
            if (set.done) {
                Icon(painter = painterResource(MdiIcons.Check), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            } else {
                Text("${index + 1}", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"), color = colors.textSecondary)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f).alpha(if (set.done) 0.45f else 1f),
        ) {
            Stepper(
                value = set.reps.toDouble(),
                suffix = repUnit,
                step = 1.0,
                min = 1.0,
                onChange = { onReps(it.roundToInt()) },
                modifier = Modifier.weight(1f),
            )
            Stepper(
                value = set.weightKg,
                suffix = "kg",
                step = 2.5,
                min = 0.0,
                decimal = true,
                onChange = onWeight,
                modifier = Modifier.weight(1f),
            )
        }
        HeaderAction(
            icon = MdiIcons.CloseCircleOutline,
            label = stringResource(R.string.muscu_remove_set_a11y, index + 1),
            tint = colors.textSecondary,
            size = 20.dp,
            onClick = onRemove,
        )
    }
}

/** Carte d'ajout : catalogue, champ libre + bouton, chips rapides. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddExerciseCard(onBrowse: () -> Unit, onAdd: (String) -> Unit) {
    val colors = ElanTheme.colors
    var draft by rememberSaveable { mutableStateOf("") }
    val submit = {
        onAdd(draft)
        draft = ""
    }
    PulseCard {
        Text(stringResource(R.string.muscu_add_exercise), style = PulseType.subtitle, color = colors.text)
        PulseButton(
            title = stringResource(R.string.muscu_browse_catalog),
            icon = MdiIcons.ViewGridOutline,
            color = colors.muscu,
            onClick = onBrowse,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.muscu_catalog_hint), style = PulseType.caption, color = colors.textSecondary)
        Text(stringResource(R.string.muscu_or_type), style = PulseType.caption, color = colors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val shape = RoundedCornerShape(Radius.sm)
            val placeholder = stringResource(R.string.muscu_exercise_name)
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f).semantics { contentDescription = placeholder },
                decorationBox = { inner ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.background, shape)
                            .border(1.dp, colors.border, shape)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        if (draft.isEmpty()) Text(placeholder, style = TextStyle(fontSize = 15.sp), color = colors.textMuted)
                        inner()
                    }
                },
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .pressableScale(haptic = HapticKind.Light, onClick = submit)
                    .background(colors.muscu, shape)
                    .clip(shape)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(
                    painter = painterResource(MdiIcons.Plus),
                    contentDescription = stringResource(R.string.muscu_add_exercise_a11y),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            COMMON_EXERCISES.forEach { name ->
                PulseChip(label = name, selected = false, color = colors.muscu, onClick = { onAdd(name) })
            }
        }
    }
}

/** Magasin d'exercices en modale plein écran. */
@Composable
private fun CatalogDialog(ui: StrengthUi, viewModel: StrengthViewModel) {
    val colors = ElanTheme.colors
    Dialog(
        onDismissRequest = { viewModel.setCatalogOpen(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .padding(top = 8.dp)
                .screenContent(),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(MdiIcons.ViewGridOutline), contentDescription = null, tint = colors.muscu, modifier = Modifier.size(22.dp))
                    Text(stringResource(R.string.muscu_catalog), style = PulseType.headline, color = colors.text)
                }
                HeaderAction(icon = MdiIcons.Close, label = stringResource(R.string.muscu_catalog_close), onClick = { viewModel.setCatalogOpen(false) }, size = 26.dp)
            }
            ExerciseCatalog(
                profile = ui.recoProfile,
                addedNames = ui.addedNames,
                onPick = viewModel::addCatalogExercise,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Barre de contrôle flottante : Pause/Reprendre (flex 1) + Terminer (flex 1.4). */
@Composable
private fun ControlBar(
    paused: Boolean,
    saving: Boolean,
    bottomInset: Dp,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
) {
    val colors = ElanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(Elevation.lg)
            .background(colors.backgroundElement)
            .padding(bottom = bottomInset),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.screenContent().padding(vertical = 12.dp),
        ) {
            PulseButton(
                title = stringResource(if (paused) R.string.muscu_resume else R.string.muscu_pause),
                icon = if (paused) MdiIcons.Play else MdiIcons.Pause,
                variant = ButtonVariant.Secondary,
                color = colors.muscu,
                enabled = !saving,
                onClick = if (paused) onResume else onPause,
                modifier = Modifier.weight(1f),
            )
            PulseButton(
                title = stringResource(R.string.muscu_finish),
                icon = MdiIcons.FlagCheckered,
                color = colors.muscu,
                loading = saving,
                onClick = onFinish,
                modifier = Modifier.weight(1.4f),
            )
        }
    }
}
