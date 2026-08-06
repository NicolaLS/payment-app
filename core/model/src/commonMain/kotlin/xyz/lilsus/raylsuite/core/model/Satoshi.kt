package xyz.lilsus.raylsuite.core.model

import kotlin.jvm.JvmInline

@JvmInline
value class Satoshi private constructor(val value: Long) {
    override fun toString(): String = "$value sat"

    operator fun plus(other: Satoshi): Satoshi {
        require(value <= Long.MAX_VALUE - other.value) { "Satoshi sum overflow" }
        return positive(value + other.value)
    }

    companion object {
        fun positive(value: Long): Satoshi {
            require(value > 0) { "Satoshi amount must be positive" }
            return Satoshi(value)
        }

        fun nonNegative(value: Long): Satoshi {
            require(value >= 0) { "Satoshi amount must not be negative" }
            return Satoshi(value)
        }
    }
}
