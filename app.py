from __future__ import annotations
import logging, threading
from pathlib import Path
from typing import Optional
import gradio as gr
import torch
from PIL import Image, ImageOps
from diffusers import AutoPipelineForImage2Image
from transformers import CLIPVisionModelWithProjection

BASE = Path.home() / "AnimeImageStudio"
OUT = BASE / "outputs"
LOG = BASE / "logs"
OUT.mkdir(parents=True, exist_ok=True)
LOG.mkdir(parents=True, exist_ok=True)
logging.basicConfig(filename=LOG / "app.log", level=logging.INFO,
                    format="%(asctime)s %(levelname)s %(message)s", encoding="utf-8")
MODEL = "stabilityai/stable-diffusion-xl-base-1.0"
IP_REPO = "h94/IP-Adapter"
IP_WEIGHT = "ip-adapter-plus_sdxl_vit-h.safetensors"
STYLES = {
 "Cinematic Japanese Anime": "cinematic Japanese anime film key visual, detailed hand-painted background, expressive eyes, refined line art, delicate shading, atmospheric lighting, rich color grading, intricate hair and clothing",
 "High-detail 2D Anime": "premium 2D anime illustration, clean precise line art, expressive anime eyes, detailed hair, carefully rendered clothing, polished cel shading",
 "Soft Romantic Anime": "soft romantic Japanese anime illustration, gentle pastel colors, luminous eyes, delicate linework, dreamy light, elegant soft shading",
 "Fantasy Anime": "fantasy anime illustration, magical cinematic atmosphere, ornate costume, luminous effects, intricate environment, dramatic lighting",
 "Modern Anime Poster": "modern anime poster art, bold composition, crisp linework, vibrant cinematic colors, detailed character, polished illustrated background"
}
NEG = "photorealistic, live action, blurry, low quality, lowres, deformed, bad anatomy, extra fingers, missing fingers, malformed hands, duplicate, extra limbs, poorly drawn face, asymmetrical eyes, text, watermark, logo"
pipe = None
lock = threading.Lock()

def gpu_status():
    if not torch.cuda.is_available():
        return "CUDA GPU not detected. A modern NVIDIA GPU is required."
    p = torch.cuda.get_device_properties(0)
    return f"{p.name} · {p.total_memory / 1024**3:.1f} GB VRAM · CUDA {torch.version.cuda}"

def load_model(progress):
    global pipe
    with lock:
        if pipe is not None:
            return pipe
        if not torch.cuda.is_available():
            raise gr.Error("No supported NVIDIA CUDA GPU found. This app requires a modern NVIDIA GPU; 8GB VRAM minimum is recommended.")
        p = torch.cuda.get_device_properties(0)
        if p.total_memory < 8 * 1024**3:
            raise gr.Error(f"Only {p.total_memory/1024**3:.1f} GB VRAM detected. 8GB minimum is recommended; 12GB+ preferred.")
        if torch.cuda.get_device_capability(0)[0] < 7:
            raise gr.Error("This GPU architecture is too old for the configured SDXL workflow.")
        progress(0.05, desc="Downloading/loading SDXL and IP-Adapter models (several GB on first run)…")
        dtype = torch.float16
        enc = CLIPVisionModelWithProjection.from_pretrained(IP_REPO, subfolder="models/image_encoder", torch_dtype=dtype)
        p = AutoPipelineForImage2Image.from_pretrained(MODEL, image_encoder=enc, torch_dtype=dtype, use_safetensors=True)
        p.load_ip_adapter(IP_REPO, subfolder="sdxl_models", weight_name=IP_WEIGHT)
        p.enable_model_cpu_offload()
        try: p.enable_vae_slicing()
        except Exception: pass
        pipe = p
        return pipe

def resize(img: Image.Image, max_side: int) -> Image.Image:
    img = ImageOps.exif_transpose(img).convert("RGB")
    w,h = img.size
    s = min(1.0, max_side / max(w,h))
    w,h = max(512, int(w*s)), max(512, int(h*s))
    return img.resize(((w//8)*8, (h//8)*8), Image.Resampling.LANCZOS)

def create(original: Optional[Image.Image], reference: Optional[Image.Image], style: str,
           extra: str, strength: float, ref_weight: float, quality: str, seed: int,
           progress=gr.Progress()):
    if original is None:
        raise gr.Error("Please upload an original image.")
    p = load_model(progress)
    size = 1024 if quality == "High" else 768
    init = resize(original, size)
    ref = resize(reference, 768) if reference is not None else None
    p.set_ip_adapter_scale(float(ref_weight) if ref is not None else 0.0)
    prompt = "masterpiece, best quality, " + STYLES.get(style, STYLES["Cinematic Japanese Anime"])
    if extra.strip():
        prompt += ", " + extra.strip()
    progress(0.4, desc="Generating anime image…")
    try:
        gen = torch.Generator(device="cpu").manual_seed(int(seed))
        result = p(prompt=prompt, negative_prompt=NEG, image=init,
                   ip_adapter_image=ref, strength=float(strength),
                   num_inference_steps=30 if quality == "High" else 22,
                   guidance_scale=6.5, generator=gen).images[0]
    except torch.cuda.OutOfMemoryError:
        torch.cuda.empty_cache()
        raise gr.Error("GPU memory exhausted. Try Balanced quality or a GPU with more VRAM.")
    except Exception as e:
        logging.exception("Image generation failed")
        raise gr.Error(f"Generation failed: {e}")
    path = OUT / f"anime_{int(seed)}.png"
    result.save(path, "PNG")
    progress(1.0, desc="Finished")
    return result, str(path), gpu_status()

with gr.Blocks(title="Anime Image Studio", theme=gr.themes.Soft()) as ui:
    gr.Markdown("# Anime Image Studio\nUpload a photo and optionally add an anime style reference.")
    with gr.Row():
        with gr.Column():
            original = gr.Image(type="pil", label="Original image (required)")
            reference = gr.Image(type="pil", label="Style reference (optional)")
            style = gr.Dropdown(list(STYLES), value="Cinematic Japanese Anime", label="Built-in style")
            extra = gr.Textbox(label="Extra details (optional)", lines=2)
            strength = gr.Slider(0.25, 0.85, value=0.55, step=0.05, label="Transformation strength")
            ref_weight = gr.Slider(0.0, 1.0, value=0.65, step=0.05, label="Reference influence")
            quality = gr.Radio(["Balanced", "High"], value="Balanced", label="Quality")
            seed = gr.Number(value=123456, precision=0, label="Seed")
            btn = gr.Button("Create Anime Image", variant="primary")
        with gr.Column():
            result = gr.Image(type="pil", label="Anime artwork")
            saved = gr.Textbox(label="Saved PNG", interactive=False)
            status = gr.Textbox(label="GPU status", value=gpu_status(), interactive=False)
    btn.click(create, [original, reference, style, extra, strength, ref_weight, quality, seed],
              [result, saved, status])
    gr.Markdown("First launch downloads several GB of model weights. Requires NVIDIA CUDA GPU with 8GB+ VRAM (12GB+ preferred). Exact face/style preservation is not guaranteed.")
if __name__ == "__main__":
    ui.queue().launch(server_name="127.0.0.1", server_port=7860, inbrowser=True, show_error=True)
