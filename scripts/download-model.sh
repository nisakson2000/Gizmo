#!/bin/bash
set -e

MODELS_DIR="$HOME/gizmo-ai/models"
mkdir -p "$MODELS_DIR" "$MODELS_DIR/mmproj" "$MODELS_DIR/qwen3-tts"

echo "╔════════════════════════════════════════════════╗"
echo "║  Gizmo-AI — Model Download                     ║"
echo "╚════════════════════════════════════════════════╝"
echo ""
echo "LLM:  Huihui-Qwen3.5-9B-abliterated Q8_0 (~9.5GB)"
echo "TTS:  Qwen3-TTS-12Hz-1.7B-Base (~3.6GB)"
echo "TTS Tokenizer: Qwen3-TTS-Tokenizer-12Hz (~651MB)"
echo ""

# Remove old 27B model if present (free disk space)
OLD_MODEL="$MODELS_DIR/Huihui-Qwen3.5-27B-abliterated.i1-Q5_K_M.gguf"
if [ -f "$OLD_MODEL" ]; then
    echo "Removing old 27B model to free disk space..."
    rm -f "$OLD_MODEL"
    echo "Removed: $OLD_MODEL"
fi

python3 << 'PYEOF'
from huggingface_hub import hf_hub_download, snapshot_download
import os

models_dir = os.path.expanduser("~/gizmo-ai/models")
mmproj_dir = os.path.join(models_dir, "mmproj")
tts_dir = os.path.join(models_dir, "qwen3-tts")

# --- LLM ---

# Download 9B Q8_0 (static quant — imatrix not available at Q8_0)
print("Downloading 9B Q8_0...")
hf_hub_download(
    repo_id="mradermacher/Huihui-Qwen3.5-9B-abliterated-GGUF",
    filename="Huihui-Qwen3.5-9B-abliterated.Q8_0.gguf",
    local_dir=models_dir,
)
print("Main model downloaded.")

# Download mmproj (vision projector)
print("\nDownloading vision projector (mmproj Q8_0)...")
hf_hub_download(
    repo_id="mradermacher/Huihui-Qwen3.5-9B-abliterated-GGUF",
    filename="Huihui-Qwen3.5-9B-abliterated.mmproj-Q8_0.gguf",
    local_dir=mmproj_dir,
)
print("Vision projector downloaded.")

# Download chat template
print("\nDownloading chat template...")
hf_hub_download(
    repo_id="huihui-ai/Huihui-Qwen3.5-9B-abliterated",
    filename="chat_template.jinja",
    local_dir=models_dir,
)
print("Chat template downloaded.")

# --- TTS ---

# Download Qwen3-TTS model (~3.6GB)
print("\nDownloading Qwen3-TTS-12Hz-1.7B-Base (~3.6GB)...")
snapshot_download(
    repo_id="Qwen/Qwen3-TTS-12Hz-1.7B-Base",
    local_dir=os.path.join(tts_dir, "1.7B-Base"),
)
print("TTS model downloaded.")

# Download Qwen3-TTS tokenizer (~651MB)
print("\nDownloading Qwen3-TTS-Tokenizer-12Hz (~651MB)...")
snapshot_download(
    repo_id="Qwen/Qwen3-TTS-Tokenizer-12Hz",
    local_dir=os.path.join(tts_dir, "tokenizer"),
)
print("TTS tokenizer downloaded.")
PYEOF

echo ""
echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║  Download complete.                                                 ║"
echo "║  LLM:        $MODELS_DIR/Huihui-Qwen3.5-9B-abliterated.Q8_0.gguf"
echo "║  Vision:     $MODELS_DIR/mmproj/Huihui-Qwen3.5-9B-abliterated.mmproj-Q8_0.gguf"
echo "║  Template:   $MODELS_DIR/chat_template.jinja"
echo "║  TTS Model:  $MODELS_DIR/qwen3-tts/1.7B-Base/"
echo "║  TTS Token:  $MODELS_DIR/qwen3-tts/tokenizer/"
echo "╚══════════════════════════════════════════════════════════════════════╝"
