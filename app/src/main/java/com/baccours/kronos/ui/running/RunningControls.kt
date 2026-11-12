package com.baccours.kronos.ui.running

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baccours.kronos.R
import com.baccours.kronos.ui.icons.Add
import com.baccours.kronos.ui.icons.Done
import com.baccours.kronos.ui.icons.Icons
import com.baccours.kronos.ui.icons.Pause
import com.baccours.kronos.ui.icons.Play
import com.baccours.kronos.ui.icons.SkipNext

/** Action controls for running workout screen. */
@Composable
internal fun Controls(
    isPaused: Boolean,
    canLap: Boolean,
    isLastSet: Boolean,
    colors: PhaseColors,
    compact: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onLap: () -> Unit
) {
    val bigSize = if (compact) 64.dp else 80.dp
    val smallSize = if (compact) 56.dp else 64.dp
    val skipDescription = stringResource(
        when {
            !canLap -> R.string.action_skip // For Time: skipping a work segment finishes the set.
            isLastSet -> R.string.action_finish
            else -> R.string.action_finish_set
        }
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (canLap) {
            Button(
                onClick = onLap,
                enabled = !isPaused,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(smallSize),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.content,
                    contentColor = colors.container,
                    disabledContainerColor = colors.content.copy(alpha = 0.2f),
                    disabledContentColor = colors.content.copy(alpha = 0.5f)
                )
            ) {
                Icon(Icons.Add, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_round),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = if (isPaused) onResume else onPause,
                modifier = Modifier.size(bigSize),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.content,
                    contentColor = colors.container
                )
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Play else Icons.Pause,
                    contentDescription = stringResource(
                        if (isPaused) R.string.action_resume else R.string.action_pause
                    ),
                    modifier = Modifier.size(40.dp)
                )
            }
            FilledTonalIconButton(
                onClick = onSkip,
                modifier = Modifier.size(smallSize),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = colors.content.copy(alpha = 0.15f),
                    contentColor = colors.content
                )
            ) {
                Icon(
                    imageVector = if (canLap) Icons.Done else Icons.SkipNext,
                    contentDescription = skipDescription,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
