#!/usr/bin/env python3
"""Restore a signing keystore from CI secrets to a private temporary file."""

import argparse
import base64
import binascii
import os
from pathlib import Path
import sys


REQUIRED = ("ANDROID_KEYSTORE_BASE64", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")


def signing_path(environment):
    temporary = Path(environment["RUNNER_TEMP"]).resolve()
    path = Path(environment["ANDROID_KEYSTORE_PATH"]).resolve()
    if path == temporary or not path.is_relative_to(temporary) or path.suffix != ".jks":
        raise ValueError("Keystore must be a .jks file inside RUNNER_TEMP")
    return path


def prepare(environment):
    missing = [name for name in REQUIRED if not environment.get(name, "").strip()]
    if missing:
        raise ValueError("Configure these GitHub Actions Secrets before tagging: " + ", ".join(missing))
    path = signing_path(environment)
    encoded = environment["ANDROID_KEYSTORE_BASE64"]
    if len(encoded) > 3 * 1024 * 1024:
        raise ValueError("Keystore exceeds size limit")
    data = base64.b64decode("".join(encoded.split()), validate=True)
    if not data or len(data) > 2 * 1024 * 1024:
        raise ValueError("Keystore is empty or exceeds size limit")
    with path.open("xb") as stream:
        stream.write(data)
    path.chmod(0o600)
    print("Fixed release keystore restored to runner temporary storage.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cleanup", action="store_true")
    args = parser.parse_args()
    try:
        if args.cleanup:
            signing_path(os.environ).unlink(missing_ok=True)
        else:
            prepare(os.environ)
    except (OSError, ValueError, KeyError, binascii.Error):
        # Print only missing variable names, never arbitrary decoded data or credentials.
        missing = [name for name in REQUIRED if not os.environ.get(name, "").strip()]
        print("Signing setup failed. " + ("Missing Secrets: " + ", ".join(missing) if missing else
              "Check the secret values and temporary keystore path."), file=sys.stderr)
        sys.exit(1)
