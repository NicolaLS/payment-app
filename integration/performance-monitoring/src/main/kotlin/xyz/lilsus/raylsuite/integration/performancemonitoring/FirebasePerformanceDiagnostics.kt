package xyz.lilsus.raylsuite.integration.performancemonitoring

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.perf.FirebasePerformance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xyz.lilsus.raylsuite.core.camera.CameraPerformanceReporter
import xyz.lilsus.raylsuite.core.camera.CameraPerformanceTrace
import xyz.lilsus.raylsuite.core.camera.CameraPerformanceTraceHandle
import xyz.lilsus.raylsuite.core.camera.installCameraPerformanceReporter
import xyz.lilsus.raylsuite.feature.settings.PerformanceDiagnostics

fun createFirebasePerformanceDiagnostics(context: Context): PerformanceDiagnostics? {
    val applicationContext = context.applicationContext
    if (FirebaseOptions.fromResource(applicationContext) == null) return null
    if (FirebaseApp.getApps(applicationContext).isEmpty()) {
        FirebaseApp.initializeApp(applicationContext) ?: return null
    }
    return FirebasePerformanceDiagnostics(applicationContext)
}

private class FirebasePerformanceDiagnostics(context: Context) : PerformanceDiagnostics {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableSharingEnabled =
        MutableStateFlow(preferences.getBoolean(SHARING_ENABLED_KEY, false))
    private val firebasePerformance = FirebasePerformance.getInstance()
    private val reporter = FirebaseCameraPerformanceReporter(firebasePerformance)

    override val sharingEnabled: StateFlow<Boolean> = mutableSharingEnabled

    init {
        applyCollectionState(mutableSharingEnabled.value)
    }

    override fun setSharingEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(SHARING_ENABLED_KEY, enabled).apply()
        mutableSharingEnabled.value = enabled
        applyCollectionState(enabled)
    }

    private fun applyCollectionState(enabled: Boolean) {
        firebasePerformance.isPerformanceCollectionEnabled = enabled
        installCameraPerformanceReporter(reporter.takeIf { enabled })
    }

    private companion object {
        const val PREFERENCES_NAME = "rayl_performance_diagnostics"
        const val SHARING_ENABLED_KEY = "sharing_enabled"
    }
}

private class FirebaseCameraPerformanceReporter(
    private val firebasePerformance: FirebasePerformance
) : CameraPerformanceReporter {
    override fun begin(trace: CameraPerformanceTrace): CameraPerformanceTraceHandle {
        val firebaseTrace = firebasePerformance.newTrace(trace.traceName)
        firebaseTrace.start()
        return CameraPerformanceTraceHandle(firebaseTrace::stop)
    }
}
