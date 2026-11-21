# Anime Image Studio for Android

The Android app is now designed for real server-side AI image-to-image style transfer. It no longer uses the old local cartoon filter as its primary conversion engine.

## Features
- Choose an original photo.
- Select Japanese Anime, Anime Character, Anime Game Art, or Hand-drawn Animation.
- Optionally upload a reference image to guide the style.
- Send the photo to the secure backend and receive generated artwork.
- Before/after preview, save PNG, and share.

## Required AI backend setup

1. Create a fal.ai account and API key: https://fal.ai/dashboard/keys
2. Deploy this repository's `render.yaml` as a Blueprint in Render.
3. Add the fal key as the Render secret environment variable `FAL_KEY`. Never place the key in this app or commit it to Git.
4. In the app, enter the deployed Render service URL in **AI server address**. The default suggested URL is `https://anime-image-studio-api.onrender.com`; use the actual URL shown by Render.
5. Check `https://YOUR-SERVICE/health` and confirm `configured: true`.

The backend uses fal.ai's `fal-ai/image-apps-v2/style-transfer` image-to-image model. AI provider usage is billed to the fal.ai account. Photos are sent to the backend and fal.ai for processing.

## Build

Requires JDK 17 and Gradle 8.9:
```sh
gradle --no-daemon assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

This app handles still photos only. Video conversion is not included.
