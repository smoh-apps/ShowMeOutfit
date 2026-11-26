package com.smoh.showmeoutfit.ui.wardrobe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GarmentId
import com.smoh.showmeoutfit.domain.model.LayerCategory
import com.smoh.showmeoutfit.ui.home.GarmentArtwork
import com.smoh.showmeoutfit.ui.home.GarmentTextMapper
import com.smoh.showmeoutfit.ui.home.GarmentVisualMapper
import com.smoh.showmeoutfit.ui.theme.DesignTokens

private val PreviewSize = 160.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeItemEditorSheet(
    editor: WardrobeEditorState,
    onIntent: (WardrobeIntent) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resources = LocalResources.current
    var categoryExpanded by rememberSaveable { mutableStateOf(false) }
    var garmentExpanded by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag(WardrobeTestTags.EDITOR)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(
                    start = DesignTokens.Spacing.xl,
                    end = DesignTokens.Spacing.xl,
                    bottom = DesignTokens.Spacing.xxl
                ),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md)
        ) {
            Text(
                text = stringResource(
                    if (editor.isExisting) R.string.wardrobe_editor_edit_title
                    else R.string.wardrobe_editor_add_title
                ),
                style = MaterialTheme.typography.headlineSmall
            )

            GarmentArtwork(
                source = GarmentVisualMapper.garmentVisualSource(editor.garmentId, editor.fit),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(PreviewSize)
                    .testTag(WardrobeTestTags.PREVIEW)
            )

            WardrobeDropdown(
                label = stringResource(R.string.wardrobe_editor_category_label),
                value = stringResource(categoryLabelRes(editor.category)),
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it },
                selectorTag = WardrobeTestTags.CATEGORY_SELECTOR,
                options = LayerCategory.entries,
                optionLabel = { category -> stringResource(categoryLabelRes(category)) },
                optionTag = WardrobeTestTags::categoryOption,
                onSelect = { category ->
                    val garmentId = editor.garmentId.takeIf {
                        WardrobeCatalog.categoryFor(it) == category
                    } ?: WardrobeCatalog.garmentsFor(category).first()
                    val defaultName = resources.getString(
                        GarmentTextMapper.garmentNameRes(garmentId, editor.fit)
                    )
                    onIntent(WardrobeIntent.UpdateEditorCategory(category, defaultName))
                }
            )

            WardrobeDropdown(
                label = stringResource(R.string.wardrobe_editor_garment_label),
                value = stringResource(
                    GarmentTextMapper.garmentNameRes(editor.garmentId, editor.fit)
                ),
                expanded = garmentExpanded,
                onExpandedChange = { garmentExpanded = it },
                selectorTag = WardrobeTestTags.GARMENT_SELECTOR,
                options = WardrobeCatalog.garmentsFor(editor.category),
                optionLabel = { garmentId ->
                    stringResource(GarmentTextMapper.garmentNameRes(garmentId, editor.fit))
                },
                optionTag = WardrobeTestTags::garmentOption,
                onSelect = { garmentId ->
                    val defaultName = resources.getString(
                        GarmentTextMapper.garmentNameRes(garmentId, editor.fit)
                    )
                    onIntent(WardrobeIntent.UpdateEditorGarment(garmentId, defaultName))
                }
            )

            Text(
                text = stringResource(R.string.wardrobe_editor_fit_label),
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm)
            ) {
                GarmentFit.entries.forEach { fit ->
                    FilterChip(
                        selected = editor.fit == fit,
                        onClick = {
                            val defaultName = resources.getString(
                                GarmentTextMapper.garmentNameRes(editor.garmentId, fit)
                            )
                            onIntent(WardrobeIntent.UpdateEditorFit(fit, defaultName))
                        },
                        label = { Text(stringResource(fitLabelRes(fit))) },
                        modifier = Modifier
                            .heightIn(min = DesignTokens.MinTouchTarget)
                            .testTag(WardrobeTestTags.fit(fit))
                    )
                }
            }

            OutlinedTextField(
                value = editor.name,
                onValueChange = { onIntent(WardrobeIntent.UpdateEditorName(it)) },
                label = { Text(stringResource(R.string.wardrobe_editor_name_label)) },
                placeholder = {
                    if (editor.defaultName.isNotBlank()) Text(editor.defaultName)
                },
                supportingText = {
                    Text(
                        stringResource(
                            when (editor.validationError) {
                                WardrobeNameError.TOO_LONG -> R.string.wardrobe_editor_name_too_long
                                WardrobeNameError.DEFAULT_NAME_MISSING ->
                                    R.string.wardrobe_editor_name_required
                                null -> R.string.wardrobe_editor_name_helper
                            }
                        )
                    )
                },
                isError = editor.validationError != null,
                singleLine = true,
                enabled = !editor.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WardrobeTestTags.NAME_INPUT)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DesignTokens.MinTouchTarget)
                    .toggleable(
                        value = editor.isWaterResistant,
                        enabled = !editor.isSaving,
                        role = Role.Switch,
                        onValueChange = {
                            onIntent(WardrobeIntent.UpdateEditorWaterResistant(it))
                        }
                    )
                    .testTag(WardrobeTestTags.WATER_SWITCH),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.wardrobe_editor_water_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = editor.isWaterResistant,
                    onCheckedChange = null,
                    enabled = !editor.isSaving
                )
            }

            Button(
                onClick = { onIntent(WardrobeIntent.SaveEditor) },
                enabled = !editor.isSaving && editor.validationError == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DesignTokens.MinTouchTarget)
                    .testTag(WardrobeTestTags.SAVE)
            ) {
                Text(stringResource(R.string.wardrobe_editor_save))
            }

            if (editor.isExisting) {
                TextButton(
                    onClick = { onIntent(WardrobeIntent.DeleteEditor) },
                    enabled = !editor.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = DesignTokens.MinTouchTarget)
                        .testTag(WardrobeTestTags.DELETE)
                ) {
                    Text(
                        text = stringResource(R.string.wardrobe_editor_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> WardrobeDropdown(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    selectorTag: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    optionTag: (T) -> String,
    onSelect: (T) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DesignTokens.MinTouchTarget)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .testTag(selectorTag)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onExpandedChange(false)
                        onSelect(option)
                    },
                    modifier = Modifier
                        .heightIn(min = DesignTokens.MinTouchTarget)
                        .testTag(optionTag(option))
                )
            }
        }
    }
}

@androidx.annotation.StringRes
private fun fitLabelRes(fit: GarmentFit): Int = when (fit) {
    GarmentFit.UNISEX -> R.string.wardrobe_fit_unisex
    GarmentFit.MEN -> R.string.wardrobe_fit_men
    GarmentFit.WOMEN -> R.string.wardrobe_fit_women
}
