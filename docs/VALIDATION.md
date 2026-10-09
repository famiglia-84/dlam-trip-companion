# Validation

## Place cards and photos, version 0.5.0

All **45 local tests pass** with no failures/errors/skips. They include a Room schema-1-to-3 upgrade that preserves all travel tables, a schema-2-to-3 upgrade retaining linked places/custom labels/addresses/notes, personal-photo URI persistence, explicit richer-detail requests, cancellation of stale results, bounded thumbnail caching and public map-link construction. The app and instrumentation APKs compile with the pinned toolchain.

Native tests exercise compact saved-place rows, Edit and exact-ID thumbnail navigation through the actual app graph, manual-entry search prefilling without an automatic request, sheet dragging/expansion/collapse/Back/Close, on-demand detail fetching, save actions, map attribution padding, keyboard dismissal and narrow/wide windows. They use a controlled provider and map renderer to avoid live Google requests. The APK workflow requires these emulator tests to pass before publishing an installable artifact.

Live Google photo responses/credits, API billing, contact/hour availability, external app actions, document-provider permissions and Samsung folding behavior still require the device checks in [the Maps guide](GOOGLE_MAPS.md). Google storage terms could not be fetched from this environment; the guide preserves the unresolved storage consideration for previously added confirmed name/address fields. Google photos and new rich fields are not stored in Room or persistent image caches.

Cloud validation on 9 October 2026 used JDK 21, Gradle 8.11.1, AGP 8.10.1, Kotlin 2.1.20 and Android SDK/build-tools 36.

```bash
source scripts/cloud-env.sh
./gradlew --no-daemon --continue testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

The command completed successfully. All 15 local tests passed, with no failures or skipped tests: four validation tests, nine repository/database tests and two Compose/Robolectric layout/saved-state tests. The database tests use simulated API 28; the layout tests use API 34 with native graphics. Robolectric is pinned to 4.17.

Android lint completed with no errors. Its remaining Kapt-to-KSP suggestion is a performance warning; Room's supported Kapt processor is intentionally used in this initial version. The app APK and the Android test APK both compiled. `apksigner verify --verbose` verified the debug app signature, and package inspection confirmed `com.famiglia.tripcompanion`, version `0.1.0` (code 1), minimum API 26 and target API 36.

Four native editor/window tests are configured for the GitHub Actions API 35 emulator: validation display, trip creation and activity restoration, editor-draft restoration, and narrow/wide window transitions. They were compiled locally but cannot run in an accelerated emulator on this machine because `/dev/kvm` is absent. This is separate from the successful local checks.

The editor tests originally ran under Robolectric. An isolated Material dialog with a single text field also failed to become idle, including with native graphics, a newer simulated SDK and Robolectric 4.17. The real editor assertions were moved to Android instrumentation tests rather than removed or weakened. GitHub Actions runs them in a dedicated emulator before uploading the APK; a successful run is required for the downloadable artifact.

No physical Samsung device testing has been performed. The emulator checks exercise Android behavior and resizing but cannot certify a physical folding hinge, Samsung firmware, photo-provider behavior or APK installation on the handset. Run the device checklist in the README before relying on personal travel data.

## Google Maps integration, version 0.2

The same complete cloud command passed after adding Maps Compose 6.4.3 and Places SDK 4.4.1. All **23 local tests passed**, with no skipped tests: four validation, nine repository, six Maps search/state, one database migration, and three Compose layout/navigation tests. The migration test creates the actual exported version-1 schema, inserts records in all five tables, upgrades it through Room's version-2 migration, and checks retained booking codes, notes, relationships and the new nullable place ID. Maps tests use a fake lookup provider to check explicit searches, request cancellation, session completion flags, scope restoration, ID-only lookups, partial failure, and missing configuration. No live Google requests are made by these tests.

Android lint, app APK assembly and instrumentation APK assembly pass. Lint retains its Kapt performance warning. Places transitively includes Glide's unused `NotificationTarget`; `app/lint.xml` suppresses only the `NotificationPermission` report naming that specific dependency class. The application posts no notifications and receives no notification permission.

APK signature verification succeeds. The actual built APK requests internet, network state and Wi-Fi state, plus AndroidX's app-specific signature permission. Coarse/fine location permissions introduced by dependencies are explicitly removed from the merged manifest. Version 0.2.0 uses version code 2 and remains a debug/testing build.

No Google API key was supplied. Live map rendering, Google billing/authentication, real search results, rejected-key behaviour and physical Samsung testing remain unverified. Follow [the restricted-key setup and live-device checklist](GOOGLE_MAPS.md) before relying on these online features. The offline planner and keyless Map setup screen are covered by cloud tests.
