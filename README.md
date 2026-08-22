# UhhNo

An Android app that listens while you talk and flags filler words ("um",
"uh", "like", "you know", etc.) in real time — a live speech coach for
practicing talks, interviews, or meetings. All speech recognition runs
on-device (offline), so no audio leaves the phone.

- Package / applicationId: `com.uhhno.app`
- minSdk 24, targetSdk/compileSdk 34
- Kotlin, View-based UI (ViewBinding, no Compose)

## Architecture

Speech recognition uses [Vosk](https://alphacephei.com/vosk/) (`vosk-android`
via the `alphacephei` Maven repo declared in `settings.gradle`), an offline
Kotlin/Java speech engine. There is no Compose or MVVM layer — it's a single
Activity coordinating a handful of small collaborators:

- **`MainActivity`** — owns the session lifecycle and UI. Wires the mic
  button, settings sheet, and clear button; renders the running filler count,
  session timer, live partial-transcript text, and the scrolling filler log;
  drives the red flash + counter bounce feedback and a vibration on each
  detected filler.
- **`ModelLoader`** — downloads and unzips the Vosk model
  (`vosk-model-small-en-us-0.15`) from alphacephei.com on first run, caches it
  under `filesDir`, and re-downloads if a previous extraction left a corrupt
  directory. Reports progress via callback so `MainActivity` can show a
  "Downloading… N%" / "Extracting…" status.
- **`SpeechService`** — runs a dedicated capture thread that reads 100 ms
  chunks from `AudioRecord` at 16 kHz mono, feeds them to a Vosk `Recognizer`,
  and posts partial/final results back to the `SpeechListener` (implemented by
  `MainActivity`) on the main thread. It also does its own RMS-based
  voice-activity detection: if the mic is "voiced" (RMS above threshold) but
  Vosk isn't producing words for longer than `hesitationMs`, it synthesizes an
  `"uh"` partial result itself — this is what catches wordless hesitation
  sounds that Vosk's language model would otherwise swallow.
- **`FillerDetector`** — pure word-list/regex matcher, no Android
  dependencies. Two independent checks:
  - `isSingleWordFiller` — an exact set (`uh`, `um`, `hmm`, `like`, …) plus
    regexes (`uh+`, `ah+`, `um+`, `hm+`, `er+`) to catch stretched spellings
    and Vosk transcription variants (Vosk often renders a drawn-out "uhh" as
    "ah"/"ahh").
  - `isTwoWordFiller` — a fixed bigram set (`"you know"`, `"i mean"`, `"kind
    of"`, `"sort of"`, `"you see"`, `"i guess"`).
- **`MainActivity.onPartialResult`** — the actual scanning logic. Vosk resends
  the whole partial transcript on every update (not just new words), so this
  diffs the new word list against the previous one to find where they
  diverge, then only scans newly-appeared words (and the single bigram
  straddling the diverge point) through `FillerDetector`. This is what keeps
  a repeated word from being counted multiple times as the partial result
  grows.
- **`SpeechSettings`** — thin `SharedPreferences` wrapper for two user-tunable
  values: mic sensitivity (0–10, mapped to an RMS `threshold` used by
  `SpeechService`'s VAD) and `hesitationMs` (300–800 ms, how long a
  wordless-voiced stretch must last before it's treated as a hesitation
  filler). Edited from `SettingsSheet`, a bottom sheet with two sliders.
- **`AudioRecorder`** — optional, independent `MediaRecorder`-based session
  recording to `.m4a` (toggled by the "record" switch in the UI); unrelated to
  the Vosk capture path, which uses its own raw `AudioRecord` stream.
- **`FillerEntry`** / **`FillerLogAdapter`** — data class + `RecyclerView`
  adapter backing the on-screen scrolling log of detected fillers
  (word + timestamp), newest at the bottom.

### Data flow

```
mic → AudioRecord (SpeechService capture thread, 16kHz/100ms chunks)
    → Vosk Recognizer → partial/final JSON
    → SpeechService.SpeechListener callbacks (main thread)
    → MainActivity.onPartialResult: diff against prevWordList
    → FillerDetector.isSingleWordFiller / isTwoWordFiller
    → onFillerDetected: count++, log entry, flash/bounce/vibrate
```

## Build & run

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

or `./gradlew installDebug` as one step. Requires `RECORD_AUDIO` (for speech
detection), `VIBRATE` (feedback), and `INTERNET` (one-time Vosk model
download, ~40 MB, cached after first launch) permissions.

Per the repo-wide Android conventions, connect over wireless `adb` (not USB)
and use `adb logcat --pid=$(adb shell pidof -s com.uhhno.app) -d` to read app
logs; `SpeechService` logs under tag `UhhNo`.

## Gotchas

- The Vosk model isn't bundled in the APK — first launch needs network
  access to fetch and unzip it. `ModelLoader.isReady()` checks whether it's
  already on disk to skip re-downloading on subsequent launches.
- `stuff` at the repo root is a stray local logcat dump, not project source —
  it's gitignored and can be ignored/deleted.
- The hesitation-detection heuristic in `SpeechService` (synthesizing an
  `"uh"` when voiced-but-wordless audio exceeds `hesitationMs`) is what
  catches nonverbal "uhhh" sounds; pure word-list matching in
  `FillerDetector` alone would miss these since Vosk's language model tends
  not to transcribe them as words at all.
