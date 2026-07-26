# Repository Guidelines

## Project Structure & Module Organization

This package is a Flutter plugin with three implementation layers:

- `lib/` contains the public Dart API, platform interface, method-channel adapter, models, logging, and progress-stream management.
- `android/src/main/kotlin/` and `ios/v_video_compressor/Sources/v_video_compressor/` contain the native compression engines, channel handlers, and mirrored models.
- `test/` holds Dart unit and method-channel tests. Android native tests live under `android/src/test/`.
- `example/` is the runnable Flutter app; its `test/` and `integration_test/` directories cover widgets and device-level behavior. Screenshots are stored in `example/screenshots/`.

Keep platform contracts synchronized. A new model field or channel method normally requires matching Dart, Kotlin, and Swift updates.

## Build, Test, and Development Commands

- `flutter pub get` installs package dependencies.
- `flutter analyze` runs the configured `flutter_lints` checks.
- `flutter test` runs the Dart unit suite.
- `dart format .` formats Dart sources and tests.
- `cd example && flutter pub get && flutter run` launches the example on a device or simulator.
- `cd example && flutter test integration_test` runs device-backed integration tests.
- `cd example/android && ./gradlew assembleDebug lintDebug` compiles and lints Android code.
- `cd example && flutter build ios --no-codesign` validates iOS compilation on macOS; run `pod install` in `example/ios/` first when dependencies change.

## Coding Style & Naming Conventions

Use null-safe, idiomatic Dart with two-space indentation and let `dart format` decide layout. Follow `flutter_lints`; use `snake_case.dart` filenames, `UpperCamelCase` types, and `lowerCamelCase` members. Follow existing Kotlin and Swift formatting in native files. Reuse `VVideoLogger` instead of ad-hoc prints, and preserve the established `VVideo*` naming prefix for public plugin types.

## Testing Guidelines

Use `flutter_test`; name Dart files `*_test.dart` and native tests `*Test.kt` or `*Tests.swift`. Add focused tests for validation, serialization, channel payloads, progress events, cancellation, and error paths. Integration tests that exercise real compression require a device or simulator. No coverage threshold is automated, so do not reduce coverage for changed behavior.

## Commit & Pull Request Guidelines

Recent history favors concise prefixes such as `feat:`, `fix:`, `docs:`, and `chore(release):`; use an imperative, specific subject. Pull requests should explain behavior and compatibility impact, link relevant issues, list platforms tested and commands run, and include screenshots for example-app UI changes. Call out any channel-schema, permission, or minimum-platform changes explicitly.
