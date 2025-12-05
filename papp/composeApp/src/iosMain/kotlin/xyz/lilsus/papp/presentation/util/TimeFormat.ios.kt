package xyz.lilsus.papp.presentation.util

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970

actual fun formatTimeHHmm(epochMillis: Long): String {
    val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)
    val formatter = NSDateFormatter().apply {
        dateFormat = "HH:mm"
    }
    return formatter.stringFromDate(date)
}
