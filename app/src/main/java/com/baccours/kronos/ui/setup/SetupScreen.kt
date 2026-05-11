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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baccours.kronos.model.WorkoutConfig
import com.baccours.kronos.model.WorkoutItemType
import com.baccours.kronos.model.WorkoutMode
import com.baccours.kronos.ui.icons.Add
import com.baccours.kronos.ui.components.HybridStepper
import com.baccours.kronos.ui.components.TimeInput
import com.baccours.kronos.ui.icons.Delete
import com.baccours.kronos.ui.icons.Icons
import com.baccours.kronos.ui.icons.Play

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: SetupViewModel = viewModel(),
    onStartWorkout: (WorkoutConfig) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
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
                    onClick = { onStartWorkout(uiState) },
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
                    Text("Workout Mode", style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = uiState.mode == WorkoutMode.TIMER,
                            onClick = { viewModel.updateMode(WorkoutMode.TIMER) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("Timer")
                        }
                        SegmentedButton(
                            selected = uiState.mode == WorkoutMode.FOR_TIME,
                            onClick = { viewModel.updateMode(WorkoutMode.FOR_TIME) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("For Time")
                        }
                    }
                }

                // Sets and Rounds
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sets", style = MaterialTheme.typography.titleMedium)
                            HybridStepper(
                                value = uiState.sets,
                                onValueChange = viewModel::updateSets,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Rounds per Set", style = MaterialTheme.typography.titleMedium)
                            HybridStepper(
                                value = uiState.roundsPerSet,
                                onValueChange = viewModel::updateRoundsPerSet,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
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
                        IconButton(onClick = viewModel::addItem) {
                            Icon(Icons.Add, contentDescription = "Add item")
                        }
                    }
                }

                // Work/Rest Sequence Items
                itemsIndexed(uiState.items) { index, item ->
                    WorkoutItemRow(
                        index = index + 1,
                        type = item.type,
                        durationSeconds = item.durationSeconds,
                        onDurationChange = { viewModel.updateItemDuration(item.id, it) },
                        onDelete = { viewModel.removeItem(item.id) }
                    )
                }

                // Rest Between Sets
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                            .padding(16.dp)
                    ) {
                        Text("Rest Between Sets", style = MaterialTheme.typography.titleMedium)
                        TimeInput(
                            durationSeconds = uiState.restBetweenSetsSeconds,
                            onDurationChange = viewModel::updateRestBetweenSets,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            showRedOnZero = false
                        )
                    }
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

@Composable
fun WorkoutItemRow(
    index: Int,
    type: WorkoutItemType,
    durationSeconds: Int,
    onDurationChange: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val backgroundColor = if (type == WorkoutItemType.WORK) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
    }
    
    val accentColor = if (type == WorkoutItemType.WORK) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = index.toString(),
                color = if (type == WorkoutItemType.WORK) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = if (type == WorkoutItemType.WORK) "WORK" else "REST",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )

        TimeInput(
            durationSeconds = durationSeconds,
            onDurationChange = onDurationChange
        )

        IconButton(onClick = onDelete) {
            Icon(Icons.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
        }
    }
}
