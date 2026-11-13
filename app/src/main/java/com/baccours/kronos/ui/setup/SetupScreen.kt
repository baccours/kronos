package com.baccours.kronos.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baccours.kronos.R
import com.baccours.kronos.format.caps
import com.baccours.kronos.format.formatDuration
import com.baccours.kronos.model.AppSettings
import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.model.numbered
import com.baccours.kronos.ui.components.DockedKeypad
import com.baccours.kronos.ui.components.LocalKeypad
import com.baccours.kronos.ui.components.rememberKeypadState
import com.baccours.kronos.ui.icons.Icons
import com.baccours.kronos.ui.icons.Play
import com.baccours.kronos.ui.settings.SettingsMenuButton
import com.baccours.kronos.ui.theme.KronosTheme

/** Encapsulates setup screen action callbacks. */
@Immutable
class SetupActions(
    val onStartTimer: (TimerConfig) -> Unit = {},
    val updateMode: (TimerMode) -> Unit = {},
    val updateSets: (Int) -> Unit = {},
    val updateSetRest: (Int) -> Unit = {},
    val updateTimeCap: (Int) -> Unit = {},
    val updateTotalTimeCap: (Int) -> Unit = {},
    val updateRounds: (Int) -> Unit = {},
    val addTask: (TaskType) -> Unit = {},
    val updateTaskDuration: (String, Int) -> Unit = { _, _ -> },
    val removeTask: (String) -> Unit = {}
)

@Composable
fun SetupScreen(
    viewModel: SetupViewModel = viewModel(),
    settings: AppSettings = AppSettings(),
    onSettingsChange: (AppSettings) -> Unit = {},
    onStartTimer: (TimerConfig) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val actions = remember(viewModel, onStartTimer) {
        SetupActions(
            onStartTimer = onStartTimer,
            updateMode = viewModel::updateMode,
            updateSets = viewModel::updateSets,
            updateSetRest = viewModel::updateSetRest,
            updateTimeCap = viewModel::updateTimeCap,
            updateTotalTimeCap = viewModel::updateTotalTimeCap,
            updateRounds = viewModel::updateRounds,
            addTask = viewModel::addTask,
            updateTaskDuration = viewModel::updateTaskDuration,
            removeTask = viewModel::removeTask
        )
    }

    // Draw nothing until the saved setup is loaded; the window background matches the theme.
    val config = uiState ?: return
    val totalDuration = remember(config) { config.totalDurationSeconds.formatDuration() }
    SetupScreenContent(
        uiState = config,
        totalDuration = totalDuration,
        actions = actions,
        settings = settings,
        onSettingsChange = onSettingsChange
    )
}

@Composable
internal fun SetupScreenContent(
    uiState: TimerConfig,
    totalDuration: String,
    actions: SetupActions,
    settings: AppSettings = AppSettings(),
    onSettingsChange: (AppSettings) -> Unit = {}
) {
    val scrollState = rememberScrollState()

    // Screen keypad state
    val keypad = rememberKeypadState()
    // Commit pending input before changing mode
    val currentUpdateMode by rememberUpdatedState { mode: TimerMode ->
        keypad.commitAndClose()
        actions.updateMode(mode)
    }
    // Commit pending input on back press
    BackHandler(enabled = keypad.isOpen) { keypad.commitAndClose() }

    val isTimerMode = uiState.mode == TimerMode.TIMER

    val isStartEnabled = !isTimerMode || uiState.totalDurationSeconds > 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Handle system window insets
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .swipeToSwitchMode(uiState.mode, currentUpdateMode)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Bottom),
            topBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(bottom = 16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column {
                        TitleRow(
                            settings = settings,
                            onSettingsChange = onSettingsChange,
                            onOpenSettings = { keypad.commitAndClose() },
                            modifier = Modifier.contentWidth()
                        )
                        ModeSelector(
                            mode = uiState.mode,
                            onModeChange = currentUpdateMode,
                            modifier = Modifier.contentWidth()
                        )
                    }
                }
             },
            bottomBar = {
                if (keypad.isOpen) {
                    DockedKeypad(keypad)
                } else {
                    StartBar(
                        showTotal = isTimerMode,
                        totalDuration = totalDuration,
                        enabled = isStartEnabled,
                        onStart = { actions.onStartTimer(uiState) }
                    )
                }
            }
        ) { padding ->
            CompositionLocalProvider(LocalKeypad provides keypad) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(scrollState)
                        .contentWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    SetsSection(
                        sets = uiState.sets,
                        restSeconds = uiState.setRestSeconds,
                        onSetsChange = actions.updateSets,
                        onRestChange = actions.updateSetRest
                    ) {
                        if (!isTimerMode) {
                            TimeCapSection(
                                fieldId = SetupFields.TimeCap,
                                title = stringResource(R.string.setup_time_cap),
                                suffix = stringResource(R.string.setup_time_cap_suffix),
                                timeCap = uiState.timeCap,
                                onDurationChange = actions.updateTimeCap
                            )
                        } else {
                            RoundsSection(
                                rounds = uiState.rounds,
                                onRoundsChange = actions.updateRounds
                            ) {
                                TaskList(uiState, actions)
                            }
                        }
                    }
                    if (!isTimerMode) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TimeCapSection(
                            fieldId = SetupFields.TotalTimeCap,
                            title = stringResource(R.string.setup_total_time_cap),
                            timeCap = uiState.totalTimeCap,
                            onDurationChange = actions.updateTotalTimeCap,
                            enabled = uiState.sets > 1
                        )
                    }
                }
            }
        }
    }
}

