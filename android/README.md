# Anime Image Studio for Android

A native Android app that runs the first photo-to-cartoon workflow entirely on the phone.

## Features in this first Android release
- Pick a photo from the Android photo picker.
- Generate an anime-inspired cartoon effect locally on the device.
- Preview the result.
- Save PNG output to Pictures/AnimeImageStudio.
- Share the result with other apps.
- No account, API key, internet connection, or GPU required for this lightweight effect.

## Build
Requires JDK 17 and Gradle 8.9:
```sh
gradle --no-daemon assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Current limitation
This first Android build uses a lightweight on-device posterization and edge-ink filter. It is not the same quality as the SDXL/IP-Adapter AI pipeline in the Python desktop prototype. The advanced AI model integration is a later phase.
