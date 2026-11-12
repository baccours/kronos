package com.baccours.kronos.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.baccours.kronos.R
import com.baccours.kronos.model.AppSettings
import com.baccours.kronos.model.ThemeMode
import com.baccours.kronos.ui.icons.Bell
import com.baccours.kronos.ui.icons.BellOff
import com.baccours.kronos.ui.icons.DarkMode
import com.baccours.kronos.ui.icons.Icons
import com.baccours.kronos.ui.icons.LightMode
import com.baccours.kronos.ui.icons.Settings
import com.baccours.kronos.ui.icons.Speech
import com.baccours.kronos.ui.icons.SpeechOff
import com.baccours.kronos.ui.icons.ThemeAuto

/** Setting wheel button opening a vertical dropdown menu. */
@Composable
internal fun SettingsMenuButton(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit = {}
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = {
            onOpen()
            expanded = true
        }) {
            Icon(Icons.Settings, contentDescription = stringResource(R.string.action_settings))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SettingsVerticalControls(settings, onChange)
        }
    }
}

/** 3 vertical icon buttons for Beeps, Voice, and Theme. */
@Composable
private fun SettingsVerticalControls(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SettingToggle(
            checked = settings.beeps,
            onCheckedChange = { onChange(settings.copy(beeps = it)) },
            icon = if (settings.beeps) Icons.Bell else Icons.BellOff,
            label = stringResource(R.string.setting_beeps)
        )
        SettingToggle(
            checked = settings.voice,
            onCheckedChange = { onChange(settings.copy(voice = it)) },
            icon = if (settings.voice) Icons.Speech else Icons.SpeechOff,
            label = stringResource(R.string.setting_voice)
        )
        // Not a toggle: cycles through three themes, the current one is announced as the state.
        val themeState = stringResource(settings.theme.label())
        IconButton(
            onClick = { onChange(settings.copy(theme = settings.theme.next())) },
            modifier = Modifier.semantics { stateDescription = themeState }
        ) {
            Icon(
                imageVector = settings.theme.icon(),
                contentDescription = stringResource(R.string.setting_theme)
            )
        }
    }
}

/** Icon button announcing its on/off state to screen readers. */
@Composable
private fun SettingToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    label: String
) {
    val state = stringResource(if (checked) R.string.setting_state_on else R.string.setting_state_off)
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.semantics { stateDescription = state },
        // The icon already shows the state: keep the content color.
        colors = IconButtonDefaults.iconToggleButtonColors(checkedContentColor = LocalContentColor.current)
    ) {
        Icon(imageVector = icon, contentDescription = label)
    }
}

private fun ThemeMode.next(): ThemeMode = when (this) {
    ThemeMode.SYSTEM -> ThemeMode.LIGHT
    ThemeMode.LIGHT -> ThemeMode.DARK
    ThemeMode.DARK -> ThemeMode.SYSTEM
}

private fun ThemeMode.icon(): ImageVector = when (this) {
    ThemeMode.SYSTEM -> Icons.ThemeAuto
    ThemeMode.LIGHT -> Icons.LightMode
    ThemeMode.DARK -> Icons.DarkMode
}

@StringRes
private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}
