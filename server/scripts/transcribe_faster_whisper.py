#!/usr/bin/env python3
import os
import sys
from faster_whisper import WhisperModel


def main():
    if len(sys.argv) < 2:
        print("", end="")
        return 1

    audio_path = sys.argv[1]
    model_size = os.getenv("WHISPER_MODEL", "tiny")
    device = os.getenv("WHISPER_DEVICE", "cpu")
    compute_type = os.getenv("WHISPER_COMPUTE_TYPE", "int8")

    model = WhisperModel(model_size, device=device, compute_type=compute_type)
    segments, _ = model.transcribe(audio_path, vad_filter=True)

    text_parts = []
    for seg in segments:
        t = (seg.text or "").strip()
        if t:
            text_parts.append(t)

    print(" ".join(text_parts).strip())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
