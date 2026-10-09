# Trip Companion AI Check

A separate Android diagnostic app for checking whether Gemini Nano is accessible through ML Kit's public Prompt API on your phone. It is **not** an AI planner or a new version of Trip Companion.

## On your phone

1. Download `trip-companion-ai-check-<run number>` from a successful **On-device AI compatibility check** GitHub Actions run. Extract the ZIP to find `app-debug.apk`.
2. Install the APK using Samsung My Files and open **Trip Companion AI Check**. Its separate package ID (`com.famiglia.tripcompanion.nanocheck`) means it does not replace or clear Trip Companion.
3. Tap **Check compatibility**. Keep the diagnostic open for up to 20 seconds.
4. Tap **Copy result** and paste the report into the chat. It contains manufacturer/model, Android and security-patch versions, AICore's installed version if visible, and the SDK result. It includes no IMEI, serial number, account details, trips or booking information.
5. Uninstall the diagnostic when you have finished; Trip Companion is unaffected.

| Result | Meaning |
| --- | --- |
| AVAILABLE | The SDK reports that the model is ready. Actual inference and planner quality still need separate tests. |
| DOWNLOADABLE | The feature is supported, but the model needs downloading. This diagnostic never starts a download. |
| DOWNLOADING | Android reports an ongoing download. Let it finish, then retry. |
| UNAVAILABLE | The API is currently unavailable; support, configuration and system updates can affect this. This is not a permanent hardware verdict. |
| CHECK ERROR / CHECK TIMED OUT | Availability was not established. Paste the report for interpretation. |

## Privacy and scope

The app calls only `Generation.getClient()` and `checkStatus()`, then closes the client. It never calls generation, warmup or download APIs. It reads public device-version information and AICore package information; clipboard copying is explicitly requested with the Copy result button.

The merged APK has **no internet, location, contacts, camera, microphone or storage permissions**. ML Kit's internet permission is removed. Its normal network-state permission is retained because the SDK's background-job infrastructure requires it; this permits checking connectivity, not making internet requests. AICore and Android's other system services operate independently and may need internet for their own model/configuration updates. The diagnostic cannot read Trip Companion's private data. Android backup and debugging are disabled. The APK uses a disposable development signing certificate and is intended only for this one-off readiness check.

The availability check does not verify actual inference, itinerary accuracy, battery performance, structured-output support, or availability under Trip Companion's own application ID. It establishes whether the phone exposes the public SDK capability to this diagnostic.

## Build in cloud

This is a standalone Gradle build; it does not modify or upgrade Trip Companion's Gradle/Kotlin/Compose configuration. It uses Java 17 bytecode, JDK 21, AGP 8.13.2, Gradle 8.13, Android SDK/build-tools 36 and ML Kit Prompt 1.0.0-beta4. The Java API avoids a Kotlin compiler upgrade in Trip Companion. Minimum Android API is 26, though actual model availability depends on the device.

```bash
source scripts/cloud-env.sh
./tools/nano-check/gradlew -p tools/nano-check --no-daemon lintDebug assembleDebug
```

The pinned Gradle distribution hash is checked by the wrapper. GitHub Actions additionally verifies the APK signature, package ID, absence of internet/sensitive permissions and disabled debugging. An API 35 emulator test checks app launch, a completed readiness/error report, recreation and clipboard copying before upload. That smoke test cannot establish compatibility with a physical Fold 8; the phone result is required.

References: [Android Gemini Nano documentation](https://developer.android.com/ai/gemini-nano), [Google's GenAI sample](https://github.com/googlesamples/mlkit/tree/master/android/genai), [Google's Prompt setup reference](https://github.com/android/skills/blob/main/device-ai/ml-kit-genai-prompt-api/references/get-started.md).
