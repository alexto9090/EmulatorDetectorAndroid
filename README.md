# EmulatorDetector
=============================================

[![Release](https://jitpack.io/v/mofneko/EmulatorDetector.svg)](https://jitpack.io/#mofneko/EmulatorDetector)

Android emulator detection library with comprehensive support for modern emulators. Works with Java, Kotlin, and Unity (C#).

## Supported Emulators

| Emulator | Status |
|----------|--------|
| Android Studio Emulator (AVD) | ✅ |
| Genymotion | ✅ |
| BlueStacks (all versions) | ✅ |
| NoxPlayer | ✅ |
| LDPlayer (including v9) | ✅ |
| MuMu Player / MuMu Player X | ✅ |
| MEmu Play | ✅ |
| Phoenix OS | ✅ |
| Waydroid | ✅ |
| Google Play Games for PC | ✅ |
| Koplayer | ✅ |
| Droid4X | ✅ |
| Andy | ✅ |
| iTools AVM | ✅ |

## Detection Methods

- **Build Properties** - Product, device, hardware, manufacturer, model, fingerprint analysis
- **File System** - Emulator-specific files and directories
- **Sensor Analysis** - Missing/fake sensors (accelerometer, gyroscope, etc.)
- **Telephony** - Network operator, SIM state, known emulator phone numbers
- **System Properties** - `ro.kernel.qemu`, `ro.hardware`, etc.
- **Package Detection** - Emulator launcher apps and services

## Requirements

- **minSdkVersion:** 21
- **compileSdkVersion:** 35
- **AndroidX**

## Installation

### Gradle (Java/Kotlin)

Add JitPack repository:

```gradle
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url "https://jitpack.io" }
    }
}
```

Add the dependency:

```gradle
dependencies {
    implementation 'com.github.alexto9090:EmulatorDetectorAndroid:2.0.1'
}
```

## Usage

### Basic Detection (Java/Kotlin)

```java
import com.alexto9090.emulatordetector.EmulatorDetector;
// Simple boolean check
boolean isEmulator = EmulatorDetector.isEmulator(context);

if (isEmulator) {
    // Running on emulator
}
```

### Detailed Detection with Confidence Score

```java
EmulatorDetector.DetectionResult result = EmulatorDetector.getDetailedResult(context);

boolean isEmulator = result.isEmulator;        // true/false
int confidence = result.confidenceScore;       // 0-100
List<String> reasons = result.reasons;         // Detection reasons

// Example output:
// isEmulator: true
// confidenceScore: 75
// reasons: ["Hardware contains: goldfish", "QEMU pipes detected", "Model indicates SDK/Emulator"]
```

### Unity (C#)

1. Create folder: `Assets/Plugins/Android`
2. Copy `library-release.aar` to the Android folder

```csharp
// Simple detection
AndroidJavaClass unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
AndroidJavaObject context = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity")
    .Call<AndroidJavaObject>("getApplicationContext");
AndroidJavaClass detector = new AndroidJavaClass("com.alexto9090.emulatordetector.EmulatorDetector");

bool isEmulator = detector.CallStatic<bool>("isEmulator", context);
```

## Development

```bash
git clone https://github.com/alexto9090/EmulatorDetectorAndroid.git
cd EmulatorDetectorAndroid
./gradlew :library:assembleRelease
```

Output AAR: `library/build/outputs/aar/library-release.aar`

## Changelog

### v2.0.0 (2025)
- **Migrated to AndroidX** and updated to compileSdk 35
- **Added support for modern emulators:** LDPlayer, MuMu, MEmu, Phoenix OS, Waydroid, Google Play Games
- **New detection methods:** Sensor analysis, telephony checks, system properties
- **New API:** `getDetailedResult()` with confidence scoring
- **Updated Gradle:** 8.7 with AGP 8.3.0

### v1.1.0 (2019)
- Initial release with basic emulator detection

## License

```
MIT License

Copyright (c) 2019 Yusuke Arakawa

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
