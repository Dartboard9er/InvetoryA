# Home AI

Private, local-first household inventory and AI assistant built with native Android.

Your home, remembered.

---

## Overview

Home AI is an intelligent home management companion that operates with a privacy-first, local storage model:

- **Universal Inventory**: Fast cataloging with photos, barcodes, categories, and serial numbers.
- **Home AI Assistant 2.0**: Context-aware assistant modes (Cooking, Garage, Home Maintenance, Find Something, General Home AI).
- **Voice Memory Clips**: Speak naturally (`[ 🎙 Tell Home AI ]`) to create instant transcribed notes and structured household knowledge.
- **Room Sweep & Visual Scanner**: Live camera-based cataloging with automatic item and detail extraction.
- **Privacy & Security**: All inventory data and memory records reside on-device in Room (SQLite).

---

## Technology Stack

- **Platform**: Native Android (API 24+)
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material Design 3
- **Database**: AndroidX Room (SQLite) with hierarchical location support
- **Intelligence**: Google Gemini API via private Bring-Your-Own-Key (BYOK) architecture
- **Camera & Hardware**: CameraX, Android SpeechRecognizer, Photo Picker
- **Build System**: Gradle 9 with Version Catalogs (`libs.versions.toml`)

---

## Downloading the APK from GitHub

Every push or pull request to the repository automatically triggers the GitHub Actions CI pipeline to build the debug APK:

1. Push your repository to **GitHub**.
2. In your GitHub repository, click on the **Actions** tab.
3. Select the latest run of the **Build APK** workflow.
4. Scroll down to the **Artifacts** section at the bottom of the summary page.
5. Click **HomeAI-debug-apk** to download the ZIP file.
6. Extract the downloaded archive to find `HomeAI-debug.apk`.
7. Transfer or install `HomeAI-debug.apk` directly onto any Android device (Android 7.0+ / API 24+).

---

## Building Locally

To build the APK locally from your terminal using the included Gradle wrapper:

### Prerequisites
- JDK 17 (or Java 11+)
- Android SDK (API 34/36)

### Command
```bash
# Clone the repository
git clone https://github.com/your-username/home-ai.git
cd home-ai

# Make Gradle wrapper executable
chmod +x ./gradlew

# Build debug APK
./gradlew assembleDebug
```

The compiled APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## Gemini API Key Configuration

Home AI uses a Bring-Your-Own-Key (BYOK) privacy model:

1. Open Home AI on your device.
2. Navigate to **More** → **Settings**.
3. Enter your Gemini API key in the dedicated settings field.
4. Your API key is stored privately in encrypted device storage and is **never** committed to Git or uploaded to third-party servers.

> **Security Note:** Never commit your `.env` file or hardcode API keys into the repository. `.env` is already configured in `.gitignore`.
