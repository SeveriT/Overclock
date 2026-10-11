<p align="center">
  <img src="https://github.com/SeveriT/Overclock/blob/master/app/src/main/res/mipmap-hdpi/app_logo_foreground.png?raw=true" width="192" />
</p>
<h1 align="center">Overclock</h1>
<p align="center">A personal gym workout tracker for Android</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" />
  <img src="https://img.shields.io/badge/API-Spotify-1DB954?style=flat-square&logo=spotify&logoColor=white" />
  <img src="https://img.shields.io/badge/Firebase-Remote%20Config-FFCA28?style=flat-square&logo=firebase&logoColor=black" />
</p>

---

Overclock is an Android app for lifters who want full control over their training data. Log workouts, track body weight, time your sessions, and keep an eye on your activity streak, all from a single fast interface. Everything is stored locally on your device.

## Features

- **Workout logging** — log sets, reps and weight with auto-fill from history, personal best tracking, and confetti celebrations when you hit a new PB
- **Workout timer** — animated ring timer with lap tracking, persistent notification, and automatic recovery if the app is killed. Save each session locally with a name, activity type, and date
- **Schedule** — an editable workout checklist you fill in and tick off as you train, with a manual reset button
- **Weight tracking** — smooth trend chart with RPI and BMI from your height, plus a recency-weighted prediction that projects your weight to a date you choose, drawn right on the chart. Configurable axis range
- **Step counter** — daily step tracking via the device's built-in sensor, with an editable goal, goal-reached notification, and a 7-day bar chart on the Summary page
- **Weekly summary** — activity count, streaks, recent sessions and last-7-days totals, latest weight, steps, and this week's exercises, with individually hideable cards
- **Calendar** — a year-at-a-glance overview of compact month grids; days you trained are marked with an accent circle and today gets a ring
- **Sessions** — browse, add, and edit saved workout sessions with duration, activity type, date, and notes
- **Volume stats** — total volume lifted broken down per exercise with animated progress bars
- **Notes** — a dedicated training journal for freeform, date-organized notes
- **Music widget** — integrated Spotify controller with progress bar, album art accent color, and swipe gesture playback controls
- **Customization** — fully dynamic Material 3 theme with user-adjustable RGB accent colors, a font-size slider, and edge-to-edge display
- **Cloud sync & backup** — automated Google Drive backups via WorkManager plus manual local backup/restore; backups are self-contained zip bundles that include both the workout database and step history
- **Demo mode** — one-tap "Try with demo data" on the welcome screen seeds ~4 weeks of realistic workouts, body weight, sessions, and step history
- **Permissions manager** — a dedicated Settings section for Notifications, Notification Listener, and Activity Recognition, with live status updates
- **Clear all data** — atomic wipe of workouts, weights, notes, sessions, and step history with automatic app restart, re-enabling demo mode afterwards
- **Premium subscription** — Google Play Billing with a monthly subscription that unlocks Google Drive backups

## Built with

- Kotlin + Jetpack Compose
- Room for local persistence
- ViewModel + StateFlow
- Navigation Compose with a horizontal swipe pager
- Google Play Billing for subscriptions
- Firebase Remote Config for premium user management
- Spotify MediaSession integration
- Google Drive API + WorkManager
- Material 3 with dynamic color customization
