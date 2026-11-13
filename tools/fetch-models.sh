#!/usr/bin/env bash
# 准备 full flavor 的内置模型。仓库里不放模型（单文件 174MB 超 GitHub 限制），
# 需要时跑一遍这个脚本，再 :app:assembleFullDebug。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$ROOT/app/src/full/assets/models"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$ASSETS/asr-stream" "$ASSETS/ocr"

echo "[1/3] 下载流式 zipformer（标准版 int8）"
curl -L --fail --retry 2 -o "$TMP/zf.tar.bz2" \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
tar -xjf "$TMP/zf.tar.bz2" -C "$TMP"
M="$TMP/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20"
cp "$M/encoder-epoch-99-avg-1.int8.onnx" "$ASSETS/asr-stream/encoder.onnx"
cp "$M/decoder-epoch-99-avg-1.int8.onnx" "$ASSETS/asr-stream/decoder.onnx"
cp "$M/joiner-epoch-99-avg-1.int8.onnx"  "$ASSETS/asr-stream/joiner.onnx"
cp "$M/tokens.txt"                       "$ASSETS/asr-stream/tokens.txt"

echo "[2/3] 下载 PP-OCRv5 mobile ONNX"
curl -L --fail --retry 2 -o "$ASSETS/ocr/det.onnx" \
  https://huggingface.co/ogkalu/ppocr-v5-onnx/resolve/main/ch_PP-OCRv5_mobile_det.onnx
curl -L --fail --retry 2 -o "$ASSETS/ocr/rec.onnx" \
  https://huggingface.co/ogkalu/ppocr-v5-onnx/resolve/main/ch_PP-OCRv5_rec_mobile_infer.onnx
curl -L --fail --retry 2 -o "$ASSETS/ocr/ppocr_keys_v1.txt" \
  https://huggingface.co/ogkalu/ppocr-v5-onnx/resolve/main/ppocrv5_dict.txt

echo "[3/3] 完成，体积："
du -sh "$ASSETS" "$ASSETS"/*
