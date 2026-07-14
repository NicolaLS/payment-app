# Greenfield Feature Catalog

**Purpose:** High-level implementation checklist for the new product.

**Assumptions:** Kotlin Multiplatform, shared Compose UI, one wallet/provider integration, no NWC, and no multi-wallet management.

## Product foundation

- [ ] Confirm product name, branding, and package/bundle identifiers
- [ ] Confirm supported Android and iOS versions/devices
- [ ] Define supported payment formats and provider capabilities
- [ ] Define confirmation and payment-safety policy
- [ ] Define privacy, backup, and account-recovery behavior

## App foundation

- [ ] Kotlin Multiplatform project and thin platform shells
- [ ] Shared Compose design system and theme
- [ ] Type-safe navigation structure
- [ ] Dependency injection and environment configuration
- [ ] Database, preferences, and credential vault
- [ ] Networking and provider client
- [ ] Logging, diagnostics, and secret redaction

## Onboarding and setup

- [ ] Welcome and product introduction
- [ ] Risk/consent agreement where required
- [ ] Single wallet/provider connection
- [ ] Credential validation and secure storage
- [ ] Initial payment-confirmation configuration
- [ ] Camera permission requested when scanning is first used
- [ ] Resume interrupted setup safely

## Payment home

- [ ] Scanner-first home screen
- [ ] Camera permission, unavailable, and denied states
- [ ] QR detection and duplicate-scan suppression
- [ ] Scanner zoom or camera mode controls where supported
- [ ] Manual paste/input fallback if required
- [ ] Clear wallet/provider connection status
- [ ] Accessible alternatives for scanner gestures

## Payment request handling

- [ ] BOLT11 invoice support
- [ ] Amountless invoice support if required
- [ ] Lightning address support if required
- [ ] LNURL-pay support if required
- [ ] App/deep-link payment input
- [ ] Unsupported-format guidance
- [ ] Full request, amount, network, and expiry validation

## Amount and confirmation

- [ ] SAT/BTC amount entry and formatting
- [ ] Fiat amount entry if required
- [ ] Exchange-rate retrieval and freshness handling
- [ ] Minimum, maximum, precision, and overflow validation
- [ ] Confirmation policy and threshold configuration
- [ ] Explicit confirmation screen
- [ ] Forced confirmation for external/deep-link requests

## Payment execution

- [ ] Durable attempt created before submission
- [ ] Provider payment submission
- [ ] Pending and progress presentation
- [ ] Success result
- [ ] Already-paid result where supported
- [ ] Rejected/failed result with actionable guidance
- [ ] Unknown/unconfirmed result distinct from failure
- [ ] Reconciliation after timeout, restart, or foregrounding
- [ ] Protection against unsafe duplicate submission

## Transactions and receipts

- [ ] Current and recent payment list
- [ ] Payment detail screen
- [ ] Pending/unknown attention state
- [ ] Fee display when known
- [ ] Receipt or preimage display where available
- [ ] Retry or reconciliation actions appropriate to payment state
- [ ] Decide transaction retention and deletion policy

## Contacts and shortcuts — optional

- [ ] Decide whether contacts belong in the new product
- [ ] Contact list, create, edit, and delete
- [ ] Contact roles/tags if required
- [ ] Saved payment shortcuts if required
- [ ] Provider contact import if supported
- [ ] Transactional import and duplicate handling

## Settings

- [ ] Payment confirmation preferences
- [ ] Primary and secondary currency
- [ ] Theme selection
- [ ] Language selection
- [ ] Scan and payment haptics
- [ ] Provider/account connection details
- [ ] Reconnect or replace credentials
- [ ] Reset local data and credentials
- [ ] Privacy, support, licenses, and version information

## Platform integration

- [ ] Android camera, deep links, secure storage, and lifecycle
- [ ] iOS camera, URL handling, Keychain, and lifecycle
- [ ] Background/foreground payment reconciliation
- [ ] Locale-aware number and time formatting
- [ ] Haptic feedback and platform fallbacks
- [ ] Backup/reinstall behavior verified on both platforms

## Accessibility and adaptive UI

- [ ] Compact, landscape, tablet, and large-screen layouts
- [ ] Large text and display scaling
- [ ] Screen-reader labels, roles, focus, and announcements
- [ ] Contrast-safe colors and status presentation
- [ ] Minimum touch targets
- [ ] Reduced-motion support
- [ ] Visible alternatives for every hidden gesture

## Security and privacy

- [ ] Secrets excluded from routes, saved state, logs, and diagnostics
- [ ] Sensitive values redacted from `toString()` and crash reports
- [ ] Credentials stored only in the platform vault
- [ ] Payment requests and remote callbacks treated as untrusted input
- [ ] Data-flow inventory and privacy policy
- [ ] Credential deletion, invalidation, and recovery tested

## Testing and release readiness

- [ ] Domain and payment-state tests
- [ ] Provider contract and failure tests
- [ ] Database migration and process-restart tests
- [ ] Compose UI and accessibility tests
- [ ] Android and iOS platform integration tests
- [ ] End-to-end happy path and uncertain-payment recovery
- [ ] CI for formatting, tests, Android, and iOS builds
- [ ] Reproducible signed release process
- [ ] Store metadata, screenshots, privacy declarations, and release checklist

## Explicitly out of scope

- [x] Nostr Wallet Connect integration
- [x] Multiple wallet storage or switching
- [x] Dynamic payment-provider plugin architecture
- [ ] Add any other intentional exclusions before implementation begins
