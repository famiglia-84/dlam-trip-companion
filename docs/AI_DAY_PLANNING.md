# AI-assisted day planning

Trip Companion 0.3 adds **Suggest a day plan** to each trip's Day plans tab. It uses ML Kit's public Prompt API and Android AICore/Gemini Nano, with no AI API key, subscription, backend or cloud generation fallback. Google Maps/Places configuration remains separate.

## On your phone

1. Open a trip → **Day plans → Suggest a day plan**.
2. Choose a date inside the trip, a same-day start/finish window, visit duration (15–240 minutes) and estimated transfer buffer (0–120 minutes).
3. Select up to eight saved places for this trip/unassigned places and up to eight bookings. Select every booking the draft must protect; unselected bookings are not checked. Hotel stays, untimed and overnight bookings require manual planning. A booking without an end time uses the visit duration you chose.
4. Tap **Check AI readiness** to check the capability under Trip Companion's actual app identity. If downloadable, **Download model** explicitly requests the system-managed model using internet/storage. No download or generation happens just by opening the planner. Keep the app in the foreground. Stop waiting cancels this app's request; AICore may continue a download independently.
5. When ready, tap **Generate AI draft**. The model suggests only an order of your selected places; app code schedules them around the fixed bookings. Alternatively, **Build without AI** uses the order in which you selected places with the same validation, without contacting AICore.
6. Review the start/end times, fixed bookings and gaps. Buffers are estimates, not Google routing or verified journey times. Check opening hours and real travel times yourself. Changing inputs discards the draft so you can regenerate it.
7. **Save reviewed day** writes all activities together. It creates a day or fills an existing empty day, preserving its title/notes. It refuses to overwrite a day that already contains activities. Once saved, edit/reorder activities through the existing manual planner. Close/back discards an unsaved draft.

Inputs and validated drafts survive activity recreation/fold changes through the ViewModel; simple input fields/IDs are restored after process recreation. Unsaved generated drafts do not persist after process death. Moving the app to the background cancels an active AI request; saving an explicitly reviewed draft is allowed to finish.

## Validation and storage

The model receives at most eight selected place IDs, user-entered names/categories/addresses (length bounded), selected booking time constraints and planning durations. It returns a JSON permutation of the selected place IDs. Duplicate, missing, unknown or non-integer IDs, extra fields/prose and oversized output are rejected. User-supplied labels are JSON-encoded as data and cannot authorize adding a new stop or modifying booking times.

The app assigns times deterministically. Every visit has the chosen duration; every selected booking preserves its start and explicit end (or disclosed default duration). Stops must be within the day window with no overlaps and at least the chosen buffer between them. Feasibility is checked before calling the model, and the returned order is checked again. If the visits cannot fit, no partial plan is produced and no stop/booking is silently dropped. The scheduler fills gaps around bookings with uniform visit/transfer durations; it does not optimize routes or derive different durations for each place.

Saving re-reads the trip, selected places and bookings inside a Room transaction. Changed/deleted sources, an altered draft schedule or a now-populated day reject the save. It rebuilds/validates the schedule and inserts all activities in one transaction, preventing partial days and duplicate saves. Existing schema version 2 is retained; start times use the existing activity time field, with end times/fixed-booking labels in notes. Booking confirmation codes, private notes and photos are never copied into the AI prompt.

## Privacy and limits

Generation is local through AICore. This feature has no HTTP client, server endpoint or cloud fallback. The app still has internet permission for Google Maps and SDK/system usage: existing Google SDK diagnostic collection is separate from the on-device prompt. Model downloads are system-managed and may remain after uninstall. Raw SDK error messages and prompts/responses are not logged; the UI receives a code or safe retry guidance. There is no device location permission. This does not add database encryption or an app lock.

AI order suggestions do not establish distances, shortest routes, opening hours, accessibility, availability or booking validity. Google Places search remains the place discovery source. Suggestions are optional; human review and the existing manual editors remain available. Readiness is checked separately in Trip Companion even though the independent diagnostic previously generated a fictional itinerary successfully in 4.277 seconds on the user's Samsung phone.

ML Kit Prompt 1.0.0-beta4 requires Kotlin 2.3 metadata support. The app uses Kotlin/Compose compiler 2.3.21, AGP 8.13.2, checksum-pinned Gradle 8.13 and Room 2.8.4; Java bytecode remains 17 and SDK/build-tools 36. Generation is capped at 256 output tokens and 120 seconds, readiness at 20 seconds, and downloading at 10 minutes followed by a readiness check. Quotas, foreground requirements, system configuration and supported hardware can affect availability. No successful main-app inference is claimed until the signed APK is tested on the phone.

Validation covers malformed/adversarial output, fixed booking times, capacity/transfer conflicts, prompt privacy, cancellation/stale results, explicit actions and manual fallback. Room tests cover stale sources, populated-day protection and rollback after an insertion failure. Native tests cover generation/review/recreation/save with a controlled provider, closing without saving, and actual SDK readiness with a manual fallback. Controlled-provider tests establish app behavior, not real Nano model quality.

References: [Android Gemini Nano](https://developer.android.com/ai/gemini-nano), [ML Kit GenAI sample](https://github.com/googlesamples/mlkit/tree/master/android/genai), [Google Prompt getting started](https://github.com/android/skills/blob/main/device-ai/ml-kit-genai-prompt-api/references/get-started.md).
