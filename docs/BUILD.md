# Build instructions

Requirements: Android Studio (recent stable), JDK 17, Android SDK platform 34 or as set in `app/build.gradle.kts`.

1. Open the project folder in Android Studio and let it sync. If Gradle sync fails on a version, update the version in `app/build.gradle.kts` / root `build.gradle.kts` to a current one.
2. Command line: `gradle wrapper --gradle-version 8.9` once (the wrapper jar is not included), then `./gradlew testDebugUnitTest assembleDebug`.
3. APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub

```
git init && git add . && git commit -m "Initial import"
git remote add origin <your-repo-url> && git push -u origin main
```
`.github/workflows/android.yml` runs unit tests and builds a debug APK on each push. It uses Gradle 8.9 directly, so the missing wrapper jar does not matter in CI. Release signing is not configured; add your keystore as GitHub secrets before building a release.

## Likely first-build issues (code was never compiled)

- Version mismatches among Kotlin, KSP, Compose BOM, Hilt.
- MediaPipe `tasks-genai` API names in `LlmSermonEngine.kt` may differ from the installed version.
- `pdfbox-android` version and init call in `ImportManager.kt`.
- Missing imports or small signature mismatches between screens.
