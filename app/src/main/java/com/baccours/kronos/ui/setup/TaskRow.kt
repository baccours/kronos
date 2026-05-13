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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.ui.components.TimeInput
import com.baccours.kronos.ui.icons.Delete
import com.baccours.kronos.ui.icons.Icons


@Composable
fun TaskRow(
    index: Int,
    type: TaskType,
    durationSeconds: Int,
    onDurationChange: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val backgroundColor = if (type == TaskType.WORK) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
    }

    val accentColor = if (type == TaskType.WORK) {
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
                color =
                    if (type == TaskType.WORK) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSecondary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = if (type == TaskType.WORK) "WORK" else "REST",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )

        TimeInput(
            durationSeconds = durationSeconds,
            onDurationChange = onDurationChange
        )

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
