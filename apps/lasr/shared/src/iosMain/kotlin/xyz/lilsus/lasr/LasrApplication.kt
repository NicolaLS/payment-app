package xyz.lilsus.lasr

import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativeString

object LasrApplication {
    fun privacyCaptureMessage(): String =
        nativeString(NativeStringResource(table = "CoreUI", key = "privacy_capture_message"))

    fun createExperience() = NwcIosExperience(LASR_EXPERIENCE)
}
