# Local Proxy — Android Secure Context Proxy

## What It Is
A lightweight Android proxy that routes remote server traffic through `localhost`, giving web apps the same privileges as a secure origin without needing HTTPS certificates. Enables testing of secure-context browser features (mic/camera, PWA install, service workers, Web NFC, Web Bluetooth) against a dev server over plain HTTP.

## Why
Browsers treat `localhost` as a secure origin, so routing remote traffic through it unlocks secure-context features without setting up TLS certificates on dev servers.

## Architecture
- **Android app** (Kotlin)
- `ProxyService.kt` — background service that proxies HTTP traffic from localhost to a configured remote host
- Build: Gradle (Kotlin DSL)

## How to Build
Open in Android Studio and build, or:
```
./gradlew assembleDebug
```

## Current State
Working proxy service. Used alongside LocalAppStore and the Home catalogue to enable PWA features on Android without HTTPS.

## Where It's Heading
- Configuration UI (target host/port)
- Per-app proxy rules
- Integration with PoPA bridge for per-bridge proxy configuration
