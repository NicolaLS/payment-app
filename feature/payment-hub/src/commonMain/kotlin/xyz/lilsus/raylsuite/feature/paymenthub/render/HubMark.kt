package xyz.lilsus.raylsuite.feature.paymenthub.render

/** One initial for a single word, two when the name has more. */
fun hubInitials(label: String): String {
    val words = label.trim().split(' ', '\t', '\n').filter(String::isNotBlank)
    return when (words.size) {
        0 -> "?"
        1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}
