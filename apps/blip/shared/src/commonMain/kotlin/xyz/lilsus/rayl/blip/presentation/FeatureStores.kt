package xyz.lilsus.rayl.blip.presentation

import fr.acinq.lightning.MilliSatoshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.lilsus.rayl.blip.application.StartPaymentOutcome
import xyz.lilsus.rayl.blip.data.InputResolution
import xyz.lilsus.rayl.blip.data.LnurlPayRequest
import xyz.lilsus.rayl.blip.data.UnsupportedInput
import xyz.lilsus.rayl.blip.domain.ConnectBlinkOutcome
import xyz.lilsus.rayl.blip.domain.ConnectionProfile
import xyz.lilsus.rayl.blip.domain.Contact
import xyz.lilsus.rayl.blip.domain.ContactId
import xyz.lilsus.rayl.blip.domain.CurrencyCode
import xyz.lilsus.rayl.blip.domain.PaymentAttempt
import xyz.lilsus.rayl.blip.domain.PaymentAttemptState
import xyz.lilsus.rayl.blip.domain.PaymentDraft
import xyz.lilsus.rayl.blip.domain.PaymentFailure
import xyz.lilsus.rayl.blip.domain.PaymentOrigin
import xyz.lilsus.rayl.blip.domain.PaymentShortcut
import xyz.lilsus.rayl.blip.domain.ShortcutId
import xyz.lilsus.rayl.blip.domain.shouldConfirmPayment
import xyz.lilsus.rayl.blip.platform.BlipRuntime
import xyz.lilsus.rayl.blip.platform.UserPreferences

enum class OnboardingStep {
    Welcome,
    Features,
    Agreement,
    Provider,
    Credentials
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val agreementAccepted: Boolean = false,
    val connecting: Boolean = false,
    val failure: ConnectBlinkOutcome? = null
)

sealed interface OnboardingAction {
    data object Continue : OnboardingAction
    data object Back : OnboardingAction
    data class SetAgreement(val accepted: Boolean) : OnboardingAction
    data object ChooseBlink : OnboardingAction
    class Connect(internal val apiKey: String, internal val alias: String) : OnboardingAction {
        override fun toString(): String = "Connect(apiKey=**redacted**, alias=$alias)"
    }
}

class OnboardingStore(private val runtime: BlipRuntime, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    fun dispatch(action: OnboardingAction) {
        when (action) {
            OnboardingAction.Continue -> mutableState.value = mutableState.value.copy(
                step = when (mutableState.value.step) {
                    OnboardingStep.Welcome -> OnboardingStep.Features

                    OnboardingStep.Features -> OnboardingStep.Agreement

                    OnboardingStep.Agreement ->
                        if (mutableState.value.agreementAccepted) {
                            OnboardingStep.Provider
                        } else {
                            OnboardingStep.Agreement
                        }

                    OnboardingStep.Provider -> OnboardingStep.Credentials

                    OnboardingStep.Credentials -> OnboardingStep.Credentials
                },
                failure = null
            )

            OnboardingAction.Back -> mutableState.value = mutableState.value.copy(
                step = when (mutableState.value.step) {
                    OnboardingStep.Welcome -> OnboardingStep.Welcome
                    OnboardingStep.Features -> OnboardingStep.Welcome
                    OnboardingStep.Agreement -> OnboardingStep.Features
                    OnboardingStep.Provider -> OnboardingStep.Agreement
                    OnboardingStep.Credentials -> OnboardingStep.Provider
                },
                failure = null
            )

            is OnboardingAction.SetAgreement ->
                mutableState.value =
                    mutableState.value.copy(agreementAccepted = action.accepted)

            OnboardingAction.ChooseBlink ->
                mutableState.value =
                    mutableState.value.copy(step = OnboardingStep.Credentials)

            is OnboardingAction.Connect -> connect(action)
        }
    }

    private fun connect(action: OnboardingAction.Connect) {
        if (mutableState.value.connecting) return
        mutableState.value = mutableState.value.copy(connecting = true, failure = null)
        scope.launch {
            val outcome = try {
                runtime.gateway.connect(action.apiKey, action.alias)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                ConnectBlinkOutcome.Unexpected
            }
            if (outcome is ConnectBlinkOutcome.Connected) {
                runtime.preferences.completeOnboarding()
            }
            mutableState.value = mutableState.value.copy(
                connecting = false,
                failure = outcome.takeUnless { it is ConnectBlinkOutcome.Connected }
            )
        }
    }
}

