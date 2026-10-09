# Trip Companion

A personal, local-first native Android travel planner, inspired by the clarity and simplicity of Google Trips. Built for narrow phone displays and larger foldable/tablet windows, using original artwork and your own destination photos.

All development, compilation and automated testing can happen in Codex cloud and GitHub Actions. You do **not** need Android Studio, an SDK or Gradle on your Windows PC. Android 8.0 (API 26) or later is required; the app targets Android 16 (API 36).

## Phase 1 features

- Create, edit and delete trips with dates, notes and a destination photo selected from your phone. Current, upcoming and past trips have separate sections and departure countdowns.
- Keep flight, hotel, transport, restaurant and other bookings with local dates/times, confirmation numbers, addresses/routes and notes.
- Create one plan per trip day; edit the day, add/edit/remove activities, and reorder activities with accessible up/down controls. Saved places can be linked to activities.
- Save attractions, restaurants, cafés, shopping spots, landmarks and custom places. Search and filter your collection, and assign places to trips or leave them unassigned.
- Room persistence: trips, reservations, places, day plans, activities and notes work offline. Photos chosen from an on-device document provider also work offline; files supplied only by a cloud photo provider may need downloading first.
- System, light and dark themes. Tap the appearance icon to cycle through them.
- Single-column phone layout, two-column trip cards on medium windows, and a trip-list/detail layout with a navigation rail at 840dp and above. Selection, section and open form drafts survive activity recreation and window changes.
- A cloud workflow that tests, lints, builds and uploads a debug APK.
- Optional embedded Google Maps and place search. Save Google place links and view linked places on a trip map or the global map. See [Google Maps setup and privacy](docs/GOOGLE_MAPS.md).

Trip deletion removes its bookings and day plans, but keeps saved places as unassigned. Deleting a place keeps existing activities and their copied notes. Deleting a day removes its activities. Deletions require confirmation. Trip-date changes that would leave an existing day plan outside the new dates are rejected: move or delete that plan first.

## Get the APK in your browser

1. Open this repository on GitHub and choose **Actions → Android APK**.
2. Open a successful run. Builds run on pull requests and pushes to `main`; you can also choose **Run workflow** on a branch containing the workflow.
3. Under **Artifacts**, download `trip-companion-debug-<run number>` while signed in to GitHub. GitHub supplies a ZIP file.
4. Extract the ZIP and copy `app-debug.apk` to your phone, or download and extract it on the phone.
5. Tap the APK in Samsung My Files. Allow installation from that app when Android asks, then install Trip Companion. Samsung Auto Blocker may prevent sideloading; if enabled, its settings must permit the installation.

An unsigned source checkout alone is not an APK. Check that the run succeeded and contains an APK artifact. Failed builds retain available test/lint reports for diagnosis. Artifacts expire after 30 days; rerun the workflow to generate another.

For a first contribution to an empty repository, `main` contains a minimal baseline and the full app is submitted in a pull request. The PR itself builds before merge if GitHub Actions is enabled; GitHub may require owner approval of a first workflow run. Merge the reviewed PR to make the workflow available on `main`.

## Cloud toolchain and build

The pinned stack is Kotlin/Compose compiler 2.3.21, Compose BOM 2025.04.01 / Material 3, Android Gradle Plugin 8.13.2, Gradle 8.13, Room 2.8.4, Navigation Compose 2.8.9, Lifecycle 2.9.0, Coroutines 1.10.2 and Coil 2.7.0. JVM bytecode targets Java 17 and builds run on JDK 21. CI installs Android SDK 36 and build-tools 36.0.0. [AI-assisted day planning](docs/AI_DAY_PLANNING.md) uses ML Kit Prompt 1.0.0-beta4 through Android AICore, with local suggestions, strict scheduling, review before saving and a manual fallback.

In this Codex cloud environment, use the existing checkout (each task is already isolated; no additional worktree is needed):

