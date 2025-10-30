package xyz.lilsus.papp.domain.repository

import xyz.lilsus.papp.domain.model.PaidInvoice

/**
 * Abstraction over a NostWalletConnect-compatible wallet capable of paying Lightning invoices.
 */
interface NwcWalletRepository {
    /**
     * Pays the provided BOLT11 [invoice] and returns the payment details.
     *
     * @throws Throwable when the underlying payment fails; callers should translate the failure to UI state.
     */
    suspend fun payInvoice(
        invoice: String,
        /**
         * Optional amount overrides in millisatoshis for amount-less invoices.
         * When null the wallet must infer the amount from the invoice itself.
         */
        amountMsats: Long? = null,
    ): PaidInvoice
}
