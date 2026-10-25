#!/usr/bin/env python3
"""Create a local fixed signing identity; never print key material or passwords."""

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]


def create(keytool):
    private = ROOT / ".signing"
    public = ROOT / "release-signing.json"
    if private.exists() or public.exists():
        raise ValueError("Signing identity already exists; back it up and reuse it. No files overwritten.")
    subprocess.run(["git", "check-ignore", "--quiet", ".signing/probe"], cwd=ROOT, check=True)
    private.mkdir(mode=0o700)
    store = private / "lunhui-release.jks"
    store_password = secrets.token_urlsafe(32)
    key_password = secrets.token_urlsafe(32)
    alias = "lunhui-release"
    environment = os.environ.copy()
    environment["LUNHUI_STORE_PASSWORD"] = store_password
    environment["LUNHUI_KEY_PASSWORD"] = key_password
    passwords = private / "passwords.env"
    passwords.write_text(
        f"ANDROID_KEYSTORE_PASSWORD={store_password}\nANDROID_KEY_ALIAS={alias}\nANDROID_KEY_PASSWORD={key_password}\n",
        encoding="utf-8", newline="\n")
    passwords.chmod(0o600)
    commands = [
        [str(keytool), "-genkeypair", "-storetype", "JKS", "-keystore", str(store),
         "-storepass:env", "LUNHUI_STORE_PASSWORD", "-keypass:env", "LUNHUI_KEY_PASSWORD",
         "-alias", alias, "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000",
         "-dname", "CN=Lunhui Log", "-noprompt"],
        [str(keytool), "-exportcert", "-keystore", str(store), "-storepass:env", "LUNHUI_STORE_PASSWORD",
         "-alias", alias, "-file", str(private / "certificate.der")],
    ]
    for command in commands:
        result = subprocess.run(command, env=environment, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode:
            raise RuntimeError("Keytool failed; inspect local signing setup. Existing files were preserved.")
    certificate = (private / "certificate.der").read_bytes()
    values = {
        "ANDROID_KEYSTORE_BASE64": base64.b64encode(store.read_bytes()).decode("ascii"),
        "ANDROID_KEYSTORE_PASSWORD": store_password,
        "ANDROID_KEY_ALIAS": alias,
        "ANDROID_KEY_PASSWORD": key_password,
    }
    secret_file = private / "github-secrets.env"
    secret_file.write_text("".join(f"{name}={value}\n" for name, value in values.items()),
                           encoding="utf-8", newline="\n")
    for path in private.iterdir():
        path.chmod(0o600)
    public.write_text(json.dumps({
        "format_version": 1,
        "application_id": "com.lunhui.log",
        "certificate_sha256": hashlib.sha256(certificate).hexdigest(),
    }, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("Created fixed release identity. Private files: .signing/ (Git ignored).")
    print("Public certificate fingerprint: release-signing.json")
    print("Back up the entire .signing/ directory securely before publishing. Do not regenerate this identity.")
    print("GitHub Secrets import file: .signing/github-secrets.env (never commit or share).")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--keytool", type=Path, required=True)
    args = parser.parse_args()
    try:
        create(args.keytool)
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"Signing setup failed: {error}", file=sys.stderr)
        sys.exit(1)
