package xyz.lilsus.papp.domain.util

/**
 * Decodes a URL-encoded component (percent-encoding and '+' for spaces).
 * Returns the original value if decoding fails.
 */
fun decodeUrlComponent(value: String): String {
    if (value.none { it == '%' || it == '+' }) return value
    val sb = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        when (val ch = value[index]) {
            '+' -> {
                sb.append(' ')
                index += 1
            }

            '%' -> {
                if (index + 2 >= value.length) {
                    return value
                }
                val hex = value.substring(index + 1, index + 3)
                val decoded = hex.toIntOrNull(16) ?: return value
                sb.append(decoded.toChar())
                index += 3
            }

            else -> {
                sb.append(ch)
                index += 1
            }
        }
    }
    return sb.toString()
}
