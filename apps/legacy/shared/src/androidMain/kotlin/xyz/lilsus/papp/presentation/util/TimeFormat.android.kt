package xyz.lilsus.papp.presentation.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun formatTimeHHmm(epochMillis: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}
