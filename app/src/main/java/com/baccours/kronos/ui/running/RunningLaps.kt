package com.baccours.kronos.ui.running

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baccours.kronos.R

/** Section displaying current pinned lap and scrollable finished laps. */
@Composable
internal fun LapSection(
    lapTime: String?,
    currentRound: Int,
    laps: List<LapUi>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        lapTime?.let {
            LapRow(
                number = currentRound,
                time = it,
                active = true
            )
        }
        LapList(
            laps = laps,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}

@Composable
private fun LapList(
    laps: List<LapUi>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(laps.size) {
        if (laps.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(laps, key = { "${it.set}-${it.number}" }) { lap ->
            LapRow(
                number = lap.number,
                time = lap.time,
                delta = lap.delta,
                isFaster = lap.isFaster
            )
        }
    }
}

@Composable
internal fun LapRow(
    number: Int,
    time: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
    isFaster: Boolean = true,
    active: Boolean = false
) {
    val textStyle = if (active) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LocalContentColor.current.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.lap_label, number),
            style = textStyle,
            fontWeight = if (active) FontWeight.Bold else null,
            modifier = Modifier.weight(1f)
        )
        delta?.let { d ->
            val spacing = Modifier.padding(end = 12.dp)
            if (isFaster) {
                Text(
                    text = d,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = LocalContentColor.current,
                    modifier = spacing
                )
            } else {
                // A slower lap sits on its own error-container pill: red text straight on the lap row
                // was 3.1:1 in the dark theme, the container pair is a guaranteed high-contrast match.
                Text(
                    text = d,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = spacing
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            text = time,
            style = textStyle,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}