sealed interface PendingAmount {
    data class Invoice(
        val value: fr.acinq.lightning.payment.Bolt11Invoice,
        val request: String,
        val origin: PaymentOrigin
    ) : PendingAmount

    data class Lnurl(val value: LnurlPayRequest) : PendingAmount
}

sealed interface PayMode {
    data object Active : PayMode
    data object Resolving : PayMode
    data class EnterAmount(
        val pending: PendingAmount,
        val minSats: Long? = null,
        val maxSats: Long? = null,
        val suggestedSats: Long? = null
    ) : PayMode

    data class Confirm(val draft: PaymentDraft) : PayMode
    data class Paying(val draft: PaymentDraft) : PayMode
    data class Result(val attempt: PaymentAttempt) : PayMode
    data class Duplicate(val attempt: PaymentAttempt) : PayMode
    data class Error(
        val failure: PaymentFailure? = null,
        val unsupported: UnsupportedInput? = null
    ) : PayMode
}

data class PayUiState(
    val mode: PayMode = PayMode.Active,
    val attempts: List<PaymentAttempt> = emptyList(),
    val permissionDenied: Boolean = false
)

sealed interface PayAction {
    data class Resolve(
        val input: String,
        val origin: PaymentOrigin,
        val suggestedSats: Long? = null
    ) : PayAction

    data class SubmitAmount(
        val value: String,
        val currency: CurrencyCode,
        val comment: String? = null
    ) : PayAction
    data object Confirm : PayAction
    data object Dismiss : PayAction
    data object Reconcile : PayAction
    data object CameraPermissionDenied : PayAction
}

