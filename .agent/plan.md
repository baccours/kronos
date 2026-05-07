# Project Plan

Kronos - A professional Gym Training Timer app. 
Features: 
- Two modes: Timer and For Time.
- Custom components: HybridStepper (integer stepper with inline editor) and TimeInput (duration input with MM:SS logic).
- Setup Screen: Configure sets, rounds, and a sequence of Work/Rest items. Dynamic list with independent numbering. Total workout duration calculation.
- Running Timer Screen: Countdown/Count-up displays, total remaining time, round counter for For Time mode.
- Foreground Service: Maintains timer accuracy in background, persistent notification.
- Audio Cues: Beeps, buzzers, and Text-to-Speech announcements for starts, 10s remaining, halfway point, and item changes.
- Material 3 Design: Full Edge-to-Edge, vibrant/energetic colors.
- Robust state management: Survives rotation, handles foreground/background transitions correctly.
- Permissions: Handles Notification permission for Android 13+.

## Project Brief

# Kronos - Project Brief

## Features
1. **Dual Training Modes**: Supports both "Timer" (countdown) and "For Time" (count-up) workout structures to accommodate various training styles like HIIT, Tabata, or AMRAP.
2. **Dynamic Workout Builder**: An interactive setup screen for configuring sets, rounds, and sequences of Work/Rest intervals with real-time total duration calculation.
3. **High-Visibility Timer Interface**: A running screen optimized for gym environments featuring large readouts, round counters, and clear progress indicators.
4. **Persistent Foreground Service**: A dedicated background service that ensures timing accuracy and provides workout controls via a persistent notification when the app is not in focus.
5. **Intelligent Audio Cues**: Automated audio feedback using beeps and Text-to-Speech (TTS) announcements for interval transitions, halfway points, and countdowns.


## High-Level Technical Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3 (Edge-to-Edge display)
- **Concurrency**: Kotlin Coroutines and Flow for precise timing and state management.
- **Background Execution**: Android Foreground Service for workout continuity and notification integration.
- **Code Generation**: KSP (Kotlin Symbol Processing).
- **Audio**: Android Text-to-Speech (TTS) and MediaPlayer APIs.

## Implementation Steps
**Total Duration:** 53m 49s

### Task_1_FoundationAndSetupUI: Initialize the Material 3 theme and define workout data models. Implement the Workout Builder (Setup) screen featuring the custom HybridStepper and TimeInput components, allowing users to configure sets, rounds, and work/rest sequences.
- **Status:** COMPLETED
- **Updates:** Initialized the Material 3 theme and defined workout data models (WorkoutConfig, WorkoutItem, WorkoutMode).
- **Acceptance Criteria:**
  - Project builds successfully with Material 3 theme.
  - Data models for WorkoutConfig and WorkoutItem are defined.
  - Setup screen allows adding/removing items and displays total duration.
  - Custom components (HybridStepper, TimeInput) are functional.
- **Duration:** 25m 17s

### Task_2_TimerEngineAndService: Implement a Foreground Service to manage the workout timer logic (countdown and count-up) ensuring it persists in the background. Integrate a persistent notification with playback controls and real-time state updates.
- **Status:** COMPLETED
- **Updates:** Implemented TimerRepository as a singleton to share workout state between the service and UI.
- **Acceptance Criteria:**
  - Foreground service maintains timer accuracy when app is backgrounded.
  - Persistent notification displays current workout status and controls.
  - Notification permission is handled for Android 13+.
  - App does not crash during service lifecycle transitions.
- **Duration:** 10m 10s

### Task_3_ActiveWorkoutUIAndAudio: Build the Running Timer screen with high-visibility readouts for time and rounds. Integrate the Audio Cues system using Text-to-Speech (TTS) and beeps for transitions and interval milestones.
- **Status:** COMPLETED
- **Updates:** Built the Running Timer screen with high-visibility large readouts (100.sp) and clear progress indicators for sets and rounds.
- **Acceptance Criteria:**
  - Running screen displays large, readable timer and progress indicators.
  - TTS announcements play for item changes and halfway points.
  - Audio beeps occur during countdowns and starts.
  - Screen orientation changes do not interrupt the timer state.
- **Duration:** 6m 2s

### Task_4_FinalPolishAndVerification: Implement full Edge-to-Edge display and refine the vibrant color scheme. Create an adaptive app icon and perform a final verification of all features to ensure stability and alignment with the requirements.
- **Status:** COMPLETED
- **Updates:** Refined the Material 3 theme with a vibrant, energetic color scheme (Deep Orange/Amber).
Implemented full Edge-to-Edge display throughout the app by configuring transparent system bars and handling window insets in all screens.
Created an adaptive app icon that matches the Kronos identity.
Verified stability and alignment with Material 3 guidelines.
Performed a final sweep of all features (Dual modes, HybridStepper, TimeInput, Audio Cues, Foreground Service) to ensure they meet the full build specification.
- **Acceptance Criteria:**
  - App icon is adaptive and matches the core function.
  - Full Edge-to-Edge display is implemented throughout the app.
  - Application is stable with no crashes during a full workout cycle.
  - UI aligns with Material 3 guidelines and project aesthetic.
- **Duration:** 12m 20s

