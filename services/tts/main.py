"""Gizmo TTS Server — faster-qwen3-tts streaming voice synthesis with voice cloning."""

import asyncio
import base64
import io
import json
import logging
import os
import queue as sync_queue
import re
import tempfile
import time
from pathlib import Path
from contextlib import asynccontextmanager

import numpy as np
import scipy.signal
import soundfile as sf
import torch
import uvicorn
from fastapi import FastAPI, Request, WebSocket, WebSocketDisconnect
from fastapi.responses import JSONResponse, Response

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("gizmo-tts")

MODEL_DIR = os.getenv("TTS_MODEL_DIR", "/models/qwen3-tts/1.7B-Base")
IDLE_UNLOAD_SECONDS = int(os.getenv("TTS_IDLE_UNLOAD_SECONDS", "60"))
DEFAULT_REF_AUDIO = "/app/assets/default_voice.wav"
DEFAULT_REF_TEXT = "Hello, I am Gizmo, your local AI assistant."
VOICES_DIR = Path("/app/voices")
EMBEDDINGS_DIR = VOICES_DIR / "embeddings"

MAX_CHUNK_CHARS = 200

# Global state
model = None
model_loaded = False
last_request_time = 0.0
unload_task = None

# GPU lock — only one generation at a time
_gpu_lock = asyncio.Lock()


def load_model():
    """Load the faster-qwen3-tts model onto GPU."""
    global model, model_loaded
    if model_loaded:
        return

    logger.info("Loading faster-qwen3-tts model from %s ...", MODEL_DIR)
    start = time.time()

    from faster_qwen3_tts import FasterQwen3TTS

    device = "cuda:0" if torch.cuda.is_available() else "cpu"
    dtype = torch.bfloat16 if torch.cuda.is_available() else torch.float32

    model = FasterQwen3TTS.from_pretrained(
        MODEL_DIR,
        device=device,
        dtype=dtype,
    )

    elapsed = round(time.time() - start, 1)
    logger.info("faster-qwen3-tts loaded on %s in %ss", device, elapsed)
    model_loaded = True


def unload_model():
    """Unload the model from VRAM (full destruction — CUDA graphs cannot migrate)."""
    global model, model_loaded
    if not model_loaded:
        return

    logger.info("Unloading faster-qwen3-tts from VRAM (idle timeout)")
    del model
    model = None
    model_loaded = False
    if torch.cuda.is_available():
        torch.cuda.empty_cache()
    logger.info("faster-qwen3-tts unloaded")


async def idle_watcher():
    """Periodically check if the model should be unloaded."""
    global last_request_time
    while True:
        await asyncio.sleep(10)
        if model_loaded and IDLE_UNLOAD_SECONDS > 0:
            elapsed = time.time() - last_request_time
            if elapsed > IDLE_UNLOAD_SECONDS:
                unload_model()


@asynccontextmanager
async def lifespan(app: FastAPI):
    global unload_task
    load_model()
    unload_task = asyncio.create_task(idle_watcher())
    yield
    unload_task.cancel()
    unload_model()


app = FastAPI(title="Gizmo TTS", version="4.0.0", lifespan=lifespan)


@app.get("/health")
async def health():
    device = "cuda:0" if torch.cuda.is_available() else "cpu"
    return {
        "status": "ok" if model_loaded else "idle",
        "model": "faster-qwen3-tts (Qwen3-TTS-12Hz-1.7B-Base)",
        "device": device,
        "loaded": model_loaded,
        "streaming": True,
    }


def _chunk_text(text: str) -> list[str]:
    """Split text into chunks at sentence boundaries, max ~200 chars each."""
    sentences = re.split(r'(?<=[.!?])\s+', text.strip())
    chunks = []
    current = ""
    for sentence in sentences:
        if len(current) + len(sentence) + 1 > MAX_CHUNK_CHARS and current:
            chunks.append(current.strip())
            current = sentence
        else:
            current = f"{current} {sentence}" if current else sentence
    if current.strip():
        chunks.append(current.strip())
    return chunks if chunks else [text]


