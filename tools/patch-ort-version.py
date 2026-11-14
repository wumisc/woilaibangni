#!/usr/bin/env python3
"""把 sherpa-onnx 预编译 .so 对 libonnxruntime.so 的版本需求，从 VERS_1.28.2 改标到 VERS_1.28.0。

背景：
- sherpa 预编译包自带的 libonnxruntime.so 导出的是 `OrtGetApiBase@@VERS_1.28.2`；
- onnxruntime-android:1.28.0 的 Java JNI 引用的是**无版本**的 `OrtGetApiBase`，
  以及自身运行时的 `VERS_1.28.0`；
- 两者同名同 soname 只能进一份。若留 sherpa 那份，OCR 侧 dlopen 直接报
  `cannot locate symbol "OrtGetApiBase"`。
- 于是改为只打包 AAR 的 1.28.0 运行时，并把 sherpa 的 jni/c-api 里
  版本需求字符串 + GNU version hash 改成 VERS_1.28.0（字符串等长，直接二进制替换）。

用法：
    python3 tools/patch-ort-version.py
只需在重新拉取 sherpa-onnx 预编译包后跑一次。
"""
import pathlib
import struct

JNI_LIBS = pathlib.Path(__file__).resolve().parent.parent / "core/asr/src/main/jniLibs/arm64-v8a"
TARGETS = ["libsherpa-onnx-jni.so", "libsherpa-onnx-c-api.so"]
OLD = b"VERS_1.28.2"
NEW = b"VERS_1.28.0"


def elf_hash(name: bytes) -> int:
    h = 0
    for c in name:
        h = (h << 4) + c
        g = h & 0xF0000000
        if g:
            h ^= g >> 24
        h &= ~g
    return h & 0xFFFFFFFF


def main() -> None:
    old_hash, new_hash = elf_hash(OLD), elf_hash(NEW)
    for name in TARGETS:
        path = JNI_LIBS / name
        if not path.is_file():
            print(f"skip {name}: 不存在")
            continue
        data = bytearray(path.read_bytes())
        strings = data.count(OLD)
        hashes = data.count(struct.pack("<I", old_hash))
        if strings == 0 and hashes == 0:
            print(f"{name}: 已是 {NEW.decode()}，无需修改")
            continue
        data = data.replace(OLD, NEW)
        data = data.replace(struct.pack("<I", old_hash), struct.pack("<I", new_hash))
        path.write_bytes(data)
        print(f"{name}: 改写 {strings} 处字符串、{hashes} 处哈希")


if __name__ == "__main__":
    main()
