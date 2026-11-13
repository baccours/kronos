package com.baccours.kronos.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baccours.kronos.R
import com.baccours.kronos.format.caps
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.ui.components.FieldId
import com.baccours.kronos.ui.components.TimeInput
import com.baccours.kronos.ui.icons.Delete
import com.baccours.kronos.ui.icons.Icons

@Composable
fun TaskRow(
    fieldId: FieldId,
    index: Int,
    type: TaskType,
    durationSeconds: Int,
    onDurationChange: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val isWork = type == TaskType.WORK

    val (backgroundColor, accentColor, label) = when (type) {
        TaskType.WORK -> Triple(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.primary,
            stringResource(R.string.task_work)
        )
        TaskType.REST -> Triple(
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
            Color.Transparent,
            stringResource(R.string.task_rest)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor),
            contentAlignment = Alignment.Center
        ) {
            if (isWork) {
                Text(
                    text = index.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = label.caps(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )

        TimeInput(
            fieldId = fieldId,
            durationSeconds = durationSeconds,
            maxSeconds = TimerConfig.MAX_DURATION_SECONDS,
            onDurationChange = onDurationChange
        )

        Spacer(modifier = Modifier.width(16.dp))

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Delete,
                contentDescription = stringResource(R.string.task_delete),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
