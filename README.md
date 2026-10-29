# Voicemator

On-device dictation for Android, one tap away in Quick Settings.

Pull down the notification shade, tap **Dictate**, speak, tap it again. The transcription is
copied to your clipboard, ready to paste into any app. Or turn on the optional floating button:
tap it while typing anywhere, speak, tap again, and the text appears right in the field. Speech
never leaves your phone.

Voicemator is inspired by [VoiceInk](https://github.com/Beingpax/VoiceInk) for macOS. It uses
[Parakeet Ultra](https://huggingface.co/moondream/parakeet-ultra) by Moondream,
a post-trained version of NVIDIA's
[Parakeet TDT 0.6B v3](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3) covering 25 European
languages. It runs locally through [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), using the
[int8 ONNX conversion](https://huggingface.co/leogaudin/sherpa-onnx-nemo-parakeet-ultra-int8) by
Hugging Face user leogaudin.

## Features

- Quick Settings tile that starts and stops dictation from anywhere
- Optional floating accessibility button that toggles dictation and types the text straight
  into the field you are editing
- Fully offline transcription after a one-time model download (~670 MB)
- Long dictations are transcribed in the background while you speak, so the text is ready right
  after you stop
- Result copied to the clipboard, with a notification showing the text
- Optional recording indicator: a pill around the camera with a live waveform, like VoiceInk's
  notch; tap it to stop
- Stop or cancel from the ongoing notification
- History of your dictations, kept on the device; it can be turned off and cleared
- Statistics: words today and in total, a streak, speaking pace, an estimate of the time saved
  compared with typing, words per day for the last two weeks and a six-month activity heatmap

## Requirements

- Android 14 or newer, 64-bit ARM
- About 1 GB of free storage and a recent phone; the model needs roughly 1 GB of RAM while
  transcribing

## Building

You need JDK 17 and the Android SDK (platform 36). Point Gradle at the SDK with
`local.properties` or the `ANDROID_HOME` environment variable, then:

```bash
./gradlew assembleDebug
```

The first build downloads the sherpa-onnx Android library from its GitHub release and checks it
against a pinned SHA-256 checksum. To install on a connected phone:

```bash
./gradlew installDebug
```

## Releases

Signed APKs are built by GitHub Actions. Every push and pull request runs
[`ci.yml`](.github/workflows/ci.yml), which checks licences, runs lint and uploads a debug APK.
Pushing a tag such as `v0.1.0` runs [`release.yml`](.github/workflows/release.yml), which builds a
signed release APK and publishes it with its SHA-256 checksum on the GitHub Releases page. The tag
must match `versionName` in `app/build.gradle.kts`.

Release signing needs four repository secrets:

| Secret | Value |
|---|---|
| `VOICEMATOR_KEYSTORE_BASE64` | The release keystore, base64-encoded |
| `VOICEMATOR_KEYSTORE_PASSWORD` | Keystore password |
| `VOICEMATOR_KEY_ALIAS` | Key alias |
| `VOICEMATOR_KEY_PASSWORD` | Key password |

Locally, set the same names (with `VOICEMATOR_KEYSTORE_FILE` pointing to the keystore instead of
the base64 value) to build a signed release with `./gradlew assembleRelease`.

## Testing

`SpeechTranscriberTest` transcribes a short Polish recording from the
[FLEURS](https://huggingface.co/datasets/google/fleurs) dataset on a device or emulator, both as is
and repeated into a 36-second recording that is transcribed in chunks. Unit tests cover the
recording segmenter and the pill layout:

```bash
./gradlew testDebugUnitTest
```

The device tests are skipped unless the model is installed, so download it in the app first,
then run:

```bash
./gradlew connectedDebugAndroidTest
```

## Usage

1. Open Voicemator and grant microphone and notification access. Optionally allow it to display
   over other apps to get the recording indicator.
2. Optionally turn on **Voicemator dictation** under Settings → Accessibility and pick the
   floating accessibility button as its shortcut, to dictate straight into text fields.
3. Download the speech model.
4. Add the **Dictate** tile to Quick Settings.
5. Tap the tile or the floating button, speak, and tap it again. With the accessibility service
   on, the text is typed into the focused field; otherwise paste it from the clipboard.

## How it works

| Component | Role |
|---|---|
| `DictationAccessibilityService` | Optional: the floating button, and pasting results into the focused field |
| `DictationTileService` | Quick Settings tile that reflects and toggles the dictation state |
| `DictationTriggerActivity` | Invisible activity that starts recording; Android only allows microphone access from services started while the app is in the foreground |
| `DictationService` | Foreground service that records, transcribes and copies the result |
| `RecordingOverlay` | Draws the recording pill (`RecordingPillView`) around the camera cutout |
| `AudioRecorder` | Captures 16 kHz mono audio into memory |
| `ChunkedTranscription` | Transcribes finished parts of the recording while you keep talking |
| `RecordingSegmenter` | Cuts long recordings at a pause after about 20 seconds |
| `SpeechTranscriber` | Wraps the sherpa-onnx offline recognizer |
| `HistoryRepository` | Saves dictations in a local Room database, unless history is turned off |
| `UsageStatistics` | Derives the numbers, the daily chart and the heatmap from the history |
| `ModelDownloader` | Fetches the model through the system `DownloadManager` |

The model loads while you speak, which hides most of its startup time. Recording never stops on
its own: dictation lasts until you tap the tile, the pill or Stop in the notification. Once a
recording passes about 20 seconds, it is cut at the quietest moment within the next few seconds,
and that part is transcribed in the background; when you stop, only the last part is left to
transcribe. The model is released when dictation finishes to keep memory free.

## Privacy

Voicemator has no analytics, accounts or servers. Audio is recorded into memory, transcribed on
the device and discarded; only the text is placed on the clipboard. With history on (the
default), the text is also saved in the app's private storage on the device, excluded from
backups; turn it off or clear it under Settings. The app connects to the
internet once, to download the speech model from Hugging Face, which sees the request like any
other download.

### Accessibility service

The accessibility service is optional and off until you enable it. It does two things: it
receives clicks on the system accessibility button, and when a dictation finishes it looks up the
focused text field and pastes the transcription into it. It does not observe what you do; it reads
the screen only at that moment, skips password fields, and has no other way to send data
anywhere, as the app's only network use is downloading the model.

## License

Voicemator is licensed under the [Apache License 2.0](LICENSE); see also [NOTICE](NOTICE).

The app includes third-party software under Apache-2.0, MIT, BSD and MPL-2.0 licences. Every
component, its copyright holders and its full licence text are listed in the app under
**Open source licences** and in
[`ThirdPartyNotices.kt`](app/src/main/java/io/github/jakub_m_arch/voicemator/licenses/ThirdPartyNotices.kt).
The `checkThirdPartyNotices` Gradle task fails the build when a dependency is not credited there.

The speech model is not distributed with the app. Parakeet TDT 0.6B v3 (NVIDIA), Parakeet Ultra
(Moondream) and the ONNX conversion are licensed under
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). NVIDIA and Moondream have not reviewed
or endorsed Voicemator; product names belong to their owners.
