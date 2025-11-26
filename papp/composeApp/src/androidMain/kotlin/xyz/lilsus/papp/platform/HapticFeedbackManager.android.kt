package xyz.lilsus.papp.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import xyz.lilsus.papp.PappApplication

private const val LIGHT_DURATION_MS = 25L
private const val STRONG_PULSE_MS = 40L
private const val STRONG_GAP_MS = 25L

private class AndroidHapticFeedbackManager(private val context: Context) : HapticFeedbackManager {

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
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0L, STRONG_PULSE_MS, STRONG_GAP_MS, STRONG_PULSE_MS)
            val amplitudes = intArrayOf(0, 220, 0, 220)
            vib.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
        }
    }

    private fun vibrate(durationMs: Long, amplitude: Int) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        }
    }
}

actual fun createHapticFeedbackManager(): HapticFeedbackManager = AndroidHapticFeedbackManager(PappApplication.instance)
