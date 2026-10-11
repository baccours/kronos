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
- **Audio Cues**: Voice announcements using Text-to-Speech (TTS) for workout phases and synthesized PCM audio tones (beeps, buzzers, completion sound) using `AudioTrack`.
- **Custom UI Components**: Built with Jetpack Compose (Material 3), including numeric inputs (`TimeInput`), keypad interface (`Keypad`), and stepper controls (`HybridStepper`).

---

## Tech Stack

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose with Material 3 and Edge-to-Edge display
- **Architecture**: MVVM with Repository pattern and pure Kotlin timing state machine (`TimerEngine`)
- **State Management**: Kotlin Coroutines and `StateFlow`
- **Data Persistence**: Jetpack DataStore (Preferences) with Kotlinx Serialization
- **Background Execution**: Android Foreground Service with system notification integration
- **Audio**: Android `TextToSpeech` and `AudioTrack` tone synthesis

---

## Project Structure

```
app/src/main/java/com/baccours/kronos/
├── engine/       # TimerEngine clock and workout state machine
├── model/        # Data models (TimerConfig, Task, TimerState, Lap, etc.)
├── repository/   # TimerRepository (live state) & TimerPreferencesRepository (DataStore)
├── service/      # TimerService (Foreground Service) & TimerAudioManager (TTS & Tones)
└── ui/           # Setup and Running screens, components (Keypad, TimeInput), Theme
```

---

## Required Permissions

- `POST_NOTIFICATIONS`: Display persistent notification controls on Android 13+ (API 33+).
- `FOREGROUND_SERVICE`: Keep the workout service active in the background.
