package xyz.lilsus.raylsuite.core.camera

/** Fixed camera intervals that may be sent to an explicitly installed performance service. */
enum class CameraPerformanceTrace(val traceName: String) {
    StartToReady("camera_start_to_ready"),
    StartToFirstFrame("camera_start_to_first_frame"),
    PreviewAttach("camera_preview_attach"),
    Stop("camera_stop")
}

fun interface CameraPerformanceTraceHandle {
    fun end()
}

fun interface CameraPerformanceReporter {
    fun begin(trace: CameraPerformanceTrace): CameraPerformanceTraceHandle?
}

private var installedCameraPerformanceReporter: CameraPerformanceReporter? = null

/**
 * Installs the process-wide camera timing reporter. Passing null disables remote reporting.
 *
 * The reporter receives only the enum above; camera and payment values cannot cross this API.
 */
fun installCameraPerformanceReporter(reporter: CameraPerformanceReporter?) {
    installedCameraPerformanceReporter = reporter
}

internal fun beginReportedCameraTrace(event: CameraTraceEvent): CameraPerformanceTraceHandle? =
    event.reportableTrace?.let { trace ->
        installedCameraPerformanceReporter?.begin(trace)
    }
