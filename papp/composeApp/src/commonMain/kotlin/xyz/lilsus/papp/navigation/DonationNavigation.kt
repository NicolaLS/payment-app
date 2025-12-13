package xyz.lilsus.papp.navigation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import xyz.lilsus.papp.domain.lnurl.LightningAddress

data class DonationRequest(val amountSats: Long, val address: LightningAddress)

object DonationNavigation {
    val donationAddress = LightningAddress(
        username = "whisperingjoy526835",
        domain = "getalby.com"
    )

    private val eventsChannel = Channel<DonationRequest>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: Flow<DonationRequest> = eventsChannel.receiveAsFlow()

    fun emit(request: DonationRequest) {
        if (request.amountSats <= 0) return
        eventsChannel.trySend(request)
    }
}
