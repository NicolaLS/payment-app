package xyz.lilsus.raylsuite.feature.paymenthub.render

import androidx.compose.runtime.Immutable
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.feature.paymenthub.HubAccent
import xyz.lilsus.raylsuite.feature.paymenthub.HubIcon

/**
 * The square mark a tile or row draws. It carries initials plus the bundled icon and accent the
 * user picked; each platform decides how to paint them.
 */
@Immutable
data class HubMark(val initials: String, val icon: HubIcon? = null, val accent: HubAccent? = null)

/** One initial for a single word, two when the name has more. */
fun hubInitials(label: String): String {
    val words = label.trim().split(' ', '\t', '\n').filter(String::isNotBlank)
    return when (words.size) {
        0 -> "?"
        1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/**
 * The bottom line of a tile is its whole trigger grammar: it says what tapping will do. A preset
 * amount still confirms before it pays, so there is no instant-send state here.
 */
@Immutable
sealed interface HubAmountLine {
    /** No preset. Tapping opens the amount entry, the same flow as scanning an address. */
    data object AskEachTime : HubAmountLine

    data class Preset(val amount: DisplayAmount) : HubAmountLine
}
