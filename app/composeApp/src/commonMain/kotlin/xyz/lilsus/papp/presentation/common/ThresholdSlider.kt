package xyz.lilsus.papp.presentation.common

import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import xyz.lilsus.papp.domain.model.PaymentPreferences

/**
 * A discrete slider for selecting auto-pay threshold values.
 * Uses predefined steps from [PaymentPreferences.THRESHOLD_STEPS] for better UX.
 */
@Composable
fun ThresholdSlider(
    thresholdSats: Long,
    onThresholdChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val thresholdSteps = PaymentPreferences.THRESHOLD_STEPS

    Slider(
        value = PaymentPreferences.thresholdToStepIndex(thresholdSats).toFloat(),
        onValueChange = { sliderValue ->
            onThresholdChanged(thresholdSteps[sliderValue.toInt()])
        },
        valueRange = 0f..(thresholdSteps.size - 1).toFloat(),
        steps = thresholdSteps.size - 2,
        modifier = modifier
    )
}
