"""`opencell brain`: self-hosted speech server for the phone's voice agent.

Speaks the OpenAI audio API so the phone (or anything else) can use it as a drop-in:
  POST /v1/audio/transcriptions  (multipart: file=<wav>, model=...)  -> {"text": "..."}   (faster-whisper)
  POST /v1/audio/speech          (json: input, voice, response_format="wav") -> audio/wav  (Piper)
Pair it with Ollama for the LLM. Install with: pip install "opencell[brain]".
"""

# No `from __future__ import annotations`: FastAPI must resolve the locally imported UploadFile/Header types.
import io
import os
import secrets
import wave
from collections.abc import Callable

SttFn = Callable[[bytes], str]
TtsFn = Callable[[str, str], bytes]


def create_app(stt: SttFn, tts: TtsFn, token: str | None = None):  # noqa: ANN201 - FastAPI app
    from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
    from fastapi.responses import Response
    from pydantic import BaseModel, Field

    app = FastAPI(title="opencell brain", version="0.1.0")

    def check_auth(authorization: str | None = Header(default=None)) -> None:
        if token is None:
            return
        supplied = (authorization or "").removeprefix("Bearer ").strip()
        if not secrets.compare_digest(supplied, token):
            raise HTTPException(status_code=401, detail="invalid token")

    class SpeechRequest(BaseModel):
        input: str = Field(min_length=1, max_length=4000)
        voice: str = "default"
        model: str = "piper"
        response_format: str = "wav"

    @app.get("/health")
    def health() -> dict:
        return {"status": "ok"}

    @app.post("/v1/audio/transcriptions", dependencies=[Depends(check_auth)])
    async def transcriptions(file: UploadFile = File(...), model: str = Form("whisper")) -> dict:  # noqa: ARG001 - model is chosen at startup
        data = await file.read()
        if len(data) > 25 * 1024 * 1024:
            raise HTTPException(status_code=413, detail="audio too large")
        return {"text": stt(data).strip()}

    @app.post("/v1/audio/speech", dependencies=[Depends(check_auth)])
    def speech(req: SpeechRequest) -> Response:
        if req.response_format != "wav":
            raise HTTPException(status_code=400, detail="only response_format=wav is supported")
        return Response(content=tts(req.input, req.voice), media_type="audio/wav")

    return app


def whisper_stt(model_size: str = "base") -> SttFn:
    from faster_whisper import WhisperModel
    import numpy as np

    # Explicit CPU default keeps the server portable on machines that have a
    # CUDA-capable GPU but do not have the matching cuBLAS runtime installed.
    # Set OPENCELL_WHISPER_DEVICE=cuda when the host is provisioned for it.
    device = os.getenv("OPENCELL_WHISPER_DEVICE", "cpu")
    model = WhisperModel(model_size, device=device, compute_type="int8")

    def run(wav_bytes: bytes) -> str:
        # The phone sends WAV. Decode it here instead of handing BytesIO to
        # PyAV: recent PyAV releases removed the metadata_errors argument that
        # faster-whisper currently passes for file-like inputs. Passing a
        # 16-kHz float array also avoids an unnecessary temporary file.
        with wave.open(io.BytesIO(wav_bytes), "rb") as wav:
            if wav.getsampwidth() != 2:
                raise ValueError("speech input must be 16-bit PCM WAV")
            channels = wav.getnchannels()
            source_rate = wav.getframerate()
            samples = np.frombuffer(wav.readframes(wav.getnframes()), dtype="<i2").astype(np.float32)
        if channels > 1:
            samples = samples.reshape(-1, channels).mean(axis=1)
        samples /= 32768.0
        if source_rate != 16_000 and len(samples):
            positions = np.linspace(0, len(samples) - 1, round(len(samples) * 16_000 / source_rate))
            samples = np.interp(positions, np.arange(len(samples)), samples).astype(np.float32)
        segments, _ = model.transcribe(samples, vad_filter=True)
        return " ".join(s.text.strip() for s in segments)

    return run


def piper_tts(voice_path: str) -> TtsFn:
    from piper import PiperVoice

    voice = PiperVoice.load(voice_path)

    def run(text: str, _voice: str) -> bytes:
        buf = io.BytesIO()
        with wave.open(buf, "wb") as wav:
            voice.synthesize_wav(text, wav)
        return buf.getvalue()

    return run


def serve(host: str, port: int, whisper_model: str, piper_voice: str, token: str | None) -> None:
    import uvicorn

    try:
        app = create_app(whisper_stt(whisper_model), piper_tts(piper_voice), token)
    except ImportError as e:
        raise SystemExit(f'missing brain dependencies ({e.name}); install with: pip install "opencell[brain]"') from e
    uvicorn.run(app, host=host, port=port)
