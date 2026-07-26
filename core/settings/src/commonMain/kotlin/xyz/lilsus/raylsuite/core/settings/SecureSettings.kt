package xyz.lilsus.raylsuite.core.settings

import androidx.compose.runtime.Composable
import com.russhwolf.settings.Settings

/**
 * Returns encrypted app-scoped storage for wallet credentials.
 *
 * [storageName] must be unique to the app and credential purpose. It is used
 * as the Android preferences/key alias and the Apple Keychain service.
 */
@Composable
expect fun rememberSecureSettings(storageName: String): Settings
