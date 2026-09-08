# Screen privacy

Screen capture is controlled by the native platform hosts and sensitive native
screens. It does not change payment authorization, cancel payments, or erase a
wallet connection. The same policy applies to normal and E2E app targets.

| Surface | Rayl, Blip, and Lasr on Android | Flint on Android | All apps on iOS |
| --- | --- | --- | --- |
| Blink API-key entry/reveal | Secure window | Not applicable | App privacy cover when inactive or recorded/mirrored |
| NWC connection URI entry/reveal and connection scanner | Secure window | Not applicable | App privacy cover when inactive or recorded/mirrored |
| Spark recovery input | Not applicable | Secure window | App privacy cover when inactive or recorded/mirrored |
| Payment confirmation/results, invoice/QR, Hub, and receipts | Foreground capture allowed | Secure window | Foreground screenshots allowed; recording/mirroring covered |
| App-switcher preview | Disabled on Android 13+; credential screens protected on earlier Android | Secure window; previews also disabled on Android 13+ | Covered before the scene's background snapshot |

Android's credential protection lasts while the sensitive screen is composed.
Overlapping navigation screens share a reference count, and the helper preserves
an existing secure flag. Flint deliberately retains its stricter app-wide
capture policy because it also owns recovery material. Explicit Copy and Share
actions remain separate from screen capture.

iOS installs one native privacy window per app scene, above app sheets and
alerts. It covers the app while the scene is inactive or its capture trait
reports recording/mirroring. The cover shows the app name, and recording adds a
localized instruction to stop recording or mirroring. It never becomes the key
window, so dismissing the cover preserves navigation and first-responder state.
The cover is removed only when the scene is active and capture is not active.

## Limits

iOS does not provide a general supported API to prevent ordinary screenshots.
A user who explicitly reveals a credential can capture it in a foreground
screenshot; secure input masking remains the default. Detection of recording
is reactive, so the cover cannot guarantee exclusion of every initial frame.
Users can deliberately share visible payment details and QR codes through
screenshots or the available Copy/Share actions.

On Android 12 and earlier, ordinary noncredential app-switcher previews in
Rayl, Blip, and Lasr can remain visible. Android secure-window enforcement also
depends on the operating system. Neither platform protection prevents an
external camera or a compromised operating system from observing the screen.

Physical-device acceptance checks the actual app-switcher and recording behavior,
including presented sheets, permission prompts, credential reveal, and returning
to the app. Compilation alone does not establish those runtime guarantees.

Platform references: [Apple background snapshots](https://developer.apple.com/documentation/uikit/preparing-your-ui-to-run-in-the-background),
[Apple scene capture state](https://developer.apple.com/documentation/uikit/uitraitcollection/scenecapturestate),
[Android secure windows](https://developer.android.com/security/fraud-prevention/activities),
and [Android recents screenshots](https://developer.android.com/reference/android/app/Activity#setRecentsScreenshotEnabled(boolean)).
