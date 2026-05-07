# Kronos - Professional Gym Training Timer

**Kronos** is a high-performance, professional-grade workout timer designed for gym environments. Built with a focus on visibility, persistence, and ease of use, it provides athletes with the tools to manage complex interval training (HIIT, Tabata, AMRAP) without distraction.

---

## 🚀 Key Features

- **Dual Training Modes**:
  - **Timer**: Precise countdown for interval-based training.
  - **For Time**: Count-up tracking with round-split logging for performance analysis.
- **Dynamic Workout Builder**: An interactive setup screen allowing for deep customization of sets, rounds, and work/rest sequences with real-time total duration estimation.
- **High-Visibility Interface**: Optimized for "sweaty environments" with large (100.sp) readouts and high-contrast Material 3 design.
- **Persistent Foreground Service**: Ensures timing accuracy remains 100% precise even when the app is in the background or the screen is locked.
- **Intelligent Audio Cues**:
  - Text-to-Speech (TTS) announcements for workout items and milestones.
  - System-level beeps for final countdowns and start buzzers.
- **Custom UI Components**:
  - **HybridStepper**: Integer stepper with inline keyboard support.
  - **TimeInput**: Duration-optimized input with MM:SS logic.

---

## 🛠 Tech Stack

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: MVVM (Model-View-ViewModel) with a centralized Repository pattern for Service-UI synchronization.
- **State Management**: Kotlin Flow (StateFlow) and Coroutines.
- **Background Execution**: Android Foreground Service with Android 14+ compatibility.
- **Design System**: Material 3 with Full Edge-to-Edge display and Dynamic Color support.
- **Audio Logic**: Android `TextToSpeech` and `ToneGenerator`.

---

## 🏗 Architecture & Standards

Kronos is built following modern Android Development standards:
- **Clean Architecture**: Separation of concerns between UI, Business Logic (Service), and Data (Repository).
- **Single Source of Truth**: `TimerRepository` acts as the definitive state holder, ensuring the UI and Background Service are always perfectly synchronized.
- **Material 3 Expressive**: Adherence to the latest design guidelines, including larger corner radii, vibrant color palettes, and expressive motion.
- **WCAG AA Compliance**: High-visibility typography and accessible touch targets (min 48dp).

---

## ⚙️ Setup Instructions

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 35
- Kotlin 2.x

### Build Steps
1. Clone the repository.
2. Open the project in Android Studio.
3. Sync Project with Gradle Files.
4. Run the `:app` module on a device or emulator (Android 8.0+ recommended for full feature support).

### Permissions
The app requires the following permissions for full functionality:
- `POST_NOTIFICATIONS`: For the persistent timer controls (Android 13+).
- `FOREGROUND_SERVICE`: For background persistence.

---

## 🎨 Design Aesthetic
Kronos utilizes a **vibrant, energetic color scheme** (Deep Orange and Amber) designed to reflect the intensity of a high-performance workout. The UI is fully responsive, supporting both portrait and landscape orientations with edge-to-edge rendering.

---

*Developed with ❤️ for athletes who demand precision.*
