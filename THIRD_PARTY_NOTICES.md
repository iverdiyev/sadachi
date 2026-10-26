# Offline speech recognition

- Vosk Android 0.3.75 — Alpha Cephei, Apache License 2.0. https://github.com/alphacep/vosk-api
- `vosk-model-small-ru-0.22` — Alpha Cephei, Apache License 2.0. https://alphacephei.com/vosk/models
- Model archive SHA-256: `961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4`. A byte-identical, hash-checked Rhasspy/Hugging Face mirror is used if the original download is unavailable.
- Java Native Access 5.18.1 — Apache License 2.0 / LGPL 2.1 or later. https://github.com/java-native-access/jna

Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0

The Russian ASR model is bundled during the GitHub build. It is a speech-recognition model, not a chat LLM. Audio is processed on the device and is not uploaded or stored as a recording. Recognition accuracy varies with noise, pronunciation and microphone quality.
