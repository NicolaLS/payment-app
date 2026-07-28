package xyz.lilsus.blip.feature.payment

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun platformCurrentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()

private const val MILLIS_PER_SECOND = 1_000L
