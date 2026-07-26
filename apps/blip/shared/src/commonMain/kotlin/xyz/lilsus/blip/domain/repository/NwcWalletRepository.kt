package xyz.lilsus.blip.domain.repository

/**
 * Abstraction over a NostWalletConnect-compatible wallet capable of paying Lightning invoices.
 * Extends [PaymentProvider] for unified payment handling.
 */
interface NwcWalletRepository : PaymentProvider
