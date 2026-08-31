package xyz.lilsus.raylsuite.core.camera

internal enum class CameraTraceEvent(val traceName: String) {
    START_TO_READY("camera.start_to_ready"),
    START_TO_FIRST_FRAME("camera.start_to_first_frame"),
    CONFIGURE_SESSION("camera.configure_session"),
    FRAME_ANALYSIS("camera.frame_analysis"),
    PREVIEW_ATTACH("camera.preview_attach"),
    PREVIEW_STREAMING("camera.preview_streaming"),
    QR_DETECTED("camera.qr_detected"),
    RESTART("camera.restart"),
    STOP("camera.stop")
}
