package com.baccours.kronos.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baccours.kronos.R
import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.ui.components.DockedKeypad
import com.baccours.kronos.ui.components.LocalKeypad
import com.baccours.kronos.ui.components.rememberKeypadState
import com.baccours.kronos.ui.icons.Icons
import com.baccours.kronos.ui.icons.Play
import com.baccours.kronos.ui.theme.KronosTheme
import kotlin.math.abs

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
    onStartTimer: (TimerConfig) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val totalDuration by viewModel.totalDuration.collectAsStateWithLifecycle()

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

    SetupScreenContent(uiState = uiState, totalDuration = totalDuration, actions = actions)
}

@Composable
internal fun SetupScreenContent(
    uiState: TimerConfig,
    totalDuration: String,
    actions: SetupActions
) {
    val listState = rememberLazyListState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var previousTaskCount by remember { mutableIntStateOf(uiState.tasks.size) }

    // Read current mode state within gesture detector
    val currentMode by rememberUpdatedState(uiState.mode)

    // Screen keypad state
    val keypad = rememberKeypadState()
    // Commit pending input before changing mode
    val currentUpdateMode by rememberUpdatedState({ mode: TimerMode ->
        keypad.commitAndClose()
        actions.updateMode(mode)
    })
    // Commit pending input on back press
    BackHandler(enabled = keypad.isOpen) { keypad.commitAndClose() }

    LaunchedEffect(uiState.tasks.size) {
        if (uiState.tasks.size > previousTaskCount) {
            // Scroll to bottom when tasks are added
            bringIntoViewRequester.bringIntoView()
        }
        previousTaskCount = uiState.tasks.size
    }

    val isTimerMode = uiState.mode == TimerMode.TIMER
    val timerWgt by animateFloatAsState(targetValue = if (isTimerMode) 2f else 1f, label = "TW")
    val forTimeWgt by animateFloatAsState(targetValue = if (!isTimerMode) 2f else 1f, label = "FTW")

    val isStartEnabled = if (isTimerMode) uiState.totalDurationSeconds > 0 else true

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Handle system window insets
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            // Swipe gesture detector for tab switching
            .pointerInput(Unit) {
                val threshold = 48.dp.toPx()
                val slop = viewConfiguration.touchSlop
                val edge = 32.dp.toPx() // Exclude edge back-gesture areas
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.position.x < edge || down.position.x > size.width - edge) {
                        return@awaitEachGesture
                    }
                    var dx = 0f
                    var dy = 0f
                    var claimed = false
                    var done = false
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        // Abort on multitouch or consumed gesture
                        if (event.changes.size > 1 || (!claimed && change.isConsumed)) break

                        val delta = change.positionChange()
                        dx += delta.x
                        dy += delta.y

                        // Consume horizontal swipe gestures
                        if (!claimed && abs(dx) > slop && abs(dx) > 2 * abs(dy)) {
                            claimed = true
                        }

                        if (claimed) {
                            change.consume()
                            if (!done && abs(dx) > threshold) {
                                done = true
                                // Map swipe direction to timer mode
                                val target = if (dx > 0) TimerMode.TIMER else TimerMode.FOR_TIME
                                if (target != currentMode) currentUpdateMode(target)
                            }
                        }
                    } while (change.pressed)
                }
            }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Bottom),
            topBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 8.dp, bottom = 16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    // Mode selector
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth(),
                        space = 2.dp
                    ) {
                        val modes = listOf(
                            TimerMode.TIMER to stringResource(R.string.mode_timer),
                            TimerMode.FOR_TIME to stringResource(R.string.mode_for_time)
                        )

                        modes.forEachIndexed { index, (mode, labelText) ->
                            val selected = uiState.mode == mode
                            SegmentedButton(
                                selected = selected,
                                onClick = { currentUpdateMode(mode) },
                                modifier = Modifier
                                    .weight(if (mode == TimerMode.TIMER) timerWgt else forTimeWgt)
                                    .height(56.dp),
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = BorderStroke(0.dp, Color.Transparent),
                                icon = {},
                                label = {
                                    val upperText = labelText.uppercase()
                                    val baseFontSize = if (selected) 20.sp else 12.sp
                                    val leadingFontSize = if (selected) 26.sp else 16.sp
                                    val annotatedString = buildAnnotatedString {
                                        withStyle(style = SpanStyle(fontSize = leadingFontSize)) {
                                            append(upperText.take(1))
                                        }
                                        withStyle(style = SpanStyle(fontSize = baseFontSize)) {
                                            append(upperText.drop(1))
                                        }
                                    }
                                    Text(
                                        text = annotatedString,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        ),
                                        textAlign = TextAlign.Center,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
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
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    item(key = "hierarchy") {
                        SetsSection(
                            sets = uiState.sets,
                            restSeconds = uiState.setRestSeconds,
                            onSetsChange = actions.updateSets,
                            onRestChange = actions.updateSetRest
                        ) {
                            if (!isTimerMode) {
                                TimeCapSection(
                                    fieldId = "time_cap",
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
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        uiState.indexedTasks.forEach { indexedTask ->
                                            key(indexedTask.task.id) {
                                                Box(
                                                    modifier = Modifier.animateContentSize()
                                                ) {
                                                    val taskFieldId = "task_" + indexedTask.task.id
                                                    TaskRow(
                                                        fieldId = taskFieldId,
                                                        index = indexedTask.displayIndex,
                                                        type = indexedTask.task.type,
                                                        durationSeconds = indexedTask.task.durationSeconds,
                                                        onDurationChange = {
                                                            actions.updateTaskDuration(
                                                                indexedTask.task.id,
                                                                it
                                                            )
                                                        },
                                                        onDelete = {
                                                            keypad.cancelIf(taskFieldId)
                                                            actions.removeTask(indexedTask.task.id)
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .bringIntoViewRequester(bringIntoViewRequester),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val canAddTask = uiState.tasks.size < TimerConfig.MAX_TASKS
                                            TaskActionButton(
                                                text = stringResource(R.string.setup_add_work),
                                                onClick = { actions.addTask(TaskType.WORK) },
                                                modifier = Modifier.weight(1.0f),
                                                enabled = canAddTask
                                            )
                                            TaskActionButton(
                                                text = stringResource(R.string.setup_add_rest),
                                                onClick = { actions.addTask(TaskType.REST) },
                                                modifier = Modifier.weight(1.0f),
                                                enabled = canAddTask
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (!isTimerMode) {
                        item(key = "total_cap") {
                            Spacer(modifier = Modifier.height(16.dp))
                            TimeCapSection(
                                fieldId = "total_time_cap",
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
                .fillMaxWidth()
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
                    text = stringResource(R.string.setup_start),
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