class PayStore(private val runtime: BlipRuntime, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(
        PayUiState(attempts = runtime.coordinator.attempts())
    )
    val state: StateFlow<PayUiState> = mutableState.asStateFlow()
    private var operation: Job? = null
    private var reconciliation: Job? = null

    fun dispatch(action: PayAction) {
        when (action) {
            is PayAction.Resolve -> resolve(action)

            is PayAction.SubmitAmount -> submitAmount(action)

            PayAction.Confirm -> {
                val draft = (mutableState.value.mode as? PayMode.Confirm)?.draft ?: return
                pay(draft)
            }

            PayAction.Dismiss -> mutableState.value = mutableState.value.copy(
                mode = PayMode.Active,
                attempts = runtime.coordinator.attempts()
            )

            PayAction.Reconcile -> reconcile()

            PayAction.CameraPermissionDenied ->
                mutableState.value =
                    mutableState.value.copy(permissionDenied = true)
        }
    }

    private fun resolve(action: PayAction.Resolve) {
        if (mutableState.value.mode !is PayMode.Active) return
        mutableState.value = mutableState.value.copy(mode = PayMode.Resolving)
        operation = scope.launch {
            val resolution = runtime.inputResolver.resolve(action.input, action.origin)
            when {
                resolution is InputResolution.NeedsLnurlAmount &&
                    action.suggestedSats != null -> {
                    val amount = action.suggestedSats.toMilliSatoshiOrNull()
                    if (amount == null) {
                        showFailure(PaymentFailure.InvalidRequest)
                    } else {
                        handleResolution(
                            runtime.inputResolver.requestLnurlInvoice(
                                request = resolution.request,
                                amount = amount,
                                comment = null
                            ),
                            action.suggestedSats
                        )
                    }
                }

                else -> handleResolution(resolution, action.suggestedSats)
            }
        }
    }

    private suspend fun handleResolution(resolution: InputResolution, suggestedSats: Long?) {
        when (resolution) {
            is InputResolution.Ready -> preparePayment(resolution.draft)

            is InputResolution.NeedsAmount -> mutableState.value = mutableState.value.copy(
                mode = PayMode.EnterAmount(
                    pending = PendingAmount.Invoice(
                        value = resolution.invoice,
                        request = resolution.normalizedRequest,
                        origin = resolution.origin
                    ),
                    suggestedSats = suggestedSats
                )
            )

            is InputResolution.NeedsLnurlAmount -> mutableState.value = mutableState.value.copy(
                mode = PayMode.EnterAmount(
                    pending = PendingAmount.Lnurl(resolution.request),
                    minSats = resolution.request.minSendable.msat.roundUpToSats(),
                    maxSats = resolution.request.maxSendable.msat / 1_000L,
                    suggestedSats = suggestedSats
                )
            )

            is InputResolution.Unsupported -> mutableState.value = mutableState.value.copy(
                mode = PayMode.Error(unsupported = resolution.kind)
            )

            is InputResolution.Rejected -> showFailure(resolution.failure)
        }
    }

    private fun submitAmount(action: PayAction.SubmitAmount) {
        val entry = mutableState.value.mode as? PayMode.EnterAmount ?: return
        mutableState.value = mutableState.value.copy(mode = PayMode.Resolving)
        operation = scope.launch {
            val converted = runtime.exchangeRates.toMilliSatoshi(
                value = action.value,
                currency = action.currency
            )
            if (converted == null) {
                showFailure(PaymentFailure.InvalidRequest)
                return@launch
            }
            val resolution = when (val pending = entry.pending) {
                is PendingAmount.Invoice -> runtime.inputResolver.withAmount(
                    invoice = pending.value,
                    normalizedRequest = pending.request,
                    amount = converted.amount,
                    origin = pending.origin
                )

                is PendingAmount.Lnurl -> runtime.inputResolver.requestLnurlInvoice(
                    request = pending.value,
                    amount = converted.amount,
                    comment = action.comment
                )
            }
            val ready = if (resolution is InputResolution.Ready) {
                InputResolution.Ready(
                    resolution.draft.copy(rateSnapshot = converted.rateSnapshot)
                )
            } else {
                resolution
            }
            handleResolution(ready, converted.amount.msat.roundUpToSats())
        }
    }

    private suspend fun preparePayment(draft: PaymentDraft) {
        val userPreferences = runtime.preferences.values.value
        val primary = CurrencyCode.parse(userPreferences.primaryCurrency)
        val secondary = CurrencyCode.parse(userPreferences.secondaryCurrency)
        val preferredQuote = primary
            ?.takeUnless { it == CurrencyCode.Sat || it == CurrencyCode.Btc }
            ?: secondary
        val rate = draft.rateSnapshot ?: if (preferredQuote == null) {
            null
        } else {
            runtime.exchangeRates.snapshot(preferredQuote)
        }
        val preparedDraft = draft.copy(rateSnapshot = rate)
        val preferences = userPreferences.payments
        if (shouldConfirmPayment(
                preparedDraft.amount.msat,
                preparedDraft.origin,
                preferences
            )
        ) {
            mutableState.value = mutableState.value.copy(mode = PayMode.Confirm(preparedDraft))
        } else {
            pay(preparedDraft)
        }
    }

    private fun pay(draft: PaymentDraft) {
        mutableState.value = mutableState.value.copy(mode = PayMode.Paying(draft))
        operation = scope.launch {
            val outcome = try {
                runtime.coordinator.pay(draft)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                showFailure(PaymentFailure.Unexpected)
                return@launch
            }
            when (outcome) {
                is StartPaymentOutcome.Attempt -> {
                    if (outcome.value.state == PaymentAttemptState.Settled &&
                        runtime.preferences.values.value.payments.vibrateOnPayment
                    ) {
                        runtime.platform.haptic()
                    }
                    mutableState.value = mutableState.value.copy(
                        mode = PayMode.Result(outcome.value),
                        attempts = runtime.coordinator.attempts()
                    )
                }

                is StartPaymentOutcome.Blocked -> mutableState.value = mutableState.value.copy(
                    mode = PayMode.Duplicate(outcome.previous),
                    attempts = runtime.coordinator.attempts()
                )

                is StartPaymentOutcome.Rejected -> showFailure(outcome.failure)
            }
        }
    }

    private fun reconcile() {
        if (reconciliation?.isActive == true) return
        reconciliation = scope.launch {
            try {
                runtime.coordinator.reconcile()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                return@launch
            }
            mutableState.value = mutableState.value.copy(
                attempts = runtime.coordinator.attempts()
            )
        }
    }

    private fun showFailure(failure: PaymentFailure) {
        mutableState.value = mutableState.value.copy(mode = PayMode.Error(failure = failure))
    }
}

