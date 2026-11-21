from __future__ import annotations

import io
import logging
from pathlib import Path

import gradio as gr
import torch
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles
from PIL import Image, ImageOps, UnidentifiedImageError

import app as anime_engine

ROOT = Path(__file__).resolve().parent
WEB = ROOT / "web"
MAX_UPLOAD_BYTES = 12 * 1024 * 1024
ALLOWED_TYPES = {"image/jpeg", "image/png", "image/webp"}

logging.basicConfig(level=logging.INFO)
api = FastAPI(title="Anime Image Studio API", version="0.1.0")

@api.get("/api/health")
def health():
    return {
        "status": "ready",
        "engine": "SDXL + IP-Adapter",
        "cuda_available": torch.cuda.is_available(),
        "gpu": torch.cuda.get_device_name(0) if torch.cuda.is_available() else None,
    }

@api.post("/api/convert")
async def convert(
    image: UploadFile = File(...),
    style: str = Form("Cinematic Japanese Anime"),
    strength: float = Form(0.55),
    quality: str = Form("Balanced"),
    details: str = Form(""),
    seed: int = Form(123456),
):
    if image.content_type not in ALLOWED_TYPES:
        raise HTTPException(415, "Please upload a JPG, PNG, or WebP image.")
    if not 0.25 <= strength <= 0.85:
        raise HTTPException(422, "Strength must be between 0.25 and 0.85.")
    if quality not in {"Balanced", "High"}:
        raise HTTPException(422, "Unknown quality setting.")
    raw = await image.read(MAX_UPLOAD_BYTES + 1)
    if len(raw) > MAX_UPLOAD_BYTES:
        raise HTTPException(413, "Image is too large. Maximum upload size is 12 MB.")
    try:
        original = Image.open(io.BytesIO(raw))
        original = ImageOps.exif_transpose(original).convert("RGB")
    except (UnidentifiedImageError, OSError):
        raise HTTPException(400, "The uploaded file is not a valid image.")

    if style not in anime_engine.STYLES:
        raise HTTPException(422, "Unknown anime style.")
    prompt = "masterpiece, best quality, " + anime_engine.STYLES[style]
    if details.strip():
        prompt += ", " + details.strip()[:400]

    try:
        pipe = anime_engine.load_model(lambda *args, **kwargs: None)
        max_side = 1024 if quality == "High" else 768
        init = anime_engine.resize(original, max_side)
        pipe.set_ip_adapter_scale(0.0)
        generator = torch.Generator(device="cpu").manual_seed(int(seed))
        result = pipe(
            prompt=prompt,
            negative_prompt=anime_engine.NEG,
            image=init,
            strength=float(strength),
            num_inference_steps=30 if quality == "High" else 22,
            guidance_scale=6.5,
            generator=generator,
        ).images[0]
        output = io.BytesIO()
        result.save(output, format="PNG")
        output.seek(0)
        return StreamingResponse(
            output,
            media_type="image/png",
            headers={"Content-Disposition": 'attachment; filename="anime-artwork.png"'},
        )
    except torch.cuda.OutOfMemoryError:
        torch.cuda.empty_cache()
        raise HTTPException(503, "GPU memory is full. Try Balanced quality or a GPU with more VRAM.")
    except gr.Error as exc:
        raise HTTPException(503, str(exc))
    except Exception:
        logging.exception("Anime conversion failed")
        raise HTTPException(500, "Conversion failed. Check the server log for details.")

@api.get("/")
def home():
    return FileResponse(WEB / "index.html")

api.mount("/", StaticFiles(directory=WEB, html=True), name="web")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(api, host="0.0.0.0", port=7861)
