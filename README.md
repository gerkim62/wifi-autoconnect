# Mgeni (WifiAuto) — Wi-Fi Auto-Login Android App

A minimal Android utility app built with Kotlin and Jetpack Compose that automates login to the **Guest** Wi-Fi captive portal (`10.10.10.10/login.html`).

## 📲 Pre-built APK Download

You can grab the ready-to-install pre-signed release APK directly from the repo:
- **[assets/WifiAuto-v1.0.0.apk](assets/WifiAuto-v1.0.0.apk)** (~1.7 MB)

---

## ⚡ Quick Start: Build APK

Follow these steps to build the APK yourself from source:

### 1. Prerequisites
- **JDK 17** (e.g. OpenJDK 17, Eclipse Temurin 17, or Android Studio bundled JDK)
- **Android SDK** (API 36 platform + build-tools) or **Android Studio Ladybug / Meerkat+**

### 2. Set Environment (if building from terminal)
Make sure `JAVA_HOME` and `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) are exported in your terminal:

```bash
# Example (adjust paths to your machine)
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=$HOME/Android/Sdk   # Linux: ~/Android/Sdk | macOS: ~/Library/Android/sdk
```

*(Alternatively, create a `local.properties` file in the root directory with `sdk.dir=/path/to/your/android/sdk`)*.

---

## 📦 Generating APKs

| APK Type | Gradle Command | Output Location | Best For |
|---|---|---|---|
| **Production / Shareable (Release)** | `./gradlew assembleRelease` | `app/build/outputs/apk/release/app-release.apk` | **Sharing with friends** (optimized, ~1.7MB, pre-signed) |
| **Development (Debug)** | `./gradlew assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` | Local testing & development debugging |

### 🚀 Build Release APK (Shareable)
```bash
./gradlew assembleRelease
```
> **Output:** `app/build/outputs/apk/release/app-release.apk`  
> *Pre-configured with self-signing so friends can install it directly without developer tools or signature errors.*

### 🛠️ Build Debug APK
```bash
./gradlew assembleDebug
```
> **Output:** `app/build/outputs/apk/debug/app-debug.apk`

### 📲 Install Directly to Connected Device
```bash
# Install Release APK
adb install -r app/build/outputs/apk/release/app-release.apk

# Or install Debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🧪 Running Tests

To run all unit tests verifying connection check logic, state transitions, and HTML parser:

```bash
./gradlew testDebugUnitTest
```

---

## 🌐 Local Captive Portal Mock Server (Optional)

Test the login flow locally on your computer, emulator, or real device using the included mock server:

```bash
# Start the mock captive portal
npm start
# or
node captive-portal/server.js
```

### Server URLs:
- **Local Browser Portal**: `http://localhost:8080/login.html`
- **Android Emulator**: `http://10.0.2.2:8080/login.html`
- **Physical Device**: `http://<your-computer-ip>:8080/login.html`
- **Live Form Inspector Dashboard**: `http://localhost:8080/inspect` (inspect submitted credentials in real-time)
- **Reset Captive Portal State**: `http://localhost:8080/reset`

---

## ✨ Features

- **Zero / One-Tap Auto-Login**: Automatically retrieves server session token (`au_pxytimetag`), submits saved credentials, and confirms true internet access via Google's `generate_204` endpoint.
- **Dynamic Version Indicator**: Displays actual version number extracted from `BuildConfig.VERSION_NAME` on the About and Settings screens.
- **8 Dedicated UI States**:
  1. **Splash / Loading**: Instant 204 connectivity check.
  2. **Already Connected**: Confirms active internet connection without login needed.
  3. **Not on Guest Wi-Fi**: Clear guidance when the supported Guest portal is unreachable.
  4. **Login Screen**: Form with password visibility toggle, "Remember me" option, and advanced settings link.
  5. **Connecting Screen**: Real-time authentication feedback.
  6. **Success Screen**: Confirms successful connection.
  7. **Login Failed Screen**: Resets password while preserving username for effortless retry.
  8. **Advanced Settings Screen**: Configurable portal URL with concise guidance, startup check toggle, and one-tap reset.
- **Mobile Data Awareness**: Banner warns when mobile data and Wi-Fi are active simultaneously.
- **Jetpack Compose Material 3 UI**: Emerald green accents, dynamic styling, and complete Dark / Light mode support.

---

## 📁 Project Structure

```
guest-auto/
├── app/
│   ├── src/main/java/com/mgeni/autologin/
│   │   ├── MainActivity.kt          # Single Activity host with edge-to-edge Compose
│   │   ├── data/
│   │   │   ├── PreferencesManager.kt # SharedPreferences persistence for credentials & settings
│   │   │   ├── NetworkMonitor.kt     # ConnectivityManager observer for cellular & Wi-Fi state
│   │   │   └── PortalClient.kt       # 204 checks, Jsoup HTML parser, and POST login handler
│   │   └── ui/
│   │       ├── MgeniApp.kt           # Composable routing the 8 screen states
│   │       ├── theme/                # Color, Type, and Material3 Theme definitions
│   │       ├── components/           # Reusable StatusIcon, Buttons, and WarningBanner
│   │       ├── screens/              # 8 dedicated composable screens + About screen
│   │       └── viewmodel/            # MainViewModel and MainUiState
│   └── src/test/java/                # Unit test suite
├── captive-portal/
│   ├── server.js                     # Local Node.js mock captive portal & inspector server
│   └── index.html                    # Mock captive portal login HTML
└── README.md
```
