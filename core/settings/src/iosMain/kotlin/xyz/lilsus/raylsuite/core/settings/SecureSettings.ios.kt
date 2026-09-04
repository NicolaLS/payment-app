@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class
)

package xyz.lilsus.raylsuite.core.settings

import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/** App-scoped credential storage whose validity is bound to this app installation. */
fun createSecureSettings(storageName: String): SecureStringStore {
    require(storageName.isNotBlank()) { "Secure storage name cannot be blank" }
    return KeychainSecureStringStore(storageName).also { store ->
        val marker = "$INSTALL_MARKER_PREFIX.$storageName"
        val defaults = NSUserDefaults.standardUserDefaults
        if (!defaults.boolForKey(marker)) {
            store.clear()
            defaults.setBool(true, marker)
        }
    }
}

/**
 * Uses Security directly so credentials are `ThisDeviceOnly` and can be purged by service after
 * an app reinstall. Query dictionaries must contain explicitly retained Core Foundation values;
 * retaining and reinterpreting a Kotlin map does not safely bridge its nested values for Security.
 */
private class KeychainSecureStringStore(private val service: String) : SecureStringStore {
    override fun putString(key: String, value: String) {
        val data = checkNotNull(
            NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding)
        )
        val updateStatus =
            withDictionary(baseMap(key)) { query ->
                withDictionary(
                    mapOf(
                        kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                        kSecValueData to data
                    )
                ) { attributes ->
                    SecItemUpdate(query, attributes)
                }
            }
        if (updateStatus == errSecSuccess) return
        check(updateStatus == errSecItemNotFound) {
            "Unable to update secure value: Keychain status $updateStatus"
        }

        val addStatus =
            withDictionary(
                baseMap(key) +
                    mapOf(
                        kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                        kSecValueData to data
                    )
            ) { attributes ->
                SecItemAdd(attributes, null)
            }
        check(addStatus == errSecSuccess) {
            "Unable to store secure value: Keychain status $addStatus"
        }
    }

    override fun getStringOrNull(key: String): String? = withDictionary(
        baseMap(key) +
            mapOf(
                kSecMatchLimit to kSecMatchLimitOne,
                kSecReturnData to kCFBooleanTrue
            )
    ) { query ->
        memScoped {
            val result = alloc<CPointerVarOf<CFTypeRef>>()
            when (val status = SecItemCopyMatching(query, result.ptr)) {
                errSecSuccess -> {
                    val data = CFBridgingRelease(result.value) as? NSData
                        ?: error("Keychain returned an invalid secure value")
                    NSString.create(data, NSUTF8StringEncoding)?.toString()
                        ?: error("Keychain returned a non-UTF-8 secure value")
                }

                errSecItemNotFound -> null

                else -> error("Unable to read secure value: Keychain status $status")
            }
        }
    }

    override fun remove(key: String) {
        val status = withDictionary(baseMap(key)) { SecItemDelete(it) }
        check(status == errSecSuccess || status == errSecItemNotFound) {
            "Unable to remove secure value: Keychain status $status"
        }
    }

    override fun clear() {
        val status = withDictionary(baseMap()) { SecItemDelete(it) }
        check(status == errSecSuccess || status == errSecItemNotFound) {
            "Unable to clear secure values: Keychain status $status"
        }
    }

    private fun baseMap(key: String? = null): Map<CFStringRef?, Any?> = buildMap {
        put(kSecClass, kSecClassGenericPassword)
        put(kSecAttrService, NSString.create(string = service))
        key?.let { put(kSecAttrAccount, NSString.create(string = it)) }
    }

    private inline fun <T> withDictionary(
        values: Map<CFStringRef?, Any?>,
        block: (CFDictionaryRef) -> T
    ): T = memScoped {
        val retainedValues =
            values.values.map { value ->
                when (value) {
                    is NSData, is NSString -> checkNotNull(CFBridgingRetain(value))
                    else -> value as CFTypeRef?
                }
            }
        return try {
            val keys = allocArrayOf(*values.keys.toTypedArray())
            val nativeValues = allocArrayOf(*retainedValues.toTypedArray())
            val dictionary =
                checkNotNull(
                    CFDictionaryCreate(
                        allocator = kCFAllocatorDefault,
                        keys = keys.reinterpret(),
                        values = nativeValues.reinterpret(),
                        numValues = values.size.convert(),
                        keyCallBacks = null,
                        valueCallBacks = null
                    )
                )
            try {
                block(dictionary)
            } finally {
                CFRelease(dictionary)
            }
        } finally {
            values.values.zip(retainedValues).forEach { (value, retained) ->
                if (value is NSData || value is NSString) CFRelease(retained)
            }
        }
    }
}

private const val INSTALL_MARKER_PREFIX = "rayl.secure-store.install.v1"
