#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = [
#   "huggingface_hub>=0.24",
# ]
# ///
"""
Download Llama-3.2-1B-Instruct-Q8_0.gguf into the llm module resources.

Q8_0 is chosen over Q4_K_M because skainet-transformers 0.21.1's
MemSegWeightConverter dequantizes Q4_K to FP32 at load time (see
SKaiNET-transformers/llm-inference/llama/.../MemSegWeightConverter.kt:102),
which kills SIMD quantized matmul. Q8_0 stays packed and runs through
JvmQuantizedVectorKernels.matmulQ8_0Vec — far faster on CPU.

Runs as `uv run scripts/download-model.py` from the repo root. The file is
~1.3 GB and lands at:

    llm/src/jvmMain/resources/models/Llama-3.2-1B-Instruct-Q8_0.gguf

This is what `EmbeddedModelLoader` extracts at runtime, and what `shadowJar`
packages into the self-contained CLI JAR. Re-runs are no-ops if the file
already exists with non-zero size.
"""
from __future__ import annotations

import pathlib
import shutil
import sys

REPO_ID = "bartowski/Llama-3.2-1B-Instruct-GGUF"
FILENAME = "Llama-3.2-1B-Instruct-Q8_0.gguf"

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
DEST_DIR = REPO_ROOT / "llm" / "src" / "jvmMain" / "resources" / "models"
DEST = DEST_DIR / FILENAME


def main() -> int:
    if DEST.exists() and DEST.stat().st_size > 0:
        print(f"[download-model] already present: {DEST} ({DEST.stat().st_size:,} bytes) — skipping")
        return 0

    DEST_DIR.mkdir(parents=True, exist_ok=True)

    from huggingface_hub import hf_hub_download

    print(f"[download-model] fetching {REPO_ID}/{FILENAME} from Hugging Face …")
    cached = hf_hub_download(repo_id=REPO_ID, filename=FILENAME)
    shutil.copy(cached, DEST)
    print(f"[download-model] copied to {DEST} ({DEST.stat().st_size:,} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
