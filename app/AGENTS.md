# Repository Guidelines

## Project Structure & Module Organization

This is a Kotlin Multiplatform Lightning payment app using Compose Multiplatform. The root Gradle project has a shared KMP module, `:shared`, a pure Android app module, `:androidApp`, plus an iOS shell app in `iosApp`.

- Shared application code lives in `shared/src/commonMain/kotlin/xyz/lilsus/papp`.
- Platform-specific code lives in `shared/src/androidMain` and `shared/src/iosMain`.
- Shared tests live in `shared/src/commonTest/kotlin`.
- Compose resources live in `shared/src/commonMain/composeResources`.
- Android app resources live in `androidApp/src/main/res`.
- iOS Swift/Xcode files live in `iosApp`.
- Maestro flows live in `flows`, with local E2E harness scripts and services in `e2e`.

Keep domain logic in `domain`, data implementations in `data`, UI/MVI code in `presentation`, Koin modules in `di`, platform abstractions in `platform`, and type-safe routes in `navigation`. Prefer `expect`/`actual` for platform behavior instead of branching on platform from common code.

## Build, Test, and Development Commands

Run commands from the repository root.

- `./gradlew check :androidApp:assembleDebug` runs tests, verification tasks, and builds the Android debug APK. Run this before submitting changes.
- `./gradlew :shared:allTests` runs unit tests.
- `./gradlew ktlintCheck` checks Kotlin formatting.
- `./gradlew ktlintFormat` auto-formats Kotlin sources.
- `./gradlew :androidApp:assembleDebug` builds the Android debug APK.
- `./gradlew :androidApp:assembleE2e` builds the Android E2E variant with test hooks enabled.
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` builds the iOS simulator framework.
- `./gradlew buildE2eIos` builds the iOS simulator app with the E2E bundle id.
- `./gradlew installE2eIos` installs the iOS E2E app on the booted simulator.
- `./gradlew :androidApp:printReleaseSigningConfig` reports Android release signing readiness without printing secrets.
- `e2e/bin/maestro-suite` runs the Maestro suite after the local E2E environment and app are ready.

## Coding Style & Naming Conventions

Use 4-space indentation and avoid wildcard imports. Keep multiline parameter and argument lists with trailing commas. Prefer immutable domain models and pure Kotlin in `domain`.

Naming follows Kotlin conventions: classes, interfaces, objects, and composables use `PascalCase`; functions and properties use `camelCase`; primitive/string constants use `SCREAMING_SNAKE_CASE`. Use cases should be named `VerbNounUseCase` and expose `operator fun invoke(...)`.

Presentation follows MVI: complete screen state in `*UiState`, user actions in `*Intent` or existing explicit handler methods, one-shot effects in `*Event`, and `StateFlow`/`SharedFlow` from ViewModels. Keep screen-specific state and events near the owning ViewModel unless a shared contract already exists.

Compose resources generate typed accessors under build outputs. Edit source resources in `composeResources`, then run the relevant build or check task; do not edit generated resource files directly.

## Testing Guidelines

Use `kotlin.test` for shared tests and `runTest` for coroutine tests. Name test classes `FeatureNameTest` or `ClassNameTest`. Prefer small fakes or manual mocks; use Ktor mock clients for API behavior. Add tests for business logic, parsing, repository behavior, and ViewModel state transitions.

For UI flows, prefer Maestro tests under `flows/tests` and reusable commands under `flows/tests/common`. Keep test tags stable because Android exposes Compose test tags as resource ids for Maestro.

## Commit & Pull Request Guidelines

Recent history uses concise Conventional Commit-style messages such as `fix(build): configure Gradle daemon memory`, `test: setup local e2e test harness`, and `chore/ci: improve workflows`. Keep commits focused and imperative.

Pull requests should include a short summary, verification commands, linked issues when applicable, and screenshots or recordings for visible UI changes. Call out configuration, signing, or resource-generation changes explicitly.

## Security & Configuration

Do not commit secrets, wallet credentials, signing keys, local password files, or `e2e/.env.local`. Release signing can be configured through Gradle properties or `PAPP_RELEASE_*` environment variables; keep machine-specific paths out of shared changes unless intentional.
