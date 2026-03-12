# Local Proxy

A lightweight Android proxy that lets you access remote servers via `localhost`, giving your web apps the same privileges as a secure origin — without needing HTTPS certificates on your dev server.

## Why?

Testing web features that require a **secure context** (HTTPS or localhost) is painful during development. Browsers treat `localhost` as a secure origin, which means features like these work without HTTPS:

- **Microphone / camera access** (getUserMedia)
- **Installing PWAs** (Add to Home Screen / service workers)
- **Clipboard API**
- **Web Bluetooth / Web USB**
- **Geolocation** (on some browsers)
- **Notifications API**

Local Proxy runs on your Android device and forwards `localhost:<port>` to your actual dev server. Your browser sees `localhost`, grants secure-context privileges, and your dev server doesn't need a certificate.

## How it works

The app creates a local proxy server on your device that listens on a configurable port and forwards all connections to a target host. Point your browser at `localhost:8080` and it transparently proxies to your real server.

## Features

- Configure target address and local port
- Start/stop proxy with a single tap
- Runs as a foreground service with persistent notification
- Real-time connection logging with color-coded log types (errors, connections, requests, info)
- Address history with quick-select and delete
- Copy/clear logs
- Handles both HTTP and HTTPS traffic
- CORS support — answers `OPTIONS` preflight requests locally and injects `Access-Control-Allow-Origin: *` into all HTTP responses
- 10-second connect timeout — fails fast when the target is unreachable
- Cache-safe 502 errors — includes `Cache-Control: no-store` to prevent cache poisoning

## Usage

1. Enter the target address (e.g. `192.168.1.100:3000` or `example.com:443`)
2. Set the local port (default: `8080`)
3. Tap **Start Proxy**
4. Configure your device or other apps to use `localhost:8080` as their proxy

The proxy will continue running in the background as a foreground service until you stop it.

## Pre-built APK

A pre-built debug APK is available in the [`apk/`](apk/) directory. Install it with:

```
adb install apk/localproxy.apk
```

## Building from source

Requires Android SDK with API level 34.

```
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Requirements

- Android 7.0+ (API 24)
- Internet permission (granted automatically)
- Notification permission (requested at launch on Android 13+)

## Tech stack

- Kotlin
- Jetpack Compose (Material 3)
- Kotlin Coroutines
- Android Foreground Service

## Current State

Working proxy service. A standalone developer tool for enabling PWA secure-context features against a local dev server without HTTPS.

## Where It's Heading

- Configuration UI (target host/port)
- Per-app proxy rules

## License

MIT
