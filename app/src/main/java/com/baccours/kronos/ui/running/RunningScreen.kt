package com.baccours.kronos.ui.running

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baccours.kronos.repository.TimerStatus
import com.baccours.kronos.ui.icons.Close
import com.baccours.kronos.ui.icons.Done
import com.baccours.kronos.ui.icons.Icons
import com.baccours.kronos.ui.icons.Pause
import com.baccours.kronos.ui.icons.Play
import com.baccours.kronos.ui.icons.Trophy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningScreen(
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSkip: () -> Unit = {},
    viewModel: RunningViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    BackHandler {
        if (uiState.status == TimerStatus.RUNNING) {
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
                        text = if (uiState.status == TimerStatus.FINISHED) "Finished" else "Kronos",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    if (uiState.status != TimerStatus.FINISHED) {
                        IconButton(onClick = onStop) {
                            Icon(Icons.Close, contentDescription = "Stop")
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
            if (uiState.status == TimerStatus.FINISHED) {
                WorkoutFinishedView(uiState, onStop)
            } else {
                WorkoutRunningView(
                    uiState = uiState,
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
    uiState: RunningUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit
) {
    // Header Info
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        InfoColumn(label = "SET", value = uiState.currentSetText)
        InfoColumn(label = "ROUND", value = uiState.currentRoundText)
    }

    Spacer(modifier = Modifier.height(32.dp))

    // Current Item Name
    Text(
        text = uiState.itemName,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = if (uiState.isWorkItem) 
            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    )

    Spacer(modifier = Modifier.weight(1f))

    // Central Timer Display
    Text(
        text = uiState.displayTime,
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = 100.sp,
            fontWeight = FontWeight.Black
        ),
        color = MaterialTheme.colorScheme.onSurface
    )

    uiState.totalRemainingTime?.let { totalRemaining ->
        Text(
            text = "Total remaining: $totalRemaining",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(modifier = Modifier.weight(1f))

    // For Time Mode extra UI
    if (uiState.nextButtonText != null) {
        Button(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Icon(Icons.Done, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(uiState.nextButtonText)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (uiState.roundTimes.isNotEmpty()) {
        Text(
            text = "History",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
        LazyColumn(
            modifier = Modifier
                .height(120.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(uiState.roundTimes.reversed()) { timeText ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = timeText,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium
                    )
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
        if (uiState.status == TimerStatus.RUNNING) {
            ControlCircleButton(Icons.Pause, onClick = onPause)
        } else if (uiState.status == TimerStatus.PAUSED) {
            ControlCircleButton(Icons.Play, onClick = onResume)
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
fun WorkoutFinishedView(uiState: RunningUiState, onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Trophy,
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
