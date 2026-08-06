# Android Camera2 evaluation and migration plan

Status: deferred; continue using CameraX

Last updated: 2026-08-05

## Decision summary

Rayl Suite will continue using CameraX on Android for now. Android camera
initialization is limited with `CameraSelector.DEFAULT_BACK_CAMERA`, QR analysis
is bound before the optional preview, and preview work remains outside the
cold-start-critical Near path.

A direct Camera2 implementation remains a credible future option because the
scanner has an unusually strict requirement: cold start to QR detection matters
more than preview latency, photography features, or a seamless Near/Far lens
transition. Camera2 could discover and persist an optimal camera profile during
onboarding, then open the cached Near camera directly on subsequent launches.

Measurements on the connected Galaxy S10e indicate that this would probably
save only about 35-48 ms on that device. The camera HAL and sensor pipeline,
which Camera2 cannot bypass, account for most of the current time to first
frame. A migration should therefore be justified by deterministic lens control
and deferred Far-camera ownership as well as by measured latency.

## Product requirements

The camera subsystem must honor these priorities in order:

1. Detect and process a QR code as quickly as possible after a cold start.
2. Make Near scanning feel forgiving by using the widest useful field of view
   and a native 4:3 analysis stream near 1920x1440 when the hardware supports
   it.
3. Never let Preview creation, surface attachment, or Preview failure delay or
   break Near analysis.
4. When the user explicitly requests Far mode or holds to reveal Preview, favor
   a camera that can scan a distant QR code even when switching cameras costs
   several hundred milliseconds.
5. Show Preview elegantly, without a black flash. Preview performance must not
   be purchased with slower Near startup.
6. Preserve the existing QR selection, debouncing, and payment-processing
   behavior unless a camera API requires a narrow adaptation.

Near scanning is the primary journey. Far mode and Preview are secondary and
may pay their setup cost after the user requests them.

## Current CameraX design

The current Android design intentionally does the following:

- Uses `CameraSelector.DEFAULT_BACK_CAMERA` as CameraX's available-camera
  limiter and binding selector, without a custom intrinsic-zoom filter.
- Binds `ImageAnalysis` at approximately 1920x1440 before considering Preview.
- Uses `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`.
- Keeps the optional Preview out of the initial bind.
- Adds Preview only after the UI requests it; a Preview failure leaves analysis
  running.
- Uses CameraX zoom controls for Near/Far zoom on the selected camera.

`DEFAULT_BACK_CAMERA` is the cleanest portable CameraX limiter, but it is not a
strict cross-device promise that exactly one rear camera is initialized. Its
lens-facing filter may retain multiple public rear cameras on a multi-camera
device. CameraX has an initialization heuristic for conventional front/back
IDs, but it has no portable built-in selector meaning "only the default rear
camera" or "ultra-wide and default rear camera."

If the selected default rear camera is a logical multi-camera, the Android HAL
may switch physical lenses as zoom changes. If the OEM exposes only standalone
cameras to third-party applications, CameraX zoom remains digital on the bound
camera unless the application explicitly rebinds another available camera.

## Important platform findings

### Camera2 is supported

Camera2 is not deprecated. The deprecated API is Camera1,
`android.hardware.Camera`. CameraX is the recommended high-level library for
most applications and is itself implemented on top of Camera2. Android regards
Camera2 as appropriate for applications that need a specialized, low-level
camera stack.

References:

