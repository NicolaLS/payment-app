package xyz.lilsus.rayl.foundation.ui.presentation.settings

import androidx.compose.runtime.Composable

@Composable
expect fun DonationCard(onDonate1k: () -> Unit, onDonate5k: () -> Unit, onDonate10k: () -> Unit)
