package xyz.lilsus.raylsuite.feature.paymenthub

import com.russhwolf.settings.Settings
import kotlin.jvm.JvmInline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Stable string identity of a home presentation. Never persist an enum ordinal. */
@JvmInline
value class PaymentHubLensId(val value: String) {
    init {
        require(value.isNotBlank()) { "Lens ID must not be blank" }
    }
}

/** The suite default home presentation. Products without it fall back to the first registered lens. */
val DefaultPaymentHubLensId: PaymentHubLensId = PaymentHubLensId("dock")

/**
 * Chooses the lens to show: the stored lens when it is registered, otherwise the suite default,
 * otherwise the first registered lens. Returns `null` only when nothing is registered.
 */
fun resolvePaymentHubLensId(
    stored: PaymentHubLensId?,
    registered: List<PaymentHubLensId>
): PaymentHubLensId? = when {
    stored != null && stored in registered -> stored
    DefaultPaymentHubLensId in registered -> DefaultPaymentHubLensId
    else -> registered.firstOrNull()
}

interface PaymentHubLensPreferences {
    val selectedLensId: StateFlow<PaymentHubLensId?>

    suspend fun select(id: PaymentHubLensId)
}

class DefaultPaymentHubLensPreferences(private val settings: Settings) :
    PaymentHubLensPreferences {
    private val mutationMutex = Mutex()
    private val state =
        MutableStateFlow(
            settings
                .getStringOrNull(KEY_SELECTED_LENS)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let(::PaymentHubLensId)
        )

    override val selectedLensId: StateFlow<PaymentHubLensId?> = state.asStateFlow()

    override suspend fun select(id: PaymentHubLensId) {
        mutationMutex.withLock {
            if (id == state.value) return
            settings.putString(KEY_SELECTED_LENS, id.value)
            state.value = id
        }
    }

    private companion object {
        const val KEY_SELECTED_LENS = "paymentHub.selectedLens"
    }
}
