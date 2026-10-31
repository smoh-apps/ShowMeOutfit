package com.smoh.showmeoutfit.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Layout tokens shared by all ShowMeOutfit components.
 *
 * Deliberately plain `dp` values instead of a Material `Shapes`/`Dimens` object so a future
 * hybrid theme (dynamic neutral surfaces + fixed weather accents) only has to change this file.
 */
object DesignTokens {

    /** 4dp-based spacing scale. */
    object Spacing {
        val none = 0.dp
        val xxs = 2.dp
        val xs = 4.dp
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 24.dp
        val xxl = 32.dp
    }

    /** Corner radii. Use [full] for pills/badges. */
    object Radius {
        val xs = 4.dp
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 24.dp
        val full = 999.dp
    }

    /** Elevation scale in dp. */
    object Elevation {
        val none = 0.dp
        val raised = 1.dp
        val floating = 3.dp
        val overlay = 6.dp
    }

    /** Icon box sizes. Pair with [MinTouchTarget] on the clickable wrapper. */
    object IconSize {
        val sm = 16.dp
        val md = 20.dp
        val lg = 24.dp
        val xl = 32.dp
    }

    /** WCAG 2.2 / Material minimum interactive target. */
    val MinTouchTarget = 48.dp

    /** Hairline border used for outlined surfaces. */
    val Hairline = 1.dp
}
