# 更新日志

## v0.5.6-m12（2026-11-14）

对着 m11 的实机结论修的一版，重点是把「为什么」写进日志。

### 1. 悬浮字幕不出字（已定位）
`asr.log` 里悬浮那一路 `level peak` 一直 0.0015 上下、`speaking=false`，
而同一时刻主界面能正常出字——同一 App 开了两路 `AudioRecord`，
系统只把声音喂给先占用的那一路，另一路拿到的是静音。

改法：**互斥**。开悬浮字幕时自动停掉主界面录音；在主界面点「开始对话」时
自动关掉悬浮字幕。两路日志现在分别带 `[main]` / `[overlay]` 前缀，一眼能分清。

### 2. 看世界不对劲（已定位）
`det boxes=1 size=192x256` —— 之前用的是 `TakePicturePreview`，
拿到的是相机**缩略预览图**，才 192×256，OCR 当然读不出东西。

改法：换成 `TakePicture` 全尺寸拍照（FileProvider 写缓存目录），
解码时按 EXIF 转正、长边压到 1600，再进 PP-OCRv5。

### 3. 新增日志（这次要能自己说话）
- 拍照：`WOILAI_OCR 相机返回 WxH`、`read 完成 lines= paragraphs= cost=ms text=...`
- 短句板：`WOILAI_BOARD 板名/行/列`、`tap label= spoken= action=`、`speak: ...`
- 发声：`WOILAI_TTS TTS 初始化 status= lang= ready=`、`speak len= ready=`、返回值
- 悬浮字幕：服务启动/停止、模型未就绪、开始录音

## v0.5.5-m11（2026-11-14）

新增**悬浮字幕**：字幕浮在其他应用上面（视频、微信、会议都能看）。

### 用法
「对话 / 只听」页底部 → 点「悬浮字幕」→ 首次会让你开两个权限：
1. 显示在其他应用上层（SYSTEM_ALERT_WINDOW）
2. 录音权限（服务启动时弹）

之后字幕条固定在屏幕下方，黑底黄字 26sp，不抢焦点、不挡点击。

### 实现
- `CaptionOverlayService`：`foregroundServiceType=microphone` 的前台服务，
  自己持有 `AsrSession`，用 `TYPE_APPLICATION_OVERLAY` + `FLAG_NOT_TOUCHABLE` 画字幕。
- 服务独立准备模型，不依赖主界面是否打开。

### 已知限制
- 通知栏会常驻一条「悬浮字幕正在听」，这是 Android 对前台麦克风服务的硬性要求，去不掉。
- 部分 HyperOS 版本会额外弹「后台弹出界面 / 悬浮窗」二次确认。

## v0.5.4-m10（2026-11-13）

对着真机日志修的第二个版本。

### 1. 拍照识字一直失败（已定位）
```
java.lang.UnsatisfiedLinkError: dlopen failed:
cannot locate symbol "OrtGetApiBase" referenced by libonnxruntime4j_jni.so
```
原因：APK 里同时存在两份 `libonnxruntime.so`——sherpa 自带的 1.28.2 与
onnxruntime-android AAR 的 1.28.0，`pickFirsts` 保了 sherpa 那份。
sherpa 那份把 `OrtGetApiBase` 导出成 `@@VERS_1.28.2`，Java 绑定要的是无版本符号，
所以 OCR 一加载就死。

修法：删掉 sherpa 那份运行时，只留 AAR 的 1.28.0；把 sherpa 的 jni/c-api 里
GNU 版本需求（字符串 + hash）从 `VERS_1.28.2` 改标到 `VERS_1.28.0`
（`tools/patch-ort-version.py`）。两者现在共用同一个运行时。

### 2. 对话一直没字幕
日志显示 `recorder start=true` 后再无任何事件 —— VAD 从未判定"有人说话"。
- 阈值从 0.012 降到 0.004；
- 每秒往 `asr.log` 写一行电平 `level peak=0.0xxx`，能直接看出麦克风有没有收到声音；
- 手动点「停止」时也把这句定稿，不再完全依赖 VAD。

## v0.5.3-m9（2026-11-13）

自查型版本：不再让你猜"到底装了什么"。

### 新增
- 界面上直接显示模型文件现状：`模型：encoder.onnx 173MB · decoder.onnx 12MB · ... 缺失`。
- 解包后做完整性校验（7 个文件逐个查存在与大小），缺任何一个都判失败，
  下次启动强制重解包；半包不会再被当成"已安装"。
- `BundledModels.describe()` 同时写 `asr.log`，只看一个文件就能判断。

### 修复
- 日志目录显示用运行时实际值，不再固定显示 `/Android/helpup`。
- 语音输入页补上可用交互：识别结果、复制、念一遍。

## v0.5.2-m8（2026-11-13）

根据真机日志定位到的三件事：

### 关键结论（日志实测）
```
20:54:39 流式模型不完整：files/models/asr-stream
20:54:39 prepare stream=false
```
装的是 **slim 包**，没有模型，引擎必然起不来。现在未就绪时字幕框会写明
「这是轻量包（slim），请装 full 包」或具体缺失的目录，不再让人猜。

### 修复
- 日志目录显示错误：以前永远显示 `Android/helpup`（初始化前的值），
  实际降级到了 `Android/data/<包名>/files/logs`。现在按真实落盘目录显示。
- 「语音输入」页原来只有一个空壳，现在可用：说话 → 上屏 → 复制 / 念一遍。

### 说明
- 不上屏不等于没录音：录音开始/停止、模型准备结果都写 asr.log。

## v0.5.1-m7（2026-11-13）

修三处反馈：

