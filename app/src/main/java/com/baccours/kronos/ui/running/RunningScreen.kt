package com.baccours.kronos.ui.running

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baccours.kronos.model.WorkoutItemType
import com.baccours.kronos.model.WorkoutMode
import com.baccours.kronos.repository.TimerRepository
import com.baccours.kronos.repository.TimerState
import com.baccours.kronos.repository.TimerStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningScreen(
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSkip: () -> Unit = {}
) {
    val timerState by TimerRepository.timerState.collectAsState()
    
    BackHandler {
        if (timerState.status == TimerStatus.RUNNING) {
            onPause()
        } else {
            onStop()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (timerState.status == TimerStatus.FINISHED) "Finished" else "Kronos",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    if (timerState.status != TimerStatus.FINISHED) {
                        IconButton(onClick = onStop) {
                            Icon(Icons.Rounded.Close, contentDescription = "Stop")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (timerState.status == TimerStatus.FINISHED) {
                WorkoutFinishedView(timerState, onStop)
            } else {
                WorkoutRunningView(
                    state = timerState,
                    onPause = onPause,
                    onResume = onResume,
                    onSkip = onSkip
                )
            }
        }
    }
}

@Composable
fun ColumnScope.WorkoutRunningView(
    state: TimerState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit
) {
    val config = state.config ?: return
    
    // Header Info
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        InfoColumn(label = "SET", value = "${state.currentSet}/${config.sets}")
        InfoColumn(label = "ROUND", value = "${state.currentRound}/${config.roundsPerSet}")
    }

    Spacer(modifier = Modifier.height(32.dp))

    // Current Item Name
    val itemName = when {
        state.isReadyCountdown -> "GET READY"
        state.isRestBetweenSets -> "REST BETWEEN SETS"
        else -> state.currentItem?.let { item ->
            item.name.ifBlank { item.type.name }
        } ?: "WORK"
    }

    Text(
        text = itemName,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = if (state.currentItem?.type == WorkoutItemType.REST || state.isRestBetweenSets) 
            MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    )

    Spacer(modifier = Modifier.weight(1f))

    // Central Timer Display
    val displaySeconds = if (config.mode == WorkoutMode.TIMER || state.isRestBetweenSets || state.isReadyCountdown) {
        state.remainingSeconds
    } else {
        state.elapsedSeconds
    }

    val minutes = displaySeconds / 60
    val seconds = displaySeconds % 60
    val timeText = "%02d:%02d".format(minutes, seconds)

    Text(
        text = timeText,
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = 100.sp,
            fontWeight = FontWeight.Black
        ),
        color = MaterialTheme.colorScheme.onSurface
    )

    if (config.mode == WorkoutMode.TIMER && !state.isReadyCountdown) {
        val totalRemaining = state.totalRemainingSeconds
        Text(
            text = "Total remaining: %02d:%02d".format(totalRemaining / 60, totalRemaining % 60),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(modifier = Modifier.weight(1f))

    // For Time Mode extra UI
    if (config.mode == WorkoutMode.FOR_TIME && !state.isReadyCountdown && !state.isRestBetweenSets) {
        Button(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Icon(Icons.Rounded.Done, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("DONE (Round Complete)")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (state.roundTimes.isNotEmpty()) {
            Text(
                text = "Previous Rounds",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
            LazyColumn(modifier = Modifier.height(100.dp).fillMaxWidth()) {
                items(state.roundTimes.reversed()) { time ->
                    Text("Round: %02d:%02d".format(time / 60, time % 60))
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    // Controls
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.status == TimerStatus.RUNNING) {
            ControlCircleButton(icon = Icons.Rounded.Pause, onClick = onPause)
        } else if (state.status == TimerStatus.PAUSED) {
            ControlCircleButton(icon = Icons.Rounded.PlayArrow, onClick = onResume)
        }
    }
}

@Composable
fun InfoColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ControlCircleButton(icon: ImageVector, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(80.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
    }
}

@Composable
fun WorkoutFinishedView(state: TimerState, onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.EmojiEvents,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "WORKOUT COMPLETE",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Great session! You've crushed it.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("EXIT")
        }
    }
}
