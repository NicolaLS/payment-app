# Performance monitoring

The suite has three deliberately small layers of performance feedback:

1. `./gradlew perfCheck` gives repeatable before/after feedback while developing.
2. The **Android performance** GitHub Actions workflow keeps a directional CI
   history and can attach a report to selected pull requests.
3. Android vitals, Xcode Organizer, and optional Android Firebase Performance
   Monitoring show what shipping versions do across real devices.

None of these measurements include payment amounts, destinations, invoices,
wallet credentials, NWC URIs, scanned QR values, or camera images.

## Everyday development

Use the same Android 10+ physical device, with the screen unlocked and its
temperature reasonably stable, before and after a performance-sensitive change:

```bash
./gradlew perfCheck
```

This installs a benchmark-only Blip build and measures:

- cold startup time; and
- camera start-to-ready and start-to-first-frame time without needing a QR code.

Gradle prints the median and distribution. JSON reports and Perfetto traces are
written below
`apps/blip/benchmark/build/outputs/connected_android_test_additional_output/`.
Use medians across the repeated measurements, not one run. A simulator or
emulator is useful for confirming that the workflow runs, but its absolute
timings should not drive a product decision.

Blip is the representative benchmark app because these camera paths live in
`core:camera`. If a change is specific to Flint or Lasr, also profile that app
with the system-trace workflow below.

### Pull-request feedback

Add the `performance` label to a pull request to run the benchmark workflow.
It posts a small current-versus-previous summary in the job summary and uploads
the raw reports and traces for 30 days. The same workflow runs weekly, manually,
and for any app-qualified release tag.

The hosted Android emulator is intentionally a trend signal, not a merge gate.
When it reports a meaningful change, reproduce it on the same physical device
with `./gradlew perfCheck` before changing the implementation.

## Investigating a result

The camera implementation also emits Android system-trace events and iOS
signposts. These explain where a regression occurred.

### Android capture

1. Run the affected Debug app on a physical device from Android Studio.
2. In **Profiler**, record a **System Trace**.
3. Open and close the scanner several times, then stop recording.
4. Search for `camera.`. The slices can be on different threads because camera
   startup and ML Kit analysis are asynchronous.

