# Hand checks on the emulator

A hand check drives the real app on this machine's Android emulator and reads what it shows and stores. Use it when a ticket asks for a screen to be "checked by hand".

## Setup

- `adb` is at `$ANDROID_HOME/platform-tools/adb`, not on `PATH`, and the emulator at `$ANDROID_HOME/emulator/emulator`.
- Hand checks use the `Medium_Phone_API_37.0` image. `uhabitsTest34` belongs to the screenshot tests (`docs/agents/screenshot-tests.md`), which boot it on port 6340.
- Several sessions on this machine run hand checks at once, so each boots and drives an emulator of its own. Build with `./gradlew :uhabits-android:assembleDebug`, then boot with `.claude/scripts/emulator-up.sh --apk uhabits-android/build/outputs/apk/debug/uhabits-android-debug.apk`. It waits for the boot, installs the APK and prints your `serial`; its header lists the failure statuses.
- Your serial is the one the script printed. A device in `adb devices` that you didn't boot belongs to another session, mid-check.
- The script boots `-read-only`, so the device drops your changes, your install included, when it stops. Reinstall with `adb -s <serial> install -r <apk>` after each rebuild.
- Pass `-s <serial>` on every `adb` call. Other sessions' emulators, such as the screenshot tests' `emulator-6340`, come and go mid-run, and a bare `adb` then fails with "more than one device".
- If the screen changes on its own, `adb -s <serial> logcat -d | grep "adbd service requested"` lists every shell command the device received; one you didn't send means another session is driving it.
- Stop it with `adb -s <serial> emu kill` once the check is done.

## Reaching a screen

- Only exported activities start from the shell. `EditHabitActivity` is one: `am start -n org.isoron.uhabits/.activities.habits.edit.EditHabitActivity --ei habitType 1` opens a new measurable habit, and `--el habitId <id>` opens an existing one. `ShowHabitActivity` is not exported, so open the main list (`monkey -p org.isoron.uhabits -c android.intent.category.LAUNCHER 1`) and tap the habit. A fresh install shows onboarding first; its skip button is bottom left.
- Run `am force-stop org.isoron.uhabits` before each `am start`. Otherwise the intent goes to the running instance and its extras are ignored.

## Driving and reading

- Take a screenshot with `adb exec-out screencap -p > <scratchpad>/<name>.png` and read the PNG. The image you are shown is scaled, so multiply its coordinates by the scale the read reports before passing them to `input tap`.
- Take one screenshot after every tap that shows or hides a view. Rows shift when a setting appears or disappears, and a batch of taps planned from an old screenshot lands on the wrong controls.
- Read stored values from the database, not from the screen. `sqlite3` isn't installed: copy the file with `adb exec-out run-as org.isoron.uhabits cat databases/uhabits.db > <scratchpad>/u.db` and query it with `python3 -I -c 'import sqlite3, sys; …' <scratchpad>/u.db`.
