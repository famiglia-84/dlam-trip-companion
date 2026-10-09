# Google Maps and place search

Version 0.4 makes the map the main part of the screen. Search is a compact field with an arrow button and keyboard Search action. Results appear over the map and close after selection; submitting/searching or selecting dismisses the keyboard. The **Saved places** button shows linked places, the information button contains help/privacy/terms, and **Expand map** hides the search controls while preserving the selected-place card. Use the search/exit buttons or Android Back to restore the controls.

The compact place card keeps **Save place link** visible below the map, with consistent 12 dp background padding on all four sides (dark green in the dark theme). Its chevron expands/collapses the full address and explanatory text. Short windows use one compact controls row with a **Map options** menu and a shorter place card. Google's map view stays above the card so its logo and map controls remain unobscured; search-result and provider attributions remain visible.

Version 0.3.1 also lets you tap a named point of interest directly on the map, such as a restaurant, shop or attraction, without another search. Its name/address load in the selection card; choose **Save place link**, enter your own label and confirm in the place editor. Tapping alone never saves a record. An unrelated previous search is not reused as the new place's label. Blank map areas are not selectable places; zoom in to reveal more named locations. This uses the existing Places API, with no additional Google service required.

Trip Companion 0.2 adds a Google Map in the main navigation and a Map tab inside each trip. Search for a place, address or city, select a result to see its location, then choose **Save place link**. Give it your own label, category and optional private notes. A trip's map shows its assigned places; the main map can show all linked saved places.

**Show saved places** fetches current map details for up to 50 distinct Google place IDs. Manually entered places have no coordinates: search for them and save a linked place first. Requests are explicit; typing alone does not search, and opening a map does not automatically look up all saved places. A request can incur Google Maps Platform charges. Place search uses an autocomplete session token and a limited details field mask.

## Enable maps in a browser-built APK

1. In [Google Cloud Console](https://console.cloud.google.com/), create or choose your project and attach billing.
2. Enable **Maps SDK for Android** and **Places API (New)**. No web-service Geocoding or Routes API is needed for this implementation.
3. Set up a stable personal signing key using the README instructions. Fresh GitHub runners have different disposable debug certificates, so an Android-restricted API key will not consistently work with ordinary PR APKs.
4. Get the signing certificate's **SHA-1** fingerprint in your trusted cloud terminal:

   ```bash
   keytool -list -v -keystore /path/to/your-private-keystore.jks -alias trip-companion
   ```

   Enter the password at the prompt. The fingerprint is public identification, not the private key. Alternatively run `apksigner verify --print-certs app-debug.apk` on a build signed with that key.
5. Create an API key. Under **Application restrictions**, choose **Android apps** and add package `com.famiglia.tripcompanion` with that signing certificate's SHA-1. Under **API restrictions**, allow only Maps SDK for Android and Places API (New). Use separate restricted keys/projects for development and personal use where practical. Set quotas and billing alerts; alerts alone do not cap spending.
6. In the private repository's **Settings → Secrets and variables → Actions**, save the key as **GOOGLE_MAPS_API_KEY**. Do not paste it into chat, source files, issues or workflow logs.
7. Run **Actions → Android APK → Run workflow** on the reviewed `feature/google-maps` branch, or merge the reviewed Maps integration PR and run on `main`. Trusted `main` and manual builds receive the Maps secret; pull-request builds receive no Maps or personal signing credentials.
8. In the successful run, open **Verify final APK and report public signing certificate**. Confirm its SHA-1 matches Google's Android key restriction, package is `com.famiglia.tripcompanion`, and personal signing and a Maps key were supplied. The step logs only public certificate/package information and whether configuration was present; no passwords, private keys or API key values are logged.
9. Download the successful build's APK artifact. Check the package, certificate and version before updating. An existing APK signed with a different key must be uninstalled, which deletes local data. Choose stable signing before storing important trips.

If you have no key yet, the build succeeds and the Map screen explains that Maps is unavailable. The rest of the app remains usable. Local cloud builds can receive `GOOGLE_MAPS_API_KEY` through a secure environment variable or an ignored `local.properties` entry. Never put the value in tracked `gradle.properties`.

An Android API key is included in the installed APK and can be extracted. GitHub Secrets prevents accidental source disclosure; **Android package/certificate and API restrictions are the actual protection against reuse**. It is not a server credential.

## Privacy, storage and connectivity

- Google receives map/tile requests, explicitly submitted search text, selected place IDs, and the network/device information its SDKs require. Google SDKs can collect usage and diagnostic information; consult [Maps SDK data disclosure](https://developers.google.com/maps/documentation/android-sdk/play-data-disclosure) and [Places SDK data disclosure](https://developers.google.com/maps/documentation/places/android-sdk/play-data-disclosure).
- Trip Companion's lookup interface accepts only search strings and place IDs. It does not send reservation confirmation numbers, travel dates, private notes, photos or the whole database to Google. Anything you type into the search box is sent when you choose Search.
- The app requests no device location permission and keeps the map's location layer and location button disabled. It does not track your movement or require Google account access.
- Only Google's place ID is persisted with your own manually entered label/address/notes. Google result names, addresses, coordinates and provider attributions remain in memory and are fetched again when requested. Map imagery/search have no guaranteed offline availability; your local travel records remain offline.
- Google's map attribution remains visible. Search results carry Google's supplied attribution image, and returned third-party place attributions are shown with their links. No Google photos, reviews or ratings are downloaded or stored.
- Maps integration does not add database encryption, an app lock, backup/export or production release hardening. This workflow still produces a **debug/testing APK**, even when it uses your personal signing key.

See [Google Privacy Policy](https://policies.google.com/privacy), [Google Maps/Google Earth additional terms](https://maps.google.com/help/terms_maps/), and [Places SDK policies](https://developers.google.com/maps/documentation/places/android-sdk/policies). Review applicable Google Maps Platform terms before distributing the app publicly.

## Verify on a device with the restricted key

Open Map, search for a known landmark, select it, pan/zoom and save a place link. Confirm that the link appears on the correct trip's map and in the global saved list. Restart and choose Show saved places to verify that the ID resolves again. Check airplane mode, unavailable results, a rejected key/API restriction, and fold/unfold transitions. Read the map's Google and third-party attribution. Confirm that existing version-1 trips, bookings, plans and notes survive a same-certificate upgrade.

Keyless builds, migration and search state can be tested in cloud automation. Actual Google authentication, billing, returned results, map rendering and Samsung handset behavior require this live-key device check; automated fakes do not establish those outcomes.
