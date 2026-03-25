# 🥊 RoundCutter

A single-purpose Android app for boxers (and anyone who records long sessions) who need to extract multiple clips from a single video. Record your 40-minute sparring session, mark individual rounds, and export them all as separate files — in seconds.

## Why?

Most phone video editors assume you want to produce one output from one input. If you need to extract 8 separate rounds from a session, you'd have to open the editor 8 times, trim to each round, and export one by one. RoundCutter does this in a single workflow.

## Features

- **Visual scrubbing** — drag your finger across the timeline and see frames update in real-time
- **Adaptive fine-scrub** — slow your finger down and the app automatically switches to 10× precision mode for frame-accurate positioning
- **Quick jump controls** — ±1 frame, ±1 second, and ±3 minutes buttons for fast navigation
- **Mark & collect** — tap SET IN at the start of a round, SET OUT at the end; clips are auto-named "Round 1", "Round 2", etc.
- **Batch export** — export all marked clips at once using FFmpeg stream copy (`-c copy`) — near-instant, no re-encoding
- **Portrait & landscape** — fully adaptive layout for both orientations
- **Dark theme** — charcoal + orange accent, easy on the eyes

## How It Works

1. Open the app → tap **Select Video**
2. Scrub through the timeline to find the start of a round
3. Tap **SET IN** to mark the start point
4. Use **3m▶** to jump near the end, then fine-tune
5. Tap **SET OUT** to mark the end — clip is added to the list
6. Repeat for all rounds
7. Tap **Export All** — clips are saved to `Movies/RoundCutter/` on your device

### Scrubbing Modes

| Mode | Trigger | Behavior |
|------|---------|----------|
| **Normal scrub** | Drag at normal speed | Full-speed timeline navigation, keyframe-based seeking for responsiveness |
| **Fine scrub** | Slow your finger down | 10× precision — tiny movements = tiny time changes. Green indicator appears |
| **On release** | Lift finger | Final frame-accurate seek to exact position |

## Screenshots

*Coming soon*

## Tech Stack

| Component | Choice |
|-----------|--------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Video player | AndroidX Media3 (ExoPlayer) |
| Export engine | FFmpeg-Kit (`io.github.maitrungduc1410:ffmpeg-kit-min:6.0.1`) |
| Architecture | Single-Activity, ViewModel |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |

## Project Structure

```
app/src/main/java/com/roundcutter/
├── MainActivity.kt                  # Entry point, sets up Compose theme
├── model/
│   └── Clip.kt                      # Data class: id, name, startMs, endMs
├── ui/
│   ├── MainScreen.kt                # Main UI: player, scrub bar, controls, clip list, export
│   └── theme/
│       ├── Color.kt                 # Charcoal palette + orange accent
│       └── Theme.kt                 # Dark Material3 theme
└── viewmodel/
    └── MainViewModel.kt             # State management, FFmpeg export logic

app/src/main/res/
├── mipmap-*/                        # Launcher icons (all densities)
├── mipmap-anydpi-v26/               # Adaptive icon XML
├── values/
│   ├── strings.xml
│   ├── themes.xml                   # Base Android theme
│   └── ic_launcher_colors.xml       # Adaptive icon background color
└── AndroidManifest.xml              # Permissions, activity declaration
```

## Building

### Prerequisites

- Android Studio (Panda 2 or later)
- Android SDK 35
- JDK 17+ (bundled with Android Studio)

### Build & Run

1. Clone the repo
2. Open in Android Studio
3. Connect an Android device with USB debugging enabled
4. Click ▶ Run

### Generate APK

```
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Export Details

- Uses FFmpeg stream copy (`-c copy -avoid_negative_ts make_zero`) for near-instant extraction
- No re-encoding means no quality loss and minimal processing time (~2-3 seconds per clip)
- Clips are saved to `Movies/RoundCutter/` and registered with the media scanner (visible in gallery)
- Trade-off: cuts align to nearest keyframe (typically within 0.5s) — perfectly acceptable for round extraction

## License

MIT
