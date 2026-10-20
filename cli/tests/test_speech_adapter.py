"""Optional brain-engine adapter tests; no models or network required."""
import io
import sys
import types
import wave

import pytest

np = pytest.importorskip("numpy", reason="install the brain extra for engine adapter tests")


def audio(rate=22050, channels=2, width=2):
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wav:
        wav.setnchannels(channels)
        wav.setsampwidth(width)
        wav.setframerate(rate)
        wav.writeframes(b"\x00" * rate * channels * width)
    return buf.getvalue()


def engine(monkeypatch):
    calls = {}

    class Model:
        def __init__(self, size, **kwargs):
            calls.update(kwargs)

        def transcribe(self, samples, **kwargs):
            calls["samples"] = samples
            return [types.SimpleNamespace(text=" hello ")], None

    monkeypatch.setitem(sys.modules, "faster_whisper", types.SimpleNamespace(WhisperModel=Model))
    monkeypatch.delenv("OPENCELL_WHISPER_DEVICE", raising=False)
    from opencell.brain import whisper_stt
    return whisper_stt("tiny"), calls


def test_pcm_array_resamples_and_uses_cpu(monkeypatch):
    transcribe, calls = engine(monkeypatch)
    assert transcribe(audio()) == "hello"
    assert calls["device"] == "cpu"
    assert calls["samples"].shape == (16000,)
    assert calls["samples"].dtype == np.float32


def test_phone_pcm_needs_no_resampling(monkeypatch):
    transcribe, calls = engine(monkeypatch)
    transcribe(audio(rate=16000, channels=1))
    assert len(calls["samples"]) == 16000


def test_unsupported_pcm_rejected(monkeypatch):
    transcribe, calls = engine(monkeypatch)
    with pytest.raises(ValueError, match="16-bit PCM"):
        transcribe(audio(width=1))
    assert "samples" not in calls
