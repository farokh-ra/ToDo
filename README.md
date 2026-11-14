# ToDo — a lightweight alarm-based reminder app

Built for a Samsung Galaxy A12 (Android 10–12) and any phone with Android 8.0+.
English interface, Persian/Farsi text input, and the Iranian **Shamsi (Jalali)**
calendar for every date.

## What it does

- Add a task with a **title + Shamsi date + time**. New dates always start on
  **today**.
- Type times directly as **`hh:mm AM/PM`** (for example `09:30 PM`). There is no
  clock dial; `9:30 PM`, `09:30PM` and `00:00 AM` are all accepted.
- Choose a **custom notification sound for each reminder**: the system default,
  **silent**, or any notification sound from the system ringtone picker.
- Add **as many dates and times as you want** to one task. Multiple times per
  date and multiple dates are all supported.
- Pick dates on a **Jalali calendar**: Saturday-first weeks, Persian month names
  (Farvardin … Esfand), month arrows and a year jump. Every date in the list,
  editor and notifications is Shamsi.
- The list is **sorted by the next occurrence**: the closest reminder is on top,
  later ones further down. Completed tasks move to a **Done** section at the bottom.
- Check the circle to mark the next occurrence done, or use the card menu to
  mark everything done, edit or delete.
- Every occurrence raises a **high-importance notification with sound, vibration
  and Done / Snooze 10 min actions**.
- Persian text is fully supported in titles: fields and cards auto-detect
  right-to-left text, while UI labels stay English and dates use the Shamsi
  calendar.

## Calendar

Dates use the **Shamsi (Jalali / Solar Hijri)** calendar, the official calendar
of Iran: Farvardin–Esfand, 31-day first half, 30-day second half, and Esfand 30
in leap years such as 1403. Weeks start on **Saturday**. Month names appear in
English transliteration (“Aban”) so the rest of the interface stays English;
numbers stay Latin and times stay 12-hour with AM/PM.

Reminders are still stored as epoch timestamps, so the calendar change affects
only labels: alarm timing, time zones and daylight saving are unchanged.
Conversions use the standard Borkowski algorithm and are checked against
published Nowruz dates (1 Farvardin 1404 = 21 March 2025, 1 Farvardin 1405 =
21 March 2026).

## Why it does not hurt the battery

There is **no background service, no polling loop and no wakelock**. Every
reminder is handed to Android's own alarm clock (`AlarmManager`). The app's
process only wakes for the instant a reminder fires, then sleeps again.
Alarms are re-armed automatically after a reboot, an app update, a clock change
or when exact-alarm permission is granted. Reminders that would have fired while
the phone was off are summarised in one notification on the next boot.

The manifest declares both `SCHEDULE_EXACT_ALARM` and `USE_EXACT_ALARM`. The
latter is auto-granted on Android 13+, so reminders stay exact without a setup
step; on Android 12 the system grants `SCHEDULE_EXACT_ALARM` at install. If a
user turns exact alarms off, the app warns with a banner and still delivers,
using the best alarm path the system allows.

## Install

1. Copy `dist/ToDo-v1.2.apk` to the phone (USB, cloud drive or `adb install`).
2. Open it on the phone and allow installing from this source when prompted.
3. Launch **ToDo** and grant the notification permission.

The APK is signed with a local key (`app/keystore/local.jks`, not committed) so
it installs directly. Keep the same key to update the app later.

## One-time reliability setup (important on Samsung)

Open **ToDo → alarm icon (top right) → Reliability**:

- **Notifications** — allow them, otherwise nothing can appear.
- **Exact alarms** — allow "Alarms & reminders". On Android 13+ `USE_EXACT_ALARM`
  grants this automatically; if it is switched off, the app shows a banner and
  still delivers with the best alarm the system allows.
- **Battery optimization** — set the app to unrestricted.
- **Samsung battery tips** — exclude the app from *Sleeping apps* /
  *Deep sleeping apps*, turn off *Put unused apps to sleep*, and use
  *Keep open* in Recents.
