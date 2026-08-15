import java.io.File
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

/**
 * One keystore identity resolved from `<PREFIX>_*` environment variables.
 *
 * Each password may be given directly, or as a path to a file holding the password on its
 * first line. Key passwords fall back to the matching store password when unset.
 */
internal class ReleaseSigningIdentity(providers: ProviderFactory, private val prefix: String) {
    val storeFile: Provider<String> = providers.environmentVariable("${prefix}_STORE_FILE")

    val keyAlias: Provider<String> = providers.environmentVariable("${prefix}_KEY_ALIAS")

    val storePassword: Provider<String> =
        providers.environmentVariable("${prefix}_STORE_PASSWORD")

    val storePasswordFile: Provider<String> =
        providers.environmentVariable("${prefix}_STORE_PASSWORD_FILE")

    val keyPassword: Provider<String> =
        providers.environmentVariable("${prefix}_KEY_PASSWORD").orElse(storePassword)

    val keyPasswordFile: Provider<String> =
        providers.environmentVariable("${prefix}_KEY_PASSWORD_FILE").orElse(storePasswordFile)

    val keystore: File?
        get() = storeFile.orNull?.takeIf { it.isNotBlank() }?.let(::File)

    val isConfigured: Boolean
        get() = keystore?.isFile == true &&
            !keyAlias.orNull.isNullOrBlank() &&
            !resolveStorePassword().isNullOrBlank() &&
            !resolveKeyPassword().isNullOrBlank()

    fun resolveStorePassword(): String? = readPassword(storePasswordFile, storePassword)

    fun resolveKeyPassword(): String? = readPassword(keyPasswordFile, keyPassword)

    /** Names of the variables a user must set, for error messages. */
    fun missingVariables(): List<String> = buildList {
        if (keystore?.isFile != true) add("${prefix}_STORE_FILE (readable keystore path)")
        if (keyAlias.orNull.isNullOrBlank()) add("${prefix}_KEY_ALIAS")
        if (resolveStorePassword().isNullOrBlank()) {
            add("${prefix}_STORE_PASSWORD or ${prefix}_STORE_PASSWORD_FILE")
        }
        if (resolveKeyPassword().isNullOrBlank()) {
            add("${prefix}_KEY_PASSWORD or ${prefix}_KEY_PASSWORD_FILE")
        }
    }

    /** Status lines that never contain secret material. */
    fun statusLines(label: String): List<String> {
        val located = if (keystore?.isFile == true) "found" else "missing"
        return listOf(
            "$label keystore: ${storeFile.orNull ?: "unset"} ($located)",
            "$label key alias: ${keyAlias.orNull ?: "unset"}",
            "$label store password: ${describe(resolveStorePassword())}",
            "$label key password: ${describe(resolveKeyPassword())}"
        )
    }

    private fun describe(password: String?): String =
        if (password.isNullOrBlank()) "missing" else "configured"

    private fun readPassword(file: Provider<String>, value: Provider<String>): String? {
        val passwordFile = file.orNull?.takeIf { it.isNotBlank() }?.let(::File)
        if (passwordFile != null && passwordFile.isFile) {
            return passwordFile.useLines { lines -> lines.firstOrNull() }
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        }
        return value.orNull?.takeIf { it.isNotBlank() }
    }
}
