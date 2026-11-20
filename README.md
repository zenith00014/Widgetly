# Widgetly

A lightweight native Android widget app. The current build is an early feature-complete prototype target, not yet a final release.

## Included
- Jetpack Compose dashboard with a quick-note editor.
- Real Android home-screen widgets built with Jetpack Glance: Quick Note and Retro Clock.
- Quick Note refreshes when saved; Retro Clock refreshes on a low-frequency system schedule.
- GitHub Actions workflow that builds and uploads a debug APK.
- Material 3 interface; Android 8.0+.

## Build locally
Requirements: JDK 17, Android SDK 35, and Gradle 8.9 (or Android Studio).

```bash
gradle :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Build on GitHub
1. Push to `main` (or open a pull request).
2. Open the repository's **Actions** tab.
3. Select **Build Android APK** and run it, or wait for the push workflow.
4. Download the `widgetly-debug-apk` artifact from the workflow run.

## Add the widget
1. Install the APK on an Android device.
2. Long-press the home screen and open **Widgets**.
3. Find **Widgetly → Quick Note** and drag it onto the home screen.
4. Open Widgetly, edit the note, and tap **Save and update widget**.

## Structure
```text
app/src/main/
  java/com/widgetly/app/
    MainActivity.kt
    QuickNoteWidget.kt
  res/
    layout/
    values/
    xml/
.github/workflows/android.yml
```

## Release builds
The workflow currently produces an unsigned debug APK for easy testing. Configure a signing keystore and GitHub Actions secrets before distributing a release APK.
