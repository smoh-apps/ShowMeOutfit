package com.smoh.showmeoutfit.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.AgeBand
import com.smoh.showmeoutfit.domain.model.GenderPreference
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.ThermalSensitivity
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.ui.onboarding.OnboardingMessageMapper
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import org.koin.androidx.compose.koinViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Test tags so instrumented tests never depend on English copy. */
object SettingsTestTags {
    const val SCREEN = "settings_screen"
    const val BACK = "settings_back"
    const val ALERT_SWITCH = "settings_alert_switch"
    const val ALERT_TIME = "settings_alert_time"
    const val TIME_DIALOG_CONFIRM = "settings_time_confirm"
    const val SENSITIVITY_COLD = "settings_sensitivity_cold"
    const val SENSITIVITY_NEUTRAL = "settings_sensitivity_neutral"
    const val SENSITIVITY_WARM = "settings_sensitivity_warm"
    const val GENDER_ALL = "settings_gender_all"
    const val GENDER_MEN = "settings_gender_men"
    const val GENDER_WOMEN = "settings_gender_women"
    const val AGE_EDIT = "settings_age_edit"
    const val AGE_INPUT = "settings_age_input"
    const val AGE_SAVE = "settings_age_save"
    const val RESET_LOCATION = "settings_reset_location"
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showAgeDialog by rememberSaveable { mutableStateOf(false) }
    val profile = state.profile

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(SettingsTestTags.BACK)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_action)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.md))
                Text(
                    text = stringResource(R.string.settings_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.Spacing.lg)
                .testTag(SettingsTestTags.SCREEN),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md)
        ) {
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.sm))

            if (state.saveFailed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_error_save),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onIntent(SettingsIntent.DismissSaveError) }) {
                        Text(stringResource(R.string.settings_error_dismiss))
                    }
                }
            }

            SectionTitle(stringResource(R.string.settings_section_alerts))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DesignTokens.MinTouchTarget),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_alert_toggle_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.settings_alert_toggle_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = profile.isNotificationEnabled,
                    onCheckedChange = {
                        onIntent(SettingsIntent.SetNotificationsEnabled(it))
                    },
                    modifier = Modifier.testTag(SettingsTestTags.ALERT_SWITCH)
                )
            }

            if (profile.isNotificationEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = DesignTokens.MinTouchTarget)
                        .clickable { showTimePicker = true }
                        .testTag(SettingsTestTags.ALERT_TIME),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_alert_time_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_alert_time_value,
                                formatTime(profile.morningNotificationTime)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(stringResource(R.string.settings_alert_time_change))
                }
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_section_comfort))

            Text(
                text = stringResource(R.string.settings_sensitivity_title),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.settings_sensitivity_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThermalSensitivity.entries.forEachIndexed { index, sensitivity ->
                    SegmentedButton(
                        selected = profile.thermalSensitivity == sensitivity,
                        onClick = {
                            onIntent(SettingsIntent.SetThermalSensitivity(sensitivity))
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ThermalSensitivity.entries.size
                        ),
                        modifier = Modifier.testTag(
                            when (sensitivity) {
                                ThermalSensitivity.FEELS_COLD_EASILY ->
                                    SettingsTestTags.SENSITIVITY_COLD
                                ThermalSensitivity.NEUTRAL -> SettingsTestTags.SENSITIVITY_NEUTRAL
                                ThermalSensitivity.FEELS_WARM_EASILY ->
                                    SettingsTestTags.SENSITIVITY_WARM
                            }
                        )
                    ) {
                        Text(stringResource(sensitivityLabelRes(sensitivity)))
                    }
                }
            }

            Text(
                text = stringResource(R.string.settings_gender_title),
                style = MaterialTheme.typography.bodyLarge
            )
            GenderPreference.entries.forEach { gender ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = DesignTokens.MinTouchTarget)
                        .selectable(
                            selected = profile.genderPreference == gender,
                            role = Role.RadioButton,
                            onClick = { onIntent(SettingsIntent.SetGender(gender)) }
                        )
                        .testTag(
                            when (gender) {
                                GenderPreference.ALL -> SettingsTestTags.GENDER_ALL
                                GenderPreference.MEN -> SettingsTestTags.GENDER_MEN
                                GenderPreference.WOMEN -> SettingsTestTags.GENDER_WOMEN
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = profile.genderPreference == gender,
                        onClick = null
                    )
                    Text(
                        text = stringResource(genderLabelRes(gender)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = DesignTokens.Spacing.sm)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DesignTokens.MinTouchTarget),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_age_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(
                            OnboardingMessageMapper.ageBandRes(profile.ageBand)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = { showAgeDialog = true },
                    modifier = Modifier
                        .heightIn(min = DesignTokens.MinTouchTarget)
                        .testTag(SettingsTestTags.AGE_EDIT)
                ) {
                    Text(stringResource(R.string.settings_age_edit))
                }
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_section_location))

            Text(
                text = profile.selectedLocation?.cityName?.takeIf { it.isNotBlank() }
                    ?.let { stringResource(R.string.settings_location_saved, it) }
                    ?: stringResource(R.string.settings_location_current),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.settings_location_reset_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { onIntent(SettingsIntent.ResetLocationToGps) },
                enabled = profile.selectedLocation != null && !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DesignTokens.MinTouchTarget)
                    .testTag(SettingsTestTags.RESET_LOCATION)
            ) {
                Text(stringResource(R.string.settings_location_reset))
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.xl))
        }
    }

    if (showTimePicker) {
        MorningTimePickerDialog(
            initialTime = profile.morningNotificationTime,
            onConfirm = { time ->
                onIntent(SettingsIntent.SetNotificationTime(time))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    if (showAgeDialog) {
        AgeEditDialog(
            onSave = { years ->
                onIntent(SettingsIntent.SetAge(years))
                showAgeDialog = false
            },
            onDismiss = { showAgeDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MorningTimePickerDialog(
    initialTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_time_picker_title)) },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(LocalTime.of(pickerState.hour, pickerState.minute))
                },
                modifier = Modifier.testTag(SettingsTestTags.TIME_DIALOG_CONFIRM)
            ) {
                Text(stringResource(R.string.settings_time_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_time_picker_cancel))
            }
        }
    )
}

