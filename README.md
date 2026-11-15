# Priyo Nots

**Your Personal Notes App — By Mohammad Parvaj**

Priyo Nots is a lightweight, offline-first Android Notes application built with **Kotlin**, **Jetpack Compose (Material 3)**, **Room SQLite**, and a local **HTML5 / CSS3 / JavaScript** rich-text editor.

## Key Features
- **Offline-First Room Database:** Create, edit, auto-save, pin, archive, trash/recover, search, and categorize notes locally without mandatory login.
- **Hybrid Rich-Text Editor:** Bold, italic, underline, strikethrough, headings, lists, interactive checklists, quotes, code blocks, highlights, dividers, undo/redo, and note colors.
- **Backup & Restore:** Manual and scheduled automatic JSON backups, optional **AES-256-GCM** password encryption (`.pnbak`), Merge/Replace restore, and **GitHub Secret Gist / Repository** cloud backup.
- **Reminders & Themes:** Local `AlarmManager` notifications with Snooze/Done actions, plus Light, Dark, and System themes.
- **App Updates:** Configurable **GitHub Releases** or custom HTTPS JSON update checker with APK verification.

## Where to Get the Installable APK
1. **In AI Studio:** Open the **Settings / Export** menu in AI Studio and choose **Download / Generate APK**.
2. **In GitHub:** Push this workflow (`.github/workflows/build-apk.yml`), open the **Actions** tab -> **Build Priyo Nots APK** -> **Run workflow** (or push a `v1.0.0` tag) to automatically build and publish `PriyoNots-v1.0.apk` under **Releases**.
3. **Locally with Gradle / Android Studio:**
   ```bash
   gradle :app:assembleDebug
   ```
   The compiled APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
