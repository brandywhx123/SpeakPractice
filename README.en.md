# English & Chinese Pronunciation Trainer (SpeakPractice)

**中文文档: [README.md](README.md)**

An Android pronunciation training app built with Kotlin: play the standard pronunciation → read after it → speech recognition → score with an edit-distance algorithm, plus a **dual-channel waveform chart** comparing the standard and user pronunciations. Supports both English and Chinese.

## Features

- 🔊 **Standard audio playback** via native Android `TextToSpeech` (`Locale.US` for English, `Locale.SIMPLIFIED_CHINESE` for Chinese)
- 🎤 **Speech recognition scoring** through `RecognizerIntent` (`en-US` / `zh-CN`)
- 📏 **Levenshtein distance scoring** — dynamic programming computes character-level differences between recognized and standard text and outputs an accuracy percentage; works for both languages
- 🌊 **Waveform comparison** — two stacked waveform panels below the result area (top blue = standard, bottom green = user); identical text produces identical waveforms
- 🌐 **One-tap language switching** via RadioGroup; TTS and recognition languages switch together
- 📱 **Compact single-screen layout** — all functionality visible without scrolling
- 🔐 **Runtime permissions** — requests `RECORD_AUDIO` dynamically on Android 6.0+

## Tech Stack

| Item | Version / Notes |
|---|---|
| Language | Kotlin 2.0.21 |
| Min SDK | Android 7.0 (API 24) |
| Target / Compile SDK | API 36 |
| Build | Gradle 8.9 + Android Gradle Plugin 8.7.2 |
| JDK | 17 |
| UI | Material Components 1.12.0 + custom View |
| Speech synthesis | Android `TextToSpeech` |
| Speech recognition | `RecognizerIntent` (system speech service) |

## Project Structure

```
SpeakPractice/
├── settings.gradle                    # Gradle project settings
├── build.gradle                       # Root build script
├── gradle.properties                  # Gradle properties
├── gradlew / gradlew.bat              # Gradle wrapper scripts
├── gradle/wrapper/
│   ├── gradle-wrapper.jar             # Wrapper JAR (committed to VCS)
│   └── gradle-wrapper.properties      # Gradle 8.9 config
└── app/
    ├── build.gradle                   # App module script (compileSdk 36)
    └── src/main/
        ├── AndroidManifest.xml        # Permissions & Activity registration
        ├── java/com/example/pronunciationassistant/
        │   ├── MainActivity.kt        # Core logic: TTS + STT + edit-distance scoring
        │   └── WaveformView.kt        # Custom waveform-drawing View
        └── res/
            ├── layout/activity_main.xml   # Compact vertical layout
            ├── values/strings.xml         # String resources
            └── mipmap/                     # Vector launcher icons
```

## How It Works

### 1. TTS Standard Audio

`TextToSpeech` initializes asynchronously via an `OnInitListener` callback. At playback time it calls `setLanguage()` according to the selected language, then speaks with `speak(text, QUEUE_FLUSH, ...)`.

### 2. Speech Recognition (STT)

An `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` intent is created with the language model and locale (`en-US` / `zh-CN`). The system recognition UI is launched with `startActivityForResult`, and the best (highest-confidence) result is read in `onActivityResult`. The service is cloud-based and **requires an internet connection**.

### 3. Levenshtein Distance Scoring

Dynamic programming computes the minimum number of single-character operations (insert / delete / replace) needed to turn the recognized text into the standard text:

```
Accuracy = (1 - editDistance / standardTextLength) × 100%
```

The algorithm operates at the Unicode character level, so Chinese characters are counted individually — the same implementation serves both languages.

### 4. Waveform Visualization

The TTS-internal PCM stream is not exposed, and `RecognizerIntent` exclusively holds the microphone, so neither can be sampled directly. Instead, a **deterministic text-based synthesis** (character code points + sine envelope + high-frequency detail) renders the waveform: identical text always yields the identical waveform, while recognition errors show visible differences at the corresponding positions.

## Usage

1. Select the training language (English / Chinese)
2. Type the content to practice, e.g. `Hello World` or `你好世界`
3. Tap **Play Standard Audio**: TTS reads it aloud and the top waveform panel renders
4. Tap **Start Training**: grant the microphone permission and read aloud
5. After recognition, review the accuracy score and compare the two waveform panels

Result example:

```
Standard:   Hello World
Recognized: Hello Word
Edit distance: 1
Difference: 9.1%
Accuracy:   90.9%
```

## Build & Run

### Requirements

- JDK 17+ (the JBR bundled with Android Studio works)
- Android SDK Platform 36
- Android Studio (Koala or newer recommended)

### Command line (Windows PowerShell)

```powershell
# If your default JDK is below 17, point to the JBR bundled with Android Studio
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Build the debug APK
.\gradlew.bat assembleDebug
```

Output: `app\build\outputs\apk\debug\SpeakPractice.apk`

macOS / Linux:

```bash
chmod +x gradlew
./gradlew assembleDebug
```

### Android Studio

1. `File → Open` and select the project directory
2. Wait for Gradle Sync to finish
3. Connect a device or start an emulator, then click **Run**

> Note: `local.properties` (machine-specific SDK path) is git-ignored. Android Studio regenerates it automatically when the project is first opened on a new machine.

## Permissions

| Permission | Purpose | Granted |
|---|---|---|
| `RECORD_AUDIO` | Microphone capture for speech recognition | Runtime request |
| `INTERNET` | Cloud speech recognition service | At install |
| `ACCESS_NETWORK_STATE` | Detect network availability | At install |

## Known Limitations

1. Speech recognition relies on the device's system speech service (usually cloud-based) and is **unavailable offline**; some devices may not ship with a recognition service installed.
2. Some devices lack Chinese TTS voice data; install the language pack in system settings if needed.
3. Edit distance only compares the textual content — it does not analyze voiceprint, timbre, or intonation.
4. The waveform is a deterministic text-based visualization, not a real PCM audio waveform.

## License

This project is licensed under the [MIT License](LICENSE). You are free to use, modify, distribute, and use it commercially, provided the original copyright and permission notices are retained.

---

*For implementation details (full algorithm derivation and code comments), see [MainActivity.kt](app/src/main/java/com/example/pronunciationassistant/MainActivity.kt).*
