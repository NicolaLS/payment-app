package xyz.lilsus.raylsuite.feature.paymenthub

import androidx.compose.runtime.Immutable
import xyz.lilsus.raylsuite.core.model.LightningAddress

/** An app-scoped address-book entry, independent of its Hub shortcuts. */
@Immutable
data class HubContact(val id: String, val title: String, val address: LightningAddress) {
    init {
        require(id.isNotBlank()) { "Contact ID must not be blank" }
    }
}
