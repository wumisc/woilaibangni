# M0 状态与交接

## 已经能跑
- 五入口 UI（Miuix 0.9.4）：对话 / 只听 / 短句 / 看世界 / 语音，全部带无障碍语义（Tab role、selected、contentDescription、heading）。
- 录音链路：`PcmRecorder`（16k 单声道）→ 能量 VAD 断句 → `AsrSession` 状态机 → 上屏；首字延迟、定稿延迟已打点到 `WOILAI_PERF`。
- 拍照链路：系统相机缩略图 → `ReadingPipeline` → `LineGrouper` 按阅读顺序重排 → 系统 TTS 朗读。
- 模型仓库：四类模型（对话 80MB / 高精度 230MB / 发声 100MB / OCR 20MB），HTTP 下载 + SHA256 + zip 校验 + `.incomplete` 防半包。
- 工程：AGP 9.5.1 / Kotlin 2.4.21 / compileSdk 37 / minSdk 26 / JDK 21；`:app` + 5 个 `core` 模块；debug APK 14MB。

## 还没做（M0 收尾项）

### 1. 流式识别（`core/asr`）
- 目前是 `DemoAsrEngine`：不推理，只验证时序。
- 接入思路：`sherpa-onnx` Android 预编译库 + `streaming-zipformer-bilingual-zh-en` int8；把 `feed()` 换成 `OnlineRecognizer.acceptWaveform()`，`finishUtterance()` 调 `decode` 取最终文本。
- 判据：安静室内首字 ≤ 600ms；不达标先查 chunk 大小与线程数，不要先换模型。

### 2. 高精度校正（`core/asr`）
- VAD 断句后，把整段 PCM 交给 SenseVoice int8，结果替换定稿；差异大时做淡入替换。

### 3. PP-OCRv5（`core/ocr`）
- 目前 `DemoTextReader` 返回固定三行。
- 接入：ONNX Runtime Android + PP-OCRv5 mobile det/rec（int8）；DB 后处理取四点框；rec 按框裁剪；charset 读 dict.txt。
- 判据：1080p 单页 ≤ 800ms；20 个真实药盒药名 ≥ 95%。

### 4. 拍照体验（盲拍引导）
- 现在是 `TakePicturePreview`（缩略图，低清）。M1 换 CameraX：全分辨率、连续 3 帧融合、语音引导"举高/左移/稳住"。

### 5. 播报仲裁 `SpeechArbiter`
- 尚未实现。读屏、TTS、语音引导同时出声会让视障用户懵，必须先做互斥队列。

### 6. 语音输入（`AccessibilityService` 只读填字）
- 未开始。只声明 `canRetrieveWindowContent`，不申请手势权限。

## 真机验收（需你在 K90 上执行）
```bash
adb install -r 我来帮你-v0.1.0-m0-debug.apk
bash tools/collect-perf.sh
```
把 `perf-records/` 里的日志发我，我按 `docs/M0-实测数据表.md` 填表并决定模型档位。

## 已知取舍
- **Miuix 的 `miuix-blur` 要求 minSdk 33**，为了让 Android 8+ 的旧机也能用，M0 先不引入它；液态玻璃在 33+ 上后续用 `RenderEffect` 条件实现，或单独出 `glass` flavor。
- 相机先用系统 Intent，避免 M0 就背 CameraX 的依赖与适配成本。
