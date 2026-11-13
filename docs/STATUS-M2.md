# M2 状态交接

版本：`v0.3.0-m2`（2026-11-13）

## 这一版真正接上了 sherpa-onnx

| 组件 | 实现 | 说明 |
|---|---|---|
| 第一遍（流式） | `SherpaStreamingAsrEngine` | streaming zipformer transducer，双引擎之一 |
| 第二遍（定稿） | `SherpaOfflineAsrEngine` | SenseVoice（language=auto） |
| 编排 | `TwoPassAsrEngine` | 先上屏、后替换，打点 `revise_ms` |
| 原生库 | `core/asr/src/main/jniLibs/arm64-v8a` | sherpa-onnx v2.0.1 官方预编译包 |
| OCR | `PpOcrEngine` | ONNX Runtime 1.28.0 + PP-OCRv5 |

一次只装对话包也能用：`prepare()` 失败的那一路会静默退化为空操作，不影响另一路。

## 模型怎么放

```
files/models/
├── asr-stream/     ← 对话包（流式）
│   ├── encoder.onnx
│   ├── decoder.onnx
│   ├── joiner.onnx
│   └── tokens.txt
├── asr-accurate/   ← 高精度包（可缺）
│   ├── model.onnx      (SenseVoice int8)
│   └── tokens.txt
└── ocr/            ← 朗读包
    ├── det.onnx
    ├── rec.onnx
    └── ppocr_keys_v1.txt
```

推荐的流式模型：sherpa-onnx 的 `streaming-zipformer-bilingual-zh-en-2023-02-20`
（中英混说不需要切模型）。下载后把 `encoder-epoch-*.onnx` 等改名成上面三个固定名字即可。

推送命令（手机已连电脑）：
```bash
adb push encoder.onnx decoder.onnx joiner.onnx tokens.txt /sdcard/
adb shell run-as io.github.wumisc.woilaibangni mkdir -p files/models/asr-stream
adb shell "run-as io.github.wumisc.woilaibangni cp /sdcard/encoder.onnx files/models/asr-stream/"
# 依次把 4 个文件 cp 进 asr-stream，然后 force-stop 重开 App
```

## 已验证 / 未验证

- ✅ 编译通过，APK 41MB（arm64，containing 两个引擎的原生库）
- ⚠️ **未在真机跑过 ASR 推理**：云主机没有手机，只能说代码路径与官方 demo 一致
- ⚠️ ORT 版本对齐到 1.28.0（sherpa 包内是 1.28.2），patch 级差异，`pickFirst` 只保留一份；
  若真机上 `libsherpa-onnx-jni.so` 报符号缺失，就把 `onnxruntime-android` 换成 1.27/1.29 再试

## 怎么看它到底转没转
```
adb logcat -s WOILAI_ASR WOILAI_PERF WOILAI_OCR
```
正常顺序：
```
I WOILAI_ASR: 流式识别已加载 threads=2
I WOILAI_ASR: 高精度识别已加载 model=model.onnx
I WOILAI_PERF: first_partial_ms=xxx
I WOILAI_PERF: finalize_ms=xxx
I WOILAI_PERF: revise_ms=xxx
```
没有 `流式识别已加载` = 模型路径/文件名不对，App 会停在「未就绪」。

## 下一步
1. 真机跑一遍，把 `revise_ms` 和 `first_partial_ms` 填进 `docs/M0-实测数据表.md`。
2. CameraX + 多帧融合 + 盲拍引导。
3. `SpeechArbiter`：字幕和朗读同时出声会打架。
