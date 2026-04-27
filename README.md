# INDEX Meet

`INDEX Meet` is an Android / Android TV meeting-room reminder app. It fetches calendar events for a selected meeting room, detects the currently active meeting, shows its status in the notification area, and triggers countdown alerts when the meeting is close to ending.

## In a Nutshell

This app is built for meeting-room display screens, especially Android TV devices. It connects to the meeting-room calendar, checks which meeting is currently running, shows the active meeting in the notification area, and warns users when the meeting is about to end by starting a countdown with alerts and optional overlay UI.

## What the App Does

- Lets the user select a meeting room / emirate.
- Fetches calendar events from the backend API.
- Detects the active meeting only.
- Shows active meeting state in the notification bar.
- Starts countdown alerts near meeting end.
- Supports overlay-based alerts on TV devices.
- Uses boot and wake recovery so the app can resume after restart or sleep.

## Project Structure

- `app/src/main/java/com/index/demoindexmettingroomreminderapp/ui/activity`
  Main UI screens.
- `app/src/main/java/com/index/demoindexmettingroomreminderapp/service`
  Foreground service entry points.
- `app/src/main/java/com/index/demoindexmettingroomreminderapp/worker`
  Background workers.
- `app/src/main/java/com/index/demoindexmettingroomreminderapp/worker/sync`
  Worker-driven meeting sync flow.
- `app/src/main/java/com/index/demoindexmettingroomreminderapp/worker/countdown`
  Countdown scheduling and countdown execution.
- `app/src/main/java/com/index/demoindexmettingroomreminderapp/receiver`
  Boot and wake broadcast receivers.

## Background Architecture

The current background design is intended to be safer for Android TV and Android 15:

- `MeetingSyncWorker`
  Runs one sync pass.
- `MeetingSyncEngine`
  Refreshes token if needed, fetches meetings, finds the active meeting, updates notification state, and schedules countdown work.
- `MeetingSyncScheduler`
  Schedules immediate sync and chains the next sync after 10 minutes.
- `RecoveryWorker`
  Periodic recovery worker that re-requests sync.
- `BootReceiver` and `WakeReceiver`
  Re-enqueue recovery/sync after reboot, package update, and wake-related events.
- `CountdownWorker`
  Handles active-meeting countdown UI/alerts.

## Worker File Roles

Current files under the `worker` package and their roles:

- `TokenRefreshWorker.kt`
  Periodically refreshes the API access token.
- `recovery/RecoveryScheduler.kt`
  Schedules the periodic recovery worker with `WorkManager`.
- `recovery/RecoveryWorker.kt`
  Safety-net worker that re-requests a sync after boot, wake, or long idle periods.
- `sync/MeetingSyncScheduler.kt`
  Schedules immediate sync and next sync runs.
- `sync/MeetingSyncWorker.kt`
  Executes one background sync cycle.
- `sync/MeetingSyncEngine.kt`
  Contains the main sync logic: token check, API call, active-meeting detection, notification update, and countdown scheduling.
- `sync/MeetingStatusNotifier.kt`
  Updates the ongoing meeting status notification shown to the user.
- `countdown/CountdownScheduler.kt`
  Schedules countdown work for the currently active meeting.
- `countdown/CountdownWorker.kt`
  Runs the active-meeting countdown and handles overlay, notification, beep, and TTS alerts.
- `countdown/manager/CountdownManager.kt`
  Internal countdown state/event helper used by UI-facing countdown flow.

In short, the primary worker flow now is:

- `RecoveryWorker` -> `MeetingSyncWorker` -> `MeetingSyncEngine` -> `CountdownScheduler` -> `CountdownWorker`

Files that are more legacy or secondary now:

- `TokenRefreshWorker.kt`
  Still useful, but separate from the main active-meeting polling flow.

## Android TV Notes

This app is designed for Android TV devices, including CleverTouch panels. Background behavior on TV devices depends on both app code and OEM firmware behavior.

Important points:

- Overlay permission is required for overlay-based countdown UI.
- Notification permission should remain enabled.
- Some TV firmware aggressively delays or kills background work during deep sleep.
- `WorkManager` is more reliable than a forever-running foreground polling service on newer Android versions.

## Android 15 Notes

The app targets modern Android SDK levels and includes changes to better match Android 15 restrictions:

- Avoids relying on a long-running `dataSync` foreground service for polling.
- Uses worker-driven sync for background recovery.
- Avoids direct boot/wake recovery through always-on foreground sync loops.
- Keeps active-meeting handling separate from periodic polling.

Even with these changes, OEM power management can still affect background execution during long sleep periods.

## Required Permissions

Declared in `AndroidManifest.xml`:

- `INTERNET`
- `POST_NOTIFICATIONS`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_DATA_SYNC`
- `SYSTEM_ALERT_WINDOW`
- `RECEIVE_BOOT_COMPLETED`

## Build

Compile the app with:

```bash
./gradlew :app:compileDebugKotlin
```

## Recommended Device Settings

For best results on Android TV / CleverTouch devices:

- Disable battery optimization for the app if available.
- Disable app sleep / standby / auto cleanup if available.
- Allow background activity / auto start if the firmware exposes such settings.
- Keep overlay and notifications enabled.

## Current Limitations

- OEM TV sleep behavior can still delay background recovery.
- Wake broadcasts may be inconsistent across TV vendors.
- Countdown behavior depends on the device allowing overlay display and background worker execution.

## Verification

Recommended manual checks:

1. Launch app and select a meeting room.
2. Confirm active meeting status appears in notifications.
3. Put the TV to sleep and wake it after 15 to 30 minutes.
4. Confirm the app resumes sync and updates status.
5. Test countdown behavior when an active meeting is within the final 10 minutes.
6. Reboot the TV and verify recovery after boot.