@Composable
private fun AgeEditDialog(
    onSave: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var input by rememberSaveable { mutableStateOf("") }
    val digits = input.filter { it.isDigit() }
    val years = digits.toIntOrNull()
    val error = digits.isNotEmpty() &&
        (digits.length > 3 || years == null || years !in 0..120)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_age_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.settings_age_dialog_title)) },
                    singleLine = true,
                    isError = error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(
                            text = stringResource(
                                if (error) R.string.settings_age_error
                                else R.string.settings_age_dialog_helper
                            )
                        )
                    },
                    modifier = Modifier.testTag(SettingsTestTags.AGE_INPUT)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(years) },
                enabled = !error,
                modifier = Modifier.testTag(SettingsTestTags.AGE_SAVE)
            ) {
                Text(stringResource(R.string.settings_age_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_age_dialog_cancel))
            }
        }
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

private fun formatTime(time: LocalTime): String =
    time.format(
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
    )

private fun sensitivityLabelRes(sensitivity: ThermalSensitivity): Int = when (sensitivity) {
    ThermalSensitivity.FEELS_COLD_EASILY -> R.string.settings_sensitivity_cold
    ThermalSensitivity.NEUTRAL -> R.string.settings_sensitivity_neutral
    ThermalSensitivity.FEELS_WARM_EASILY -> R.string.settings_sensitivity_warm
}

private fun genderLabelRes(gender: GenderPreference): Int = when (gender) {
    GenderPreference.ALL -> R.string.settings_gender_all
    GenderPreference.MEN -> R.string.settings_gender_men
    GenderPreference.WOMEN -> R.string.settings_gender_women
}

// --- Previews ----------------------------------------------------------------------------

@Preview(name = "Settings", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun SettingsPreview() {
    ShowMeOutfitTheme(dynamicColor = false) {
        SettingsScreen(
            state = SettingsUiState(
                profile = UserProfile(
                    genderPreference = GenderPreference.WOMEN,
                    ageBand = AgeBand.ADULT_20_39,
                    thermalSensitivity = ThermalSensitivity.FEELS_COLD_EASILY,
                    isNotificationEnabled = true,
                    morningNotificationTime = LocalTime.of(7, 0),
                    selectedLocation = LocationInfo(52.52, 13.405, "Berlin", "Germany"),
                    isConfigured = true
                ),
                isLoading = false
            ),
            onIntent = {},
            onBack = {}
        )
    }
}

@Preview(name = "Settings – loading", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun SettingsLoadingPreview() {
    ShowMeOutfitTheme(dynamicColor = false) {
        SettingsScreen(state = SettingsUiState(isLoading = true), onIntent = {}, onBack = {})
    }
}
