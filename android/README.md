# Anime Image Studio for Android

A photo-only Android app for converting still photos into anime-inspired artwork.

## Included photo workflow
- Select a photo using the Android system photo picker.
- Apply an offline, on-device cartoon/anime-inspired color and ink filter.
- Adjust effect strength.
- Preview the converted photo.
- Save the result as PNG in `Pictures/AnimeImageStudio`.
- Share the resulting image with other apps.

## Important quality note
The current image engine is a lightweight image-processing filter. It does not use a generative AI model such as Stable Diffusion, and it should not be described as equivalent to a true AI anime transformation. No API key, account, network connection, or cloud processing is needed.

## Build
Requires JDK 17 and Gradle 8.9:
```sh
gradle --no-daemon assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

This Android app is intentionally photo-only. Video import and video conversion are out of scope.
