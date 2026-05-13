# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Papp is a Kotlin Multiplatform (KMP) Lightning payment app targeting Android and iOS. It uses Compose Multiplatform for the UI and connects to Lightning wallets via Nostr Wallet Connect (NWC) and Blink API wallets.

**Key Dependencies:** Kotlin 2.3.21, Compose Multiplatform 1.10.0, nwc-kmp 0.3.1-SNAPSHOT, Koin 4.1.1, Ktor 3.4.0

## Build Commands

```bash
# Build Android debug APK
./gradlew :androidApp:assembleDebug

# Build Android release APK (minified)
./gradlew :androidApp:assembleRelease

# Build iOS framework for simulator
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64

# Build iOS framework for device
./gradlew :composeApp:linkReleaseFrameworkIosArm64

# Run all checks (tests + lint) and build the Android debug APK
./gradlew check :androidApp:assembleDebug

# Run unit tests only
./gradlew :composeApp:allTests

# Run Android unit tests
./gradlew :composeApp:testAndroidHostTest

# Run iOS simulator tests
./gradlew :composeApp:iosSimulatorArm64Test

# Lint check
./gradlew ktlintCheck

# Lint format
./gradlew ktlintFormat
```

## Architecture

The app follows Clean Architecture with three main layers:

### Domain Layer (`domain/`)
- **model/**: Core types (WalletConnection, PaymentPreferences, AppError, CurrencyCatalog)
- **repository/**: Interface contracts (PaymentProvider, WalletSettingsRepository, ExchangeRateRepository)
- **usecases/**: Single-responsibility use cases following `Verb + Noun + UseCase` naming (PayInvoiceUseCase, ObserveWalletConnectionUseCase)
- **lnurl/**: LNURL and Lightning Address parsing/handling
- **bolt11/**: BOLT11 invoice parsing

### Data Layer (`data/`)
- **nwc/**: NWC wallet implementation using nwc-kmp library
  - `NwcWalletRepositoryImpl`: Creates fresh client per operation (no cached websocket state)
  - `NwcClientFactory`: Client creation with configurable timeouts
- **blink/**: Blink API wallet implementation
- **settings/**: Secure settings storage for wallet credentials and preferences
- **exchange/**: CoinGecko exchange rate fetching
- **lnurl/**: LNURL endpoint resolution

### Presentation Layer (`presentation/`)
- **main/**: Main payment screen with QR scanner
  - `MainViewModel`: Orchestrates payment flow (scan → parse → confirm → pay)
  - `PendingPaymentTracker`: Tracks in-flight payments with background verification
  - `CurrencyManager`: Exchange rate caching and display amount conversion
- **settings/**: Settings screens and wallet management
- **navigation/**: Type-safe navigation with Kotlin Serialization routes

### Dependency Injection (`di/`)
- Single Koin module (`NwcModule.kt`) wires all dependencies
- ViewModels are factory-scoped (new instance per screen)
- Repositories and services are singleton-scoped

## Key Patterns

### Payment Flow
1. Invoice detected (QR scan, clipboard, manual entry, or LNURL)
2. `LightningInputParser` routes to appropriate handler (BOLT11, LNURL, Lightning Address)
3. Amount entry if zero-amount invoice or LNURL range
4. Confirmation check based on user preferences
5. `PaymentService` routes to NWC or Blink based on active wallet type
6. `PendingPaymentTracker` manages async completion with timeout verification

### Wallet Types
- **NWC**: Wallet connection via Nostr Wallet Connect protocol
- **Blink**: Wallet connection via API key

`PaymentService` abstracts wallet routing from the rest of the app.

### State Management
- ViewModels expose `StateFlow<UiState>` for UI state
- One-shot events via `SharedFlow<Event>`
- `rememberRetainedInstance` survives configuration changes

## Platform-Specific Code

- `androidMain/`: Camera (CameraX + ML Kit), haptics, secure storage (EncryptedSharedPreferences)
- `iosMain/`: AVFoundation camera, UIKit haptics, Keychain storage
- `commonMain/`: All shared business logic and Compose UI

Platform abstractions use Kotlin's `expect`/`actual` pattern for: `SecureSettings`, `HapticFeedbackManager`, `NetworkConnectivity`, `ClipboardHelper`, `HttpClientFactory`

## Deep Link Handling

The app handles `nostr+walletconnect://` URIs for wallet connections. See `navigation/DeepLinkEvents.kt` for URI routing and `presentation/addconnection/` for the connection flow.

## Local Development

The project includes a composite build for local nwc-kmp development. The path in `settings.gradle.kts` may need adjustment for your local setup:
```kotlin
includeBuild("../../../../Nostr/nwc-kmp") { ... }
```

Comment out or adjust the path if not developing nwc-kmp locally. Note: `TYPESAFE_PROJECT_ACCESSORS` is disabled due to naming conflicts with the composite build.

**Android ABI restriction:** Debug and release builds currently target only `arm64-v8a` due to 16KB page alignment requirements in ML Kit and acinq-secp256k1 native libraries.

## Testing

Tests are in `commonTest/` and run on JVM. Key test files:
- `MainViewModelTest.kt`: Payment flow scenarios
- `NwcWalletRepositoryImplTest.kt`: NWC protocol handling
- `Bolt11InvoiceParserTest.kt`: Invoice parsing edge cases
- `ManualAmountControllerTest.kt`: Amount entry logic

Run a single test class:
```bash
./gradlew :composeApp:testAndroidHostTest --tests "xyz.lilsus.papp.domain.bolt11.Bolt11InvoiceParserTest"
```

## Onboarding Flow

New users go through `presentation/onboarding/` screens: Welcome → Features → Agreement → WalletTypeChoice → AddWalletInstructions → AutoPaySettings. State tracked via `OnboardingRepository`.
