import io
import wave

from fastapi.testclient import TestClient

from opencell.brain import create_app


def _wav(samples: int = 1600) -> bytes:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        w.writeframes(b"\x00\x00" * samples)
    return buf.getvalue()


def _app(token=None):
    heard = []

    def stt(data: bytes) -> str:
        heard.append(data)
        return "  hello from whisper "

    def tts(text: str, voice: str) -> bytes:
        return _wav(len(text))

    return TestClient(create_app(stt, tts, token)), heard


def test_transcription_openai_shape():
    client, heard = _app()
    res = client.post("/v1/audio/transcriptions", files={"file": ("speech.wav", _wav(), "audio/wav")}, data={"model": "whisper"})
    assert res.status_code == 200
    assert res.json() == {"text": "hello from whisper"}
    assert heard[0][:4] == b"RIFF"


def test_speech_returns_wav():
    client, _ = _app()
    res = client.post("/v1/audio/speech", json={"input": "Hi there", "voice": "default", "response_format": "wav"})
    assert res.status_code == 200
    assert res.headers["content-type"] == "audio/wav"
    assert res.content[:4] == b"RIFF"


def test_speech_validates_input():
    client, _ = _app()
    assert client.post("/v1/audio/speech", json={"input": ""}).status_code == 422
    assert client.post("/v1/audio/speech", json={"input": "hi", "response_format": "mp3"}).status_code == 400


def test_token_required_when_configured():
    client, _ = _app(token="s3cret")
    assert client.post("/v1/audio/speech", json={"input": "hi"}).status_code == 401
    assert client.post("/v1/audio/speech", json={"input": "hi"}, headers={"Authorization": "Bearer wrong"}).status_code == 401
    assert client.post("/v1/audio/speech", json={"input": "hi"}, headers={"Authorization": "Bearer s3cret"}).status_code == 200
    assert client.get("/health").status_code == 200
