"""Publish only after tests and APK assembly have succeeded."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

gradle = Path("app/build.gradle").read_text(encoding="utf-8")
code = int(re.search(r"versionCode\s+(\d+)", gradle).group(1))
version = re.search(r"versionName\s+'([0-9.]+)'", gradle).group(1)
tag = f"v{version}"
repository = os.environ["GITHUB_REPOSITORY"]
directory = Path("release-files")
directory.mkdir(exist_ok=True)
apk = directory / f"Sadachi-{code}.apk"
shutil.copyfile(directory / "Sadachi-new.apk", apk)
legacy = directory / f"Sadachi-legacy-{code}.apk"
shutil.copyfile(directory / "Sadachi-legacy.apk", legacy)
metadata = {
    "versionCode": code,
    "versionName": version,
    "apkUrl": f"https://github.com/{repository}/releases/download/{tag}/{apk.name}",
    "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
}
manifest = directory / "update.json"
new_manifest = directory / "update-com.ilkinsadachi.json"
new_manifest.write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
legacy_metadata = dict(metadata, apkUrl=f"https://github.com/{repository}/releases/download/{tag}/{legacy.name}", sha256=hashlib.sha256(legacy.read_bytes()).hexdigest())
manifest.write_text(json.dumps(legacy_metadata, ensure_ascii=False, indent=2), encoding="utf-8")
exists = subprocess.run(["gh", "release", "view", tag], capture_output=True).returncode == 0
assets = [str(apk), str(legacy), str(manifest), str(new_manifest)]
if exists:
    subprocess.run(["gh", "release", "upload", tag, *assets, "--clobber"], check=True)
else:
    subprocess.run([
        "gh", "release", "create", tag, *assets,
        "--target", os.environ["GITHUB_SHA"], "--latest",
        "--title", f"Sadachi {version}",
        "--notes", Path("CHANGELOG.md").read_text(encoding="utf-8").split("\n# ",1)[0]
        + "\n\nОсновное приложение: com.ilkinsadachi. Файл legacy обновляет старое приложение для переноса данных. Инструкции — в README. Установка подтверждается Android.",
    ], check=True)
