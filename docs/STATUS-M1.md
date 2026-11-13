# M1 状态交接

版本：`v0.2.0-m1`（2026-11-13）

## 已能跑
| 能力 | 状态 | 备注 |
|---|---|---|
| 五入口界面 | ✅ | Miuix；短句页已换成真实板块 |
| 录音 → VAD → 上屏 | ✅ | 能量 VAD，600ms 静音断句 |
| 两遍识别编排 | ✅ | 第一遍立刻上屏；第二遍后台替换 |
| PP-OCRv5 推理 | ✅ 代码就绪 | 需放模型文件，缺失则返回空 |
| OBF/OBZ 导入 | ✅ 解析层 | UI 已渲染文字按钮，图片按钮待补 |
| 开关扫描 | ✅ | 逐格 + 行列两段式 |
| 朗读 | ✅ | 系统 TTS |

## 未做
- sherpa-onnx JNI 未打包 → `SherpaOfflineAsrEngine` 恒不可用，第二遍退化为空操作。
- 相机缩略图，未上 CameraX、多帧融合、盲拍引导。
- `SpeechArbiter`（多声源仲裁）未实现：朗读与字幕同时出声仍可能打架。
- 语音输入（只用 AccessibilityService 填字）未开始。
- OBF 图片渲染、OBZ 声音文件（mp3 发声）未接。
- 四套读屏（TalkBack / 天坦 / 保益 / 点明）真机走查未做。

## 怎么让它真的会认字
1. 下载 PP-OCRv5 mobile 的 ONNX 版（det + rec），放进手机：
   `/data/data/io.github.wumisc.woilaibangni/files/models/ocr/`
2. 同目录放字典 `ppocr_keys_v1.txt`。
3. 打开「看世界」→ 拍照。日志看 `WOILAI_OCR`：
   ```
   adb logcat -s WOILAI_OCR WOILAI_PERF
   ```
   正常应看到 `det boxes=N` 与 `revise_ms=`。

## 性能预期（M1 目标）
- 检测：1080p 单页 ≤ 400ms（arm64，2 线程）
- 识别：单行 ≤ 60ms，一页 20 行 ≤ 1.2s
- 两遍校正：整句 3 秒音频 ≤ 600ms

## 打包注意
- 只打 `arm64-v8a`；若要给 32 位旧机，在 `app/build.gradle.kts` 的 `abiFilters` 加 `armeabi-v7a`，APK 会涨约 12MB。