The exported trace can also be opened in Perfetto. See Android's
[system tracing guide](https://developer.android.com/topic/performance/tracing).

### iOS capture

1. Run the affected Debug app on a physical iPhone from Xcode.
2. Choose **Product > Profile**, then use a **Blank** Instruments template.
3. Add the `os_signposts` instrument and record several scanner openings.
4. Filter for `camera.` and, if necessary, subsystem
   `xyz.lilsus.raylsuite` and category `PointsOfInterest`.

See Apple's [performance and metrics](https://developer.apple.com/documentation/xcode/performance-and-metrics)
documentation for the broader Instruments and Organizer workflow.

## Marker reference

| Marker | Platform | Meaning |
| --- | --- | --- |
| `camera.start_to_ready` | Android, iOS | Accepted start until CameraX analysis is bound, or the AVFoundation session is configured and running. |
| `camera.start_to_first_frame` | Android | Accepted start until the first active image reaches the analyzer. |
| `camera.configure_session` | iOS | AVFoundation input and metadata-output session configuration. |
| `camera.frame_analysis` | Android | One ML Kit request and preferred-code selection. |
| `camera.qr_detected` | Android, iOS | A new QR value was selected; the value is never recorded. |
| `camera.restart` | Android, iOS | A controller starts again after an earlier start. |
| `camera.stop` | Android, iOS | Camera shutdown and teardown. |

The benchmark reads `camera.start_to_ready` and
`camera.start_to_first_frame`. Firebase receives only durations for
`camera_start_to_ready`, `camera_start_to_first_frame`, and `camera_stop`. It
never receives frame-analysis or QR-detection events.

Typical interpretations:

- slower `camera.start_to_ready` points to discovery, configuration, or binding
  work before the camera is usable;
- a larger first-frame gap with stable ready time points to the camera pipeline,
  resolution negotiation, hardware, or scheduling;
- slower `camera.frame_analysis` points to ML Kit work, input resolution, CPU
  contention, or thermal state;
- unexpected `camera.restart` events suggest lifecycle or Compose effect churn;
  and
- slower `camera.stop` points to unbinding or teardown.

QR detection is not a repeatable benchmark without controlled QR size,
distance, light, and device position.

## Shipping-version monitoring

### Store dashboards

These require no extra app SDK and should be checked first after every release:

- **Android:** Play Console > Android vitals. Compare the new version with the
  previous one for startup, slow/frozen rendering, crashes, ANRs, battery, and
  device-specific outliers. Android vitals is based on data users allow Google
  Play to collect; Google's [Android vitals guide](https://developer.android.com/topic/performance/vitals)
  describes the available signals and alerts.
- **iOS:** Xcode > Window > Organizer > Metrics. Compare versions for launch,
  responsiveness, memory, disk writes, and energy. Apple's
  [shipping-app performance guide](https://developer.apple.com/documentation/xcode/improving-your-app-s-performance)
  describes the version and device breakdowns.

These dashboards cover store-distributed installs. Direct APK installs do not
appear in Android vitals, which is why the optional Firebase layer is useful.

Firebase is limited to Android here because it has a widely used native SDK,
hosted aggregation, release/version filters, and the custom duration traces this
workflow needs. Adding it to the KMP/iOS graph would create more build and SDK
maintenance while Xcode Organizer already supplies the most useful iOS release
signals. Sentry is the better next addition if crash diagnosis becomes the goal;
Datadog is better suited to a larger paid RUM/observability program; and raw
OpenTelemetry still requires operating a collector and backend. None is simpler
for this narrowly scoped performance need today.

### Optional Android camera telemetry

Android builds support Firebase Performance Monitoring, but collection is
disabled in the manifest and remains off until a person explicitly enables
**Performance diagnostics** in Settings. The row is absent when the build has
no Firebase configuration.

When enabled, Firebase receives its standard performance metadata (including a
Firebase installation ID, session ID, app/device/OS information, network type,
and country derived from IP address), lifecycle and screen-rendering timings,
and the three fixed camera durations listed above. Google's
[Firebase privacy documentation](https://firebase.google.com/support/privacy/)
describes the exact fields and retention.

This project intentionally does not apply the Firebase Performance Gradle
plugin, so HTTP instrumentation and URL collection are disabled. It also does
not add Firebase Analytics, Crashlytics, custom attributes, custom metrics, or
dynamic trace names. Keep that boundary: never attach an amount, address,
destination, invoice, QR value, wallet identifier, credential, preimage, relay,
URL, error message, or free-form string to a performance trace.

Disabling the setting stops future collection. The preference is local to the
app and defaults to off after a fresh install.

## One-time Firebase setup

The code is complete without Firebase credentials: apps still build and the
setting stays hidden. To activate Android field monitoring:

1. Create one Firebase project for the suite. Do not enable Google Analytics;
   Performance Monitoring does not require it.
2. In that project, register Android clients matching every variant that the
   repository builds: each product's base package plus `.dev` and `.e2e`, and
   Blip's `.benchmark` package.
3. Download a `google-services.json` containing those clients into each app
   module:
   `apps/blip/androidApp/`, `apps/flint/androidApp/`, and
   `apps/lasr/androidApp/`.
4. Review and commit the files so local, CI, and release builds use the same
   project. Firebase documents these files as containing unique but
   [non-secret identifiers](https://firebase.google.com/docs/android/google-services-plugin-and-file).
5. Build and install a Debug app. The Performance diagnostics setting should
   now appear. Enable it, exercise startup and the scanner, then check Firebase
   Console > Performance > **Custom traces**. Initial data can take several
   hours to appear.
6. Grant Firebase Console access only to maintainers who need it and review
   access periodically. Keep production and ad-hoc experiments in the same
   documented project unless data isolation provides a concrete benefit.

Before activating Firebase in a published build, confirm that the app's linked
privacy policy contains the performance-diagnostics disclosure and that the
store privacy declarations match it.

## Release routine

For every candidate:

1. Run `./gradlew perfCheck` on the usual device for performance-sensitive
   changes, or add the `performance` PR label and inspect the CI summary.
2. Verify the Firebase configuration is present for Android and collection is
   still opt-in; never enable collection by default in the console or manifest.
3. After staged rollout begins, compare Android vitals and Xcode Organizer by
   app version. Check Firebase camera and startup distributions after enough
   opted-in samples exist.
4. Investigate a signal with a local system trace or signpost capture. Do not
   optimize from one aggregate or emulator result alone.

Preserve marker meanings over time so release comparisons remain useful. Add a
new marker only when it isolates an actionable stage and can be represented by
a fixed, non-sensitive name.
