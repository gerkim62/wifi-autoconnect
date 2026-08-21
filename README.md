# Mgeni — Wi-Fi Auto-Login Android App

A minimal, reliable Android utility app built with Kotlin and Jetpack Compose that automates login to the Safaricom "Guest" captive portal (`10.10.10.10/login.html`).

---

## Features

- **One-Tap / Zero-Tap Auto-Login**: Fetches dynamic server session tokens (`au_pxytimetag`), submits saved credentials, and confirms true internet access via Google's `generate_204` endpoint.
- **8 Dedicated UI States**:
  1. **Splash / Loading**: Instant 204 connectivity check.
  2. **Already Connected**: Confirms active internet connection without login needed.
  3. **Not on Guest Wi-Fi**: Clear guidance when captive portal is unreachable.
  4. **Login Screen**: Clean form with password visibility toggle, "Remember me" option, and advanced settings link.
  5. **Connecting Screen**: Real-time authentication feedback.
  6. **Success Screen**: Confirms successful connection.
  7. **Login Failed Screen**: Resets password while preserving username for effortless retry.
  8. **Advanced Settings Screen**: Configurable portal URL with safety guidance and one-tap reset.
- **Smart Mobile Data Awareness**: Sincere advice banner appears if mobile data is active simultaneously, advising users to temporarily turn off cellular data if routing issues occur.
- **Jetpack Compose Material 3 UI**: Emerald green accents, dynamic styling, and complete Dark/Light mode support.

---

## Technology Stack

- **Language**: Kotlin 2.0+
- **UI**: Jetpack Compose (Material 3)
- **HTTP Client**: OkHttp 4.12
- **HTML Parsing**: Jsoup 1.18
- **Storage**: Android `SharedPreferences` (plaintext, per spec)
- **Minimum SDK**: API 26 (Android 8.0)
- **Target SDK**: API 35 (Android 15)

---

## Project Structure

```
com.mgeni.autologin/
├── MainActivity.kt                  # Single Activity host with edge-to-edge Compose
├── data/
│   ├── PreferencesManager.kt        # SharedPreferences persistence for credentials & portal URL
│   ├── NetworkMonitor.kt            # ConnectivityManager observer for cellular & Wi-Fi state
│   └── PortalClient.kt              # 204 checks, Jsoup HTML parser, and POST login handler
└── ui/
    ├── MgeniApp.kt                  # Composable routing the 8 screen states
    ├── theme/                       # Color, Type, and Material3 Theme definitions
    ├── components/                  # Reusable StatusIcon, Buttons, and MobileDataWarningBanner
    ├── screens/                     # 8 dedicated composable screens
    └── viewmodel/                   # MainViewModel and MainUiState
```

---

## Building and Testing

Open the project in **Android Studio (Ladybug / Koala or newer)** or run using Gradle:

```bash
# Run unit tests
./gradlew test

# Assemble Debug APK
./gradlew assembleDebug
```
