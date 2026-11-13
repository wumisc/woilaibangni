# 更新日志

## v0.2.0-m1（2026-11-13）

三条线同时推进：真实 OCR、两遍识别、OBF/OBZ 短句板。

### 新增
- **`core:ocr` 接入真实 PP-OCRv5**：ONNX Runtime + DB 检测 + CTC 识别。
  - 预处理：最长边 960、32 对齐、ImageNet 均值方差；
  - 后处理：二值化 + 连通域外接矩形 + unclip 1.6；
  - 识别：高 48、宽上限 320、[-1,1] 归一化、CTC collapse；
  - 置信度 < 0.5 的行直接丢弃——宁可漏读，不念错药名。
  - 模型约定：`filesDir/models/ocr/{det.onnx,rec.onnx,ppocr_keys_v1.txt}`；模型缺失时返回空列表，不崩。
- **`core:asr` 两遍识别**：`TwoPassAsrEngine` = 流式第一遍 + 非流式第二遍。
  - 第一遍定稿立刻上屏，第二遍在后台把整句 PCM 重跑，成功且不同才替换（`revised`）；
  - 新增 `WOILAI_PERF` 打点 `revise_ms`；
  - `AsrSession` 按块缓存单句 PCM（上限 60 秒），不整段复制。
- **`core:aac` 新模块**：Open Board Format 支持。
  - `ObfParser` 解析单板/多板 JSON；`BoardImporter` 支持 `.obf` / `.obz`（按魔数识别 zip，防 zip slip）；
  - `SwitchScanner` 开关扫描状态机（逐格 / 行列两段式）；
  - `BoardDefaults` 内置 6 场景中文板（疼痛/就医/出行/购物/求助/心情）。
- **短句页改为真板渲染**：组句栏、回首页、清空、开关扫描开关与「选择」键，格子带无障碍描述。

### 构建
- 引入 `com.microsoft.onnxruntime:onnxruntime-android:1.20.0`；
- 只打 `arm64-v8a`，APK 从 81MB 降到 28MB（debug）。

### 已知限制
- sherpa-onnx 的 JNI 未打包（AAR 不在 Maven），`SherpaOfflineAsrEngine` 当前恒为不可用 → 第二遍退化，不影响第一遍；
- 相机仍是缩略图（`TakePicturePreview`），未上 CameraX / 多帧融合；
- OBF 的图片按钮只解析路径，UI 暂未渲染图片；
- 未做 `SpeechArbiter` 播报仲裁。

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
