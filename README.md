<p align="center">
  <img src="https://github.com/SeveriT/Overclock/blob/master/app/src/main/res/mipmap-hdpi/app_logo_foreground.png?raw=true" width="192" />
</p>
<h1 align="center">Overclock</h1>
<p align="center">A personal fitness companion for Android</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" />
  <img src="https://img.shields.io/badge/API-Strava-FC4C02?style=flat-square&logo=strava&logoColor=white" />
  <img src="https://img.shields.io/badge/API-Spotify-1DB954?style=flat-square&logo=spotify&logoColor=white" />
  <img src="https://img.shields.io/badge/API-Gemini-886FBF?style=flat-square&logo=googlegemini&logoColor=white" />
  <img src="https://img.shields.io/badge/Firebase-Remote%20Config-FFCA28?style=flat-square&logo=firebase&logoColor=black" />
</p>

---

Overclock is an Android app built for athletes who want full control over their training data. Log workouts, track body weight, monitor Strava activities, generate AI-powered training plans, and time your sessions, all from a single fast interface.

## Features

- **Workout logging** — log sets, reps and weight with auto-fill from history and personal best tracking
- **AI workout assistant** — generate one-off workouts and full training programs using Google Gemini, with quick prompts and custom requests
- **Weight tracking** — smooth trend chart with BMI, height profile, and historical data management
- **Strava integration** — view your activity calendar, sync profile data, and upload workouts directly from the timer
- **Workout timer** — animated ring timer with lap tracking, persistent notification, and automatic recovery if the app is killed
- **Sessions** — browse, add, and edit saved workout sessions with duration, activity type, date, and notes
- **Music widget** — integrated Spotify controller with wavy progress bar, album art, and swipe gesture playback controls
- **Volume stats** — total volume lifted broken down per exercise with animated progress bars and volume tracking
- **Notes** — dedicated training journal for freeform notes with date-based organization
- **Weekly summary** — overview of recent workouts, weight trends, and activity streaks from both Strava and local sessions
- **Calendar** — activity history with dot indicators combining Strava activities and local sessions, works without Strava
- **Customization** — fully dynamic Material 3 theme with user-adjustable RGB accent colors and edge-to-edge display
- **Cloud sync & backup** — automated Google Drive backups via WorkManager and manual local backup/restore options
- **Premium subscription** — Google Play Billing with monthly subscription gating Drive backups, Strava sync, and AI assistant

## Built with

- Kotlin + Jetpack Compose
- Room for local persistence
- ViewModel + StateFlow
- Navigation Compose with horizontal swipe gestures
- Google Gemini API for AI workout generation
- Google Play Billing for subscriptions
- Firebase Remote Config for premium user management
- Strava OAuth2 + REST API
- Spotify MediaSession integration
- Google Drive API + WorkManager
- Material 3 with dynamic color customization
