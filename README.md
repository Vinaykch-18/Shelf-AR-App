# Shelf AR App

Shelf AR App is an experimental Android ARCore application for mapping retail shelves and guiding a later auditor back to the same shelf position. The app captures one stable store reference point, onboards shelves in a continuous AR walk, and then uses saved AR pose data, route checkpoints, shelf photos, and audit images to support a repeatable visual audit flow.

> Status: experimental / prototype. This repository is intended for personal R&D, demo, and iteration before production hardening.

## What this app does

- Saves a **store reference point** that auditors can find again later.
- Captures shelf onboarding photos with AR pose metadata.
- Records route checkpoints while shelves are onboarded.
- Starts an audit session and guides the user back to the next pending shelf.
- Shows camera-relative movement guidance such as walking forward, left, right, back, or turning.
- Displays AR floor/route cues, shelf cue markers, saved reference photos, and saved shelf photos.
- Captures audit images and stores them locally against the shelf audit record.
- Can be launched from SalesDiary-style external flow and return a callback status.

## Tech stack

- **Language:** Java
- **UI:** Android XML layouts + ViewBinding
- **AR:** Google ARCore + Sceneform
- **Local storage:** SQLite via `SQLiteOpenHelper`
- **Build system:** Gradle Kotlin DSL
- **Minimum SDK:** 24
- **Target SDK:** 36
- **Package:** `com.salesdairy.shelfarapp`

## Main app flow

```text
MainActivity
 ├─ StoreReferenceActivity       # Save the store entry/reference point
 ├─ OnboardShelfActivity         # Capture and save shelves in AR
 ├─ AuditNavigationActivity      # Guide auditor to pending shelves and capture audit photo
 ├─ AuditReviewActivity          # View saved shelf image and latest audit image
 └─ ShelfListActivity            # View mapped shelves and audit state
```

## Important modules

```text
app/src/main/java/com/salesdairy/shelfarapp/
├── activities/
│   ├── MainActivity.java
│   ├── StoreReferenceActivity.java
│   ├── OnboardShelfActivity.java
│   ├── AuditNavigationActivity.java
│   ├── AuditReviewActivity.java
│   └── ShelfListActivity.java
├── ar/
│   ├── ARCoreManager.java
│   ├── CloudAnchorHelper.java
│   ├── AuditGuidanceEngine.java
│   └── PoseUtils.java
├── audit/
│   ├── AuditNavigationUi.java
│   ├── AuditArStandMarkerHelper.java
│   ├── AuditGuideImageHelper.java
│   ├── AuditPoseHelper.java
│   ├── AuditSessionBundle.java
│   ├── AuditSessionProgress.java
│   ├── AuditRecoveryText.java
│   └── ReferencePointText.java
├── data/
│   ├── DBHelper.java
│   ├── StoreReferenceRepository.java
│   ├── ShelfRepository.java
│   ├── RouteRepository.java
│   ├── AuditRepository.java
│   └── TelemetryRepository.java
├── models/
├── onboarding/
├── sensors/
└── utils/
```

## Local database

The app stores data locally in SQLite database `shelf_ar.db`.

Main tables:

- `store_references`
- `shelves`
- `audit_sessions`
- `shelf_audits`
- `audit_images`
- `route_checkpoints`
- `route_edges`
- `audit_telemetry`

The schema is maintained in `DBHelper.ensureSchema()` using `CREATE TABLE IF NOT EXISTS` plus column checks, which makes local experimental upgrades easier while iterating.

## Setup

### Requirements

- Android Studio with support for Gradle 9.x / Android Gradle Plugin 9.x
- JDK 11 or newer
- Android device with ARCore support
- Google Play Services for AR installed on the test device
- Camera permission enabled

### Clone

```bash
git clone https://github.com/<your-github-username>/shelf-ar-app.git
cd shelf-ar-app
```

### Configure ARCore API key

This project uses ARCore Cloud Anchor APIs, so configure a Google API key before running the app.

Recommended approach for local development:

1. Add the key to `local.properties`:

   ```properties
   ARCORE_API_KEY=your_api_key_here
   ```

2. Wire the key through `manifestPlaceholders` in `app/build.gradle.kts`:

   ```kotlin
   defaultConfig {
       manifestPlaceholders["ARCORE_API_KEY"] = providers.gradleProperty("ARCORE_API_KEY")
           .orElse(providers.environmentVariable("ARCORE_API_KEY"))
           .orElse("")
           .get()
   }
   ```

3. Use the placeholder in `AndroidManifest.xml`:

   ```xml
   <meta-data
       android:name="com.google.android.ar.API_KEY"
       android:value="${ARCORE_API_KEY}" />
   ```

Do not commit real API keys to GitHub. Keep secrets in `local.properties`, environment variables, or your CI/CD secret store.

## Build and run

From Android Studio:

1. Open the project root folder.
2. Let Gradle sync complete.
3. Connect an ARCore-supported Android device.
4. Select the `app` run configuration.
5. Run the app on the device.

From terminal:

```bash
./gradlew clean assembleDebug
```

Install debug APK manually:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Testing checklist

### Store reference onboarding

1. Open the app.
2. Tap **Onboard shelves**.
3. If no store reference exists, the app opens the store reference screen.
4. Point the camera at a stable entrance/store reference detail.
5. Wait until tracking, center point, scan quality, and posture are ready.
6. Save the reference point.

### Shelf onboarding

1. After the store reference is ready, open shelf onboarding.
2. Walk through shelves in one continuous AR session.
3. Keep the full shelf inside the capture frame.
4. Capture the shelf image.
5. Enter or confirm the shelf name.
6. Save and continue for the next shelf.

### Audit flow

1. Tap **Start audit**.
2. Re-scan the saved store reference point.
3. Follow the route/direction guidance to the next pending shelf.
4. Use saved reference/shelf photo only as a visual check.
5. Capture the audit photo when the app reaches the ready state.
6. Review the captured audit image.
7. Continue until all shelves are audited.

### External SalesDiary-style launch

The app can receive launch extras such as:

- `source_app=salesdiary`
- `audit_session_token`
- `callback_scheme`
- `callback_host`

On completion/cancel/error, the app builds a callback URI with status and message values.

## Known limitations / experiment notes

- AR localization quality depends heavily on the saved store reference and physical store conditions.
- Cloud Anchor TTL is currently configured as a short experiment value in constants.
- Local pose reuse across fresh sessions can drift; saved photos and route guidance are used as fallback cues.
- The app stores audit/onboarding data locally. Backend sync is not included in this standalone experiment.
- Example unit/instrumentation tests are still default placeholder tests.