```bash
cd /workspace/dlam-trip-companion
bash scripts/setup-cloud.sh
source scripts/cloud-env.sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The setup script installs tools under `/workspace/.trip-toolchain`, verifies pinned download checksums, accepts required Android SDK licenses, and uses the platform's proxy and public CA without disabling TLS verification. Source `scripts/cloud-env.sh` again in each new shell. Setup requires `curl`, `unzip`, `tar`, `node`, and the ordinary base Linux utilities; these are present in this environment. Installation and the first build need network access; the installed app's travel records do not.

GitHub Actions uses its standard Java/Android/Gradle setup actions and does not need the Codex-specific scripts. The Gradle wrapper downloads the exact distribution and checks its pinned SHA-256. APK output is `app/build/outputs/apk/debug/app-debug.apk`; HTML test reports are in `app/build/reports/tests/testDebugUnitTest/` and lint reports in `app/build/reports/lint-results-debug.html`.

## Architecture

`TripApplication` owns the Room database and repository. `TravelViewModel` exposes lifecycle-aware Flow state, operations, selection and appearance. Compose screens use that state and route between trips and the global place collection with Navigation Compose. Editor drafts use `rememberSaveable`; selection and the active trip section use `SavedStateHandle`.

The database has five related entities: `Trip`, `Reservation`, `Place`, `DayPlan` and `PlanActivity`. Foreign keys enforce ownership and deletion rules, a unique trip/date index prevents duplicate day plans, and transactional repository operations protect date changes and activity ordering. ISO dates and local 24-hour times are stored directly; no time-zone conversion is applied. Validation errors keep the editing form open. Version 1 exports its Room schema; later schema changes must introduce tested migrations, never destructive fallback.

The repository is the integration boundary. Future Gmail import or AI providers can return editable drafts and commit reviewed data through the same validation and persistence methods. The offline planner needs no API key. Embedded maps and place search optionally use Google Maps Platform with a restricted Android API key and billing; see [the setup guide](docs/GOOGLE_MAPS.md).

Room version 2 adds a nullable Google place ID to saved places. Its explicit version-1 migration preserves existing travel records. Google map details remain in memory; only the place ID and your own labels/notes are persisted. Maps Compose 6.4.3 and Places SDK 4.4.1 are pinned to match the existing Kotlin/Compose toolchain. Search state cancels superseded requests and reports connection/configuration failures without displaying raw SDK errors.

## Tests and device checks

`./gradlew testDebugUnitTest` runs strict date/time and trip-status tests, Robolectric-backed Room tests (CRUD, cascading deletes, activity ordering and close/reopen persistence), and Compose/Robolectric layout and saved-selection tests. These exercise Android APIs on the JVM and do not need an emulator. `./gradlew lintDebug` runs Android lint; `./gradlew assembleDebug` builds an installable APK.

GitHub Actions additionally launches a dedicated Android API 35 emulator and runs `./gradlew connectedDebugAndroidTest`. These tests exercise validation, text entry, trip creation, editor-draft restoration, activity recreation and changes between narrow and wide windows. The display overrides are restored after each test; run them only in a test emulator. Real dialog input tests are kept here because a minimal Material dialog also triggers an idle-loop failure in this cloud machine's Robolectric environment. This machine has no `/dev/kvm`, so the accelerated emulator runs in GitHub Actions.

Before relying on the app on your phone, check: create a trip, edit each type of record, reorder activities, restart the app offline, select a local photo, switch themes, and fold/unfold while viewing a plan or editing a form. These are device checks; JVM simulations do not establish that the physical Fold has been tested. See [validation notes](docs/VALIDATION.md) for the actual results of this implementation.

## Keeping updates compatible

First-test APKs use Android's normal debug key. A cloud runner generates a new debug key on each fresh machine, so different runs may not be installable over one another. Uninstalling to replace a mismatched key deletes local data. The app currently has no export/restore feature: choose stable signing **before entering data you want to keep**.

For consistent trusted `main` or manual builds, use a personal signing key. Keep it outside the repository, store a secure backup, and put these four values in GitHub **Settings → Secrets and variables → Actions**:

| Secret | Meaning |
| --- | --- |
| `TRIP_KEYSTORE_BASE64` | Base64-encoded personal JKS/PKCS12 keystore |
| `TRIP_STORE_PASSWORD` | Keystore password |
| `TRIP_KEY_ALIAS` | Key alias |
| `TRIP_KEY_PASSWORD` | Key password |

Create a key in a trusted cloud terminal with the interactive `keytool -genkeypair -alias trip-companion -keyalg RSA -keysize 3072 -validity 10000 -keystore /tmp/trip-companion.jks`. Enter passwords at the prompt, keep the key securely, and enter its Base64 representation only in GitHub's secret editor. Never paste secrets into chat or commit them. This is optional setup; no signing secrets have been created or requested for the MVP.

The workflow decodes the key into a temporary runner file, signs the debug APK and deletes the temporary copy. Pull requests always use disposable debug signing and never receive the personal key. The application ID must stay fixed, future version codes must increase, and subsequent builds must use the same signing key to update in place. Moving from a disposable key to a personal key requires a reinstall. Room migrations will be needed when the schema changes.

## Limits and next phases

Google Maps and place search are optional online features introduced in version 0.2; version 0.3 adds optional on-device AI-assisted day planning. There is no Gmail access, account login or remote synchronization. Photos are selected manually; the supplied mountain illustration is original fallback artwork. No third-party destination photos are bundled. Times do not encode time zones, and confirmations are manually entered. Activities are reordered with buttons rather than drag-and-drop.

Android application sandboxing protects the local database, but it is not separately encrypted. Cloud backup is disabled because bookings and notes may contain personal information. Clearing app storage or uninstalling deletes travel data. A device hinge is not explicitly mapped; adaptation follows available window width. Actual handset behavior and installation must be checked on the device.

Further Phase 2 work includes routes, richer imagery and offline improvements. Phase 3 covers optional AI planning and securely consented Gmail importing.
