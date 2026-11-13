# 日志系统

## 目录

目标目录：

```
/storage/emulated/0/Android/helpup/
├── app.log          启动、崩溃、模块初始化
├── perf.log         性能打点（first_partial_ms / finalize_ms / revise_ms）
├── asr.log          识别引擎加载、定稿、第二遍失败
└── ocr.log          det 框数、模型加载、识别失败
```

单文件超过 2MB 自动轮转：`app.log.1`、`app.log.2`。

## 为什么可能不在这个目录

Android 11 起，`/storage/emulated/0/Android/` 根目录下的自建目录属于受限区，
没拿到「所有文件访问权限」写不进去。`LogHub` 会按顺序降级：

1. `/storage/emulated/0/Android/helpup`（目标）
2. `/storage/emulated/0/Android/data/io.github.wumisc.woilaibangni/files/logs`（adb 可直接 pull）
3. `/data/data/io.github.wumisc.woilaibangni/files/logs`（需 root 或 run-as）

App 底部会实时显示**当前实际目录**，降级时是红字，点一下去授权。

## 授权（二选一）

**手机端**：设置 → 应用管理 → 我来帮你 → 权限 → 所有文件访问权限 → 允许。
回来点 App 底部的「授权后点这里刷新目录」。

**命令行**：
```bash
adb shell appops set io.github.wumisc.woilaibangni MANAGE_EXTERNAL_STORAGE allow
```

## 取日志

目标目录（已授权）：
```bash
adb pull /storage/emulated/0/Android/helpup ./helpup
```

降级后的备用目录（**不需要 root**）：
```bash
adb pull /storage/emulated/0/Android/data/io.github.wumisc.woilaibangni/files/logs ./logs
```

手机端也能看：文件管理 → Android → data → io.github.wumisc.woilaibangni → files → logs
（HyperOS 需在文件管理里开启「显示隐藏文件/访问 Android/data」）。

## 实时看

原来的 logcat 用法完全不受影响，日志同时镜像到 logcat：
```bash
adb logcat -s WOILAI_PERF WOILAI_ASR WOILAI_OCR WOILAI_LOG WOILAI_CRASH
```

## 崩溃

`LogHub` 装了 `UncaughtExceptionHandler`，崩溃会带设备信息写进 `app.log`：
```
[APP] WOILAI_CRASH: 崩溃 thread=main sdk=36 device=Xiaomi/... abi=arm64-v8a
java.lang.RuntimeException: ...
```
崩溃后 App 会退出（仍交给系统默认处理器），但堆栈已经落盘，下次打开还能看到。
