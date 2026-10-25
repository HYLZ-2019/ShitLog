import base64
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from package_prerelease import APK_DIRECTORY, OUTPUT_DIRECTORY, certificate_fingerprint, package
from prepare_release_signing import prepare, signing_path


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        parent = Path(__file__).resolve().parents[2] / "app/build/release-tests"
        parent.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(prefix="case-", dir=parent)
        self.root = Path(self.temp.name).resolve()
        assert self.root.is_relative_to(parent.resolve())
        self.addCleanup(self.temp.cleanup)
        self.git("init", "--quiet")
        (self.root / ".gitignore").write_text("app/build/\n.signing/\n", encoding="utf-8")
        (self.root / "LICENSE").write_text("Synthetic license\n", encoding="utf-8")
        (self.root / "README.md").write_text("Synthetic source\n", encoding="utf-8")
        (self.root / "app").mkdir()
        self.gradle = self.root / "app/build.gradle.kts"
        self.gradle.write_text('versionCode = 2\nversionName = "0.1.1"\n', encoding="utf-8")
        self.fingerprint = "a" * 64
        (self.root / "release-signing.json").write_text(json.dumps({
            "application_id": "com.lunhui.log", "certificate_sha256": self.fingerprint
        }), encoding="utf-8")
        (self.root / "third_party").mkdir()
        (self.root / "third_party/dependencies.json").write_text(json.dumps({
            "modules": [{"coordinate": "example:reviewed:1"}]
        }), encoding="utf-8")
        self.git("add", ".")
        self.git("commit", "--quiet", "-m", "Old version")
        self.git("tag", "v0.1.1")
        self.gradle.write_text('versionCode = 3\nversionName = "0.1.2"\n', encoding="utf-8")
        self.git("add", "app/build.gradle.kts")
        self.git("commit", "--quiet", "-m", "New version")
        self.git("tag", "v0.1.2")
        self.directory = self.root / APK_DIRECTORY
        self.directory.mkdir(parents=True)
        (self.directory / "app-release.apk").write_bytes(b"Synthetic signed APK fixture")
        self.metadata = {"applicationId": "com.lunhui.log", "variantName": "release", "elements": [
            {"versionName": "0.1.2", "versionCode": 3, "outputFile": "app-release.apk"}
        ]}
        self.write_metadata()
        self.model = self.root / "app/build/intermediates/lint_report_lint_model/release/generateReleaseLintReportModel/release-artifact-libraries.xml"
        self.model.parent.mkdir(parents=True)
        self.model.write_text('<libraries><library resolved="example:reviewed:1"/></libraries>', encoding="utf-8")

    def git(self, *args):
        return subprocess.check_output(
            ["git", "-c", f"safe.directory={self.root.as_posix()}",
             "-c", "user.name=Release tests", "-c", "user.email=tests@example.invalid",
             "-c", "core.autocrlf=false", "-c", "commit.gpgsign=false", *args],
            cwd=self.root, stderr=subprocess.PIPE,
        )

    def write_metadata(self):
        (self.directory / "output-metadata.json").write_text(json.dumps(self.metadata), encoding="utf-8")

    def run_package(self, **kwargs):
        return package(self.root, "v0.1.2", verifier=lambda apk: kwargs.get("fingerprint", self.fingerprint))

    def test_packages_only_committed_source_and_correct_hashes(self):
        private = self.root / ".signing"
        private.mkdir()
        (private / "fixture.txt").write_text("Synthetic private fixture", encoding="utf-8")
        (self.root / "untracked-personal.txt").write_text("Synthetic untracked fixture", encoding="utf-8")
        output = self.run_package()
        self.assertEqual(len(list(output.iterdir())), 5)
        with zipfile.ZipFile(output / "LunhuiLog-0.1.2-source.zip") as archive:
            names = archive.namelist()
            self.assertIn("LunhuiLog-0.1.2/LICENSE", names)
            self.assertFalse(any("personal" in name or "app/build/" in name or ".signing/" in name for name in names))
        for line in (output / "SHA256SUMS.txt").read_text().splitlines():
            expected, filename = line.split("  ", 1)
            self.assertEqual(expected, hashlib.sha256((output / filename).read_bytes()).hexdigest())
        manifest = json.loads((output / "RELEASE_MANIFEST.json").read_text())
        self.assertEqual(manifest["certificate_sha256"], self.fingerprint)
        self.assertEqual(manifest["version_code"], 3)

    def test_rejects_debug_build(self):
        self.metadata["variantName"] = "debug"
        self.write_metadata()
        with self.assertRaises(ValueError):
            self.run_package()

    def test_rejects_different_signing_identity(self):
        with self.assertRaises(ValueError):
            self.run_package(fingerprint="b" * 64)
        self.assertFalse((self.root / OUTPUT_DIRECTORY).exists())

    def test_rejects_nonincreasing_version_code(self):
        self.metadata["elements"][0]["versionCode"] = 2
        self.write_metadata()
        with self.assertRaises(ValueError):
            self.run_package()

    def test_rejects_unreviewed_release_dependency(self):
        self.model.write_text('<libraries><library resolved="example:unreviewed:1"/></libraries>', encoding="utf-8")
        with self.assertRaises(ValueError):
            self.run_package()

    def test_rejects_uncommitted_source(self):
        (self.root / "README.md").write_text("Changed source", encoding="utf-8")
        with self.assertRaises(subprocess.CalledProcessError):
            self.run_package()

    def test_rejects_invalid_or_mismatched_tag(self):
        for tag in ("v0.1.3", "v0.1.2/other", "v01.1.2", "latest"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                package(self.root, tag, verifier=lambda apk: self.fingerprint)

    def test_rejects_tag_pointing_to_another_commit(self):
        (self.root / "README.md").write_text("Another commit", encoding="utf-8")
        self.git("add", "README.md")
        self.git("commit", "--quiet", "-m", "Another commit")
        with self.assertRaises(ValueError):
            self.run_package()

    def test_rejects_apk_outside_build_directory(self):
        (self.directory.parent / "outside.apk").write_bytes(b"Synthetic APK fixture")
        self.metadata["elements"][0]["outputFile"] = "../outside.apk"
        self.write_metadata()
        with self.assertRaises(ValueError):
            self.run_package()

    def test_rejects_stale_output(self):
        self.run_package()
        with self.assertRaises(ValueError):
            self.run_package()

    def test_parses_one_certificate_and_rejects_missing_or_multiple(self):
        line = f"Signer #1 certificate SHA-256 digest: {self.fingerprint}"
        self.assertEqual(certificate_fingerprint(line), self.fingerprint)
        for text in ("", line + "\n" + line.replace("#1", "#2")):
            with self.assertRaises(ValueError):
                certificate_fingerprint(text)


class SigningRestoreTest(unittest.TestCase):
    def setUp(self):
        parent = Path(__file__).resolve().parents[2] / "app/build/release-tests"
        parent.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(prefix="key-", dir=parent)
        self.root = Path(self.temp.name).resolve()
        assert self.root.is_relative_to(parent.resolve())
        self.addCleanup(self.temp.cleanup)
        self.path = self.root / "synthetic.jks"
        self.environment = {
            "RUNNER_TEMP": str(self.root), "ANDROID_KEYSTORE_PATH": str(self.path),
            "ANDROID_KEYSTORE_BASE64": base64.b64encode(b"Synthetic key fixture").decode(),
            "ANDROID_KEYSTORE_PASSWORD": "fixture", "ANDROID_KEY_ALIAS": "fixture", "ANDROID_KEY_PASSWORD": "fixture",
        }

    def test_restores_exact_key_once(self):
        prepare(self.environment)
        self.assertEqual(self.path.read_bytes(), b"Synthetic key fixture")
        with self.assertRaises(FileExistsError):
            prepare(self.environment)

    def test_rejects_missing_secrets_without_creating_key(self):
        self.environment.pop("ANDROID_KEY_PASSWORD")
        with self.assertRaises(ValueError):
            prepare(self.environment)
        self.assertFalse(self.path.exists())

    def test_rejects_path_outside_runner_temporary_directory(self):
        self.environment["ANDROID_KEYSTORE_PATH"] = str(self.root.parent / "outside.jks")
        with self.assertRaises(ValueError):
            signing_path(self.environment)

    def test_rejects_invalid_base64_before_writing(self):
        self.environment["ANDROID_KEYSTORE_BASE64"] = "invalid$payload"
        with self.assertRaises(ValueError):
            prepare(self.environment)
        self.assertFalse(self.path.exists())


if __name__ == "__main__":
    unittest.main()
