package xyz.lilsus.blip

import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativeString

object BlipApplication {
    fun privacyCaptureMessage(): String =
        nativeString(NativeStringResource(table = "CoreUI", key = "privacy_capture_message"))

    fun createExperience() = BlinkIosExperience(BLIP_EXPERIENCE)
}
