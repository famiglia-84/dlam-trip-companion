# Validation

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
