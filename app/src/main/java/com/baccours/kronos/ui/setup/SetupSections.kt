package com.baccours.kronos.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baccours.kronos.R
import com.baccours.kronos.format.caps
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.ui.components.FieldId
import com.baccours.kronos.ui.components.HybridStepper
import com.baccours.kronos.ui.components.TimeInput
import com.baccours.kronos.ui.icons.Add
import com.baccours.kronos.ui.icons.Icons

@Composable
internal fun TaskActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            3.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.38f)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(imageVector = Icons.Add, contentDescription = null)
        Spacer(Modifier.width(2.dp))
        Text(
            text = text.caps(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun SetsSection(
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
                .heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HybridStepper(
                fieldId = SetupFields.Sets,
                value = sets,
                onValueChange = onSetsChange,
                range = 1..TimerConfig.MAX_SETS,
                modifier = Modifier.weight(1.1f)
            )
            Row(
                modifier = Modifier.weight(0.9f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Rest between sets requires multiple sets
                val restEnabled = sets > 1
                Text(
                    text = stringResource(R.string.setup_rest_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (restEnabled) 1f else 0.38f
                    )
                )
                TimeInput(
                    fieldId = SetupFields.SetRest,
                    durationSeconds = restSeconds,
                    maxSeconds = TimerConfig.MAX_DURATION_SECONDS,
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
internal fun TimeCapSection(
    fieldId: FieldId,
    title: String,
    suffix: String? = null,
    timeCap: Int,
    onDurationChange: (Int) -> Unit,
    enabled: Boolean = true
) {
    SetupParam(
        title = title,
        titleSuffix = suffix,
        titleAlpha = if (enabled) 1f else 0.38f
    ) {
        TimeInput(
            fieldId = fieldId,
            durationSeconds = timeCap,
            maxSeconds = TimerConfig.MAX_DURATION_SECONDS,
            onDurationChange = onDurationChange,
            showRedOnZero = false,
            placeholder = stringResource(R.string.setup_time_cap_none),
            enabled = enabled,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
internal fun RoundsSection(
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
            fieldId = SetupFields.Rounds,
            value = rounds,
            onValueChange = onRoundsChange,
            range = 1..TimerConfig.MAX_ROUNDS,
            modifier = Modifier.fillMaxWidth()
                .offset(y = (-2).dp)
                .heightIn(min = 44.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
internal fun SetupSection(
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
internal fun SetupParam(
    title: String,
    modifier: Modifier = Modifier,
    titleSuffix: String? = null,
    titleAlpha: Float = 1f,
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
            modifier = Modifier
                .fillMaxWidth()
                .alpha(titleAlpha),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
