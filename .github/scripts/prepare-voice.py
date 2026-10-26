"""Fetch the Apache-2.0 Russian Vosk model into the APK, not onto a user server."""
from pathlib import Path
import hashlib
import shutil
import time
import urllib.request
import zipfile

url = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"
mirror = "https://huggingface.co/rhasspy/vosk-models/resolve/main/ru/vosk-model-small-ru-0.22.zip"
expected_sha256 = "961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4"
target = Path("app/src/main/assets/voice-ru-0.22.zip")
target.parent.mkdir(parents=True, exist_ok=True)
license_file = target.parent / "ASR-LICENSE.txt"
with urllib.request.urlopen("https://www.apache.org/licenses/LICENSE-2.0.txt", timeout=30) as response:
    license_file.write_bytes(response.read())
(target.parent / "ASR-NOTICE.txt").write_text(Path("THIRD_PARTY_NOTICES.md").read_text(encoding="utf-8"), encoding="utf-8")

def valid(path):
    if not path.is_file() or not 30_000_000 <= path.stat().st_size <= 80_000_000:
        return False
    if hashlib.sha256(path.read_bytes()).hexdigest() != expected_sha256:
        return False
    try:
        with zipfile.ZipFile(path) as archive:
            if sum(entry.file_size for entry in archive.infolist()) > 200_000_000:
                return False
            if any(".." in Path(entry.filename).parts or entry.filename.startswith("/") for entry in archive.infolist()):
                return False
            return "vosk-model-small-ru-0.22/am/final.mdl" in archive.namelist() and archive.testzip() is None
    except (OSError, zipfile.BadZipFile):
        return False

if not valid(target):
    temporary = target.with_suffix(".part")
    for attempt, source in enumerate((url, mirror, mirror)):
        try:
            request = urllib.request.Request(source, headers={"User-Agent": "Sadachi-build/0.6"})
            with urllib.request.urlopen(request, timeout=30 if source == url else 120) as response, temporary.open("wb") as output:
                shutil.copyfileobj(response, output)
            if not valid(temporary):
                raise RuntimeError("Incomplete Russian ASR model")
            temporary.replace(target)
            break
        except Exception:
            if attempt == 2:
                raise
            time.sleep(5)

print("Russian offline model:", target.stat().st_size, "bytes")
print("SHA-256:", hashlib.sha256(target.read_bytes()).hexdigest())
