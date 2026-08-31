# Performance monitoring

The suite exposes camera performance stages as Android system-trace events and
iOS signposts. This gives a quick answer to “did this camera-related change make
startup or analysis slower?” without a fixed QR-code fixture or production
telemetry.

## The short daily workflow

1. Use the same physical device and Debug app before and after a change.
2. Start an Android System Trace or an iOS `os_signposts` recording.
3. Open the scanner, wait for the preview, close it, and repeat several times.
4. Filter for `camera.` and compare the interval durations. Prefer the median of
   at least five runs over a single result.

No QR code is needed to measure camera startup or the Android analysis loop. A
real device is still required for meaningful camera timings; simulator results
only confirm that the flow and markers are wired correctly.

## Android capture

1. Install and run a Debug build on the device from Android Studio.
2. Open **Profiler**, select the app process, and record a **System Trace**.
3. Open and close the scanner a few times, then stop the recording.
4. Search the trace for `camera.`. The app slices may appear on different
   threads because startup and ML Kit analysis are asynchronous.

The same trace can be exported and opened in the Perfetto web UI. Android’s
[system tracing overview](https://developer.android.com/topic/performance/tracing)
describes the other supported capture methods.

## iOS capture

1. Run a Debug build on a physical iPhone from Xcode.
2. Choose **Product > Profile**.
3. Select the **Blank** template and add the `os_signposts` instrument.
4. Record while opening and closing the scanner, then filter names for
   `camera.`. If needed, narrow the view to subsystem
   `xyz.lilsus.raylsuite` and category `PointsOfInterest`.

Apple’s [Recording Performance Data](https://developer.apple.com/documentation/os/recording-performance-data)
guide shows the same Instruments workflow.

## What the markers mean

| Marker | Kind | Platform | Meaning |
| --- | --- | --- | --- |
| `camera.start_to_ready` | Interval | Android, iOS | From an accepted scanner start until the analysis use case is bound on Android, or the capture session is configured, running, and zoom is applied on iOS. |
| `camera.start_to_first_frame` | Interval | Android | From scanner start until the first active, non-null `ImageProxy` reaches the analyzer. AVFoundation’s metadata-only path has no equivalent fixture-independent callback. |
| `camera.configure_session` | Interval | iOS | Rebuilding the AVFoundation inputs, metadata output, selected format, and session configuration. |
| `camera.frame_analysis` | Interval | Android | One ML Kit request, including completion and preferred-code selection. |
| `camera.preview_attach` | Interval | Android, iOS | Binding the optional CameraX preview use case or attaching the AVFoundation preview layer. |
| `camera.preview_streaming` | Event | Android, iOS | The platform preview reports that it is visibly streaming. |
| `camera.qr_detected` | Event | Android, iOS | A new, non-duplicate QR value was selected. The value itself is never recorded. |
| `camera.restart` | Event | Android, iOS | A controller starts again after an earlier start. |
| `camera.stop` | Interval | Android, iOS | Camera analysis/session shutdown and teardown. |

Trace names are an internal diagnostic contract. They are deliberately static:
do not add QR contents, invoices, NWC URIs, credentials, payment preimages, or
other dynamic user data to a marker.

## Reading a regression

- A slower `camera.start_to_ready` points to provider/session discovery,
  configuration, binding, focus, or zoom work before the camera is usable.
- On Android, a larger gap between `camera.start_to_ready` and the end of
  `camera.start_to_first_frame` points more toward the camera pipeline,
  resolution negotiation, hardware, or scheduling than app navigation.
- Slower or more variable `camera.frame_analysis` slices point to ML Kit work,
  input resolution, CPU contention, or thermal state.
- A slow `camera.preview_attach` with stable first-frame timing isolates the
  optional visual preview from the QR-analysis critical path.
- Unexpected `camera.restart` events suggest lifecycle or Compose effect churn.
- A slow `camera.stop` suggests CameraX unbinding, analyzer cleanup, or
  AVFoundation session teardown.

`camera.qr_detected` is useful for inspecting one trace, but it is not a stable
benchmark endpoint without controlled QR size, distance, lighting, and device
position. Do not choose a camera implementation from that timing alone. The
more demanding fixture-based comparison method remains documented in
[camera2-evaluation.md](camera2-evaluation.md).

## Keeping this useful

- Preserve existing marker meanings so before/after traces remain comparable.
- Add a marker only when it separates two actionable stages.
- Keep tracing in `core:camera`; app modules should not duplicate these stages.
- Compare the same app variant, device, scan mode, device temperature, and
  permission state when investigating a regression.
- Save a representative trace with the issue or pull request only when it
  explains a finding; routine captures do not belong in Git.

An automated Blip macrobenchmark and a root `perfCheck` entry point are planned
next. Until those exist, this trace workflow is diagnostic rather than a CI
performance gate.
