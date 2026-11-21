# Anime Image Studio AI backend

This service replaces the old local cartoon filter with fal.ai image-to-image style transfer.

## Deploy

1. Create a fal.ai account and an API key at https://fal.ai/dashboard/keys.
2. In Render, choose **New + → Blueprint**, connect this repository, and deploy the included `render.yaml`.
3. When Render asks for `FAL_KEY`, add the API key as a secret environment variable. Never commit the key or put it in the Android app.
4. The service URL should be `https://anime-image-studio-api.onrender.com` if that Render service name is available. Check `/health` after deployment.

## Endpoint

`POST /v1/transform` as multipart form data:
- `photo`: original JPG, PNG or WEBP (max 10 MB)
- `style`: `anime`, `anime_character`, `anime_game_style`, or `hand_drawn_animation`
- `reference`: optional style reference image

The API forwards the image to fal.ai's `fal-ai/image-apps-v2/style-transfer` queue and returns the generated image bytes. It has a basic per-IP hourly request limit. Production deployments should add authenticated user accounts and stronger abuse controls before public launch.

## Privacy and cost

Photos are transmitted to the backend and fal.ai for generation. Do not upload sensitive images unless you accept that processing. fal.ai usage is billed to the API-key owner; check current model pricing and account limits. The service does not persist uploaded files itself.
