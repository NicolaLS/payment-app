package xyz.lilsus.raylsuite.core.camera

import androidx.tracing.Trace
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal object AndroidCameraTrace {
    private val nextCookie = AtomicInteger()

    fun beginInterval(event: CameraTraceEvent): AndroidCameraTraceInterval? {
        val reportedTrace = beginReportedCameraTrace(event)
        val cookie = if (Trace.isEnabled()) nextCookie.incrementAndGet() else null
        cookie?.let { Trace.beginAsyncSection(event.traceName, it) }
        if (cookie == null && reportedTrace == null) return null
        return AndroidCameraTraceInterval(event, cookie, reportedTrace)
    }

    fun event(event: CameraTraceEvent) {
        if (!Trace.isEnabled()) return
        Trace.beginSection(event.traceName)
        Trace.endSection()
    }
}

internal class AndroidCameraTraceInterval(
    private val event: CameraTraceEvent,
    private val cookie: Int?,
    private val reportedTrace: CameraPerformanceTraceHandle?
) {
    private val ended = AtomicBoolean(false)

    fun end() {
        if (ended.compareAndSet(false, true)) {
            cookie?.let { Trace.endAsyncSection(event.traceName, it) }
            reportedTrace?.end()
        }
    }
}
