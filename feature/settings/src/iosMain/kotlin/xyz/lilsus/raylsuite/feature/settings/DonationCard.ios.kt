package xyz.lilsus.raylsuite.feature.settings

import androidx.compose.runtime.Composable

@Composable
actual fun DonationCard(
    appName: String,
    onDonate1k: () -> Unit,
    onDonate5k: () -> Unit,
    onDonate10k: () -> Unit
) {
    // Donations remain disabled on iOS to preserve the existing App Store behavior.
}