private val MaxContentWidth = 600.dp

/** Centers content and caps its width on wide screens (tablets, landscape). */
private fun Modifier.contentWidth(): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = MaxContentWidth)

/** App name and the settings entry point. */
@Composable
private fun TitleRow(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 3.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        SettingsMenuButton(
            settings = settings,
            onChange = onSettingsChange,
            onOpen = onOpenSettings
        )
    }
}

/** Task rows plus the add buttons; scrolls the buttons into view when a task is added. */
@Composable
private fun TaskList(config: TimerConfig, actions: SetupActions) {
    val keypad = LocalKeypad.current
    val addButtons = remember { BringIntoViewRequester() }
    val numbered = remember(config.tasks) { config.tasks.numbered() }
    var previousCount by remember { mutableIntStateOf(config.tasks.size) }

    LaunchedEffect(config.tasks.size) {
        if (config.tasks.size > previousCount) addButtons.bringIntoView()
        previousCount = config.tasks.size
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        numbered.forEach { (task, displayIndex) ->
            key(task.id) {
                Box(modifier = Modifier.animateContentSize()) {
                    val fieldId = SetupFields.task(task.id)
                    TaskRow(
                        fieldId = fieldId,
                        index = displayIndex,
                        type = task.type,
                        durationSeconds = task.durationSeconds,
                        onDurationChange = { actions.updateTaskDuration(task.id, it) },
                        onDelete = {
                            keypad?.cancelIf(fieldId)
                            actions.removeTask(task.id)
                        }
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(addButtons),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val canAddTask = config.tasks.size < TimerConfig.MAX_TASKS
            TaskActionButton(
                text = stringResource(R.string.setup_add_work),
                onClick = { actions.addTask(TaskType.WORK) },
                modifier = Modifier.weight(1f),
                enabled = canAddTask
            )
            TaskActionButton(
                text = stringResource(R.string.setup_add_rest),
                onClick = { actions.addTask(TaskType.REST) },
                modifier = Modifier.weight(1f),
                enabled = canAddTask
            )
        }
    }
}

/** Bottom bar displaying total duration and start button. */
@Composable
private fun StartBar(
    showTotal: Boolean,
    totalDuration: String,
    enabled: Boolean,
    onStart: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .contentWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showTotal) {
                Text(
                    text = stringResource(R.string.setup_total_duration, totalDuration),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onStart,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(imageVector = Icons.Play, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.setup_start).caps(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


@Preview(name = "Light Mode", showBackground = true)
@Preview(
    name = "Dark Mode",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun SetupScreenPreview() {
    KronosTheme {
        SetupScreenContent(
            uiState = TimerConfig(
                mode = TimerMode.TIMER,
                //mode = TimerMode.FOR_TIME,
                sets = 2,
                rounds = 3,
                setRestSeconds = 60,
                tasks = listOf(
                    Task(type = TaskType.WORK, durationSeconds = 30),
                    Task(type = TaskType.REST, durationSeconds = 10)
                )
            ),
            totalDuration = "12:00",
            actions = SetupActions()
        )
    }
}