data class SettingsUiState(
    val preferences: UserPreferences,
    val connection: ConnectionProfile?,
    val contacts: List<Contact>,
    val shortcuts: List<PaymentShortcut>,
    val busy: Boolean = false,
    val message: String? = null
)

sealed interface SettingsAction {
    data object Refresh : SettingsAction
    data object Disconnect : SettingsAction
    data object RefreshWallet : SettingsAction
    data object ImportBlinkContacts : SettingsAction
    data class AddContact(val name: String, val address: String) : SettingsAction
    data class DeleteContact(val id: ContactId) : SettingsAction
    data class AddShortcut(val label: String, val address: String, val amountSats: Long?) :
        SettingsAction

    data class DeleteShortcut(val id: ShortcutId) : SettingsAction
}

class SettingsStore(private val runtime: BlipRuntime, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(snapshot())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            runtime.preferences.values.collectLatest {
                mutableState.value = snapshot().copy(
                    busy = mutableState.value.busy,
                    message = mutableState.value.message
                )
            }
        }
    }

    fun dispatch(action: SettingsAction) {
        when (action) {
            SettingsAction.Refresh -> mutableState.value = snapshot()

            SettingsAction.Disconnect -> {
                val profile = mutableState.value.connection ?: return
                launchBusy {
                    runtime.gateway.disconnect(profile)
                    "Wallet disconnected"
                }
            }

            SettingsAction.RefreshWallet -> {
                val profile = mutableState.value.connection ?: return
                launchBusy {
                    val outcome = runtime.gateway.refreshWallet(profile)
                    if (outcome is ConnectBlinkOutcome.Connected) {
                        "Default wallet refreshed"
                    } else {
                        "Could not refresh the wallet"
                    }
                }
            }

            SettingsAction.ImportBlinkContacts -> launchBusy {
                val imported = runtime.addressBook.importBlinkContacts()
                "Imported ${imported.size} contacts"
            }

            is SettingsAction.AddContact -> {
                val saved = runtime.addressBook.addContact(action.name, action.address)
                mutableState.value = snapshot().copy(
                    message = if (saved == null) "Enter a valid contact" else "Contact saved"
                )
            }

            is SettingsAction.DeleteContact -> {
                runtime.addressBook.deleteContact(action.id)
                mutableState.value = snapshot().copy(message = "Contact deleted")
            }

            is SettingsAction.AddShortcut -> {
                val amountMsat = action.amountSats?.toMilliSatoshiOrNull()?.msat
                val saved = runtime.addressBook.addShortcut(
                    label = action.label,
                    lightningAddress = action.address,
                    amountMsat = amountMsat
                )
                mutableState.value = snapshot().copy(
                    message = if (saved == null) "Enter a valid shortcut" else "Shortcut saved"
                )
            }

            is SettingsAction.DeleteShortcut -> {
                runtime.addressBook.deleteShortcut(action.id)
                mutableState.value = snapshot().copy(message = "Shortcut deleted")
            }
        }
    }

    private fun launchBusy(operation: suspend () -> String) {
        if (mutableState.value.busy) return
        scope.launch {
            mutableState.value = mutableState.value.copy(busy = true, message = null)
            val message = try {
                operation()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                "Operation failed. Try again."
            }
            mutableState.value = snapshot().copy(message = message)
        }
    }

    private fun snapshot(): SettingsUiState = SettingsUiState(
        preferences = runtime.preferences.values.value,
        connection = runtime.store.currentConnection(),
        contacts = runtime.addressBook.contacts(),
        shortcuts = runtime.addressBook.shortcuts()
    )
}

private fun Long.toMilliSatoshiOrNull(): MilliSatoshi? =
    takeIf { it > 0L && it <= Long.MAX_VALUE / 1_000L }
        ?.let { MilliSatoshi(it * 1_000L) }

private fun Long.roundUpToSats(): Long = if (this <= 0L) 0L else ((this - 1L) / 1_000L) + 1L
