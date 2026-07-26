package xyz.lilsus.raylsuite.core.ui.platform

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun formatTimeHHmm(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))
