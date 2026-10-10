package com.baccours.kronos.ui.setup

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalFocusManager
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baccours.kronos.R
import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.ui.components.HybridStepper
import com.baccours.kronos.ui.components.KeypadInsetState
import com.baccours.kronos.ui.components.LocalKeypadInset
import com.baccours.kronos.ui.components.TimeInput
import com.baccours.kronos.ui.components.trackKeypadInset
import com.baccours.kronos.ui.icons.Add
import com.baccours.kronos.ui.icons.Icons
import com.baccours.kronos.ui.icons.Play
import com.baccours.kronos.ui.theme.KronosTheme
import kotlin.math.abs

@Composable
fun SetupScreen(
    viewModel: SetupViewModel = viewModel(),
    onStartTimer: (TimerConfig) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val totalDuration by viewModel.totalDuration.collectAsState()

    // Stateful wrapper passing state and events down
    SetupScreenContent(
        uiState = uiState,
        totalDuration = totalDuration,
        onStartTimer = onStartTimer,
        updateMode = viewModel::updateMode,
        updateSets = viewModel::updateSets,
        updateSetRest = viewModel::updateSetRest,
        updateTimeCap = viewModel::updateTimeCap,
        updateRounds = viewModel::updateRounds,
        addTask = viewModel::addTask,
        updateTaskDuration = viewModel::updateTaskDuration,
        removeTask = viewModel::removeTask
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SetupScreenContent(
    uiState: TimerConfig,
    totalDuration: String,
    onStartTimer: (TimerConfig) -> Unit,
    updateMode: (TimerMode) -> Unit,
    updateSets: (Int) -> Unit,
    updateSetRest: (Int) -> Unit,
    updateTimeCap: (Int) -> Unit,
    updateRounds: (Int) -> Unit,
    addTask: (TaskType) -> Unit,
    updateTaskDuration: (String, Int) -> Unit,
    removeTask: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var previousTaskCount by remember { mutableIntStateOf(uiState.tasks.size) }

    // Keep the swipe gesture block (keyed on Unit) reading fresh values
    val currentMode by rememberUpdatedState(uiState.mode)
    val currentUpdateMode by rememberUpdatedState(updateMode)

    val keypadInset = remember { KeypadInsetState() }

    LaunchedEffect(uiState.tasks.size) {
        if (uiState.tasks.size > previousTaskCount) {
            // Scroll to the bottom (add buttons)
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
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(pass = PointerEventPass.Initial)
                        focusManager.clearFocus()
                    }
                }
            }
            // Global horizontal swipe to change TimerMode
            .pointerInput(Unit) {
                val threshold = 48.dp.toPx()
                val slop = viewConfiguration.touchSlop
                val edge = 32.dp.toPx() // leave the system back-gesture zones alone
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

                        // Multitouch or child took the gesture -> abort
                        if (event.changes.size > 1 || (!claimed && change.isConsumed)) break

                        val delta = change.positionChange()
                        dx += delta.x
                        dy += delta.y

                        // Claim the gesture if it's clearly horizontal
                        if (!claimed && abs(dx) > slop && abs(dx) > 2 * abs(dy)) {
                            claimed = true
                        }

                        if (claimed) {
                            change.consume() // Prevent buttons/scroll from triggering
                            if (!done && abs(dx) > threshold) {
                                done = true
                                // Swipe Right (dx > 0) -> First Tab (TIMER)
                                // Swipe Left (dx < 0) -> Second Tab (FOR_TIME)
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
                        .statusBarsPadding() // Ensures content doesn't go under the clock/icons
                        .padding(top = 8.dp, bottom = 16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    // Mode Selection
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
                                onClick = { updateMode(mode) },
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
                Surface(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isTimerMode) {
                            Text(
                                text = stringResource(R.string.setup_total_duration, totalDuration),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { onStartTimer(uiState) },
                            enabled = isStartEnabled,
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
        ) { padding ->
            CompositionLocalProvider(LocalKeypadInset provides keypadInset) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 12.dp)
                        .trackKeypadInset(keypadInset),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp + keypadInset.bottomPadding())
                ) {
                    item(key = "hierarchy") {
                        SetsSection(
                            sets = uiState.sets,
                            restSeconds = uiState.setRestSeconds,
                            onSetsChange = updateSets,
                            onRestChange = updateSetRest
                        ) {
                            if (!isTimerMode) {
                                TimeCapSection(
                                    timeCap = uiState.timeCap,
                                    onDurationChange = updateTimeCap
                                )
                            } else {
                                RoundsSection(
                                    rounds = uiState.rounds,
                                    onRoundsChange = updateRounds
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        uiState.indexedTasks.forEach { indexedTask ->
                                            key(indexedTask.task.id) {
                                                Box(
                                                    modifier = Modifier.animateContentSize()
                                                ) {
                                                    TaskRow(
                                                        index = indexedTask.displayIndex,
                                                        type = indexedTask.task.type,
                                                        durationSeconds = indexedTask.task.durationSeconds,
                                                        onDurationChange = {
                                                            updateTaskDuration(
                                                                indexedTask.task.id,
                                                                it
                                                            )
                                                        },
                                                        onDelete = { removeTask(indexedTask.task.id) }
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
                                            TaskActionButton(
                                                text = stringResource(R.string.setup_add_work),
                                                onClick = { addTask(TaskType.WORK) },
                                                modifier = Modifier.weight(1.0f)
                                            )
                                            TaskActionButton(
                                                text = stringResource(R.string.setup_add_rest),
                                                onClick = { addTask(TaskType.REST) },
                                                modifier = Modifier.weight(1.0f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(imageVector = Icons.Add, contentDescription = null)
        Spacer(Modifier.width(2.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SetsSection(
    sets: Int,
    restSeconds: Int,
    onSetsChange: (Int) -> Unit,
    onRestChange: (Int) -> Unit,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    SetupSection(
        title = stringResource(R.string.setup_sets),
        contentPadding = PaddingValues(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HybridStepper(
                value = sets,
                onValueChange = onSetsChange,
                modifier = Modifier.weight(1.2f)
            )
            Row(
                modifier = Modifier.weight(0.8f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Rest between sets only makes sense with more than one set.
                val restEnabled = sets > 1
                Text(
                    text = stringResource(R.string.setup_rest_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (restEnabled) 1f else 0.38f
                    )
                )
                TimeInput(
                    durationSeconds = restSeconds,
                    onDurationChange = onRestChange,
                    showRedOnZero = false,
                    enabled = restEnabled
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun TimeCapSection(
    timeCap: Int,
    onDurationChange: (Int) -> Unit
) {
    SetupParam(
        title = stringResource(R.string.setup_time_cap),
        titleSuffix = stringResource(R.string.setup_time_cap_suffix)
    ) {
        TimeInput(
            durationSeconds = timeCap,
            onDurationChange = onDurationChange,
            showRedOnZero = false,
            placeholder = stringResource(R.string.setup_time_cap_none),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun RoundsSection(
    rounds: Int,
    onRoundsChange: (Int) -> Unit,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    SetupSection(
        title = stringResource(R.string.setup_rounds),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        HybridStepper(
            value = rounds,
            onValueChange = onRoundsChange,
            modifier = Modifier.fillMaxWidth()
                .offset(y = (-2).dp)
                .height(44.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun SetupSection(
    title: String,
    border: BorderStroke,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .border(border, RoundedCornerShape(16.dp))
                .padding(contentPadding)
        ) {
            content()
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 24.dp, y = (-16).dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(32.dp))
                .border(border, RoundedCornerShape(32.dp))
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun SetupParam(
    title: String,
    modifier: Modifier = Modifier,
    titleSuffix: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))
            .padding(contentPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            if (titleSuffix != null) {
                Text(
                    text = titleSuffix,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
        content()
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
            onStartTimer = {},
            updateMode = {},
            updateSets = {},
            updateSetRest = {},
            updateTimeCap = {},
            updateRounds = {},
            addTask = {},
            updateTaskDuration = { _, _ -> },
            removeTask = {}
        )
    }
}