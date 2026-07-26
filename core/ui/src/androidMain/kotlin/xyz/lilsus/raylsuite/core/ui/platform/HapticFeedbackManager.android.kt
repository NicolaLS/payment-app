package xyz.lilsus.raylsuite.core.ui.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private class AndroidHapticFeedbackManager(private val context: Context) :
    HapticFeedbackManager {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun notifyScanSuccess() {
        vibrate(durationMs = LIGHT_DURATION_MS, amplitude = 128)
    }

    override fun notifyPaymentSuccess() {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0L, STRONG_PULSE_MS, STRONG_GAP_MS, STRONG_PULSE_MS),
                    intArrayOf(0, 220, 0, 220),
                    -1
                )
            )
        }
    }

    private fun vibrate(durationMs: Long, amplitude: Int) {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        }
    }
}

@Composable
actual fun rememberHapticFeedbackManager(): HapticFeedbackManager {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        AndroidHapticFeedbackManager(context)
    }
}

private const val LIGHT_DURATION_MS = 25L
private const val STRONG_PULSE_MS = 40L
private const val STRONG_GAP_MS = 25L
