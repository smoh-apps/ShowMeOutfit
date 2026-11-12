package com.smoh.showmeoutfit.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.AgeBand
import com.smoh.showmeoutfit.domain.model.GenderPreference
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.ThermalSensitivity
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.ui.onboarding.OnboardingMessageMapper
import com.smoh.showmeoutfit.ui.permissions.AlertsPermissionReader
import com.smoh.showmeoutfit.ui.permissions.AlertsPermissionState
import com.smoh.showmeoutfit.ui.permissions.AlertsRequirement
import com.smoh.showmeoutfit.ui.permissions.AlertsSettingsIntents
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
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
    const val LANGUAGE_SYSTEM = "settings_language_system"
    const val LANGUAGE_ENGLISH = "settings_language_english"
    const val LANGUAGE_VIETNAMESE = "settings_language_vietnamese"
    const val LANGUAGE_FRENCH = "settings_language_french"
    const val PERMISSION_NOTICE = "settings_permission_notice"
    const val PERMISSION_ACTION = "settings_permission_action"
    const val PERMISSION_DIALOG_CONFIRM = "settings_permission_dialog_confirm"
    const val PERMISSION_DIALOG_CANCEL = "settings_permission_dialog_cancel"
    const val EXACT_ALARM_NOTICE = "settings_exact_alarm_notice"
    const val EXACT_ALARM_ACTION = "settings_exact_alarm_action"
}

@SuppressLint("InlinedApi") // POST_NOTIFICATIONS is only requested behind an API 33+ check.
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
    permissionReader: AlertsPermissionReader = koinInject()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // The user may grant or revoke either capability in system settings and come back, so the
    // snapshot is refreshed on every resume rather than only once at composition.
    var permissionState by remember { mutableStateOf(permissionReader.read()) }
    DisposableEffect(lifecycleOwner, permissionReader) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionState = permissionReader.read()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // The callback result is deliberately ignored: the reader is the single source of truth
        // (it also covers "don't ask again" and channel-level blocks).
        permissionState = permissionReader.read()
    }
    // Android only shows the prompt while it still considers the request reasonable; after two
    // denials the launcher becomes a no-op, so the card has to route to app settings instead.
    var askedThisSession by rememberSaveable { mutableStateOf(false) }

    SettingsScreen(
        state = state,
        onIntent = { intent ->
            viewModel.onIntent(intent)
            // API 33+ recreates the activity itself when the application locales change; below
            // that the language only applies on the next attachBaseContext, so recreate here.
            if (intent is SettingsIntent.SetLanguage &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                context.findActivity()?.recreate()
            }
        },
        onBack = onBack,
        modifier = modifier,
        permissionState = permissionState,
        onRequestNotifications = {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            val granted = ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
            val activity = context.findActivity()
            val mayPromptAgain = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !granted &&
                (!askedThisSession || mayPromptAgain)

            if (canAsk) {
                askedThisSession = true
                notificationLauncher.launch(permission)
            } else {
                runCatching { context.startActivity(AlertsSettingsIntents.appNotificationSettings(context)) }
            }
        },
        onOpenExactAlarms = {
            AlertsSettingsIntents.exactAlarmSettings(context)?.let { intent ->
                runCatching { context.startActivity(intent) }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    permissionState: AlertsPermissionState = AlertsPermissionState(),
    onRequestNotifications: () -> Unit = {},
    onOpenExactAlarms: () -> Unit = {}
) {
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showAgeDialog by rememberSaveable { mutableStateOf(false) }
    var showNotificationRationale by rememberSaveable { mutableStateOf(false) }
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
                    onCheckedChange = { enabled ->
                        if (enabled && !permissionState.notificationsAllowed) {
                            // Explain before the system prompt: a denial on Android 13 costs two
                            // taps to undo, and the rationale is the only place we can say why.
                            showNotificationRationale = true
                        }
                        onIntent(SettingsIntent.SetNotificationsEnabled(enabled))
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

            if (profile.isNotificationEnabled) {
                when (permissionState.firstMissing) {
                    AlertsRequirement.NOTIFICATIONS -> PermissionNotice(
                        title = stringResource(R.string.settings_permission_notifications_title),
                        summary = stringResource(R.string.settings_permission_notifications_summary),
                        action = stringResource(R.string.settings_permission_notifications_action),
                        onClick = onRequestNotifications,
                        testTag = SettingsTestTags.PERMISSION_NOTICE,
                        actionTestTag = SettingsTestTags.PERMISSION_ACTION
                    )

                    AlertsRequirement.EXACT_ALARMS -> PermissionNotice(
                        title = stringResource(R.string.settings_permission_exact_alarm_title),
                        summary = stringResource(R.string.settings_permission_exact_alarm_summary),
                        action = stringResource(R.string.settings_permission_exact_alarm_action),
                        onClick = onOpenExactAlarms,
                        testTag = SettingsTestTags.EXACT_ALARM_NOTICE,
                        actionTestTag = SettingsTestTags.EXACT_ALARM_ACTION
                    )

                    null -> Unit
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
            // At 200% text size a three-segment row clips its labels, so the control reflows into a
            // vertical radio list. Selection semantics, touch targets and test tags stay identical.
            if (LocalDensity.current.fontScale >= LargeFontScale) {
                Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs)) {
                    ThermalSensitivity.entries.forEach { sensitivity ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = DesignTokens.MinTouchTarget)
                                .selectable(
                                    selected = profile.thermalSensitivity == sensitivity,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onIntent(SettingsIntent.SetThermalSensitivity(sensitivity))
                                    }
                                )
                                .testTag(sensitivityTestTag(sensitivity)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = profile.thermalSensitivity == sensitivity,
                                onClick = null
                            )
                            Text(
                                text = stringResource(sensitivityLabelRes(sensitivity)),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = DesignTokens.Spacing.sm)
                            )
                        }
                    }
                }
            } else {
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
                            modifier = Modifier.testTag(sensitivityTestTag(sensitivity))
                        ) {
                            Text(stringResource(sensitivityLabelRes(sensitivity)))
                        }
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

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_section_language))

            Text(
                text = stringResource(R.string.settings_language_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppLanguage.entries.forEach { language ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = DesignTokens.MinTouchTarget)
                        .selectable(
                            selected = state.language == language,
                            role = Role.RadioButton,
                            onClick = { onIntent(SettingsIntent.SetLanguage(language)) }
                        )
                        .testTag(languageTestTag(language)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.language == language,
                        onClick = null
                    )
                    Text(
                        text = stringResource(languageLabelRes(language)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = DesignTokens.Spacing.sm)
                    )
                }
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

    if (showNotificationRationale) {
        AlertDialog(
            onDismissRequest = { showNotificationRationale = false },
            title = { Text(stringResource(R.string.settings_permission_dialog_title)) },
            text = { Text(stringResource(R.string.settings_permission_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotificationRationale = false
                        onRequestNotifications()
                    },
                    modifier = Modifier.testTag(SettingsTestTags.PERMISSION_DIALOG_CONFIRM)
                ) {
                    Text(stringResource(R.string.settings_permission_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNotificationRationale = false },
                    modifier = Modifier.testTag(SettingsTestTags.PERMISSION_DIALOG_CANCEL)
                ) {
                    Text(stringResource(R.string.settings_permission_dialog_cancel))
                }
            }
        )
    }
}

/**
 * One missing capability, one action.
 *
 * Rendered as a card rather than a snackbar: the alert still looks enabled above it, so the notice
 * has to stay on screen until the user actually fixes the OS state.
 */
@Composable
private fun PermissionNotice(
    title: String,
    summary: String,
    action: String,
    onClick: () -> Unit,
    testTag: String,
    actionTestTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = summary, style = MaterialTheme.typography.bodySmall)
            TextButton(
                onClick = onClick,
                modifier = Modifier
                    .heightIn(min = DesignTokens.MinTouchTarget)
                    .testTag(actionTestTag)
            ) {
                Text(text = action)
            }
        }
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

