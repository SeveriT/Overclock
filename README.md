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

- **Workout logging** — a bottom-sheet form with recent-exercise chips, history auto-complete and a tap-to-reuse "Last time" chip. Weight uses ±5 kg buttons for big jumps plus a fine-tune slider (±5 kg in 0.5 kg steps), dates have Today / Yesterday quick picks, and personal bests are one toggle away
- **Workout timer** — animated ring timer with lap tracking, persistent notification, and automatic recovery if the app is killed. When you stop, save the session with a name and activity type, resume, or discard it
- **Tools** — one screen, opened from the wrench icon in the top bar, collecting small utilities in expandable sections (one open at a time, remembered between launches):
  - **Schedule** — an editable workout checklist you fill in and tick off as you train, with a manual reset button
  - **Pace calculator** — linked min/km and km/h sliders (3:00–8:00 /km) with fine-tune buttons, min/mile and mph readouts, and finish-time estimates for 400 m, 5K, 10K, half marathon and marathon, plus your Cooper test distance
- **Weight tracking** — smooth trend chart with RPI and BMI from your height, plus a recency-weighted prediction that projects your weight to a date you choose, drawn right on the chart. Configurable axis range, and quick weigh-ins with ±1 kg buttons and a 0.1 kg fine-tune slider
- **Step counter** — daily step tracking via the device's built-in sensor, with an editable goal, goal-reached notification, and a 7-day bar chart on the Home page
- **Home** — weekly activity count, streaks, recent sessions and last-7-days totals, latest weight, steps, and this week's exercises, with individually hideable sections
- **Calendar** — a year-at-a-glance overview of compact month grids; days you trained are marked with an accent circle and today gets a ring
- **Sessions** — browse, add, and edit saved workout sessions with duration, activity type, date, and notes; opened from Calendar's history icon or "See all" on Home
- **Workout stats** — total volume lifted plus a per-exercise breakdown ranked by sets × reps over the last 12 weeks. Tap an exercise for a reps-per-week bar chart with a 12-week average line; long-press to hide it from the list and the total
- **Notes** — a dedicated training journal for freeform, date-organized notes
- **Music widget** — integrated Spotify / YouTube Music controller with progress bar, album art accent color, and swipe gesture playback controls
- **Material 3, container-free design** — flat lists and sections instead of cards; every add/edit form opens as a bottom sheet and quick questions share one dialog style. Deletes come with an Undo snackbar
- **Customization** — fully dynamic Material 3 theme with user-adjustable RGB accent colors, a background brightness slider, a font-size slider, and edge-to-edge display
- **Cloud sync & backup** — automated Google Drive backups via WorkManager plus manual local backup/restore; backups are self-contained zip bundles that include both the workout database and step history
- **Demo mode** — one-tap "Try with demo data" on the welcome screen seeds ~4 weeks of realistic workouts, body weight, sessions, notes, and step history
- **Permissions manager** — a dedicated Settings section for Notifications, Notification Listener, Activity Recognition, and Unrestricted Battery, mirrored on the welcome screen, with live status updates
- **Clear all data** — atomic wipe of workouts, weights, notes, sessions, and step history with automatic app restart, re-enabling demo mode afterwards
- **Premium subscription** — Google Play Billing with a monthly subscription that unlocks Google Drive backups

## Built with

- Kotlin 2.4 + Jetpack Compose (Compose BOM 2026.09), built with AGP 9 and its built-in Kotlin support
- Room for local persistence
- ViewModel + StateFlow
- Navigation Compose 2.10 with a horizontal swipe pager for the five main tabs
- Google Play Billing for subscriptions
- Firebase Remote Config for premium user management
- MediaSession integration for Spotify and YouTube Music
- Google Drive API + WorkManager
- Material 3 with dynamic color customization
