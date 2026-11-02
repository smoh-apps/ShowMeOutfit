package com.smoh.showmeoutfit.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.ui.theme.DesignTokens

object AppTestTags {
    const val STARTUP_LOADING = "app_startup_loading"
}

/**
 * The only screen shown while [AppViewModel] reads the persisted profile. It contains no action:
 * routing resolves in milliseconds, and a spinner cannot be mistaken for a dead end.
 */
@Composable
fun AppStartupLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(AppTestTags.STARTUP_LOADING),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.md))
        Text(
            text = stringResource(R.string.app_startup_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
