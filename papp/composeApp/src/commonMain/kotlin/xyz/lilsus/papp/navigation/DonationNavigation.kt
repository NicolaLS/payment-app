package xyz.lilsus.papp.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import xyz.lilsus.papp.domain.lnurl.LightningAddress

data class DonationRequest(val amountSats: Long, val address: LightningAddress)

object DonationNavigation {
    val donationAddress = LightningAddress(
        username = "whisperingjoy526835",
        domain = "getalby.com"
    )

    private val _events = MutableSharedFlow<DonationRequest>(
        replay = 1,
        extraBufferCapacity = 1
    )
    val events: SharedFlow<DonationRequest> = _events.asSharedFlow()

    fun emit(request: DonationRequest) {
        if (request.amountSats <= 0) return
        _events.tryEmit(request)
    }

    fun consume() {
        _events.resetReplayCache()
    }
}
