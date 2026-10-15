# Recording view screenshots

The Android view tests (`uhabits-android/src/androidTest`, e.g. `StreakChartTest`) compare a render against a baseline PNG under `uhabits-android/src/androidTest/assets/views/`. A new or changed render needs a baseline recorded on the test emulator. Each step below ends on its check.

## 1. Have the test emulator

The baselines match a Nexus 4 AVD (768x1280, density 320). The old CI ran them at APIs 28, 29, 30, 32, 33 and 34; record at 34.

- `$ANDROID_HOME/emulator/emulator -list-avds` lists `uhabitsTest34`: done.
- Otherwise create it with `$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --install "system-images;android-34;google_apis;x86_64"`, then `echo no | $ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd --name uhabitsTest34 --package "system-images;android-34;google_apis;x86_64" --device "Nexus 4"`. If `cmdline-tools/latest` is missing, ask the user before installing it: it changes their SDK outside the repo.

`build.sh` holds these steps (`android_setup`, `android_launch`), but it refuses to run at all without `ts` (moreutils). Run its steps by hand as written here.

## 2. Boot it the way `build.sh` does

- Start `$ANDROID_HOME/emulator/emulator -avd uhabitsTest34 -port 6340 -no-snapshot -no-window` as a background command.
- With `adb -s emulator-6340`: wait for `getprop sys.boot_completed`, `root`, set `window_animation_scale`, `transition_animation_scale` and `animator_duration_scale` to 0 (`shell settings put global …`), and `shell 'echo android-test > /sys/power/wake_lock'`.
- Check: `shell wm size` prints 768x1280 and `shell wm density` prints 320.

## 3. Run the test class, keeping its render

```
ANDROID_SERIAL=emulator-6340 ./gradlew :uhabits-android:connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=<fully.qualified.TestClass>[#method]
```

A failing render is saved on the device. Gradle uninstalls the app after the run, deleting that render, unless you pass `leaveApksInstalledAfterRun`. Results are in `uhabits-android/build/outputs/androidTest-results/connected/debug/*.xml`.

Check: the failure for your render reads "Could not open expected image" (a new baseline) or "Image differs", and every other test in the class passes. If an existing, untouched render fails too, the emulator doesn't match the baselines: fix step 1 or 2 before recording anything.

## 4. Accept the render

- `adb -s emulator-6340 pull /sdcard/Android/data/org.isoron.uhabits/files/test-screenshots/views/<path>.png` into your scratchpad.
- Open it with Read and look at it: the baseline is the spec for that view.
- Copy it to `uhabits-android/src/androidTest/assets/views/<path>.png` and rerun step 3. Done when the class passes with no failures.
- Stop the emulator with `adb -s emulator-6340 emu kill`.
