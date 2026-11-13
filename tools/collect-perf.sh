#!/usr/bin/env bash
# 采集一次「我来帮你」的性能打点。需要已连接真机并开启 adb 调试。
set -euo pipefail

PKG="io.github.wumisc.woilaibangni"
OUT_DIR="perf-records"
mkdir -p "$OUT_DIR"
STAMP=$(date +%Y%m%d-%H%M%S)

echo "== 1. 清空日志并抓取 WOILAI_PERF =="
adb logcat -c
adb logcat -s WOILAI_PERF > "$OUT_DIR/perf-$STAMP.log" &
LOGCAT_PID=$!
trap 'kill $LOGCAT_PID 2>/dev/null || true' EXIT

echo "请在手机上操作：开始对话 → 说 5 句 → 停止；再去「看世界」拍 3 张药盒。"
read -r -p "完成后按回车继续..."

echo "== 2. 内存 =="
adb shell dumpsys meminfo "$PKG" | head -20 | tee "$OUT_DIR/mem-$STAMP.txt"

echo "== 3. 温度 =="
adb shell dumpsys thermalservice | head -30 | tee "$OUT_DIR/thermal-$STAMP.txt"

echo "== 4. 电量 =="
adb shell dumpsys battery | grep -E "level|temperature" | tee "$OUT_DIR/battery-$STAMP.txt"

echo "== 完成，记录在 $OUT_DIR =="
echo "提示：首字/定稿延迟直接从 perf 日志里取 first_partial_ms / finalize_ms。"
