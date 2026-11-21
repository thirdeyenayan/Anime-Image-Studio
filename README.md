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


## Mobile-friendly web app (PWA)

The repository includes a responsive browser app in `web/`. Start the FastAPI server on a supported NVIDIA CUDA machine:

```bash
python -m pip install -r requirements.txt
python web_server.py
```

Open `http://127.0.0.1:7861` on the computer. To test from an Android phone on the same Wi-Fi, open `http://<computer-LAN-IP>:7861`. For installable PWA mode on Android, deploy behind HTTPS (service workers require a secure context outside localhost).

The browser interface sends images to this server for processing. The AI model runs on the server GPU; the PWA itself is not an on-device Android AI model. No paid inference API is used. The first conversion downloads the SDXL/IP-Adapter model weights.

This mobile PWA is an early implementation. Authentication, subscription billing, user quotas, hosted production infrastructure, and native Android packaging are not implemented yet.


## Windows desktop app
The Windows installer launches the same responsive web interface locally in your default browser. The desktop build includes the web UI and local FastAPI server. The AI model still requires a supported NVIDIA CUDA GPU (8 GB VRAM practical minimum, 12 GB+ preferred); the installer does not include model weights, which download on first conversion.