/** Compose's `LocalContext` is usually the Activity, but it can be a themed wrapper. */
private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
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

private fun sensitivityTestTag(sensitivity: ThermalSensitivity): String = when (sensitivity) {
    ThermalSensitivity.FEELS_COLD_EASILY -> SettingsTestTags.SENSITIVITY_COLD
    ThermalSensitivity.NEUTRAL -> SettingsTestTags.SENSITIVITY_NEUTRAL
    ThermalSensitivity.FEELS_WARM_EASILY -> SettingsTestTags.SENSITIVITY_WARM
}

/** Font scale at which the segmented control reflows into a vertical radio list. */
private const val LargeFontScale = 1.5f

private fun genderLabelRes(gender: GenderPreference): Int = when (gender) {
    GenderPreference.ALL -> R.string.settings_gender_all
    GenderPreference.MEN -> R.string.settings_gender_men
    GenderPreference.WOMEN -> R.string.settings_gender_women
}

private fun languageLabelRes(language: AppLanguage): Int = when (language) {
    AppLanguage.SYSTEM -> R.string.settings_language_system
    AppLanguage.ENGLISH -> R.string.settings_language_english
    AppLanguage.VIETNAMESE -> R.string.settings_language_vietnamese
    AppLanguage.FRENCH -> R.string.settings_language_french
}

private fun languageTestTag(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> SettingsTestTags.LANGUAGE_SYSTEM
    AppLanguage.ENGLISH -> SettingsTestTags.LANGUAGE_ENGLISH
    AppLanguage.VIETNAMESE -> SettingsTestTags.LANGUAGE_VIETNAMESE
    AppLanguage.FRENCH -> SettingsTestTags.LANGUAGE_FRENCH
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