- **Send a test reminder** — verifies the whole path in 10 seconds, even after
  closing the app.

## Project layout

```
app/src/main/java/com/remindly/app/
  App.kt                     process init: storage, channel, reschedule
  data/Models.kt             Reminder + Occurrence
  data/ReminderStore.kt      atomic JSON persistence (filesDir/reminders.json)
  alarm/AlarmScheduler.kt    exact alarms + alarm-clock fallback + snooze/test
  alarm/ReminderReceiver.kt  alarm delivery, Done / Snooze actions
  alarm/BootReceiver.kt      re-arm after reboot/update/time change
  notify/Notifications.kt    per-sound channels, reminder notifications, missed summary
  ui/MainActivity.kt         sorted list, sections, banners, menus
  ui/EditActivity.kt         title, typed hh:mm AM/PM times, sound
  ui/JalaliDatePicker.kt     Shamsi calendar dialog (Saturday-first)
  ui/ReliabilityActivity.kt  permissions/status and test alarm
  util/Jalali.kt             Jalali conversion, leap years, month names
  util/TimeFmt.kt            Jalali date/time and countdown formatting
```

Storage is a single small JSON file, so there is no database service running.
Shell: one activity stack, classic views + Material 3 (no Compose runtime),
`minSdk 26`, `targetSdk 34`.

## Tested on Android 15 (Galaxy A-series emulator)

- Created and saved reminders through the real UI with several times in one
  date, then verified the occurrences in storage.
- List sorted correctly with an overdue task on top; Persian title rendered
  right-to-left; the Shamsi calendar and typed time dialog opened.
- The Shamsi picker opened on **Aban 1405** with a Saturday-first week; choosing
  **25 Aban** stored 16 November 2026, 9:00 PM Tehran time, and both the card and
  the "Next" line showed **Mon 25 Aban**.
- Month arrows rolled from Aban 1405 into **Farvardin 1406** (31 days, 1
  Farvardin on Sunday), matching the published Nowruz date of 21 March 2027.
- Conversions were checked against published Nowruz dates: 1 Farvardin 1404 =
  21 March 2025, 1 Farvardin 1405 = 21 March 2026 (18 Aban 1405 = 9 November
  2026), and 1403's leap Esfand 30 = 20 March 2025.
- Exact delivery measured at **54 ms after the scheduled second**.
- Notification showed title, scheduled time, MARK DONE and SNOOZE 10 MIN;
  Snooze cleared it and queued an alarm ten minutes out.
- Marking a task done persisted immediately and moved the card to Done.
- Reminders and alarms survived a full reboot. The boot receiver was verified by
  delivering its boot intent directly: it re-armed 10 exact alarms and posted a
  "You missed 1 reminder(s)" summary. This MuMu image blocks the system boot
  broadcast for apps in its restricted battery bucket (the same Samsung
  behaviour the Reliability screen addresses), so the receiver was tested via an
  explicit broadcast rather than the emulator's own boot.
- The editor opened existing reminders with their title and dates loaded, and
  the Shamsi date picker plus a typed `hh:mm AM/PM` time changed the schedule.
- Typed `09:30 PM` saved and displayed as **9:30 PM**; `25:00 PM` was rejected
  with a format hint, and new reminders defaulted to **today**.
- A reminder set to the **Antimony** ringtone fired on its own channel with that
  sound URI, and a second reminder set to **Silent** fired on the silent
  channel with no sound. A clean install contains no sample reminders.
- No services and no JobScheduler jobs exist for the app between reminders.

## Build

Requirements: JDK 17 and an Android SDK with platform 34 + build-tools 34.

```bash
./gradlew assembleDebug     # debug APK
./gradlew assembleRelease   # signed, minified release APK
```

A portable JDK 17 is included under `.tooling/` for this machine, and
`local.properties` points at the local SDK. Both are git-ignored.
