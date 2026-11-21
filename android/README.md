# Anime Image Studio — Android

This Android package is based on the official **Local Dream** arm64 release, with the visible launcher name changed to **Anime Image Studio**. It retains Local Dream's on-device Stable Diffusion engine, model manager, image-to-image workflow, and CPU/GPU support. It is not the old remote fal.ai prototype.

## Build the APK

Run the **Build Anime Image Studio Android APK** workflow in GitHub Actions, or push a change to one of the workflow's watched files. The workflow:

1. Downloads the official Local Dream v2.8.1 arm64 APK.
2. Verifies its published SHA-256 digest.
3. Rebrands the launcher label and adds third-party attribution.
4. Rebuilds, aligns, signs, and verifies the APK.
5. Uploads `Anime-Image-Studio-Android-APK` as a workflow artifact.

The package is arm64-v8a and requires Android 9 (API 28) or newer. The internal Android application ID remains `io.github.xororz.localdream` to preserve the upstream application code and data layout; the launcher name is Anime Image Studio. If the original Local Dream app is already installed, Android may require uninstalling it before installing this differently signed build.

## AI models and offline use

The app runs inference on the phone; it does not send photos to a paid inference API or require a hosted backend. Internet is needed to download a model the first time. Once the selected model files are stored locally, generation can run offline. Choose an anime checkpoint such as Anything V5 or another anime model in the model catalogue, and use image-to-image for transforming an existing photo.

For an 8 GB RAM phone, start with an SD 1.5 CPU/GPU model at 512×512 and conservative generation settings. Actual speed and stability depend on the phone's chipset, available memory, thermal limits, and supported GPU backend. NPU acceleration is device-specific and is not guaranteed.

## Important licensing note

Local Dream is distributed under **Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)**. This rebranded build is intended for personal, non-commercial use. Do not use or distribute it commercially unless you obtain the necessary permissions. The upstream project and attribution details are included in the APK assets and in `THIRD_PARTY_NOTICES.txt`.

Upstream project: https://github.com/xororz/local-dream
Upstream release used: https://github.com/xororz/local-dream/releases/tag/v2.8.1
