#!/usr/bin/env python3
"""Check publishable Git files without reading ignored local/private directories."""

import argparse
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
BLOCKED_DIRS = {
    ".android", ".aws", ".gradle", ".idea", ".kotlin", ".ssh",
    "__pycache__", "backups", "build", "captures", "dist", "exports",
}
BLOCKED_NAMES = {"local.properties", "key.properties", "keystore.properties"}
BLOCKED_SUFFIXES = {
    ".aab", ".apk", ".db", ".har", ".hprof", ".jks", ".key", ".keystore",
    ".log", ".mitm", ".p12", ".pcap", ".pcapng", ".pem", ".pfx",
    ".sqlite", ".sqlite3",
}
BINARY_SUFFIXES = {".jar", ".png", ".jpg", ".jpeg", ".webp", ".ico"}
PATTERNS = {
    "private key": re.compile(r"-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----"),
    "AWS access key": re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
    "GitHub token": re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})\b"),
    "OpenAI-style secret": re.compile(r"\bsk-(?:proj-)?[A-Za-z0-9_-]{32,}\b"),
    "local machine path": re.compile(
        r"(?i)(?:[a-z]:[/\\](?:users|1-projects|my_software)[/\\]|"
        r"/(?:users|home)/[^/\s<>]+/)"
    ),
    "embedded credential": re.compile(
        r"(?i)(?:password|passwd|api[_-]?key|access[_-]?token|storePassword|keyPassword)"
        r"\s*[:=]\s*[\"'][^\s\"'<>$]{8,}[\"']"
    ),
}


def git(*args):
    result = subprocess.run(
        ["git", *args], cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.PIPE
    )
    if result.returncode:
        # Do not echo arbitrary command output that might contain local details.
        raise RuntimeError("Git command failed; initialize the repository first.")
    return result.stdout


def check(staged=False):
    if staged:
        raw = git("ls-files", "--cached", "-z")
    else:
        raw = git("ls-files", "--cached", "--others", "--exclude-standard", "-z")
    files = sorted({p.decode("utf-8") for p in raw.split(b"\0") if p})
    issues = []
    for name in files:
        path = PurePosixPath(name)
        lower = PurePosixPath(name.lower())
        if (
            BLOCKED_DIRS.intersection(lower.parts[:-1])
            or lower.name in BLOCKED_NAMES
            or lower.suffix in BLOCKED_SUFFIXES
            or (lower.name.startswith(".env") and lower.name != ".env.example")
            or ".db-" in lower.name
            or name == "docs/screenshots/current.png"
        ):
            issues.append(f"{name}: local/private/generated file")
            continue
        full = ROOT.joinpath(*path.parts)
        if not staged and full.is_symlink():
            issues.append(f"{name}: review symbolic link manually")
            continue
        if not staged and not full.exists():
            continue  # Tracked deletion.
        data = git("show", f":{name}") if staged else full.read_bytes()
        if len(data) > 2 * 1024 * 1024:
            issues.append(f"{name}: file exceeds 2 MiB; review manually")
        if lower.suffix in BINARY_SUFFIXES:
            continue
        try:
            content = data.decode("utf-8-sig")
        except UnicodeDecodeError:
            issues.append(f"{name}: unexpected binary or non-UTF-8 file")
            continue
        for label, pattern in PATTERNS.items():
            for match in pattern.finditer(content):
                line = content.count("\n", 0, match.start()) + 1
                issues.append(f"{name}:{line}: possible {label}")
                # Never print the matched value.
    if issues:
        print("Repository check failed:")
        print("\n".join(f"- {issue}" for issue in issues))
        return 1
    print(f"Repository check passed ({len(files)} files; ignored data not read).")
    print("Review images, licenses, and the final Git diff manually before publishing.")
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--staged", action="store_true", help="inspect exact index content")
    args = parser.parse_args()
    try:
        sys.exit(check(args.staged))
    except (OSError, RuntimeError, UnicodeError) as error:
        print(f"Repository check could not complete: {type(error).__name__}", file=sys.stderr)
        sys.exit(2)
