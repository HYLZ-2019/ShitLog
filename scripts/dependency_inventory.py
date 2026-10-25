#!/usr/bin/env python3
"""Read built Android Lint dependency models and verify official Maven license metadata."""

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "app/build/reports/oss/poms"
OUTPUT = ROOT / "app/build/reports/oss/dependencies.json"
BASELINE = ROOT / "third_party/dependencies.json"
MODELS = {
    "debug": "lint_report_lint_model/debug/generateDebugLintReportModel",
    "debugUnitTest": "unit_test_lint_model/debug/generateDebugUnitTestLintModel",
    "debugAndroidTest": "android_test_lint_model/debug/generateDebugAndroidTestLintModel",
}
NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}
OVERRIDE_FILE = ROOT / "third_party/license_overrides.json"


def pom_metadata(coordinate, offline, seen=()):
    if coordinate in seen or len(seen) > 12:
        raise ValueError("Invalid Maven parent chain")
    group, artifact, version = coordinate.split(":")
    if any(not part or "/" in part or "\\" in part or "${" in part
           for part in (group, artifact, version)):
        raise ValueError(f"Unsupported Maven coordinate: {coordinate}")
    base = ("https://dl.google.com/dl/android/maven2/" if group.startswith("androidx.")
            else "https://repo.maven.apache.org/maven2/")
    url = f"{base}{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"
    filename = CACHE / (hashlib.sha256(coordinate.encode()).hexdigest() + ".pom")
    if filename.exists():
        data = filename.read_bytes()
    elif offline:
        raise ValueError(f"Missing cached official POM: {coordinate}")
    else:
        request = urllib.request.Request(url, headers={"User-Agent": "Lunhui-license-inventory/1"})
        with urllib.request.urlopen(request, timeout=45) as response:
            data = response.read(2 * 1024 * 1024 + 1)
        if len(data) > 2 * 1024 * 1024:
            raise ValueError(f"POM exceeds size limit: {coordinate}")
        filename.parent.mkdir(parents=True, exist_ok=True)
        filename.write_bytes(data)
    root = ET.fromstring(data)
    licenses = [{"name": item.findtext("m:name", default="", namespaces=NAMESPACE).strip(),
                 "url": item.findtext("m:url", default="", namespaces=NAMESPACE).strip()}
                for item in root.findall("m:licenses/m:license", NAMESPACE)]
    if not licenses:
        parent = root.find("m:parent", NAMESPACE)
        if parent is not None:
            parent_id = ":".join(parent.findtext("m:" + key, default="", namespaces=NAMESPACE)
                                 for key in ("groupId", "artifactId", "version"))
            licenses = pom_metadata(parent_id, offline, (*seen, coordinate))["licenses"]
    override = None
    if not licenses and OVERRIDE_FILE.exists():
        override = json.loads(OVERRIDE_FILE.read_text(encoding="utf-8")).get(coordinate)
        if override:
            licenses = override["licenses"]
    if not licenses or any(not item["name"] for item in licenses):
        raise ValueError(f"Missing declared Maven license: {coordinate}")
    result = {"licenses": licenses, "pom_url": url, "pom_sha256": hashlib.sha256(data).hexdigest()}
    if override:
        result["license_evidence"] = override["evidence"]
    return result


def inventory(offline):
    modules = {}
    for configuration, directory in MODELS.items():
        model = ROOT / "app/build/intermediates" / directory / "debug-artifact-libraries.xml"
        if not model.exists():
            raise ValueError("Missing Lint dependency models; run :app:lintDebug first")
        for library in ET.parse(model).getroot().findall("library"):
            coordinate = library.get("resolved")
            if not coordinate:
                continue
            coordinate = coordinate.split("@", 1)[0]
            if len(coordinate.split(":")) != 3:
                raise ValueError(f"Unsupported dependency model entry: {coordinate}")
            modules.setdefault(coordinate, set()).add(configuration)

    def resolve(item):
        coordinate, configurations = item
        return {"coordinate": coordinate, "configurations": sorted(configurations),
                **pom_metadata(coordinate, offline)}

    with ThreadPoolExecutor(max_workers=6) as pool:
        resolved = list(pool.map(resolve, sorted(modules.items())))
    return {"format_version": 1,
            "scope": "Debug application and test artifacts from Android Lint models, including provided compile dependencies; release and build tooling excluded",
            "modules": resolved}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true", help="use locally cached official POMs")
    parser.add_argument("--check", action="store_true", help="compare with committed inventory")
    args = parser.parse_args()
    result = inventory(args.offline)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.check:
        if not BASELINE.exists() or json.loads(BASELINE.read_text(encoding="utf-8")) != result:
            raise ValueError("Dependency inventory changed; review licenses and update third_party/dependencies.json")
    print(f"Verified license metadata for {len(result['modules'])} built dependency artifacts.")
    print("Inventory: app/build/reports/oss/dependencies.json")
    print("Also review bundled copyrights and NOTICE files before distributing APKs.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, ET.ParseError, urllib.error.URLError) as error:
        print(f"Dependency inventory failed: {error}", file=sys.stderr)
        sys.exit(1)
