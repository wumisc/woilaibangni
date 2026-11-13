# 我来帮你（woilaibangni）

离线无障碍沟通与「读世界」应用。给听不见的、说不出的、看不见的人。

> 目标：不联网、不登录、不订阅，也能完成一段对话、读懂一张纸。

## 当前状态

**M0 骨架**：工程可编译，五个入口的 UI 壳、模型仓库、录音器、TTS、OCR 接口已就位；流式识别与 PP-OCRv5 推理待接入（真机联调）。

## 结构

```
app/                 Compose + Miuix 0.9.4，五入口 UI
core/model/          模型分包目录、下载、SHA256 校验、解压
core/audio/          16kHz 单声道 PCM 采集（AudioRecord）
core/asr/            流式识别接口 + 播报事件模型
core/tts/            系统 TTS 封装（可挂第三方离线引擎）
core/ocr/            行/段落重排、PP-OCRv5 接入位
```

## 技术基线

- Kotlin 2.4.21 / Compose 1.12.2 / Miuix 0.9.4
- AGP 9.5.1 / compileSdk 37 / minSdk 26 / JDK 21
- 协议：AGPL-3.0

## 构建

```bash
./gradlew :app:assembleDebug
```

## 下一步（M0 收尾）

1. 接入 sherpa-onnx：流式 zipformer → 中间字幕；SenseVoice → 句末定稿。
2. 接入 PP-OCRv5：拍照 → 文本框 → 按阅读顺序朗读。
3. 在 K90 至尊实测：首字延迟、单页 OCR 耗时、温度、耗电、内存。