def _resolve_voice(voice_id: str | None = None, voice_reference: str | None = None):
    """Resolve voice to a voice_clone_prompt dict for generation.

    Priority: precomputed embedding > voice WAV on disk > voice_reference base64 > default voice.
    Returns (voice_clone_prompt, tmp_path_to_clean) tuple.
    """
    # Precomputed embedding
    if voice_id:
        emb_path = EMBEDDINGS_DIR / f"{voice_id}.pt"
        try:
            device = "cuda:0" if torch.cuda.is_available() else "cpu"
            spk_emb = torch.load(str(emb_path), weights_only=True).to(device)
            logger.info("Using precomputed embedding for voice %s", voice_id)
            return {"ref_spk_embedding": [spk_emb]}, None
        except FileNotFoundError:
            pass

        wav_path = VOICES_DIR / f"{voice_id}.wav"
        if wav_path.exists():
            prompt = model.model.create_voice_clone_prompt(
                ref_audio=str(wav_path), ref_text="", x_vector_only_mode=True,
            )
            logger.info("Using voice WAV for %s (x-vector only)", voice_id)
            return prompt, None

    # Base64 voice reference
    if voice_reference:
        ref_bytes = base64.b64decode(voice_reference)
        tmp = tempfile.NamedTemporaryFile(suffix=".wav", delete=False)
        tmp.write(ref_bytes)
        tmp.close()
        prompt = model.model.create_voice_clone_prompt(
            ref_audio=tmp.name, ref_text="", x_vector_only_mode=True,
        )
        return prompt, tmp.name

    # Default voice
    prompt = model.model.create_voice_clone_prompt(
        ref_audio=DEFAULT_REF_AUDIO, ref_text=DEFAULT_REF_TEXT, x_vector_only_mode=False,
    )
    return prompt, None


# ---------------------------------------------------------------------------
# Streaming WebSocket endpoint
# ---------------------------------------------------------------------------

@app.websocket("/v1/audio/stream")
async def stream_tts(ws: WebSocket):
    """Stream TTS audio chunks over WebSocket.

    Client sends JSON config: {text, language, speed, voice_id, voice_reference}
    Server responds with alternating JSON metadata + binary PCM float32 frames,
    ending with {done: true}.
    """
    global last_request_time
    await ws.accept()

    try:
        raw = await ws.receive_text()
        config = json.loads(raw)

        text = config.get("text", "")
        if not text:
            await ws.send_json({"error": "Missing text"})
            return

        language = config.get("language", "Auto")
        speed = max(0.5, min(float(config.get("speed", 1.0)), 2.0))
        voice_id = config.get("voice_id")
        voice_reference = config.get("voice_reference")

        last_request_time = time.time()
        if not model_loaded:
            load_model()

        async with _gpu_lock:
            last_request_time = time.time()
            tmp_path = None
            try:
                voice_prompt, tmp_path = _resolve_voice(voice_id, voice_reference)

                # Run streaming generator in a thread, queue chunks
                q = sync_queue.Queue()

                def _generate():
                    try:
                        for chunk, sr, timing in model.generate_voice_clone_streaming(
                            text=text,
                            language=language,
                            voice_clone_prompt=voice_prompt,
                            chunk_size=8,
                        ):
                            q.put((chunk, sr, timing))
                    except Exception as e:
                        q.put(e)
                    finally:
                        q.put(None)

                loop = asyncio.get_running_loop()
                gen_future = loop.run_in_executor(None, _generate)

                chunk_idx = 0
                while True:
                    item = await loop.run_in_executor(None, q.get)
                    if item is None:
                        break
                    if isinstance(item, Exception):
                        logger.error("Streaming generation error: %s", item)
                        await ws.send_json({"error": str(item)})
                        break

                    chunk_data, sr, timing = item

                    if abs(speed - 1.0) > 0.05:
                        new_len = int(len(chunk_data) / speed)
                        if new_len > 0:
                            chunk_data = scipy.signal.resample(chunk_data, new_len).astype(np.float32)

                    pcm_bytes = chunk_data.astype(np.float32).tobytes()

                    await ws.send_json({
                        "sample_rate": sr,
                        "samples": len(chunk_data),
                        "chunk_index": chunk_idx,
                        "is_final": timing.get("is_final", False),
                    })
                    await ws.send_bytes(pcm_bytes)
                    chunk_idx += 1

                    if timing.get("is_final", False):
                        break

                await gen_future
            finally:
                if tmp_path:
                    os.unlink(tmp_path)

            await ws.send_json({"done": True})

    except WebSocketDisconnect:
        logger.info("Streaming client disconnected")
    except Exception as e:
        logger.error("Stream endpoint error: %s", e, exc_info=True)
        try:
            await ws.send_json({"error": str(e)})
        except Exception:
            pass


# ---------------------------------------------------------------------------
# Precomputed embedding extraction
# ---------------------------------------------------------------------------

