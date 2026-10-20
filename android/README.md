# Android client

Native Android client built with Kotlin and Jetpack Compose. It connects to the same Spring Boot API with OAuth 2.0 and includes paper account overview, paper orders, strategy configuration/start/stop, backtesting, Angel One session/account/order-book status, and audit log access.

## Build in Android Studio

1. Open the `android/` folder in Android Studio.
2. Allow Gradle sync and install the Android SDK for API 35 if requested.
3. Run the `app` configuration on an emulator or physical device.

No Gradle wrapper is included in this source package; use the Gradle installation bundled with Android Studio.

## Backend URL

- Android emulator: `http://10.0.2.2:8080` routes to the development host computer.
- Physical phone on the same private Wi-Fi: use the computer's LAN IP, for example `http://192.168.1.20:8080` (replace with the actual address).

For a physical phone, set `APP_BIND_ADDRESS=0.0.0.0` in the same terminal before starting the backend, and allow port 8080 only on the private network in Windows Firewall. Keep the computer and phone on a trusted LAN. Do not port-forward this port to the public internet. The app's cleartext HTTP setting is for local development only; use HTTPS for real deployment and never send PIN/TOTP over an untrusted connection.

Use the Android **Sign in** button to authenticate through the system browser with OAuth 2.0 and PKCE. Access tokens expire after 10 minutes; sign in again when prompted. Create the first account on the backend computer's web dashboard before connecting the phone. Live-order placement is not exposed in this initial Android screen; use the web or iPhone client only after all server-side trading gates are validated.
