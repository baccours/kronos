# Kronos

Kronos is an Android workout timer application supporting customizable interval training and count-up timing with background service execution and audio cues.

---

## Features

- **Training Modes**:
  - **Timer**: Countdown timer for interval workouts with configurable sets, rounds, work tasks, rest tasks, and rest between sets.
  - **For Time**: Count-up timer with round/lap tracking and an optional time cap.
- **Workout Configuration**: Setup screen for configuring sets, rounds, work/rest task sequences, and rest durations with estimated total workout duration.
- **Config Persistence**: Saves the last used workout setup locally using Jetpack DataStore.
- **Background Execution**: Runs as an Android Foreground Service (`TimerService`) with persistent notifications, notification controls (pause, resume, stop, skip, lap), and a partial wake lock to keep timing active when the screen is locked or the app is in the background.
- **Audio Cues**: Voice announcements from pre-recorded clips (`assets/audio/*.ogg`, played through an `Announcer`) for workout phases, and synthesized PCM audio tones (beeps, buzzers, completion sound) using `AudioTrack`.
- **Custom UI Components**: Built with Jetpack Compose (Material 3), including numeric inputs (`TimeInput`), keypad interface (`Keypad`), and stepper controls (`HybridStepper`).

---

## Tech Stack

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose with Material 3 and Edge-to-Edge display
- **Architecture**: MVVM with Repository pattern and pure Kotlin timing state machine (`TimerEngine`)
- **State Management**: Kotlin Coroutines and `StateFlow`
- **Data Persistence**: Jetpack DataStore (Preferences) with Kotlinx Serialization
- **Background Execution**: Android Foreground Service with system notification integration
- **Audio**: recorded voice clips decoded to PCM (`MediaCodec`) and played through `AudioTrack` (behind the `Announcer` interface) and `AudioTrack` tone synthesis

---

## Project Structure

```
app/src/main/java/com/baccours/kronos/
├── engine/       # TimerEngine clock and workout state machine
├── model/        # Data models (TimerConfig, Task, TimerState, Lap, etc.)
├── repository/   # TimerRepository (live state) & TimerPreferencesRepository (DataStore)
├── audio/        # Announcer interface, ClipAnnouncer (voice clips), ClipCatalog (clip names), AudioFocusController
├── service/      # TimerService (Foreground Service) & TimerAudioManager (tones + delegates speech to the Announcer)
└── ui/           # Setup and Running screens, components (Keypad, TimeInput), Theme
```

---

## Required Permissions

- `POST_NOTIFICATIONS`: Display persistent notification controls on Android 13+ (API 33+).
- `FOREGROUND_SERVICE`: Keep the workout service active in the background.

---

## Voice clips

Spoken cues are described by the engine as `Speech` values (`Cue.Speak(Speech.SetNumber(2), Speech.Work(4))`) and
played by an `Announcer`. `ClipAnnouncer` maps them to `.ogg` files in `app/src/main/assets/audio/` (names in
`ClipCatalog`). Missing clips are never fatal: an announcement with a missing clip is skipped and logged
(`ClipAnnouncer` tag), while beeps and buzzers keep working.

To use another voice (e.g. text-to-speech, or clips with a TTS fallback), implement `Announcer` and pass it to
`TimerAudioManager(context) { focus -> MyAnnouncer(..., focus) }`. `say()` returns `false` when it could not
announce something, which is the hook for a fallback. `prepare()` is an optional hint and can be ignored.

**Loading.** Decoding a clip is slow (about 250 ms on some devices), so it is done ahead of time:
- `ClipLibrary` holds the decoded clips (mono PCM, 48 kHz) for the life of the app process, so a clip is decoded
  at most once, not once per workout.
- `MainActivity` decodes the fixed phrases (`ClipCatalog.PHRASES`) when the app opens, so "Get ready" is instant.
- At Start, `TimerService` calls `announcer.prepare(engine.speechNeeded())`: the clips this workout can say are
  decoded in the order they are first needed, during the "get ready" countdown.
- A clip still missing when needed is decoded on demand (a late cue, never a lost one).

**Files** (66 with `MAX_DEDICATED = 10`; `36 + 3 x MAX_DEDICATED`), all lower case, `.ogg` (Vorbis or Opus), with
leading/trailing silence trimmed since clips are joined into one gapless announcement. Mono is enough.

| Said | Files |
|---|---|
| Phrases | `get_ready` `go` `rest` `rest_between_sets` `halfway` `ten_seconds` `well_done` |
| "Work 1" to "Work 10" | `work01` `work02` ... `work10` |
| "Set 2" to "Set 10" | `set02` `set03` ... `set10` (Set 1 is never spoken) |
| "Round 2" to "Round 10" | `round02` `round03` ... `round10` (Round 1 is never spoken) |
| Labels, above 10 | `work` `set` `round` |
| Number words, above 10 | `one` `two` ... `twenty`, then `thirty` `forty` `fifty` `sixty` `seventy` `eighty` `ninety`, and `hundred` |

Above 10 the label is followed by number words: "Work 11" is `work` `eleven`, "Work 25" is `work` `twenty` `five`,
"Set 40" is `set` `forty`, "Round 100" is `round` `one` `hundred`, "Work 125" is `work` `one` `hundred` `twenty` `five`.
Numbers go up to 999; above that only the label is said.
