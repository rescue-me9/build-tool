#!/usr/bin/env python3
"""计算 APK 签名证书的 SHA-256（小写 hex），用于 onyx_build/onyx.txt 的 fingerprint 行。

用法:
    python3 onyx_fingerprint.py app-release.apk
输出:
    fingerprint=<64位hex>
"""
import sys
import subprocess
import hashlib
import re


def apk_signer_fingerprint(apk_path: str) -> str:
    """用 apksigner 导出签名证书再算 SHA-256（结果与客户端 signingSha256 一致）。"""
    try:
        out = subprocess.check_output(
            ["apksigner", "verify", "--print-certs", "--verbose", apk_path],
            stderr=subprocess.DEVNULL,
            text=True,
        )
    except FileNotFoundError:
        sys.exit("未找到 apksigner，请把它加入 PATH，例如 build-tools/35.0.0/")
    except subprocess.CalledProcessError as e:
        sys.exit(f"apksigner 校验失败: {e}")
    # 输出形如 "Signer #1 certificate SHA-256 digest: 3ff5..."
    for line in out.splitlines():
        m = re.search(r"SHA-256 digest:\s*([0-9a-fA-F]{64})", line)
        if m:
            return m.group(1).lower()
    sys.exit("未能在 apksigner 输出中找到证书 SHA-256 digest")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    print(f"fingerprint={apk_signer_fingerprint(sys.argv[1])}")