import asyncio
import base64
import os
import time
from collections import defaultdict, deque
from io import BytesIO
from urllib.parse import urlparse

import httpx
from fastapi import FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
from PIL import Image

MODEL = "fal-ai/image-apps-v2/style-transfer"
FAL_KEY = os.getenv("FAL_KEY", "")
MAX_BYTES = 10 * 1024 * 1024
ALLOWED_STYLES = {"anime": "anime", "anime_character": "anime_character", "anime_game_style": "anime_game_style", "hand_drawn_animation": "hand_drawn_animation"}
app = FastAPI(title="Anime Image Studio API", version="2.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["GET", "POST"], allow_headers=["*"])
requests_by_ip = defaultdict(deque)

def rate_limit(ip: str):
    now = time.time()
    q = requests_by_ip[ip]
    while q and now - q[0] > 3600:
        q.popleft()
    if len(q) >= 12:
        raise HTTPException(429, "Hourly image limit reached. Please try again later.")
    q.append(now)

async def read_image(file: UploadFile) -> tuple[bytes, str]:
    data = await file.read()
    if not data or len(data) > MAX_BYTES:
        raise HTTPException(413, "Each image must be smaller than 10 MB.")
    try:
        image = Image.open(BytesIO(data))
        image.verify()
    except Exception:
        raise HTTPException(400, "Please upload a valid JPG, PNG or WEBP image.")
    mime = file.content_type or "image/jpeg"
    if mime not in ("image/jpeg", "image/png", "image/webp"):
        raise HTTPException(415, "Only JPG, PNG and WEBP images are supported.")
    return data, mime

def data_uri(data: bytes, mime: str) -> str:
    return f"data:{mime};base64," + base64.b64encode(data).decode("ascii")

@app.get("/health")
async def health():
    return {"ok": True, "engine": "fal.ai image-to-image", "configured": bool(FAL_KEY)}

@app.post("/v1/transform")
async def transform(request: Request, photo: UploadFile = File(...), style: str = Form("anime"), reference: UploadFile | None = File(None)):
    if not FAL_KEY:
        raise HTTPException(503, "AI service is not configured yet.")
    rate_limit(request.client.host if request.client else "unknown")
    if style not in ALLOWED_STYLES:
        raise HTTPException(400, "Unsupported anime style.")
    photo_data, photo_mime = await read_image(photo)
    payload = {"image_url": data_uri(photo_data, photo_mime), "target_style": ALLOWED_STYLES[style], "aspect_ratio": {"ratio": "1:1"}}
    if reference is not None:
        ref_data, ref_mime = await read_image(reference)
        payload["style_reference_image_url"] = data_uri(ref_data, ref_mime)
    headers = {"Authorization": f"Key {FAL_KEY}", "Content-Type": "application/json"}
    timeout = httpx.Timeout(240.0, connect=30.0)
    async with httpx.AsyncClient(timeout=timeout, follow_redirects=True) as client:
        submit = await client.post(f"https://queue.fal.run/{MODEL}", headers=headers, json=payload)
        if submit.status_code >= 400:
            raise HTTPException(502, "AI provider rejected the request. Check the server API key and account.")
        request_id = submit.json().get("request_id")
        if not request_id:
            raise HTTPException(502, "AI provider did not return a job ID.")
        result = None
        for _ in range(120):
            await asyncio.sleep(2)
            status_response = await client.get(f"https://queue.fal.run/{MODEL}/requests/{request_id}/status", headers=headers)
            if status_response.status_code >= 400:
                raise HTTPException(502, "Could not check AI generation status.")
            state = status_response.json().get("status")
            if state == "COMPLETED":
                result_response = await client.get(f"https://queue.fal.run/{MODEL}/requests/{request_id}", headers=headers)
                if result_response.status_code >= 400:
                    raise HTTPException(502, "AI generation completed but result retrieval failed.")
                result = result_response.json()
                break
            if state in ("FAILED", "CANCELLED"):
                raise HTTPException(502, "AI image generation failed. Please try another photo.")
        if not result:
            raise HTTPException(504, "AI generation took too long. Please try again.")
        images = result.get("images") or []
        output_url = images[0].get("url") if images else None
        parsed = urlparse(output_url or "")
        if parsed.scheme != "https" or not parsed.hostname:
            raise HTTPException(502, "AI provider returned an invalid image URL.")
        image_response = await client.get(output_url)
        if image_response.status_code != 200 or not image_response.content:
            raise HTTPException(502, "Could not download the generated artwork.")
        content_type = image_response.headers.get("content-type", "image/png").split(";")[0]
        if not content_type.startswith("image/"):
            raise HTTPException(502, "AI provider returned a non-image result.")
        return Response(content=image_response.content, media_type=content_type, headers={"Cache-Control": "no-store", "X-Image-Engine": "fal-ai-image-to-image"})
