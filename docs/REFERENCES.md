# 可借鉴的开源前辈项目（调研记录 2026-11）

> 结论先说：**没有"一个项目全做完"的先例**，但有 5 个项目各自把我们计划里的一块做透了。
> 我们不做重复造轮子，直接借结构、借格式、借交互；代码层面注意许可证兼容（本项目 AGPL-3.0，可吸收 GPL-3.0 / MIT / Apache-2.0）。

## 一、对话字幕类

### 1. SpeechKiosk — guinotr/SpeechKiosk（Kotlin, MIT, ★0）
把旧平板变成"字幕机"：Kiosk（Device Owner）锁定、黑底白字大字号、**本地 sherpa-onnx + Zipformer 离线识别**、静音前置过滤省算力、可选 OpenAI 实时接口。
**我们借鉴**：
- 「只听」模式直接对标它的 kiosk 形态：教室/会议室放一台旧平板当字幕屏，比手机举着强。
- 静音门控（VAD 前过滤）省电思路，和我们 `AsrSession` 的能量 VAD 一致，可对齐参数。
- 支持 Android 9，证明我们的 minSdk 26 路线可行。

### 2. UHO Global — obstino-org/UHO_Global（C++, MIT, Android + Windows）
听障字幕 App：**用 AccessibilityService / 音频捕获拿到"其他 App 的声音"**（YouTube、网课），模型本地跑，23 种语言（含中英）。
**我们借鉴**：
- 「看视频时给外部 App 加字幕」是刚需，需要用 `AudioPlaybackCapture`（Android 10+，需 MediaProjection 授权）。这是 M2 的重要增量。
- 隐私话术："模型可下载、声音不上传"——直接写进我们的 README。

### 3. google/live-transcribe-speech-engine（Java, Apache-2.0, ★1504）
Google 实时转写的 Android 客户端（引擎本身在云端，但 UI/交互开源）。
**我们借鉴**：大字幕滚动策略、断句留白、键盘回复面板的布局；以及"文本不清屏"的可读性设计。

### 4. google/talkback（Java, Apache-2.0, ★524）+ trypsynth/backtalk
**我们借鉴**：焦点顺序、自定义无障碍操作、键盘导航的实现约定；用来对齐"四套读屏都能读"的目标。

## 二、AAC / 短句板类

### 5. LibreAAC — libretastic/libreaac-android（Kotlin, GPL-3.0, Android 8+）
离线 AAC 板：**支持 OBF/OBZ 开放板格式（OpenAAC）**、内置 9 套现成板、**开关扫描（switch scanning）**、编辑入口默认隐藏、无账号无广告。
**我们借鉴（优先级最高）**：
- **别再自己发明词库格式**：直接支持 OBF/OBZ 导入，能立刻获得大量现成中文/符号板（注意各板自带 CC 许可与署名）。
- **开关扫描**：给手部无力、只能按一个键的用户用，比"点按钮"更关键，我们的「短句板」必须做。
- "编辑态与使用态分离"：家属配置完就藏起来，避免使用者误触。

### 6. Cboard — cboard-org/cboard（JavaScript, GPL-3.0, ★767）
符号沟通板系统，多语言、符号库、可编辑。
**我们借鉴**：ARASAAC 等符号集的许可与署名方式；板的层级（home → 分类 → 词）结构。

### 7. Brooklyn-git/Unmute-AAC-App（Kotlin, GPL-3.0）
极简离线 AAC，可自定义。
**我们借鉴**：最小可用板的交互（少层级、少点击）。

## 三、视障 / OCR 类

### 8. RapidOCR / RapidOcrOnnx — RapidAI（Apache-2.0, ★8522 / ★378）
PP-OCR 的 ONNX 推理与前后处理参考实现（DB 后处理、方向分类、CTC 解码）。
**我们借鉴（优先级最高）**：`core/ocr` 的 det/rec 前后处理照它的实现来，别从零啃 PaddleOCR 文档；模型可直接用 PP-OCRv5 mobile 的 onnx 版本。

### 9. VisionAssist（学术开源，UPC）
手机视觉辅助：找物、读标签/文档、场景描述三合一。
**我们借鉴**：三功能的入口组织方式；"先找物再读字"的任务流。

### 10. Tesseract4Android（Apache-2.0）
老牌离线 OCR。**结论：不用**——中文竖排/手写不如 PP-OCRv5，且体积更大。

## 四、语音引擎 / 系统集成类

### 11. k2-fsa/sherpa-onnx（C++, Apache-2.0, ★15916）
**我们借鉴**：`android/SherpaOnnx2Pass` 就是"流式 + 非流式两遍识别"的官方示例，正是我们 `AsrSession` 的双引擎方案；直接照它的两遍拼接逻辑，少走弯路。
另：`android/SherpaOnnxTtsEngine` 演示了**注册成系统 TTS 引擎**——意味着"我来帮你"可以把离线音色开放给其它 App 用，这对视障用户价值很大（读屏也能用我们的声音）。

### 12. VoxSherpa-TTS（Java, GPL-3.0）/ HayaiTTS（Kotlin）
离线神经 TTS + 系统级注册。**我们借鉴**：TTS 引擎注册、音色切换、低延迟首字策略。

### 13. niedev/RTranslator（Java, Apache-2.0, ★10646）
全离线翻译/识别，模型管理与下载器设计成熟。**我们借鉴**：模型分包下载、断点续传、存储布局。

## 五、失语 / 音色类

### 14. aphasia-sox — ScottxxMcMullen（Dart, MIT）
给失语者用"自己的声音"说话。
**结论**：印证我们的判断——音色复刻是失语人群的高价值点；但端侧克隆成本高，M3 再做，先做"多音色选择"。

## 六、明确不采用

| 项目 | 原因 |
|---|---|
| whisper_android / whisper.cpp 端侧 | 中文短句延迟与功耗不如流式 zipformer；适合离线转写，不适合实时对话 |
| Tesseract4Android | 中文竖排、手写、生僻字弱于 PP-OCRv5 |
| 手语识别类项目 | 手语是独立语言，误识别在医疗/报警场景有实际风险，本项目明确不做 |

## 七、给我们的三条直接动作

1. **短句板改用 OBF/OBZ 格式**（借 LibreAAC），并补"开关扫描"。
2. **`core/ocr` 的前后处理照 RapidOcrOnnx 实现**，模型用 PP-OCRv5 mobile onnx。
3. **`core/asr` 双引擎照 `SherpaOnnx2Pass` 的两遍拼接**；同时评估把 TTS 注册成系统引擎（借 SherpaOnnxTtsEngine）。

## 许可证备忘

- MIT / Apache-2.0：可直接参考与改造，保留版权声明。
- GPL-3.0：可与 AGPL-3.0 组合（GPLv3 §13 允许），但整体仍须开源；引用其代码时在文件头注明来源与许可。
- 符号板（ARASAAC/OpenAAC）：CC 许可，需逐板署名，不能整体改成 AGPL 后抹掉原作者。
