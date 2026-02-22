# localproxy — Android Local Proxy App

## What It Is
An Android app that runs a local HTTP proxy, enabling PWAs and web apps to make cross-origin requests to local network services that would otherwise be blocked by browser CORS policies.

## Architecture
- **Android app** (Kotlin)
- Build: Gradle (Kotlin DSL)

## How to Build
Open in Android Studio and build, or:
```
./gradlew assembleDebug
```

## Current State
Working proxy app. Used alongside LocalAppStore and the Home catalogue server.

## Where It's Heading
- Request filtering / rules engine
- Integration with PoPA bridge for per-app proxy configuration
