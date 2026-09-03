package xyz.lilsus.lasr.feature.walletconnection

import org.jetbrains.compose.resources.getString
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.Res
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.add_wallet_description
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.add_wallet_scan_allow_camera
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.add_wallet_scan_instruction
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.add_wallet_scan_open_settings
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.add_wallet_scan_permission
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.add_wallet_scan_restricted
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.add_wallet_title
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.add_wallet_uri_label
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.add_wallet_uri_paste
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.add_wallet_uri_placeholder
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_alias_label
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_cancel
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_confirm
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_description
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_details_encryption
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_details_encryption_active
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_details_lud16
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_details_methods
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_details_pubkey
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_details_relay
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_loading
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_required_methods
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_retry
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_title
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_warning_heading
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_warning_legacy_nip04
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_warning_legacy_nip04_default
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_warning_missing_lookup_invoice
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_warning_missing_nip44
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_warning_missing_pay_invoice
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.error_invalid_wallet_uri
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.error_relay_connection_failed
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.error_wallet_already_connected

data class NativeLasrWalletConnectionText(
    val addTitle: String,
    val addDescription: String,
    val uriLabel: String,
    val uriPlaceholder: String,
    val paste: String,
    val scanInstruction: String,
    val scanPermission: String,
    val scanAllowCamera: String,
    val scanOpenSettings: String,
    val scanRestricted: String,
    val confirmTitle: String,
    val confirmDescription: String,
    val cancel: String,
    val confirm: String,
    val aliasLabel: String,
    val publicKeyLabel: String,
    val relayLabel: String,
    val lightningAddressLabel: String,
    val methodsLabel: String,
    val encryptionLabel: String,
    val loading: String,
    val retry: String,
    val warningHeading: String,
    val warningMissingPayInvoice: String,
    val warningMissingLookupInvoice: String,
    val warningMissingNip44: String,
    val warningLegacyNip04: String,
    val warningLegacyNip04Default: String,
    val requiredMethods: String,
    val errorAlreadyConnected: String,
    val errorInvalidUri: String,
    val errorConnection: String
)

suspend fun nativeLasrWalletConnectionText(): NativeLasrWalletConnectionText =
    NativeLasrWalletConnectionText(
        addTitle = getString(Res.string.add_wallet_title),
        addDescription = getString(Res.string.add_wallet_description),
        uriLabel = getString(Res.string.add_wallet_uri_label),
        uriPlaceholder = getString(Res.string.add_wallet_uri_placeholder),
        paste = getString(Res.string.add_wallet_uri_paste),
        scanInstruction = getString(Res.string.add_wallet_scan_instruction),
        scanPermission = getString(Res.string.add_wallet_scan_permission),
        scanAllowCamera = getString(Res.string.add_wallet_scan_allow_camera),
        scanOpenSettings = getString(Res.string.add_wallet_scan_open_settings),
        scanRestricted = getString(Res.string.add_wallet_scan_restricted),
        confirmTitle = getString(Res.string.connect_wallet_title),
        confirmDescription = getString(Res.string.connect_wallet_description),
        cancel = getString(Res.string.connect_wallet_cancel),
        confirm = getString(Res.string.connect_wallet_confirm),
        aliasLabel = getString(Res.string.connect_wallet_alias_label),
        publicKeyLabel = getString(Res.string.connect_wallet_details_pubkey),
        relayLabel = getString(Res.string.connect_wallet_details_relay),
        lightningAddressLabel = getString(Res.string.connect_wallet_details_lud16),
        methodsLabel = getString(Res.string.connect_wallet_details_methods),
        encryptionLabel = getString(Res.string.connect_wallet_details_encryption),
        loading = getString(Res.string.connect_wallet_loading),
        retry = getString(Res.string.connect_wallet_retry),
        warningHeading = getString(Res.string.connect_wallet_warning_heading),
        warningMissingPayInvoice =
            getString(Res.string.connect_wallet_warning_missing_pay_invoice),
        warningMissingLookupInvoice =
            getString(Res.string.connect_wallet_warning_missing_lookup_invoice),
        warningMissingNip44 = getString(Res.string.connect_wallet_warning_missing_nip44),
        warningLegacyNip04 = getString(Res.string.connect_wallet_warning_legacy_nip04),
        warningLegacyNip04Default =
            getString(Res.string.connect_wallet_warning_legacy_nip04_default),
        requiredMethods = getString(Res.string.connect_wallet_required_methods),
        errorAlreadyConnected = getString(Res.string.error_wallet_already_connected),
        errorInvalidUri = getString(Res.string.error_invalid_wallet_uri),
        errorConnection = getString(Res.string.error_relay_connection_failed)
    )

suspend fun nativeLasrActiveEncryptionText(value: String): String = getString(
    Res.string.connect_wallet_details_encryption_active,
    formatNativeEncryptionScheme(value)
)

private fun formatNativeEncryptionScheme(value: String): String = when (value.lowercase()) {
    "nip44_v2" -> "NIP-44 v2"
    "nip04" -> "NIP-04"
    else -> value
}
