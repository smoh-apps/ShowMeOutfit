package com.smoh.showmeoutfit.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.AgeBand
import com.smoh.showmeoutfit.domain.model.GenderPreference
import com.smoh.showmeoutfit.domain.model.ThermalSensitivity
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import org.koin.androidx.compose.koinViewModel

/** Test tags so instrumented tests never depend on English copy. */
object OnboardingTestTags {
    const val SCREEN = "onboarding_screen"
    const val AGE_INPUT = "onboarding_age_input"
    const val AGE_ERROR = "onboarding_age_error"
    const val AGE_BAND = "onboarding_age_band"
    const val CONTINUE = "onboarding_continue"
    const val SKIP = "onboarding_skip"
    const val GENDER_ALL = "onboarding_gender_all"
    const val GENDER_MEN = "onboarding_gender_men"
    const val GENDER_WOMEN = "onboarding_gender_women"
    const val SENSITIVITY_COLD = "onboarding_sensitivity_cold"
    const val SENSITIVITY_NEUTRAL = "onboarding_sensitivity_neutral"
    const val SENSITIVITY_WARM = "onboarding_sensitivity_warm"
}

@Composable
fun OnboardingRoute(
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is OnboardingEffect.Completed) onCompleted()
        }
    }

    OnboardingScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier
    )
}

/**
 * Stateless first-run form.
 *
 * Every control is reachable from the arguments, so previews and Compose tests can drive the
 * whole screen without Koin, coroutines or DataStore.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.Spacing.lg)
                .testTag(OnboardingTestTags.SCREEN),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.lg)
        ) {
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.md))

            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            GenderSection(
                selected = state.gender,
                onSelect = { onIntent(OnboardingIntent.GenderSelected(it)) }
            )

            AgeSection(
                state = state,
                onAgeChanged = { onIntent(OnboardingIntent.AgeChanged(it)) }
            )

            SensitivitySection(
                selected = state.thermalSensitivity,
                onSelect = { onIntent(OnboardingIntent.SensitivitySelected(it)) }
            )

            if (state.saveFailed) {
                Text(
                    text = stringResource(R.string.onboarding_error_save),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = { onIntent(OnboardingIntent.Submit) },
                enabled = state.canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DesignTokens.MinTouchTarget)
                    .testTag(OnboardingTestTags.CONTINUE)
            ) {
                Text(
                    text = stringResource(
                        if (state.isSaving) R.string.onboarding_saving
                        else R.string.onboarding_get_started
                    )
                )
            }

            TextButton(
                onClick = { onIntent(OnboardingIntent.Skip) },
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DesignTokens.MinTouchTarget)
                    .testTag(OnboardingTestTags.SKIP)
            ) {
                Text(text = stringResource(R.string.onboarding_skip))
            }

            Text(
                text = stringResource(R.string.onboarding_footer_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.xl))
        }
    }
}

@Composable
private fun GenderSection(
    selected: GenderPreference,
    onSelect: (GenderPreference) -> Unit
) {
    SectionHeading(
        title = stringResource(R.string.onboarding_gender_title),
        helper = stringResource(R.string.onboarding_gender_helper)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm)
    ) {
        GenderPreference.entries.forEach { gender ->
            ChoiceCard(
                label = stringResource(OnboardingMessageMapper.genderLabelRes(gender)),
                selected = selected == gender,
                onClick = { onSelect(gender) },
                modifier = Modifier
                    .weight(1f)
                    .testTag(
                        when (gender) {
                            GenderPreference.ALL -> OnboardingTestTags.GENDER_ALL
                            GenderPreference.MEN -> OnboardingTestTags.GENDER_MEN
                            GenderPreference.WOMEN -> OnboardingTestTags.GENDER_WOMEN
                        }
                    )
            )
        }
    }
}

@Composable
private fun AgeSection(
    state: OnboardingUiState,
    onAgeChanged: (String) -> Unit
) {
    SectionHeading(
        title = stringResource(R.string.onboarding_age_title),
        helper = stringResource(R.string.onboarding_age_helper)
    )

    OutlinedTextField(
        value = state.ageInput,
        onValueChange = onAgeChanged,
        label = { Text(stringResource(R.string.onboarding_age_label)) },
        placeholder = { Text(stringResource(R.string.onboarding_age_placeholder)) },
        singleLine = true,
        isError = state.ageError != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = {
            val error = state.ageError
            if (error != null) {
                Text(
                    text = stringResource(OnboardingMessageMapper.ageErrorRes(error)),
                    modifier = Modifier.testTag(OnboardingTestTags.AGE_ERROR)
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(OnboardingTestTags.AGE_INPUT)
    )

    if (state.ageBand != AgeBand.UNSPECIFIED) {
        Text(
            text = stringResource(
                R.string.onboarding_age_band_label,
                stringResource(OnboardingMessageMapper.ageBandRes(state.ageBand))
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(OnboardingTestTags.AGE_BAND)
        )
    }
}

@Composable
private fun SensitivitySection(
    selected: ThermalSensitivity,
    onSelect: (ThermalSensitivity) -> Unit
) {
    SectionHeading(
        title = stringResource(R.string.onboarding_sensitivity_title),
        helper = stringResource(R.string.onboarding_sensitivity_helper)
    )

    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm)) {
        ThermalSensitivity.entries.forEach { sensitivity ->
            SelectionRow(
                label = stringResource(OnboardingMessageMapper.sensitivityLabelRes(sensitivity)),
                description = stringResource(
                    OnboardingMessageMapper.sensitivityDescriptionRes(sensitivity)
                ),
                selected = selected == sensitivity,
                onClick = { onSelect(sensitivity) },
                modifier = Modifier.testTag(
                    when (sensitivity) {
                        ThermalSensitivity.FEELS_COLD_EASILY -> OnboardingTestTags.SENSITIVITY_COLD
                        ThermalSensitivity.NEUTRAL -> OnboardingTestTags.SENSITIVITY_NEUTRAL
                        ThermalSensitivity.FEELS_WARM_EASILY -> OnboardingTestTags.SENSITIVITY_WARM
                    }
                )
            )
        }
    }
}

@Composable
private fun SectionHeading(title: String, helper: String) {
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xxs)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = helper,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChoiceCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .heightIn(min = DesignTokens.MinTouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else DesignTokens.Hairline,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = DesignTokens.Spacing.md)
        )
    }
}

@Composable
private fun SelectionRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DesignTokens.MinTouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else DesignTokens.Hairline,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xxs)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- Previews ----------------------------------------------------------------------------

@Preview(name = "Onboarding – empty", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun OnboardingEmptyPreview() {
    ShowMeOutfitTheme(dynamicColor = false) {
        OnboardingScreen(state = OnboardingUiState(), onIntent = {})
    }
}

@Preview(name = "Onboarding – filled", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun OnboardingFilledPreview() {
    ShowMeOutfitTheme(dynamicColor = false) {
        OnboardingScreen(
            state = OnboardingUiState(
                gender = GenderPreference.WOMEN,
                ageInput = "34",
                ageBand = AgeBand.ADULT_20_39,
                thermalSensitivity = ThermalSensitivity.FEELS_COLD_EASILY
            ),
            onIntent = {}
        )
    }
}

@Preview(name = "Onboarding – invalid age", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun OnboardingErrorPreview() {
    ShowMeOutfitTheme(dynamicColor = false) {
        OnboardingScreen(
            state = OnboardingUiState(
                ageInput = "150",
                ageError = AgeInputError.OUT_OF_RANGE
            ),
            onIntent = {}
        )
    }
}
