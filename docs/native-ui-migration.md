# Native UI architecture

Rayl Suite uses Kotlin Multiplatform for behavior and presentation state while each platform owns
its user interface:

- Android renders with Android-owned Jetpack Compose.
- iOS renders with SwiftUI/UIKit and native Apple frameworks.
- `commonMain` contains state machines, immutable snapshots, localization projections, and plain
  renderer-neutral values. It does not contain composables.
- App/provider credentials, lifecycles, failures, repositories, and payment decisions stay in the
  owning app.

This is the current architecture, not a transitional fallback. No iOS screen uses
`ComposeUIViewController`.

## Ownership

| Concern | Owner |
| --- | --- |
| App and provider behavior | App-owned Kotlin modules |
| Provider-neutral state and presentation snapshots | `commonMain` in the owning feature |
| Android UI, navigation, permissions, and drawing | `androidMain` Compose/platform code |
| iOS UI, navigation, permissions, and drawing | SwiftUI/UIKit plus `iosMain` Kotlin controllers |
| Cross-app design values and hero state/geometry | Plain values in `core:ui/commonMain` |

An iOS Kotlin controller exposes an immutable localized snapshot, explicit intent methods, and an
observer that returns a cancellation closure. Swift `ObservableObject` models are main-actor
isolated and cancel their observation in `deinit`. Provider-specific state is projected into the
smallest values the renderer needs before it crosses this boundary.

Shared Swift renderers live once under their feature's `src/iosMain/swift` directory. Gradle does
not compile these files; every consuming Xcode target must reference that one file in its Sources
build phase. Do not copy a renderer into individual apps.

## Native hero

The payment hero is native on both platforms. Android uses Compose `Canvas`; iOS uses SwiftUI
`Canvas` and derives each frame from elapsed time since the shared semantic phase changed.

`RaylHeroPhase`, the ARGB palette, and normalized static geometry live in `core:ui/commonMain`.
Each renderer converts the geometry into its native numeric and path types. Animation clocks,
interpolation, interruption behavior, drawing operations, and frame state remain platform-owned.
This prevents accidental visual drift without putting a renderer or animation state machine in
shared Kotlin.

## Current product coverage

Blip, Flint, and Lasr have native platform implementations for the root shell, Scan/payment
presentation, Recent/detail where exposed, Payment Hub, common settings, onboarding, wallet
connection, and wallet management/details. Blip's Blink contact import remains an app-owned native
feature. Blip presents session history from Scan; Flint and Lasr expose Recent as a tab.

Camera authorization is represented explicitly as not determined, authorized, denied, restricted,
or unavailable. CameraX and AVFoundation mechanics stay native. A denied user receives an
explanation and a retry or app-settings route where the operating system permits one.

## Compose artifacts in the iOS framework

Native rendering does not currently mean a Compose-free Apple binary. Generated localization
accessors and the KMP Compose convention keep Compose Resources/runtime dependencies reachable from
some iOS compilations, which can also make Skiko transitively reachable. The plain hero geometry
does not depend on Compose and is unrelated to that packaging cost.

Treat removal of Compose Resources/Skiko as a measured follow-up project. Compare framework/app
size, link time, launch time, and memory before replacing localization or build infrastructure.

## Deferred platform improvements

The native renderer migration is complete independently of these follow-ups:

- adaptive Android top-level navigation and removal of the compact-orientation workaround;
- a deliberate regular-width/iPad layout pass for Flint;
- lifecycle-aware collection across existing Android Compose screens;
- broad VoiceOver, TalkBack, Dynamic Type, and font-scale validation;
- typed Kotlin/Swift boundary cleanup beyond values touched during the migration;
- automated validation of shared Swift Xcode target membership; and
- screenshot/app-switcher privacy policy per product.

The implementation audit and the scoped completion record remain in
`issues/native-ui-migration-review.md` and `issues/native-ui-migration-completion.md` in working
copies where the local issue directory is enabled.
