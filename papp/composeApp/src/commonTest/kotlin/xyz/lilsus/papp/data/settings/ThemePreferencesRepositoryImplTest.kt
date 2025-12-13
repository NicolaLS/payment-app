package xyz.lilsus.papp.data.settings

import com.russhwolf.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import xyz.lilsus.papp.domain.model.ThemePreference

class ThemePreferencesRepositoryImplTest {

    @Test
    fun defaultsToSystem_whenUnset() = runTest {
        val settings = InMemorySettings()
        val repository = ThemePreferencesRepositoryImpl(settings)

        assertEquals(ThemePreference.System, repository.getThemePreference())
    }

    @Test
    fun persistsLightPreference() = runTest {
        val settings = InMemorySettings()
        val repository = ThemePreferencesRepositoryImpl(settings)

        repository.setThemePreference(ThemePreference.Light)

        assertEquals(ThemePreference.Light, repository.getThemePreference())
        assertEquals(ThemePreference.Light, ThemePreferencesRepositoryImpl(settings).getThemePreference())
    }

    @Test
    fun clearingToSystemRemovesOverride() = runTest {
        val settings = InMemorySettings()
        val repository = ThemePreferencesRepositoryImpl(settings)

        repository.setThemePreference(ThemePreference.Dark)
        repository.setThemePreference(ThemePreference.System)

        assertEquals(ThemePreference.System, ThemePreferencesRepositoryImpl(settings).getThemePreference())
    }
}

private class InMemorySettings : Settings {
    private val data = mutableMapOf<String, Any>()

    override val keys: Set<String>
        get() = data.keys

    override val size: Int
        get() = data.size

    override fun clear() {
        data.clear()
    }

    override fun remove(key: String) {
        data.remove(key)
    }

    override fun hasKey(key: String): Boolean = data.containsKey(key)

    override fun putInt(key: String, value: Int) {
        data[key] = value
    }

    override fun getInt(key: String, defaultValue: Int): Int = (data[key] as? Int) ?: defaultValue

    override fun getIntOrNull(key: String): Int? = data[key] as? Int

    override fun putLong(key: String, value: Long) {
        data[key] = value
    }

    override fun getLong(key: String, defaultValue: Long): Long = (data[key] as? Long) ?: defaultValue

    override fun getLongOrNull(key: String): Long? = data[key] as? Long

    override fun putString(key: String, value: String) {
        data[key] = value
    }

    override fun getString(key: String, defaultValue: String): String = (data[key] as? String) ?: defaultValue

    override fun getStringOrNull(key: String): String? = data[key] as? String

    override fun putFloat(key: String, value: Float) {
        data[key] = value
    }

    override fun getFloat(key: String, defaultValue: Float): Float = (data[key] as? Float) ?: defaultValue

    override fun getFloatOrNull(key: String): Float? = data[key] as? Float

    override fun putDouble(key: String, value: Double) {
        data[key] = value
    }

    override fun getDouble(key: String, defaultValue: Double): Double = (data[key] as? Double) ?: defaultValue

    override fun getDoubleOrNull(key: String): Double? = data[key] as? Double

    override fun putBoolean(key: String, value: Boolean) {
        data[key] = value
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = (data[key] as? Boolean) ?: defaultValue

    override fun getBooleanOrNull(key: String): Boolean? = data[key] as? Boolean
}
