package xyz.lilsus.flint.feature.walletconnection

import org.jetbrains.compose.resources.getString
import xyz.lilsus.flint.feature.walletconnection.generated.resources.Res
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_cancel
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_credential_corrupt
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_credential_invalidated
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_credential_title
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_credential_unavailable
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_error_already_configured
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_error_connection
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_error_invalid_mnemonic
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_error_reset
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_error_storage
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_import_action
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_import_body
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_import_title
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_phrase_hint
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_phrase_label
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_progress_connecting
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_progress_loading
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_progress_removing
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_progress_working
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_reconnect_body
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_reconnect_title
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_remove_body
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_remove_confirm
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_remove_title
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_reset_action
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_reset_body
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_reset_title
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_retry
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_storage_note

data class NativeFlintWalletConnectionText(
    val importTitle: String,
    val importBody: String,
    val phraseLabel: String,
    val phraseHint: String,
    val storageNote: String,
    val importAction: String,
    val removeTitle: String,
    val removeBody: String,
    val removeConfirm: String,
    val cancel: String,
    val retry: String,
    val resetAction: String,
    val loading: String,
    val connecting: String,
    val removing: String,
    val working: String,
    val reconnectTitle: String,
    val reconnectBody: String,
    val resetTitle: String,
    val resetBody: String,
    val credentialTitle: String,
    val credentialUnavailable: String,
    val credentialInvalidated: String,
    val credentialCorrupt: String,
    val errorAlreadyConfigured: String,
    val errorInvalidMnemonic: String,
    val errorConnection: String,
    val errorStorage: String,
    val errorReset: String
)

suspend fun nativeFlintWalletConnectionText(): NativeFlintWalletConnectionText =
    NativeFlintWalletConnectionText(
        importTitle = getString(Res.string.wallet_import_title),
        importBody = getString(Res.string.wallet_import_body),
        phraseLabel = getString(Res.string.wallet_phrase_label),
        phraseHint = getString(Res.string.wallet_phrase_hint),
        storageNote = getString(Res.string.wallet_storage_note),
        importAction = getString(Res.string.wallet_import_action),
        removeTitle = getString(Res.string.wallet_remove_title),
        removeBody = getString(Res.string.wallet_remove_body),
        removeConfirm = getString(Res.string.wallet_remove_confirm),
        cancel = getString(Res.string.wallet_cancel),
        retry = getString(Res.string.wallet_retry),
        resetAction = getString(Res.string.wallet_reset_action),
        loading = getString(Res.string.wallet_progress_loading),
        connecting = getString(Res.string.wallet_progress_connecting),
        removing = getString(Res.string.wallet_progress_removing),
        working = getString(Res.string.wallet_progress_working),
        reconnectTitle = getString(Res.string.wallet_reconnect_title),
        reconnectBody = getString(Res.string.wallet_reconnect_body),
        resetTitle = getString(Res.string.wallet_reset_title),
        resetBody = getString(Res.string.wallet_reset_body),
        credentialTitle = getString(Res.string.wallet_credential_title),
        credentialUnavailable = getString(Res.string.wallet_credential_unavailable),
        credentialInvalidated = getString(Res.string.wallet_credential_invalidated),
        credentialCorrupt = getString(Res.string.wallet_credential_corrupt),
        errorAlreadyConfigured = getString(Res.string.wallet_error_already_configured),
        errorInvalidMnemonic = getString(Res.string.wallet_error_invalid_mnemonic),
        errorConnection = getString(Res.string.wallet_error_connection),
        errorStorage = getString(Res.string.wallet_error_storage),
        errorReset = getString(Res.string.wallet_error_reset)
    )
