package com.smoh.showmeoutfit.ui.wardrobe

import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GarmentId
import com.smoh.showmeoutfit.domain.model.LayerCategory
import com.smoh.showmeoutfit.domain.model.WardrobeItem

/** Top-level closet filter. A null category means every owned item. */
enum class WardrobeFilter(val category: LayerCategory?) {
    ALL(null),
    BASE(LayerCategory.BASE),
    MID(LayerCategory.MID),
    OUTER(LayerCategory.OUTER),
    BOTTOM(LayerCategory.BOTTOM),
    FOOTWEAR(LayerCategory.FOOTWEAR)
}

enum class WardrobeNameError {
    DEFAULT_NAME_MISSING,
    TOO_LONG
}

/**
 * Transient editor form. [existingItem] is retained so saving never loses the stable id, creation
 * timestamp, or a photo path added by a later sprint.
 */
data class WardrobeEditorState(
    val existingItem: WardrobeItem? = null,
    val category: LayerCategory = LayerCategory.BASE,
    val garmentId: GarmentId = WardrobeCatalog.garmentsFor(LayerCategory.BASE).first(),
    val fit: GarmentFit = GarmentFit.UNISEX,
    val name: String = "",
    val defaultName: String = "",
    val hasCustomName: Boolean = false,
    val isWaterResistant: Boolean = false,
    val isSaving: Boolean = false
) {
    val isExisting: Boolean get() = existingItem != null

    val validationError: WardrobeNameError?
        get() = when {
            name.trim().length > MAX_WARDROBE_NAME_LENGTH -> WardrobeNameError.TOO_LONG
            name.trim().isEmpty() && defaultName.trim().isEmpty() ->
                WardrobeNameError.DEFAULT_NAME_MISSING
            else -> null
        }

    /** The exact value persisted: a trimmed custom label, otherwise the localized catalog name. */
    val resolvedName: String?
        get() = if (validationError != null) null else name.trim().ifBlank { defaultName.trim() }
}

data class WardrobeUiState(
    val items: List<WardrobeItem> = emptyList(),
    val selectedFilter: WardrobeFilter = WardrobeFilter.ALL,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val editor: WardrobeEditorState? = null
)

sealed interface WardrobeIntent {
    data class SelectFilter(val filter: WardrobeFilter) : WardrobeIntent

    /** [defaultName] is resolved by the active locale in Compose, keeping the ViewModel JVM-pure. */
    data class OpenAdd(val category: LayerCategory, val defaultName: String) : WardrobeIntent

    data class OpenEdit(val item: WardrobeItem, val defaultName: String) : WardrobeIntent

    data class UpdateEditorCategory(
        val category: LayerCategory,
        val defaultName: String
    ) : WardrobeIntent

    data class UpdateEditorGarment(
        val garmentId: GarmentId,
        val defaultName: String
    ) : WardrobeIntent

    data class UpdateEditorFit(val fit: GarmentFit, val defaultName: String) : WardrobeIntent

    data class UpdateEditorName(val name: String) : WardrobeIntent

    data class UpdateEditorWaterResistant(val isWaterResistant: Boolean) : WardrobeIntent

    data object SaveEditor : WardrobeIntent

    data object DeleteEditor : WardrobeIntent

    data object DismissEditor : WardrobeIntent

    data object RetryLoad : WardrobeIntent
}

enum class WardrobeError {
    LOAD_FAILED,
    SAVE_FAILED,
    DELETE_FAILED
}

sealed interface WardrobeEffect {
    data object ItemSaved : WardrobeEffect
    data object ItemDeleted : WardrobeEffect
    data class ShowError(val error: WardrobeError) : WardrobeEffect
}

const val MAX_WARDROBE_NAME_LENGTH = 80