@app.post("/v1/audio/embedding")
async def extract_embedding(request: Request):
    """Extract and save a speaker embedding for a voice_id.

    Body JSON: {voice_id: str}
    Reads /app/voices/{voice_id}.wav, extracts x-vector embedding,
    saves to /app/voices/embeddings/{voice_id}.pt (~4KB).
    """
    global last_request_time
    last_request_time = time.time()

    try:
        body = await request.json()
    except Exception:
        return JSONResponse(status_code=400, content={"error": "Invalid JSON"})

    voice_id = body.get("voice_id", "")
    if not voice_id or not re.match(r'^[a-f0-9]{8}$', voice_id):
        return JSONResponse(status_code=400, content={"error": "Invalid voice_id"})

    wav_path = VOICES_DIR / f"{voice_id}.wav"
    if not wav_path.exists():
        return JSONResponse(status_code=404, content={"error": "Voice WAV not found"})

    if not model_loaded:
        load_model()

    try:
        prompt_items = model.model.create_voice_clone_prompt(
            ref_audio=str(wav_path), ref_text="", x_vector_only_mode=True,
        )
        spk_emb = prompt_items[0].ref_spk_embedding

        EMBEDDINGS_DIR.mkdir(parents=True, exist_ok=True)
        emb_path = EMBEDDINGS_DIR / f"{voice_id}.pt"
        torch.save(spk_emb.detach().cpu(), str(emb_path))

        size = emb_path.stat().st_size
        logger.info("Saved speaker embedding for %s (%d bytes)", voice_id, size)
        return {"status": "ok", "voice_id": voice_id, "embedding_size": size}

    except Exception as e:
        logger.error("Embedding extraction failed for %s: %s", voice_id, e, exc_info=True)
        return JSONResponse(status_code=500, content={"error": str(e)})


# ---------------------------------------------------------------------------
# Batch endpoint (unchanged API, updated internals for faster-qwen3-tts)
# ---------------------------------------------------------------------------

@app.post("/v1/audio/speech")
async def synthesize(request: Request):
    """OpenAI-compatible TTS endpoint (batch mode).

    Body JSON:
        model: str (ignored)
        input: str — text to synthesize
        voice: str — "default" or voice name
        response_format: str — "wav" (default)
        speed: float — speech speed multiplier (0.5-2.0)
        voice_reference: str — base64-encoded WAV for voice cloning (optional)
        voice_reference_text: str — transcript of the reference audio (optional)
        language: str — language hint (default: "Auto")
    """
    global last_request_time
    last_request_time = time.time()

    try:
        body = await request.json()
    except Exception:
        return JSONResponse(status_code=400, content={"error": "Invalid JSON"})

    text = body.get("input", "")
    if not text:
        return JSONResponse(status_code=400, content={"error": "Missing 'input' field"})

    voice_ref = body.get("voice_reference")
    voice_ref_text = body.get("voice_reference_text", "")
    language = body.get("language", "Auto")
    speed = max(0.5, min(float(body.get("speed", 1.0)), 2.0))

    if not model_loaded:
        load_model()

    async with _gpu_lock:
        last_request_time = time.time()
        try:
            chunks = _chunk_text(text) if len(text) > MAX_CHUNK_CHARS else [text]
            logger.info("Generating TTS (batch): %d chars, %d chunks, speed=%.1f, lang=%s",
                         len(text), len(chunks), speed, language)

            voice_prompt, tmp_path = _resolve_voice(voice_reference=voice_ref)
            try:
                if voice_ref:
                    wavs, sr = model.generate_voice_clone(
                        text=text, language=language, voice_clone_prompt=voice_prompt,
                    )
                    final_wav = wavs[0]
                else:
                    all_wavs = []
                    sr = 24000
                    for chunk in chunks:
                        chunk_wavs, sr = model.generate_voice_clone(
                            text=chunk, language=language, voice_clone_prompt=voice_prompt,
                        )
                        all_wavs.append(chunk_wavs[0])
                    final_wav = np.concatenate(all_wavs) if len(all_wavs) > 1 else all_wavs[0]
            finally:
                if tmp_path:
                    os.unlink(tmp_path)

        except Exception as e:
            logger.error("TTS generation error: %s", e, exc_info=True)
            return JSONResponse(status_code=500, content={"error": str(e)})

    if abs(speed - 1.0) > 0.05:
        original_len = len(final_wav)
        new_len = int(original_len / speed)
        final_wav = scipy.signal.resample(final_wav, new_len).astype(np.float32)
        logger.info("Applied speed %.1fx: %d -> %d samples", speed, original_len, new_len)

    buf = io.BytesIO()
    sf.write(buf, final_wav, sr, format="WAV")
    buf.seek(0)

    return Response(content=buf.read(), media_type="audio/wav")


@app.post("/v1/audio/unload")
async def api_unload():
    """Unload model from VRAM."""
    unload_model()
    return {"status": "unloaded"}


@app.post("/v1/audio/load")
async def api_load():
    """Reload model into VRAM."""
    load_model()
    return {"status": "loaded"}


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8400, log_level="info")
