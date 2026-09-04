package xyz.lilsus.raylsuite.core.camera

internal class QrPresenceGate(
    private val absentObservationsToRearm: Int = DEFAULT_ABSENT_OBSERVATIONS_TO_REARM
) {
    private var blockedValue: String? = null
    private var absentObservations = 0

    init {
        require(absentObservationsToRearm > 0) {
            "absentObservationsToRearm must be positive"
        }
    }

    fun observe(value: String?): String? {
        if (value == null) {
            if (blockedValue != null) {
                absentObservations++
                if (absentObservations >= absentObservationsToRearm) {
                    blockedValue = null
                    absentObservations = 0
                }
            }
            return null
        }

        absentObservations = 0
        if (value == blockedValue) return null
        blockedValue = value
        return value
    }

    fun reset() {
        blockedValue = null
        absentObservations = 0
    }

    private companion object {
        const val DEFAULT_ABSENT_OBSERVATIONS_TO_REARM = 3
    }
}
