package com.smoh.showmeoutfit.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.AccessoryAlert
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import java.util.Locale

/**
 * Compact, tappable accessory pill: artwork plus a localized name and trigger.
 *
 * The visible text and the single merged TalkBack label carry the same information. Text is allowed
 * to wrap (rather than ellipsize) so long Vietnamese and French labels remain complete at 200%.
 */
@Composable
fun AccessoryTriggerChip(
    alert: AccessoryAlert,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = stringResource(GarmentTextMapper.accessoryNameRes(alert.type))
    val trigger = GarmentTextMapper.accessoryCompactTrigger(alert.reason)
    val triggerText = stringResource(trigger.resId, *trigger.resolve(Locale.getDefault()))
    val label = stringResource(R.string.home_accessory_trigger_format, name, triggerText)
    val description = stringResource(
        R.string.home_accessory_trigger_content_description,
        name,
        triggerText
    )

    Surface(
        modifier = modifier
            .heightIn(min = DesignTokens.MinTouchTarget)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        shape = RoundedCornerShape(DesignTokens.Radius.full),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(DesignTokens.Hairline, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = DesignTokens.Spacing.md,
                vertical = DesignTokens.Spacing.sm
            ),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GarmentArtwork(
                source = GarmentVisualMapper.accessoryVisualSource(alert.type),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                contentPadding = 0.dp,
                showStudioMat = false
            )
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Accessory pills wrap naturally, one full-width pill per row at large font scales. */
@Composable
fun AccessoryTriggerChips(
    alerts: List<AccessoryAlert>,
    onAccessoryClick: (AccessoryAlert) -> Unit,
    modifier: Modifier = Modifier
) {
    if (alerts.isEmpty()) {
        Text(
            text = stringResource(R.string.home_accessories_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }

    val largeFont = LocalDensity.current.fontScale >= LargeFontScale
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        maxItemsInEachRow = if (largeFont) 1 else 2
    ) {
        alerts.forEach { alert ->
            AccessoryTriggerChip(
                alert = alert,
                onClick = { onAccessoryClick(alert) },
                modifier = if (largeFont) Modifier.weight(1f) else Modifier
            )
        }
    }
}