### 1. 点拍照闪退
Manifest 声明了 `CAMERA` 权限却没请求，`TakePicturePreview` 启动系统相机时抛 `SecurityException`，
异常没接住 → 进程直接死。现在点拍照先申请权限，授权后再拉起相机；
相机返回空、识别抛异常都只显示一行提示，不再崩。

### 2. 深色模式
界面颜色原先全写死（白底黑字），系统切暗色后仍是白板。现在统一收敛到 Light/Dark 两套
`Palette`，随系统 `isSystemInDarkTheme()` 切换；`values-night` 另配了窗口底色，避免启动白闪。

### 3. 点「开始对话」没反应
- 按钮不再受 `engineReady` 限制，随时可按；未就绪时直接把原因写在字幕框里
  （`SherpaStreamingAsrEngine.lastError`），不再"点了像没点"。
- 录音源 `VOICE_RECOGNITION` 被系统拒绝时自动退到 `MIC`。
- 录音开始/停止写 `asr.log`，方便回传排查。

## v0.5.0-m6（2026-11-13）

首次出现两种发行包。

| 包 | 体积 | 模型 |
|---|---|---|
| `slim` | 41MB | 不含模型，按需下载 / adb push |
| `full` | 227MB | 内置标准 zipformer（int8）+ PP-OCRv5 mobile，装完即用 |

### 新增
- `productFlavors`：`slim` / `full`，差异只在 assets，代码不变。
- `BundledModels`：full 包首启把 `assets/models`（约 210MB）解包到 `filesDir/models`，
  带百分比进度提示；OCR 因为 ONNX Runtime 只吃文件路径，必须走这一步。
- `tools/fetch-models.sh`：仓库不存模型（encoder 174MB 超 GitHub 单文件限制），需要重建时跑它。

### 说明
- full 包保留 INTERNET 权限（按用户决定），但运行时不联网。
- 模型来源：k2-fsa/sherpa-onnx release `asr-models`；PP-OCRv5 mobile ONNX 取自 HF `ogkalu/ppocr-v5-onnx`。

## v0.4.2-m5（2026-11-13）

启动不再碰原生库。

### 修复
- `PpOcrEngine` 的 `OrtEnvironment` 改为 `by lazy`：之前一构造就加载 `libonnxruntime.so`，
  模型缺失时也会走 native 初始化，是启动闪退的头号嫌疑。
- `PpOcrTextReader.prepare` 与启动阶段的模型准备全部 `runCatching` 兜底。

### 排查用
- 启动后先看 `app.log` 有没有「日志开始」：有 → 说明 `onCreate` 跑完了，崩溃在 UI 之后；
  没有 → 崩在更早的初始化。

## v0.4.1-m4（2026-11-13）

启动加固。

### 修复
- `LogHub.init` 整体 `runCatching` 兜底：写日志失败不再可能拦住 App 启动。
- 初始化提前到 `MainActivity.onCreate`，早于 `setContent`，先把崩溃处理器装上再进 UI。

### 说明
- 版本号与内容差异不大，纯粹为了排查「打不开」——如果上一版是启动闪退，这版应当能起来。

## v0.4.0-m3（2026-11-13）

日志落盘。

### 新增
- **`core:log` 模块 / `LogHub`**：四路日志分流落盘
  - `app.log`（启动、崩溃、模块状态）、`perf.log`（`WOILAI_PERF`）、`asr.log`、`ocr.log`；
  - 目标目录 `/storage/emulated/0/Android/helpup/`，单文件 2MB 轮转，保留 2 份；
  - 分区存储写不进去时自动降级，App 底部实时显示**当前实际目录**并给一键授权入口；
  - 同时镜像 logcat，`adb logcat -s WOILAI_PERF` 照旧可用。
- 全局 `UncaughtExceptionHandler`：崩溃带机型/ABI 写进 `app.log` 后再交还系统。
- `core:asr` / `core:ocr` / App 的日志调用全部改走 `LogHub`，不再散落 `android.util.Log`。
- `docs/LOGGING.md`：目录说明、授权命令、`adb pull` 两种路径。

### 说明
- Android 11+ 直接写 `/Android/helpup` 需要「所有文件访问权限」（MANAGE_EXTERNAL_STORAGE）；
  没授权时日志落到 `Android/data/<包名>/files/logs`，adb 可直接 pull，不影响调试。

## v0.3.0-m2（2026-11-13）

真正接上 sherpa-onnx。

### 新增
- **`SherpaStreamingAsrEngine`**：streaming zipformer transducer，第一遍实时出字。
  - 关闭 sherpa 自带 endpoint，断句仍由我们的能量 VAD 负责，避免两套端点打架。
- **`SherpaOfflineAsrEngine`**：SenseVoice（language=auto）+ 逆文本规整，第二遍定稿。
- **sherpa-onnx v2.0.1 官方 arm64 原生库**进入 `core/asr/src/main/jniLibs`；
  Kotlin API 从官方 `kotlin-api` 取来，包名保持 `com.k2fsa.sherpa.onnx`，便于对照上游修 bug。
- `AsrSession.prepare(streamDir, offlineDir)`：两个包独立准备，缺一个只降级不罢工。
- UI 增加「未就绪」态：模型没放对时按钮禁用并给出路径提示，而不是假装在听。

### 构建
- ONNX Runtime 对齐到 1.28.0（sherpa 包内 1.28.2），`jniLibs.pickFirsts` 只保留一份 `libonnxruntime.so`。
- APK 41MB（arm64，两个引擎的原生库都在）。

### 已知限制
- 未在真机跑过推理（云主机无设备）；ORT 版本差异可能导致符号缺失，见 `docs/STATUS-M2.md`。

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
