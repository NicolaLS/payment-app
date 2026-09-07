# Performance monitoring

Use local benchmarks, pull-request reports, Android system traces, and iOS
signposts to investigate performance changes. This guide documents the tools,
marker meanings, and telemetry behavior contributors need to preserve.

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

## Optional Android camera telemetry

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

Preserve marker meanings over time so comparisons remain useful. Add a new
marker only when it isolates an actionable stage and has a fixed, non-sensitive
name. Apps build without Firebase configuration; collection controls remain
hidden when configuration is absent.
