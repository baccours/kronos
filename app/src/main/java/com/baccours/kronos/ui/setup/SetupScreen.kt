package com.baccours.kronos.ui.setup

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
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
import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.ui.components.HybridStepper
import com.baccours.kronos.ui.components.TimeInput
import com.baccours.kronos.ui.icons.Icons
import com.baccours.kronos.ui.icons.Play
import com.baccours.kronos.ui.theme.KronosTheme

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

@OptIn(ExperimentalMaterial3Api::class)
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

    val showZeroDurationError = remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    var previousTaskCount by remember { mutableIntStateOf(uiState.tasks.size) }
    LaunchedEffect(uiState.tasks.size) {
        if (uiState.tasks.size > previousTaskCount) {
            // Scroll to the bottom (add buttons)
            val buttonIndex = uiState.tasks.size + 2
            listState.scrollToItem(buttonIndex)
        }
        previousTaskCount = uiState.tasks.size
    }

    val isTimerMode = uiState.mode == TimerMode.TIMER
    val timerWgt by animateFloatAsState(targetValue = if (isTimerMode) 2f else 1f, label = "TW")
    val forTimeWgt by animateFloatAsState(targetValue = if (!isTimerMode) 2f else 1f, label = "FTW")

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
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Bottom),
            topBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding() // Ensures content doesn't go under the clock/icons
                        .padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    // Mode Selection
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth(),
                        space = 2.dp
                    ) {
                        val modes = listOf(
                            TimerMode.TIMER to "Beat the beep",
                            TimerMode.FOR_TIME to "Stop the clock"
                        )

                        modes.forEachIndexed { index, (mode, labelText) ->
                            val selected = uiState.mode == mode
                            SegmentedButton(
                                selected = selected,
                                onClick = { updateMode(mode) },
                                modifier = Modifier
                                    .weight(if (mode == TimerMode.TIMER) timerWgt else forTimeWgt)
                                    .height(64.dp),
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    activeContentColor = MaterialTheme.colorScheme.primary,
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
                                text = "Total duration: $totalDuration",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                if (isTimerMode && uiState.totalDurationSeconds <= 0) {
                                    showZeroDurationError.value = true
                                } else {
                                    onStartTimer(uiState)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),

                            ) {
                                Icon(imageVector = Icons.Play, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "START",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sets
                item(key = "sets") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(text = "Sets", style = MaterialTheme.typography.titleLarge)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HybridStepper(
                                value = uiState.sets,
                                onValueChange = updateSets,
                                modifier = Modifier.weight(1.2f)
                            )

                            Row(
                                modifier = Modifier.weight(0.8f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "rest:",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TimeInput(
                                    durationSeconds = uiState.setRestSeconds,
                                    onDurationChange = updateSetRest,
                                    showRedOnZero = false
                                )
                            }
                        }
                    }
                }

                if (!isTimerMode) {
                    // Time CAP
                    item(key = "time_cap") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Time Cap",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = " (per set)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center

                            ) {
                                TimeInput(
                                    durationSeconds = uiState.timeCap,
                                    onDurationChange = updateTimeCap,
                                    showRedOnZero = false,
                                    placeholder = "none"
                                )
                            }
                        }
                    }
                }
                else {
                    // Rounds
                    item(key = "rounds") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(text = "Rounds", style = MaterialTheme.typography.titleLarge)
                            HybridStepper(
                                value = uiState.rounds,
                                onValueChange = updateRounds,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Work/Rest Sequence Tasks
                    items(
                        items = uiState.indexedTasks,
                        key = { it.task.id }
                    ) { indexedTask ->
                        Box(modifier = Modifier.animateItem(
                            fadeInSpec = spring(stiffness = Spring.StiffnessLow),
                            placementSpec = null,
                            fadeOutSpec = null
                        )) {
                            TaskRow(
                                index = indexedTask.displayIndex,
                                type = indexedTask.task.type,
                                durationSeconds = indexedTask.task.durationSeconds,
                                onDurationChange = { updateTaskDuration(indexedTask.task.id, it) },
                                onDelete = { removeTask(indexedTask.task.id) }
                            )
                        }
                    }

                    item(key = "add_buttons") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { addTask(TaskType.WORK) },
                                modifier = Modifier
                                    .weight(1.0f)
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = "ADD WORK",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = { addTask(TaskType.REST) },
                                modifier = Modifier
                                    .weight(1.0f)
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )

                            ) {
                                Text(
                                    text = "ADD REST",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showZeroDurationError.value) {
        AlertDialog(
            onDismissRequest = { showZeroDurationError.value = false },
            title = { Text(text = "Empty Timer") },
            text = {
                Text(text = "The total duration cannot be 0.\nPlease add some work time to start.")
            },
            confirmButton = {
                TextButton(onClick = { showZeroDurationError.value = false }) {
                    Text("OK")
                }
            }
        )
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