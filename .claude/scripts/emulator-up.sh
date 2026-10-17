#!/usr/bin/env bash
# emulator-up.sh: boot a private hand-check emulator and report its serial,
# so a session drives only the device it booted. The emulator takes the
# first free console port itself and reports it back, so two sessions
# booting at once never land on the same serial.
#
# Usage:
#   .claude/scripts/emulator-up.sh [--apk <path>] [--avd <name>]
#
#   --apk  APK to install once booted (default: none). The device boots
#          -read-only and drops installs when it stops, so pass your build
#          on every boot.
#   --avd  AVD to boot (default: Medium_Phone_API_37.0).
#
# The emulator keeps running after the script exits, detached from it.
# Stop it with `adb -s <serial> emu kill`.
#
# Output: one JSON line on stdout:
#   {"status": ..., "serial": ..., "pid": N, "log": path, "next": ...}
#   status          meaning                                   exit
#   ready           booted (and the APK installed)            0
#   no_port         the emulator never reported a console     1
#                   port: it exited or hung starting up
#   boot_timeout    the device never finished booting         1
#   install_failed  booted, but `adb install` failed          1
#   serial  emulator-<port>; pass `-s <serial>` on every adb call
#   pid     the emulator's process id
#   log     the emulator's output
#   next    follow-up, present on every status but ready

set -u

ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"
AVD="Medium_Phone_API_37.0"
APK=""
while [ $# -gt 0 ]; do
  case "$1" in
    --apk) APK="${2:?--apk needs a path}"; shift 2 ;;
    --avd) AVD="${2:?--avd needs a name}"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

# A unix socket path must stay under 108 characters, which a session
# scratchpad path can exceed, so the run folder lives directly under /tmp.
RUN="$(mktemp -d /tmp/emu.XXXXXX)"
LOG="$RUN/emulator.log"

report() {
  jq -nc --arg status "$1" --arg serial "${SERIAL:-}" --arg pid "${PID:-}" \
    --arg log "$LOG" --arg next "${2:-}" \
    '{status: $status, serial: $serial, pid: ($pid | tonumber? // null), log: $log}
     + (if $next == "" then {} else {next: $next} end)'
}

timeout 120 socat -u "UNIX-LISTEN:$RUN/console.sock" - > "$RUN/port" &
LISTENER=$!
for _ in $(seq 50); do [ -S "$RUN/console.sock" ] && break; sleep 0.1; done

setsid nohup "$EMULATOR" -avd "$AVD" -no-window -no-audio -no-snapshot-save \
  -read-only -report-console "unix:$RUN/console.sock" > "$LOG" 2>&1 < /dev/null &
PID=$!

wait "$LISTENER"
PORT="$(tr -dc 0-9 < "$RUN/port")"
if [ -z "$PORT" ]; then
  kill "$PID" 2>/dev/null
  report no_port "read the log; another instance of this AVD booted without -read-only locks it, and only 16 emulators fit the auto-picked ports"
  exit 1
fi
SERIAL="emulator-$PORT"

booted=""
for _ in $(seq 150); do
  if [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; then
    booted=1; break
  fi
  sleep 2
done
if [ -z "$booted" ]; then
  report boot_timeout "read the log, then stop it with: adb -s $SERIAL emu kill"
  exit 1
fi

if [ -n "$APK" ] && ! "$ADB" -s "$SERIAL" install -r "$APK" > "$RUN/install.log" 2>&1; then
  report install_failed "read $RUN/install.log; the emulator is up, so fix the APK and rerun adb -s $SERIAL install -r <apk>"
  exit 1
fi

report ready
