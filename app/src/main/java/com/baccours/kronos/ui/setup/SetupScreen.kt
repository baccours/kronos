package com.baccours.kronos.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.ui.icons.Add
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

    // Stateful wrapper passing state and events down
    SetupScreenContent(
        uiState = uiState,
        onStartTimer = onStartTimer,
        updateMode = viewModel::updateMode,
        updateSets = viewModel::updateSets,
        updateSetRest = viewModel::updateSetRest,
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
    onStartTimer: (TimerConfig) -> Unit,
    updateMode: (TimerMode) -> Unit,
    updateSets: (Int) -> Unit,
    updateSetRest: (Int) -> Unit,
    updateRounds: (Int) -> Unit,
    addTask: () -> Unit,
    updateTaskDuration: (String, Int) -> Unit,
    removeTask: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current

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
            contentWindowInsets = WindowInsets.systemBars,
            topBar = {
                TopAppBar(
                    title = { Text("Kronos Setup", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { onStartTimer(uiState) },
                    icon = { Icon(Icons.Play, contentDescription = null) },
                    text = { Text("START") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Mode Selection
                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = uiState.mode == TimerMode.TIMER,
                            onClick = { updateMode(TimerMode.TIMER) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("Timer")
                        }
                        SegmentedButton(
                            selected = uiState.mode == TimerMode.FOR_TIME,
                            onClick = { updateMode(TimerMode.FOR_TIME) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("For Time")
                        }
                    }
                }

                // Sets
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                            .padding(16.dp)
                    ) {
                        Text(text = "Sets", style = MaterialTheme.typography.titleLarge)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HybridStepper(
                                value = uiState.sets,
                                onValueChange = updateSets,
                                modifier = Modifier.weight(1.1f)
                            )

                            Row(
                                modifier = Modifier.weight(0.9f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "rest: ",
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


                // Rounds
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rounds", style = MaterialTheme.typography.titleLarge)
                        HybridStepper(
                            value = uiState.rounds,
                            onValueChange = updateRounds,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Work/Rest Sequence Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Sequence",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = addTask) {
                            Icon(Icons.Add, contentDescription = "Add task")
                        }
                    }
                }

                // Work/Rest Sequence Tasks
                itemsIndexed(uiState.tasks) { index, task ->
                    TaskRow(
                        index = index + 1,
                        type = task.type,
                        durationSeconds = task.durationSeconds,
                        onDurationChange = { updateTaskDuration(task.id, it) },
                        onDelete = { removeTask(task.id) }
                    )
                }

                // Total Duration
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val totalSecs = uiState.totalDurationSeconds
                            val h = totalSecs / 3600
                            val m = (totalSecs % 3600) / 60
                            val s = totalSecs % 60
                            val timeStr =
                                if (h > 0) "%02d:%02d:%02d".format(h, m, s)
                                else "%02d:%02d".format(m, s)

                            Text(
                                "Total Estimated Duration",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(80.dp)) // Extra space for FAB
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SetupScreenPreview() {
    KronosTheme {
        SetupScreenContent(
            uiState = TimerConfig(
                mode = TimerMode.TIMER,
                sets = 2,
                rounds = 3,
                setRestSeconds = 60,
                tasks = listOf(
                    Task(type = TaskType.WORK, durationSeconds = 30),
                    Task(type = TaskType.REST, durationSeconds = 10)
                )
            ),
            onStartTimer = {},
            updateMode = {},
            updateSets = {},
            updateRounds = {},
            addTask = {},
            updateTaskDuration = { _, _ -> },
            removeTask = {},
            updateSetRest = {}
        )
    }
}