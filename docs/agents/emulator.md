# Hand checks on the emulator

A hand check drives the real app on this machine's Android emulator and reads what it shows and stores. Use it when a ticket asks for a screen to be "checked by hand".

## Setup

- `adb` is at `~/Android/Sdk/platform-tools/adb`, not on `PATH`. The emulator is `~/Android/Sdk/emulator/emulator`, with one image, `Medium_Phone_API_37.0`.
- Boot it headless as a background command: `emulator -avd Medium_Phone_API_37.0 -no-window -no-audio -no-snapshot-save`. Wait with `adb wait-for-device`, then loop until `adb shell getprop sys.boot_completed` prints `1`.
- Pass `-s emulator-5554` (your serial, from `adb devices`) on every `adb` call. Another session's emulator can appear mid-run, and a bare `adb` then fails with "more than one device".
- Build with `./gradlew :uhabits-android:assembleDebug` and install with `adb install -r uhabits-android/build/outputs/apk/debug/uhabits-android-debug.apk`.
- Stop it with `adb -s <serial> emu kill` once the check is done.

## Reaching a screen

- Only exported activities start from the shell. `EditHabitActivity` is one: `am start -n org.isoron.uhabits/.activities.habits.edit.EditHabitActivity --ei habitType 1` opens a new measurable habit, and `--el habitId <id>` opens an existing one. `ShowHabitActivity` is not exported, so open the main list (`monkey -p org.isoron.uhabits -c android.intent.category.LAUNCHER 1`) and tap the habit. A fresh install shows onboarding first; its skip button is bottom left.
- Run `am force-stop org.isoron.uhabits` before each `am start`. Otherwise the intent goes to the running instance and its extras are ignored.

## Driving and reading

- Take a screenshot with `adb exec-out screencap -p > <scratchpad>/<name>.png` and read the PNG. The image you are shown is scaled, so multiply its coordinates by the scale the read reports before passing them to `input tap`.
- Take one screenshot after every tap that shows or hides a view. Rows shift when a setting appears or disappears, and a batch of taps planned from an old screenshot lands on the wrong controls.
- Read stored values from the database, not from the screen. `sqlite3` isn't installed: copy the file with `adb exec-out run-as org.isoron.uhabits cat databases/uhabits.db > <scratchpad>/u.db` and query it with `python3 -I -c 'import sqlite3, sys; …' <scratchpad>/u.db`.
