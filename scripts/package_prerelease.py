#!/usr/bin/env python3
"""Verify fixed signing and increasing version, then package a matching tagged release."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
APK_DIRECTORY = Path("app/build/outputs/apk/release")
OUTPUT_DIRECTORY = Path("app/build/outputs/prerelease")
VERSION = r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?"


def certificate_fingerprint(output):
    fingerprints = re.findall(r"^Signer #\d+ certificate SHA-256 digest:\s*([a-fA-F0-9]{64})\s*$", output, re.MULTILINE)
    if len(fingerprints) != 1:
        raise ValueError("Expected exactly one verified APK signing certificate")
    return fingerprints[0].lower()


def verify_apk(apk):
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    executable = shutil.which("apksigner")
    if not executable and sdk:
        for version in ("36.1.0", "36.0.0"):
            name = "apksigner.bat" if os.name == "nt" else "apksigner"
            candidate = Path(sdk) / "build-tools" / version / name
            if candidate.is_file():
                executable = str(candidate)
                break
    if not executable:
        raise ValueError("apksigner not found; configure Android SDK Build Tools")
    result = subprocess.run([executable, "verify", "--verbose", "--print-certs", str(apk)],
                            check=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    aapt = Path(executable).parent / ("aapt2.exe" if os.name == "nt" else "aapt2")
    badging = subprocess.run([str(aapt), "dump", "badging", str(apk)], check=True, text=True,
                             stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout
    if "application-debuggable" in badging or "package: name='com.lunhui.log'" not in badging:
        raise ValueError("APK must be non-debuggable and use the expected package name")
    return certificate_fingerprint(result.stdout)


def package(root, tag, verifier=verify_apk):
    if not re.fullmatch("v" + VERSION, tag):
        raise ValueError("Expected a version tag such as v0.1.3")
    directory = root / APK_DIRECTORY
    metadata = json.loads((directory / "output-metadata.json").read_text(encoding="utf-8"))
    if metadata.get("applicationId") != "com.lunhui.log":
        raise ValueError("Unexpected APK application ID")
    if metadata.get("variantName") != "release":
        raise ValueError("Only the signed release build variant may be published")
    elements = metadata.get("elements", [])
    if len(elements) != 1:
        raise ValueError("Expected exactly one universal release APK")
    element = elements[0]
    version = element.get("versionName")
    if tag != f"v{version}":
        raise ValueError("Tag must exactly match the built APK versionName")
    version_code = element.get("versionCode")
    if not isinstance(version_code, int) or isinstance(version_code, bool) or version_code < 1:
        raise ValueError("Invalid APK versionCode")
    apk = (directory / element["outputFile"]).resolve()
    if not apk.is_relative_to(directory.resolve()) or not apk.is_file() or apk.suffix != ".apk":
        raise ValueError("Invalid APK output path")

    commit = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=root, text=True
    ).strip()
    tag_commit = subprocess.check_output(
        ["git", "rev-parse", "--verify", f"refs/tags/{tag}^{{commit}}"], cwd=root, text=True
    ).strip()
    if commit != tag_commit:
        raise ValueError("Version tag must point to the checked-out commit")
    subprocess.run(["git", "diff", "--quiet", "HEAD", "--"], cwd=root, check=True)

    tags = subprocess.check_output(["git", "tag", "--list", "v*"], cwd=root, text=True).splitlines()
    for previous in tags:
        if previous == tag or not re.fullmatch("v" + VERSION, previous):
            continue
        source = subprocess.check_output(["git", "show", f"refs/tags/{previous}:app/build.gradle.kts"], cwd=root, text=True)
        codes = re.findall(r"\bversionCode\s*=\s*(\d+)\b", source)
        if len(codes) != 1 or version_code <= int(codes[0]):
            raise ValueError("versionCode must exceed every previous version tag")

    identity = json.loads((root / "release-signing.json").read_text(encoding="utf-8"))
    expected = identity.get("certificate_sha256", "").lower()
    if identity.get("application_id") != metadata["applicationId"] or not re.fullmatch(r"[a-f0-9]{64}", expected):
        raise ValueError("Missing or invalid fixed public signing identity")
    actual = verifier(apk)
    if actual != expected:
        raise ValueError("APK certificate differs from the fixed signing identity; refusing release")

    model = root / "app/build/intermediates/lint_report_lint_model/release/generateReleaseLintReportModel/release-artifact-libraries.xml"
    release_dependencies = {item.get("resolved").split("@", 1)[0] for item in ET.parse(model).getroot().findall("library") if item.get("resolved")}
    inventory = json.loads((root / "third_party/dependencies.json").read_text(encoding="utf-8"))
    reviewed = {item["coordinate"] for item in inventory["modules"]}
    if not release_dependencies or release_dependencies - reviewed:
        raise ValueError("Release dependencies must all be present in the reviewed license inventory")

    output = root / OUTPUT_DIRECTORY
    output.mkdir(parents=True, exist_ok=True)
    # A fresh directory prevents accidentally uploading stale versioned assets.
    if any(output.iterdir()):
        raise ValueError("Prerelease output directory must be empty; review existing files first")
    prefix = f"LunhuiLog-{version}"
    apk_copy = output / f"{prefix}.apk"
    source_zip = output / f"{prefix}-source.zip"
    subprocess.run(
        ["git", "archive", "--format=zip", f"--prefix={prefix}/", "-o", str(source_zip), "HEAD"],
        cwd=root, check=True,
    )
    shutil.copyfile(apk, apk_copy)
    manifest = output / "RELEASE_MANIFEST.json"
    manifest.write_text(json.dumps({
        "tag": tag, "commit": commit, "application_id": metadata["applicationId"],
        "version_name": version, "version_code": version_code,
        "certificate_sha256": actual, "release_dependencies": sorted(release_dependencies),
    }, indent=2) + "\n", encoding="utf-8", newline="\n")
    lines = []
    for file in (apk_copy, source_zip, manifest):
        with file.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        lines.append(f"{digest}  {file.name}\n")
    (output / "SHA256SUMS.txt").write_text("".join(lines), encoding="utf-8", newline="\n")
    notes = f"""# 轮回记录 {tag}（实验版）

