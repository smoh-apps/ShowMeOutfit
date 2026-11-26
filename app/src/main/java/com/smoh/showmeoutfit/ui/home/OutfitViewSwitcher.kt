package com.smoh.showmeoutfit.ui.home

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.smoh.showmeoutfit.R

/** The two supported presentations of the same recommendation. */
enum class OutfitViewMode { LOOK, GRID }

/**
 * Material 3 single-choice switcher between the editorial lookbook and the layered grid.
 *
 * LOOK is the default. Home disables it at large font scales, where ADR-DESIGN-V2 D4 requires the
 * more readable one-column grid; the selected state still reports the mode actually on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitViewSwitcher(
    onModeChange: (OutfitViewMode) -> Unit,
    modifier: Modifier = Modifier,
    mode: OutfitViewMode = OutfitViewMode.LOOK,
    lookEnabled: Boolean = true
) {
    val options = listOf(
        OutfitViewMode.LOOK to R.string.home_outfit_view_look,
        OutfitViewMode.GRID to R.string.home_outfit_view_grid
    )
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.testTag(HomeTestTags.OUTFIT_VIEW_SWITCHER)
    ) {
        options.forEachIndexed { index, (option, labelRes) ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onModeChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                enabled = option != OutfitViewMode.LOOK || lookEnabled,
                label = { Text(text = stringResource(labelRes)) }
            )
        }
    }
}
