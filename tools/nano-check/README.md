# Trip Companion AI Check

A separate Android diagnostic app for checking whether Gemini Nano is accessible through ML Kit's public Prompt API on your phone. Version 1.1 adds an explicit model download and a fictional itinerary generation test. It is **not** an AI planner or a new version of Trip Companion.

## On your phone

1. Download `trip-companion-ai-check-<run number>` from a successful **On-device AI compatibility check** GitHub Actions run. Extract the ZIP to find `app-debug.apk`.
2. If Android refuses to update the old checker because the signing certificate differs, uninstall **Trip Companion AI Check** only, then install the new APK. CI uses a disposable development signing certificate. Do not uninstall Trip Companion.
3. Install using Samsung My Files and open **Trip Companion AI Check**. Its separate package ID (`com.famiglia.tripcompanion.nanocheck`) means it does not replace or clear Trip Companion. Confirm the report says version 1.1.
4. Tap **Check compatibility**. Keep the diagnostic open for up to 20 seconds.
5. If `DOWNLOADABLE`, connect to Wi-Fi and tap **Download model**. This explicitly asks AICore to download the model using internet and device storage. Progress shows reported bytes; the app waits up to 10 minutes. No account/API key is needed. If `DOWNLOADING`, wait and check again.
6. When `AVAILABLE`, tap **Test fictional itinerary**. Keep the checker in the foreground for up to 2 minutes. No real trip information is supplied. It shows the exact prompt, output and elapsed milliseconds including cold model startup; this is not a battery or sustained-performance benchmark.
7. Tap **Copy result** and paste the report into the chat, even if a step fails. The report contains manufacturer/model, Android/security-patch versions, AICore's visible version, results and any fictional output. It includes no IMEI, serial number, account details, trips or booking information.
8. Uninstall the diagnostic when you have finished; Trip Companion is unaffected. The downloaded system model may remain managed by AICore.

**Stop waiting** cancels this app's SDK request, but AICore may continue a download independently. Folding/unfolding, rotation or leaving/reopening the activity can interrupt a test; use a stable screen configuration and recheck readiness before retrying. Errors/timeouts also require a fresh check before another download or generation request.

| Result | Meaning |
| --- | --- |
| AVAILABLE | The SDK reports that the model is ready. Tap Test fictional itinerary. |
| DOWNLOADABLE | The feature is supported, but the model needs downloading. Tap Download model to request it explicitly. |
| DOWNLOADING | Android reports an ongoing download. Let it finish, then retry. |
| UNAVAILABLE | The API is currently unavailable; support, configuration and system updates can affect this. This is not a permanent hardware verdict. |
| SDK ERROR / ERROR / TIMED OUT | The step did not complete. Paste the report for interpretation; no readiness/success is assumed. |
| TEXT RETURNED | The model returned text. This does not mean it obeyed the itinerary constraints. |
| NO TEXT RETURNED | Generation completed without usable text. Paste the report. |

The fixed fictional test asks for Amber Museum at 09:00 for 45 minutes, then Willow Garden and Pebble Market for 45 minutes each, with 15-minute walks between visits. The expected finish is 11:45. It must preserve the reservation, order and durations without inventing stops or opening hours. The checker includes this reference schedule for human review; it does not label unvalidated output as a correct plan. The request is capped at 384 output tokens and supplies no maps/search input.

## Privacy and scope

Compatibility checking calls only `checkStatus()`. The separate Download model button calls `download()` and then rechecks readiness. The separate Test fictional itinerary button calls `generateContent()` with a fixed fictional text prompt through AICore. There are no automatic downloads, generation requests or warmup calls. Each operation closes its client and cancels unfinished futures on interruption/timeout. Public device/AICore version information and reports stay in activity memory/state; copying is explicitly requested with the Copy result button. No trip-storage access, custom prompt input, backend or API account is added.

The merged APK has **no internet, location, contacts, camera, microphone or storage permissions**. ML Kit's internet permission is removed. Its normal network-state permission is retained because the SDK's background-job infrastructure requires it; this permits checking connectivity, not making internet requests. AICore and Android's other system services operate independently and may need internet for their own model/configuration updates. The diagnostic cannot read Trip Companion's private data. Android backup and debugging are disabled. The APK uses a disposable development signing certificate and is intended only for this one-off readiness check.

The availability check alone does not verify inference. A successful fictional generation would demonstrate basic local inference under the diagnostic's application ID, not itinerary accuracy, battery performance, structured-output support, live place discovery or availability under Trip Companion's own application ID. Those remain separate planner-integration questions.

## Build in cloud

This is a standalone Gradle build; it does not modify or upgrade Trip Companion's Gradle/Kotlin/Compose configuration. It uses Java 17 bytecode, JDK 21, AGP 8.13.2, Gradle 8.13, Android SDK/build-tools 36 and ML Kit Prompt 1.0.0-beta4. The Java API avoids a Kotlin compiler upgrade in Trip Companion. Minimum Android API is 26, though actual model availability depends on the device.

```bash
source scripts/cloud-env.sh
./tools/nano-check/gradlew -p tools/nano-check --no-daemon lintDebug assembleDebug
```

The pinned Gradle distribution hash is checked by the wrapper. GitHub Actions additionally verifies the APK signature, package ID, absence of internet/sensitive permissions and disabled debugging. An API 35 emulator test calls the real SDK for a completed readiness/error report, recreation and clipboard copying. Additional native tests use a controlled engine to verify explicit download/generation actions, prompt/output handling, SDK errors, cancellation and recreation during a download, without downloading or generating on the emulator. These tests cannot establish physical Fold compatibility or actual model quality; the phone result is required.

References: [Android Gemini Nano documentation](https://developer.android.com/ai/gemini-nano), [Google's GenAI sample](https://github.com/googlesamples/mlkit/tree/master/android/genai), [Google's Prompt setup reference](https://github.com/android/skills/blob/main/device-ai/ml-kit-genai-prompt-api/references/get-started.md).
