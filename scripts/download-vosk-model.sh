#!/usr/bin/env bash
# Download and install vosk-model-small-en-us-0.15 into a Minecraft config folder.
# Usage:
#   ./scripts/download-vosk-model.sh [minecraft_instance_dir]
# Example:
#   ./scripts/download-vosk-model.sh ~/Library/Application\ Support/minecraft
#   ./scripts/download-vosk-model.sh ./run
set -euo pipefail

INSTANCE_DIR="${1:-.}"
MODEL_NAME="vosk-model-small-en-us-0.15"
URL="https://alphacephei.com/vosk/models/${MODEL_NAME}.zip"
TARGET_ROOT="${INSTANCE_DIR}/config/universe_verity_voice/models"
TARGET="${TARGET_ROOT}/${MODEL_NAME}"

mkdir -p "${TARGET_ROOT}"
TMP="$(mktemp -d)"
cleanup() { rm -rf "${TMP}"; }
trap cleanup EXIT

echo "Downloading ${URL} ..."
curl -L --fail --progress-bar -o "${TMP}/model.zip" "${URL}"

echo "Extracting to ${TARGET_ROOT} ..."
unzip -q -o "${TMP}/model.zip" -d "${TARGET_ROOT}"

if [[ ! -d "${TARGET}/am" && ! -d "${TARGET}/conf" && ! -d "${TARGET}/graph" ]]; then
  echo "ERROR: extracted folder missing am/conf/graph under ${TARGET}" >&2
  echo "Check that the zip contains a top-level ${MODEL_NAME}/ directory." >&2
  exit 1
fi

echo "Installed Vosk model at:"
echo "  ${TARGET}"
echo "In-game: /verityvoice model"
echo "Then press V (push-to-talk) — MODEL_MISSING should clear without restart."
