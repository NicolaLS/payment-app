package xyz.lilsus.raylsuite.feature.settings

import androidx.compose.runtime.Composable

@Composable
expect fun DonationCard(
    appName: String,
    onDonate1k: () -> Unit,
    onDonate5k: () -> Unit,
    onDonate10k: () -> Unit
)