- [Choose a camera library](https://developer.android.com/media/camera/choose-camera-library)
- [Camera2 package](https://developer.android.com/reference/android/hardware/camera2/package-summary)

### A custom CameraX limiter still evaluates candidates

A custom CameraX filter that selects the camera with the smallest
`intrinsicZoomRatio` must receive candidate `CameraInfo` objects and inspect
their metadata. This retains some post-filter initialization benefit, but it
does not provide the ideal "already know the exact ID; do not inspect other
cameras" path.

CameraX's limiter is permanent for a `ProcessCameraProvider`: filtered cameras
behave as though they do not exist. Restricting that provider to only an
ultra-wide camera therefore prevents it from rebinding a normal camera later.

References:

- [CameraX camera limiter](https://developer.android.com/media/camera/camerax/configuration#camera-limiter)
- [Camera selection](https://developer.android.com/media/camera/camerax/configuration#camera-selection)

### CameraX can support a lazy secondary provider

CameraX 1.5 and newer expose the experimental `LifecycleCameraProvider`, which
allows separately configured provider instances. A future CameraX-only design
could keep a restricted Near provider and create a broader Far provider only
after the hold gesture. This is substantially less code than a Camera2 rewrite,
but the API is experimental and lifecycle ownership must be handled carefully.

Reference:

- [LifecycleCameraProvider](https://developer.android.com/reference/androidx/camera/lifecycle/LifecycleCameraProvider)

### Native camera applications are not an exact capability baseline

An OEM camera application may use private logical cameras or camera IDs that
are hidden from third-party applications. On the measured Galaxy S10e, the
Samsung camera application used private camera ID `20`, while Blip can see
public IDs `0` through `3`. Seeing a wider frame in the native application does
not prove that the same virtual camera is directly available to Blip.

The appropriate comparison is the widest public standalone camera or physical
camera that Android permits a third-party capture session to address.

## Measured CameraX latency

### Test environment

- Device: Samsung Galaxy S10e (`SM-G970F`)
- OS: Android 12, API 31
- Application: installed Blip release, version 1.0.0
- Camera: public default rear camera ID `0`
- Analysis stream: 1920x1440 YUV
- Method: force-stop followed by three launches with camera-service and CameraX
  timestamps collected from logcat
- Screen: awake and unlocked

The installed build used the default-back limiter. CameraX retained public rear
IDs `0` and `2` and opened ID `0`.

### Results

| Stage | Observed latency |
| --- | ---: |
| Process creation to CameraX initialization | 189-221 ms |
| CameraX initialization/selection to open request | 48-50 ms |
| Camera device open | 13-19 ms |
| 1920x1440 capture-session configuration | 77-94 ms |
| Configured session to first sensor frame | approximately 506-512 ms |
| Process creation to first camera frame | 841-884 ms |

Median process-to-first-frame latency was approximately 862 ms. The Samsung
HAL separately reported 574-594 ms from starting stream configuration to its
first frame. This HAL/sensor work dominates the budget and would remain under
Camera2.

Camera metadata retrieval was fast on this device: individual public-camera
queries were below 1 ms. CameraX's approximately 50 ms of pre-open time also
includes library initialization, camera adapters, quirks, resolution
selection, surface-combination calculation, and camera-graph creation.

The release build emitted roughly 142 CameraX/CXCP log lines between CameraX
initialization and first frame. Raising CameraX's release logging threshold is
a lower-risk optimization worth measuring before replacing the camera stack.

### Estimated Camera2 result

A fully cached Camera2 path could plausibly request `openCamera()` within about
2-8 ms of scanner startup instead of CameraX's measured 48-50 ms. The expected
net saving on the S10e is approximately 35-48 ms, producing a rough
process-to-first-frame estimate of 815-830 ms.

This is an estimate, not a demonstrated Camera2 result. Different camera IDs
may have different open and first-frame costs. Low-end devices may benefit more
because CameraX documents camera-characteristic IPC taking hundreds of
milliseconds in some cases.

ML Kit's cold first-frame processing and payment-network latency were not
included in this measurement. Camera2 does not inherently improve either one.

## Candidate Camera2 architecture

### Onboarding discovery

After Android grants camera permission during onboarding:

1. Enumerate public rear cameras once.
2. Read the characteristics needed for selection and session construction.
3. Select a Near camera/profile.
4. Select a Far camera/profile.
5. Persist a versioned profile in ordinary app-scoped preferences.
6. Continue onboarding without waiting for Preview or opening a long-lived
   camera session.

Discovery should also run when permission is granted outside onboarding. An
existing installation upgraded to the Camera2 implementation would pay this
cost once on its first cache miss.

### Near profile selection

The Near selector should preserve the intent of the experimented camera setup:

- Rear-facing and accessible to a third-party application.
- Widest useful field of view, ranked from sensor physical width and focal
  length rather than camera-ID naming assumptions.
- Prefer an exact or close 4:3 YUV stream.
- Prefer approximately 1920x1440, not the absolute largest sensor output.
- Require a usable frame rate, normally at least 30 fps.
- Prefer a directly openable standalone camera when it represents the desired
  lens.
- When the desired lens exists only as a physical member of a logical camera,
  record both logical and physical IDs and configure the output accordingly.

Camera IDs are opaque. ID `0` is a common default rear camera convention, not a
portable definition of "normal" or "wide."

### Far profile selection

The Far selector should favor a main/normal rear camera suitable for 2x zoom,
not a digitally cropped standalone ultra-wide camera. It should persist a
compatible analysis size and Preview size and enough zoom information to apply
the requested base zoom without re-querying every camera.

If the Near and Far profiles resolve to the same logical camera and that camera
supports hardware lens switching through its zoom range, Far may only require
a session reconfiguration plus zoom. Otherwise it is acceptable to close Near
and open the cached Far camera after the user requests it.

### Persisted profile

Persist a complete validated profile so the normal path does not call
`getCameraIdList()` or `getCameraCharacteristics()`:

- Near logical/top-level camera ID
- Near physical camera ID, when applicable
- Near analysis width, height, and image format
- Near sensor orientation
- Near autofocus mode
- Far logical/top-level camera ID
- Far physical camera ID, when applicable
- Far analysis width, height, and image format
- Far Preview size
- Far sensor orientation and autofocus mode
- Active-array/crop information or zoom-ratio range needed for 2x zoom
- Selection-algorithm/schema version
- `Build.FINGERPRINT`

Camera images and QR contents must never be persisted by this profile store.
The profile is non-secret device configuration and does not need encrypted
storage.

### Cache-hit cold start

On a valid cache hit:

1. Read the compact profile once.
2. Create the analysis `ImageReader` from cached dimensions.
3. Call `CameraManager.openCamera(cachedNearId)` directly.
4. Initialize ML Kit and analysis resources while the asynchronous open is in
   progress where safe.
5. On `CameraDevice.StateCallback.onOpened`, create a session containing only
   the Near analysis surface.
6. Start a repeating `TEMPLATE_PREVIEW` request with the cached supported AF/AE
   choices.

Camera2 does not require an application-side characteristics query before
opening a known camera ID.

Reference:

- [CameraManager.openCamera](https://developer.android.com/reference/android/hardware/camera2/CameraManager#openCamera(java.lang.String,%20java.util.concurrent.Executor,%20android.hardware.camera2.CameraDevice.StateCallback))

### Far and Preview transition

Only after the user selects Far mode or holds to reveal Preview:

1. Stop or close the Near session.
2. Open the cached Far camera if it differs from Near.
3. Create a Far session with analysis and Preview outputs.
4. Apply the 2x base zoom and any user-controlled additional zoom.
5. Reveal the Preview after the first rendered frame.

A `TextureView`-backed Preview is the likely Android choice when avoiding a
SurfaceView black flash matters. Its composition cost is acceptable because it
exists only on the secondary Far path.

When the gesture ends, close Far and reopen the cached analysis-only Near
profile. The switch may be visibly slower; this is an accepted tradeoff.

### Cache invalidation and recovery

Invalidate the profile when:

- `Build.FINGERPRINT` changes after an OTA.
- The selection schema changes.
- The app deliberately changes its target resolution or camera-ranking policy.

If a cached `openCamera()` call or session configuration fails:

1. Close all partial device/session/image-reader state.
2. Rediscover once.
3. Persist the refreshed profile.
4. Retry once.
5. Report scanner unavailable rather than entering an unbounded retry loop.

## Potential benefits

- Opens a cached Near camera without enumerating or ranking candidates on every
  process start.
- Allows a carefully selected ultra-wide Near camera while deferring the main
  Far camera completely.
- Prevents Preview creation and Far-camera metadata from entering the Near
  critical path.
- Gives direct control over logical/physical camera outputs and session timing.
- Can overlap `openCamera()` with `ImageReader` and ML Kit preparation.
- May reduce library class loading, adapter creation, and CameraX graph setup.
- Makes the chosen resolution and lens policy explicit and inspectable.
- Could reduce latency more substantially on devices with slow camera-service
  characteristic queries.

## Potential risks

### Compatibility and correctness

- CameraX currently supplies device quirks and compatibility workarounds that a
  direct implementation would lose.
- Logical and physical camera exposure varies significantly by OEM and Android
  version.
- A physical camera can sometimes be queried but not opened directly.
- Far analysis plus Preview may not support the preferred pair of resolutions.
- Camera2 zoom uses different mechanisms across API levels, including crop
  regions and newer zoom-ratio controls.
- Rotation metadata must be calculated correctly before creating ML Kit
  `InputImage` values.
- Autofocus modes are not uniform; some ultra-wide cameras are fixed-focus.

### Lifecycle and concurrency

- Rapid hold/release gestures can race asynchronous open, close, and session
  callbacks.
- Permission revocation, backgrounding, camera eviction, and another app taking
  the camera require explicit recovery.
- A stale callback must never attach a session from an obsolete generation.
- Camera device, session, surfaces, images, ML Kit scanner, and executors must
  close exactly once.

### Detection latency

- `ImageReader.acquireNextImage()` or too many outstanding images can build a
  stale queue. Use `acquireLatestImage()` and permit at most one ML Kit request
  in flight.
- Selecting an unnecessarily large sensor mode can make first frame and every
  analysis frame slower.
- Creating Near and Preview surfaces in one session would violate the primary
  requirement.
- Reconfiguring a session unnecessarily can cost hundreds of milliseconds.

### Tail latency

A Camera2 implementation might improve median startup by approximately 40 ms
while worsening P95/P99 latency through rare session failures or retries. The
decision must consider failed and slow launches, not only successful medians.

## Ways added complexity could consume the saving

- Blocking the main thread while loading a large or fragmented preference file.
- Starting camera threads only after serial cache validation.
- Querying characteristics again despite having a complete valid profile.
- Waiting for ML Kit initialization before calling `openCamera()` instead of
  overlapping the operations.
- Performing camera discovery as an unconditional application-start task.
- Selecting and validating output sizes again on every launch.
- Mounting the Preview view or allocating its surface during Near startup.
- Closing and reopening Near because of duplicate Compose lifecycle effects.
- Logging excessively on the release critical path.

The cache should be compact, read once, and retained in process memory. Camera
open should begin immediately after parsing it. Independent preparation should
be overlapped rather than serialized.

## Complexity estimate

| Area | Estimated implementation size | Complexity |
| --- | ---: | --- |
| Camera discovery and ranking | 150-250 lines | Medium |
| Versioned camera-profile persistence | 60-100 lines | Low |
| Camera2 device/session state machine | 400-650 lines | High |
| ImageReader to ML Kit delivery | 100-180 lines | Medium |
| Texture-based optional Preview | 100-180 lines | Medium |
| Permission, onboarding, and recovery wiring | 60-120 lines | Medium |

Approximately 800-1,200 lines would be introduced or substantially rewritten.
Much of the existing Android CameraX controller would be replaced, so the net
increase is likely around 300-600 lines.

A rough engineering estimate is two to three focused days for implementation
and another two to four days for lifecycle hardening and physical-device
validation. Device validation is less predictable than writing the initial
code.

## Validation strategy

### Performance markers

A comparison implementation should record trace markers for:

1. Process/activity start
2. Scanner start requested
3. Camera profile loaded
4. `openCamera()` requested
5. `CameraDevice.onOpened`
6. Capture session configured
7. Repeating request submitted
8. First `ImageReader` image acquired
9. First ML Kit request submitted
10. First ML Kit result returned
11. QR value dispatched to the payment coordinator

Use monotonic timestamps and Perfetto trace sections rather than relying only
on wall-clock logs.

### Benchmark method

- Compare optimized release builds, not debug builds.
- Use the same camera ID, resolution, QR code, lighting, device position, and
  application state for CameraX and Camera2.
- Force-stop between cold-start runs without clearing configured app data.
- Keep the screen awake and unlocked.
- Run enough iterations to report median, P90, P95, failures, and retries.
- Separate process-to-first-frame, scanner-start-to-first-frame, first-frame-to-
  QR, and QR-to-payment timings.
- Capture Perfetto camera, binder, scheduling, and application trace events for
  representative fast and slow runs.

### Functional device matrix

At minimum, validate:

- A device with separate public ultra-wide and normal camera IDs, such as the
  Galaxy S10e.
- A modern device exposing a logical multi-camera with a zoom range below 1x.
- A single-rear-camera device.
- A limited or legacy Camera2 hardware-level device.
- The minimum supported Android API or the oldest available representative.
- A recent Android/API device.

### QR behavior

- Near detection at the center and edges of the full 4:3 frame.
- Rough pointing where the QR is visibly off-center.
- Small and distant QR codes in Far mode.
- Multiple QR codes and preservation of the existing preferred-code policy.
- Rotation and device orientation handling.
- Dark scenes and autofocus/fixed-focus behavior.
- Sustained scanning without stale ML Kit frames or increasing latency.

### Preview behavior

- No Preview surface exists before a hold/Far request.
- Analysis begins and detects QR codes without Preview.
- Preview failure does not stop Near analysis.
- First Preview reveal has no black flash.
- Rapid press, release, and repeated gestures do not leak or race sessions.
- Releasing Far reliably restores the cached Near camera.

### Recovery behavior

- First run after onboarding.
- Existing installation with no cache.
- OTA/build-fingerprint invalidation.
- Deliberately stale or missing camera ID.
- Permission revoked while backgrounded.
- Camera occupied or evicted by another application.
- Activity pause/resume and process recreation.

## Proposed migration plan

### Phase 0: retain and measure the CameraX baseline

- Keep CameraX as production behavior.
- Keep the initial session analysis-only.
- Keep Preview lazy and non-fatal.
- Reduce CameraX release logging and remeasure.
- Record baseline performance for default and ultra-wide public cameras where
  available.

### Phase 1: implement profile discovery and persistence

- Add a pure camera-ranking model where practical.
- Discover after permission during onboarding.
- Store a versioned Near/Far profile.
- Expose diagnostic summaries in logs for development builds.
- Do not alter the production scanner yet.

### Phase 2: implement Camera2 Near analysis

- Place the implementation behind a temporary development-only selection.
- Open the cached Near camera directly.
- Bind only a YUV `ImageReader`.
- Reuse existing ML Kit options, QR preference, debouncing, and payment
  callback behavior.
- Measure against CameraX before proceeding.

Do not keep two permanent production camera stacks. The temporary comparison
path should be removed after the decision.

### Phase 3: implement Far and Preview

- Add the serialized Near/Far state machine.
- Open Far only after the user asks for it.
- Add analysis plus TextureView Preview to the Far session.
- Reveal only after the first rendered frame.
- Restore Near after release.

### Phase 4: harden and validate

- Exercise the functional matrix and recovery cases.
- Compare median and tail latency.
- Confirm that the chosen Near field of view is at least as useful as the
  CameraX baseline.
- Confirm no Preview or Far allocation appears in Near traces.

### Phase 5: decide and clean up

Adopt Camera2 only if it meets all decision gates. If adopted, remove Android
CameraX camera dependencies, provider configuration, and the temporary
comparison path in the same migration series. Keep iOS unchanged.

If rejected, delete the Camera2 experiment and retain this document and its
measurements as the decision record.

## Decision gates

A production Camera2 migration should require:

- Demonstrably correct selection of the widest useful Near camera.
- A Far camera that can detect QR codes at useful zoom.
- No Preview/Far work on the Near startup trace.
- No regression in QR detection rate or selected 4:3 analysis resolution.
- A meaningful measured latency benefit or a lens-control capability CameraX
  cannot provide cleanly.
- No worse P95/P99 startup or material increase in camera failures.
- Successful lifecycle and recovery validation on representative devices.
- A single maintainable Android camera backend at the end of the migration.

Given the current S10e measurement, a Camera2 rewrite is not justified by
median latency alone. It becomes reasonable if deterministic cached lens
selection and fully deferred Far-camera ownership materially improve the core
product experience.