Android 8.0 及以上。应用版本 {version}，versionCode {version_code}。
构建提交：{commit}

- `{prefix}.apk`：固定签名的 release 构建，支持后续同一签名版本覆盖升级。
- `{prefix}-source.zip`：该标签提交的源码，包含 Apache-2.0、第三方许可和已知限制文档。
- `RELEASE_MANIFEST.json`：版本、提交、公开签名指纹与 release 依赖清单。
- `SHA256SUMS.txt`：APK、源码包和发布清单的 SHA-256 校验值。

签名证书 SHA-256：`{actual}`
本渠道从 0.1.3 开始采用固定签名；后续版本保持包名及签名，递增 versionCode，可直接覆盖升级保留记录。
首次从旧 debug 包切换时，先导出 JSON 并确认可恢复，再重新安装一次并导入。不要直接卸载未备份的旧版。
JSON 备份和 CSV 为明文。

发布前自动检查包括构建、JVM 单元测试、Lint、设备测试包编译和依赖许可清单核对。
没有自动运行真机或模拟器完整流程；单屏显示、重启恢复和厂商备份行为仍需验证。
更多限制见源码中的 `docs/VERIFICATION.md`。
"""
    (output / "RELEASE_NOTES.md").write_text(notes, encoding="utf-8", newline="\n")
    print(f"Packaged {tag}: fixed-signed APK, tracked source, manifest, SHA-256, and release notes")
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    args = parser.parse_args()
    package(ROOT, args.tag)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        print(f"Prerelease packaging failed: {error}", file=sys.stderr)
        sys.exit(1)
