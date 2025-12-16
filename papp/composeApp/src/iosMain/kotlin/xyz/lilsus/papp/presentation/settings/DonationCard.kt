package xyz.lilsus.papp.presentation.settings

import androidx.compose.runtime.Composable

@Composable
actual fun DonationCard(onDonate1k: () -> Unit, onDonate5k: () -> Unit, onDonate10k: () -> Unit) {
    /*
    Intentionally empty: donations disabled on iOS
    Reason: We noticed that your app allows users to contribute donations to the development of
    your app with a mechanism other than in-app purchase. Although these donations may be optional,
    they must use in-app purchase since they are associated with receiving digital content or services.
     */
}
