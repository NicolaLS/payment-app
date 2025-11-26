package xyz.lilsus.papp.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import xyz.lilsus.papp.PappApplication

private const val KEY_ALIAS = "wallet_secure_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val KEYSTORE = "AndroidKeyStore"
private const val PREF_NAME = "secure_wallet_settings"

actual fun createSecureSettings(): Settings {
    val context = PappApplication.instance.applicationContext
    val delegate = SharedPreferencesSettings(
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    )
    return EncryptedSettings(delegate)
}

private class EncryptedSettings(private val delegate: Settings) : Settings by delegate {
    override fun putString(key: String, value: String) {
        delegate.putString(key, encrypt(value))
    }

    override fun getString(key: String, defaultValue: String): String = getStringOrNull(key) ?: defaultValue

    override fun getStringOrNull(key: String): String? {
        val encrypted = delegate.getStringOrNull(key) ?: return null
        return runCatching { decrypt(encrypted) }.getOrNull()
    }
}

private fun encrypt(value: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION).apply {
        init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
    }
    val ciphertext = cipher.doFinal(value.encodeToByteArray())
    val combined = cipher.iv + ciphertext
    return Base64.encodeToString(combined, Base64.NO_WRAP)
}

private fun decrypt(encoded: String): String {
    val combined = Base64.decode(encoded, Base64.NO_WRAP)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    val ivSize = 12 // GCM standard IV size
    val iv = combined.copyOfRange(0, ivSize)
    val ciphertext = combined.copyOfRange(ivSize, combined.size)
    val spec = GCMParameterSpec(128, iv)
    cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
    val plainBytes = cipher.doFinal(ciphertext)
    return plainBytes.decodeToString()
}

private fun getOrCreateSecretKey(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
    return existing?.secretKey ?: createSecretKey()
}

private fun createSecretKey(): SecretKey {
    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
    val spec = KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
}
