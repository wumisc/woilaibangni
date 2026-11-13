# 更新日志

## v0.1.0-m0（2026-11-13）

首个可安装骨架版，验证"录音—字幕"和"拍照—朗读"两条链路的时序与工程结构。

### 新增
- 五入口界面：对话 / 只听 / 短句 / 看世界 / 语音（Miuix 0.9.4）。
- 全量无障碍语义：Tab 角色与选中态、标题层级、按钮 `contentDescription`；字号随系统缩放。
- 录音链路：`PcmRecorder`（16kHz 单声道）→ 能量 VAD 断句 → `AsrSession` 状态机 → 上屏。
- 字幕延迟打点：`first_partial_ms`、`finalize_ms`，标签 `WOILAI_PERF`。
- 拍照链路：系统相机 → `ReadingPipeline` → `LineGrouper` 阅读顺序重排 → 系统 TTS 朗读。
- 模型仓库：四类分包（对话 80MB / 高精度 230MB / 发声 100MB / OCR 20MB），下载、SHA256、zip 校验、`.incomplete` 防半包。
- 工程：AGP 9.5.1 / Kotlin 2.4.21 / compileSdk 37 / minSdk 26 / JDK 21；`:app` + 5 个 core 模块；debug APK 14MB。
- 文档：`docs/STATUS-M0.md`（交接）、`docs/M0-实测数据表.md`（真机表格）、`tools/collect-perf.sh`（采集脚本）。

### 已知限制
- 识别引擎为 `DemoAsrEngine`，不推理；sherpa-onnx 未接入。
- OCR 为 `DemoTextReader`，返回固定文本；PP-OCRv5 未接入。
- 相机为缩略图（`TakePicturePreview`），未上 CameraX。
- 未实现 `SpeechArbiter`（多声源仲裁）、语音输入、悬浮字幕。
- 因 `miuix-blur` 要求 minSdk 33，液态玻璃暂缓，M0 保 minSdk 26 以覆盖旧机。

### 决策记录
- **不打包模型进 APK**：主包 14MB，按人群分包下载，避免一次性 450MB 吓退用户。
- **不做手语识别**：手语是独立语言体系，误识别在医疗/报警场景有实际风险。
- **`AccessibilityService` 只读**：只填字、读屏幕，不申请手势权限，不自动点击。
