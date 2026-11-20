# Anime Image Studio

Local AI image-to-anime desktop app using Stable Diffusion XL (SDXL) + IP-Adapter.

## Features
- Upload an original image.
- Optionally upload a style-reference image.
- If no reference is provided, choose from built-in anime styles.
- Adjust transformation strength and reference influence.
- Save generated artwork as PNG.

## Hardware
A modern NVIDIA CUDA GPU is required. 8 GB VRAM is a practical minimum; 12 GB+ is recommended. The user's older GeForce 820M with 2 GB VRAM is unsupported. CPU-only generation is disabled because it is impractically slow for this model.

## Windows build
GitHub Actions builds a Windows folder package and an Inno Setup installer. Open the Actions tab and run the **Build Windows Installer** workflow, or push to main. Download the workflow artifact after a successful run.

Model weights are downloaded on first launch and are not bundled into the installer.

## Important limitations
Reference image guidance is approximate; exact identity, pose, and style replication are not guaranteed. This build has not yet been tested on the user's GPU hardware.

## License
Application source: MIT. Model weights and dependencies retain their upstream licenses. See THIRD_PARTY_NOTICES.md.
