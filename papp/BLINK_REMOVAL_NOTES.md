# Blink API Key Wallet Connector - Removal Notes

This document describes how to cleanly remove the Blink wallet connector feature when Blink supports NWC natively.

## Overview

The Blink connector is a **temporary bridge** that allows Blink wallet users to use the app via API key authentication. It is designed to be isolated and easy to remove.

## Files to Remove

### Data Layer (Blink-specific)

Delete the entire `data/blink` package:
```
composeApp/src/commonMain/kotlin/xyz/lilsus/papp/data/blink/
├── BlinkApiClient.kt
├── BlinkCredentialStore.kt
└── BlinkPaymentRepository.kt
```

### Domain Layer

1. **Remove `WalletType.BLINK`** from `domain/model/WalletType.kt`
   - If NWC is the only remaining type, consider removing `WalletType.kt` entirely

2. **Remove `AuthenticationFailure`** from `domain/model/AppError.kt` (if not used elsewhere)

3. **Remove `PaymentService`** from `domain/service/PaymentService.kt`
   - Update `PayInvoiceUseCase` to use `NwcWalletRepository` directly again

### Presentation Layer

Delete the Blink wallet UI:
```
composeApp/src/commonMain/kotlin/xyz/lilsus/papp/presentation/settings/addblink/
├── AddBlinkWalletScreen.kt
└── AddBlinkWalletViewModel.kt
```

Delete the wallet type selection screen:
```
composeApp/src/commonMain/kotlin/xyz/lilsus/papp/presentation/settings/ChooseWalletTypeScreen.kt
```

### Navigation

In `navigation/SettingsNavigation.kt`:
1. Remove `SettingsChooseWalletType` and `SettingsAddBlinkWallet` route objects
2. Remove `composable<SettingsChooseWalletType>` and `composable<SettingsAddBlinkWallet>` entries
3. Remove `navigateToSettingsChooseWalletType()` and `navigateToSettingsAddBlinkWallet()` functions
4. Remove `ChooseWalletTypeEntry` and `AddBlinkWalletEntry` composables
5. Update `WalletSettingsEntry` to navigate directly to `SettingsAddWallet` instead of `SettingsChooseWalletType`

### DI Module

In `di/NwcModule.kt`:
1. Remove Blink-related imports
2. Remove these singletons:
   - `BlinkCredentialStore`
   - `BlinkApiClient`
   - `BlinkPaymentRepository`
3. Remove `PaymentService` singleton
4. Update `PayInvoiceUseCase` factory to use `NwcWalletRepository` directly:
   ```kotlin
   factory { PayInvoiceUseCase(paymentProvider = get<NwcWalletRepository>()) }
   ```
5. Remove `AddBlinkWalletViewModel` factory

### Resources

In `composeResources/values/strings.xml`, remove:
- `add_blink_wallet_title`
- `add_blink_wallet_description`
- `add_blink_wallet_alias_label`
- `add_blink_wallet_alias_placeholder`
- `add_blink_wallet_api_key_label`
- `add_blink_wallet_api_key_placeholder`
- `add_blink_wallet_connect`
- `error_authentication_failure`
- `error_authentication_failure_message`
- `wallet_type_nwc`
- `wallet_type_blink`
- `add_wallet_choose_type`

### Tests

Delete test files:
```
composeApp/src/commonTest/kotlin/xyz/lilsus/papp/data/blink/BlinkApiClientTest.kt
composeApp/src/commonTest/kotlin/xyz/lilsus/papp/data/blink/BlinkPaymentRepositoryTest.kt
composeApp/src/commonTest/kotlin/xyz/lilsus/papp/data/blink/BlinkCredentialStoreTest.kt
composeApp/src/commonTest/kotlin/xyz/lilsus/papp/presentation/settings/addblink/AddBlinkWalletViewModelTest.kt
```

### Model Updates

In `domain/model/WalletConnection.kt`:
1. Remove `type` field (or keep it with only NWC)
2. Remove `isNwc` and `isBlink` helper properties

In `data/settings/WalletSettingsRepositoryImpl.kt`:
1. Remove `type` field from `StoredWallet`
2. Remove type mapping in `toStored()` and `toDomain()`

In `presentation/settings/wallet/WalletSettingsViewModel.kt`:
1. Remove `type` from `WalletDisplay`
2. Remove type mapping in `toDisplay()`

In `presentation/settings/ManageWalletsScreen.kt`:
1. Remove `WalletTypeBadge` composable
2. Remove type-conditional display logic

In `presentation/common/ErrorMessages.kt`:
1. Remove `AuthenticationFailure` case handling

### Dependencies

No external dependencies were added specifically for Blink. The feature uses existing Ktor HTTP client.

## Migration Considerations

### Existing Blink Wallets

Before removing the feature:
1. Consider adding a migration that removes stored Blink wallets
2. Or show a message to users that Blink wallets need to be re-added via NWC

### Stored API Keys

Blink API keys are stored with prefix `blink.apikey.` in secure storage. These should be cleaned up:
```kotlin
// In a migration or cleanup routine
val settings = createSecureSettings()
// Remove all keys starting with "blink.apikey."
```

## Verification After Removal

1. Run all existing tests: `./gradlew :composeApp:check`
2. Build Android: `./gradlew :composeApp:assembleDebug`
3. Build iOS: `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`
4. Verify NWC wallet connection still works
5. Verify NWC payments still work for all payment types

## Estimated Effort

- **Time**: 1-2 hours
- **Risk**: Low (feature is isolated)
- **Testing**: Run existing test suite + manual verification
