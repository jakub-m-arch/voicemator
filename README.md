# Voicemator

On-device dictation for Android, one tap away in Quick Settings.

Website: [jakub-m-arch.github.io/voicemator](https://jakub-m-arch.github.io/voicemator/)

Pull down the notification shade, tap **Dictate**, speak, tap it again. The transcription is
copied to your clipboard, ready to paste into any app. Or turn on the optional accessibility
service: a small mic button then appears at the keyboard whenever you type; tap it, speak, tap
again, and the text appears right in the field. With FUTO Keyboard or another keyboard that hands
its mic key to the system's voice input, Voicemator can be that voice input. Speech never leaves
your phone.

Voicemator is inspired by [VoiceInk](https://github.com/Beingpax/VoiceInk) for macOS. It uses
[Parakeet Ultra](https://huggingface.co/moondream/parakeet-ultra) by Moondream,
a post-trained version of NVIDIA's
[Parakeet TDT 0.6B v3](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3) covering 25 European
languages. It runs locally through [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), using the
[int8 ONNX conversion](https://huggingface.co/leogaudin/sherpa-onnx-nemo-parakeet-ultra-int8) by
Hugging Face user leogaudin.

## Features

- Quick Settings tile that starts and stops dictation from anywhere
- Home screen widget and a launcher shortcut (long-press the app icon) that do the same
- A short vibration when recording starts and a double one when it stops, and an optional beep
- Optional mic button at the keyboard, shown only while a keyboard is open, that types the text
  straight into the field you are editing; drag it wherever suits your keyboard
- The accessibility shortcut (for example holding both volume keys) toggles dictation too
- Voice input method for keyboards with a voice key, such as FUTO Keyboard: the mic key opens
  Voicemator, and stopping types the text and returns to the keyboard
- Fully offline transcription after a one-time model download (~670 MB), over Wi-Fi only unless
  you allow mobile data; the download is checked against pinned SHA-256 checksums, and the model
  can be deleted in Settings to free the space
- Live preview while you speak: the text appears in the field through the voice input method,
  or in a caption under the recording pill
- Long dictations are transcribed in the background while you speak, so the text is ready right
  after you stop
- Dictionary: teach it names and words it gets wrong ("voice mator" → "Voicemator")
- App profiles, like Power Mode in VoiceInk: for example no capital letter and no full stop at
  the end in messengers, full punctuation in email; each profile can have its own dictionary
  entries and its own final pass setting. Voicemator knows the app when you dictate through the
  voice input method or with the accessibility service on
- Removes hesitation sounds such as "yyy", "eee" and "um" (can be turned off)
- Transcribes audio files of any length: share a voice message from WhatsApp, Messenger or a
  recorder app to Voicemator, open a file with it, or pick one in Settings. The text appears as
  it is transcribed and can be saved as TXT or as SRT subtitles, or added to the history
- Optional final pass: after you stop, the whole recording (up to three minutes) is transcribed
  again as one piece, for the most natural punctuation at the cost of a few seconds
- Result copied to the clipboard, with a notification showing the text
- Optional recording indicator: a pill around the camera with a live waveform, like VoiceInk's
  notch; tap it to stop
- Stop or cancel from the ongoing notification
- History of your dictations, kept on the device, in one of three modes: full (text and
  statistics), statistics only (word counts and times, no text, nothing to read back) or off;
  saved texts can be removed while keeping the statistics
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

## Website

The website lives in [`site/`](site), with the Polish version in `site/pl/`. It is plain HTML and
CSS with no build step. Every push to `main` that changes it runs
[`pages.yml`](.github/workflows/pages.yml), which publishes it to GitHub Pages. To preview it
locally:

```bash
python3 -m http.server --directory site
```

## Testing

`SpeechTranscriberTest` transcribes a short Polish recording from the
[FLEURS](https://huggingface.co/datasets/google/fleurs) dataset on a device or emulator, both as is
and repeated into a 36-second recording that is transcribed in chunks, with a live preview that
must grow while the audio arrives, and with the final pass. Unit tests cover the recording segmenter, the statistics, the
pill layout and the caption:

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
2. Optionally turn on **Voicemator dictation** under Settings → Accessibility to get the mic
   button at the keyboard. If Android says the setting is restricted (it does for apps installed
   from an APK), open App info for Voicemator, tap ⋮ and allow restricted settings first.
3. Optionally turn on **Voicemator voice input** under Settings → System → Keyboard. In FUTO
   Keyboard, also turn on "Disable built-in voice input" in its voice input settings, so its mic
   key opens Voicemator.
4. Download the speech model.
5. Add the **Dictate** tile to Quick Settings.
6. Tap the tile, the keyboard mic button or your keyboard's mic key, speak, and stop. Through
   the voice input method or the accessibility service the text is typed into the field;
   otherwise paste it from the clipboard.

## How it works

| Component | Role |
|---|---|
| `DictationAccessibilityService` | Optional: the keyboard mic button, the accessibility shortcut, and pasting results into the focused field |
| `KeyboardMicButton` | The mic button shown at the keyboard while it is open |
| `DictationTileService` | Quick Settings tile that reflects and toggles the dictation state |
| `DictationTriggerActivity` | Invisible activity that starts recording; Android only allows microphone access from services started while the app is in the foreground |
| `DictationSession` | One dictation: records, transcribes while recording, saves to the history |
| `DictationService` | Foreground service that runs a session in the background and copies the result |
| `VoiceInputMethodService` | Voice input method: runs a session itself and types the result into the field |
| `RecordingOverlay` | Draws the recording pill (`RecordingPillView`) around the camera cutout |
| `AudioRecorder` | Captures 16 kHz mono audio into memory |
| `ChunkedTranscription` | Transcribes finished parts of the recording while you keep talking |
| `RecordingSegmenter` | Cuts long recordings at a pause after about 10 seconds |
| `SpeechTranscriber` | Wraps the sherpa-onnx offline recognizer |
| `TextPostProcessor` | Removes fillers and applies the user's dictionary to every transcription |
| `TranscribeFileActivity` | Share target that decodes an audio file (`AudioFileDecoder`) and transcribes it |
| `HistoryRepository` | Saves dictations in a local Room database, with or without their text, as the history mode says |
| `UsageStatistics` | Derives the numbers, the daily chart and the heatmap from the history |
| `ModelDownloader` | Fetches the model through the system `DownloadManager` |

The model loads while you speak, which hides most of its startup time. Recording never stops on
its own: dictation lasts until you stop it. Once a recording passes about 10 seconds, it is cut
at the quietest moment within the next few seconds, and that part is transcribed in the
background; when you stop, only the last part is left to transcribe. In between, the part still
being recorded is transcribed about once a second for the live preview. With the final pass
turned on in Settings, the whole recording is transcribed once more after you stop and that text
is used instead; recordings over three minutes skip it. The model is released when dictation
finishes to keep memory free.

## Privacy

Voicemator has no analytics, accounts or servers. Audio is recorded into memory, transcribed on
the device and discarded; only the text is placed on the clipboard. With full history (the
default), the text is also saved in the app's private storage on the device, excluded from
backups. Transcriptions of shared audio files are shown but never saved. Under Settings you can
keep statistics only, which stores word counts, lengths and times
but no text, turn history off, remove saved texts, or clear everything. The app connects to the
internet once, to download the speech model from Hugging Face, which sees the request like any
other download.

### Accessibility service

The accessibility service is optional and off until you enable it. It watches window changes only
to know whether a keyboard is open, so it can show the mic button; it receives the accessibility
shortcut; and when a dictation finishes it looks up the focused text field and pastes the
transcription into it. It reads screen content only at that moment, skips password fields, and has no other way to send data
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
