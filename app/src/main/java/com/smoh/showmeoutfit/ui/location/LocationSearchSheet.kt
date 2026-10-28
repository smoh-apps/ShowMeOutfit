package com.smoh.showmeoutfit.ui.location

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSearchSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocationSearchViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val activity = context.findActivity()
    var showRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            viewModel.onIntent(LocationSearchIntent.UseCurrentLocation)
        } else {
            viewModel.onIntent(LocationSearchIntent.LocationPermissionDenied)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LocationSearchEffect.LocationSelected -> onDismissRequest()
                LocationSearchEffect.OpenAppSettings -> context.openAppSettings()
            }
        }
    }

    // Only actionable after a denial: settings is the sole recovery path once the system stops
    // showing the permission dialog.
    val permanentlyDenied = state.permissionDenied &&
        activity != null &&
        LocationPermissions.isPermanentlyDenied(activity)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier
    ) {
        LocationSearchContent(
            state = state,
            onQueryChange = { viewModel.onIntent(LocationSearchIntent.QueryChanged(it)) },
            onSubmit = { viewModel.onIntent(LocationSearchIntent.SearchNow) },
            onClearQuery = { viewModel.onIntent(LocationSearchIntent.QueryChanged("")) },
            onUseCurrentLocation = {
                if (LocationPermissions.isGranted(context)) {
                    viewModel.onIntent(LocationSearchIntent.UseCurrentLocation)
                } else {
                    showRationale = true
                }
            },
            onSelect = { viewModel.onIntent(LocationSearchIntent.SelectLocation(it)) },
            onRetry = { viewModel.onIntent(LocationSearchIntent.SearchNow) },
            onDismiss = onDismissRequest,
            onOpenSettings = if (permanentlyDenied) {
                { context.openAppSettings() }
            } else {
                null
            }
        )
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(stringResource(R.string.location_search_permission_title)) },
            text = { Text(stringResource(R.string.location_search_permission_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRationale = false
                        permissionLauncher.launch(LocationPermissions.requested)
                    }
                ) {
                    Text(stringResource(R.string.location_search_permission_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) {
                    Text(stringResource(R.string.location_search_permission_not_now))
                }
            }
        )
    }
}

/**
 * Stateless sheet content. Rendering is separated from the ViewModel so it can be driven by
 * Compose tests and previews with hand-written state.
 */
@Composable
fun LocationSearchContent(
    state: LocationSearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClearQuery: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onSelect: (LocationInfo) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.lg)
            .padding(bottom = DesignTokens.Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.location_search_title),
                style = MaterialTheme.typography.titleLarge
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.location_search_dismiss))
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.location_search_hint)) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Search,
                capitalization = KeyboardCapitalization.Words
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { onSubmit() }
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onUseCurrentLocation,
                enabled = !state.isLocating,
                modifier = Modifier.heightIn(min = DesignTokens.MinTouchTarget)
            ) {
                Text(stringResource(R.string.location_search_use_current))
            }
            if (state.isLocating) {
                CircularProgressIndicator(modifier = Modifier.size(DesignTokens.IconSize.sm))
                Text(
                    text = stringResource(R.string.location_search_locating),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (state.query.isNotEmpty()) {
                TextButton(onClick = onClearQuery) {
                    Text(stringResource(R.string.location_search_clear))
                }
            }
        }

        state.error?.let { error ->
            LocationErrorMessage(
                error = error,
                onRetry = onRetry,
                onOpenSettings = onOpenSettings
            )
        }

        when {
            state.isSearching -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(DesignTokens.IconSize.sm))
                Text(
                    text = stringResource(R.string.location_search_searching),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            state.results.isNotEmpty() -> {
                Text(
                    text = pluralStringResource(
                        R.plurals.location_search_result_count,
                        state.results.size,
                        state.results.size
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(state.results, key = { "${it.latitude},${it.longitude},${it.cityName}" }) { location ->
                        LocationResultRow(location = location, onSelect = onSelect)
                        HorizontalDivider()
                    }
                }
            }

            state.showsEmptyResults -> Text(
                text = stringResource(R.string.location_search_empty, state.query)
            )

            else -> Text(
                text = stringResource(R.string.location_search_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LocationResultRow(
    location: LocationInfo,
    onSelect: (LocationInfo) -> Unit
) {
    val cityLabel = if (location.country.isNullOrBlank()) {
        location.cityName
    } else {
        stringResource(R.string.location_search_city_with_country, location.cityName, location.country)
    }
    val region = location.adminRegion?.takeIf { it.isNotBlank() }
    // Read as one sentence: "Berlin, Germany. Berlin. Double tap to use this city."
    val description = stringResource(
        R.string.location_search_result_content_description,
        listOfNotNull(cityLabel, region).joinToString(". ")
    )

    ListItem(
        headlineContent = { Text(cityLabel) },
        supportingContent = if (region != null) {
            { Text(region) }
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DesignTokens.MinTouchTarget)
            .clickable { onSelect(location) }
            .semantics { contentDescription = description }
    )
}

@Composable
private fun LocationErrorMessage(
    error: DomainError,
    onRetry: () -> Unit,
    onOpenSettings: (() -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs)) {
        Text(
            text = error.messageRes(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm)) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.location_search_retry))
            }
            if (onOpenSettings != null) {
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.location_search_permission_open_settings))
                }
            }
        }
    }
}

@Composable
private fun DomainError.messageRes(): String = when (this) {
    is DomainError.Offline -> stringResource(R.string.location_search_error_offline)
    is DomainError.ApiFailure -> stringResource(R.string.location_search_error_api)
    is DomainError.MalformedData -> stringResource(R.string.location_search_error_malformed)
    is DomainError.LocationPermissionDenied -> stringResource(R.string.location_search_error_permission_denied)
    is DomainError.LocationDisabled -> stringResource(R.string.location_search_error_disabled)
    is DomainError.LocationUnavailable,
    is DomainError.NoLocation -> stringResource(R.string.location_search_error_unavailable)
    is DomainError.CacheExpired,
    is DomainError.Unknown -> stringResource(R.string.location_search_error_unknown)
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

@Preview(name = "Results", showBackground = true)
@Composable
private fun LocationSearchResultsPreview() {
    ShowMeOutfitTheme(dynamicColor = false) {
        LocationSearchContent(
            state = LocationSearchUiState(
                query = "Ber",
                hasSearched = true,
                results = listOf(
                    LocationInfo(52.52, 13.405, "Berlin", "Germany", "Berlin", "Europe/Berlin"),
                    LocationInfo(52.51, 13.40, "Berlin Köpenick", "Germany", "Berlin", "Europe/Berlin")
                )
            ),
            onQueryChange = {},
            onSubmit = {},
            onClearQuery = {},
            onUseCurrentLocation = {},
            onSelect = {},
            onRetry = {},
            onDismiss = {}
        )
    }
}

@Preview(name = "Permission denied", showBackground = true)
@Composable
private fun LocationSearchDeniedPreview() {
    ShowMeOutfitTheme(dynamicColor = false) {
        LocationSearchContent(
            state = LocationSearchUiState(
                error = DomainError.LocationPermissionDenied(),
                permissionDenied = true
            ),
            onQueryChange = {},
            onSubmit = {},
            onClearQuery = {},
            onUseCurrentLocation = {},
            onSelect = {},
            onRetry = {},
            onDismiss = {}
        )
    }
}
