# Native app shell

The suite defines four possible destinations: **Scan**, **Recent**, **Hub**, and **Settings**.
Blip currently exposes Scan, Hub, and Settings and presents session history from Scan. Flint and
Lasr expose all four destinations. Rayl mounts Blink’s three-tab experience or NWC’s
four-tab experience, after a Rayl-owned wallet choice and provider-specific setup.

Android renders the selected product's destinations with the Material 3 shell in
`feature:app-shell/androidMain`. Each app supplies its own Android tab content from its
`androidMain` composition root.

iOS renders a real SwiftUI `TabView` from the single shared
`feature/app-shell/src/iosMain/swift/NativeAppShell.swift` source. An app-local `ContentView`
provides a `NativeShellAdapter` whose closures:

- observe the onboarding/tabs stage, selected tab, theme, badge count, and localized tab titles;
- report tab selection to the Kotlin app runtime; and
- create the native SwiftUI view for every visible destination and onboarding.

Tab views are created once and retained so selection changes preserve navigation paths, sheets,
and camera/controller lifecycles. Provider-specific native composition lives in `providers/blink/experience` and
`providers/nwc/experience`, consumed directly by the purpose-built apps and Rayl.
The active-tab environment value lets Scan and Recent stop
platform work while their retained view is hidden.

The shell has no Compose escape hatch and no per-tab controller bridge. Scan, Recent, Hub,
Settings, onboarding, wallet setup, and wallet management are SwiftUI/UIKit on iOS. The custom hero
is also native: Compose Canvas on Android and SwiftUI Canvas on iOS.

Shared localized titles and presentation snapshots come from Kotlin. SwiftUI owns tab chrome,
navigation stacks, lists, sheets, alerts, safe areas, keyboard behavior, and scene lifecycle. A
product-specific section—such as a provider wallet detail—enters through a
small app-supplied SwiftUI view rather than a provider flag in the shared shell.

When adding or renaming a shared Swift renderer, add the same source reference to every consuming
app target (including E2E targets) and run one relevant Debug simulator build for each consumer.
