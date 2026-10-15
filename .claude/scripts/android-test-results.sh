#!/usr/bin/env bash
# android-test-results.sh: summarise the last connectedDebugAndroidTest run
# (instrumented and view screenshot tests) from its JUnit XML, so a run's
# outcome reads as one line instead of an XML or HTML report.
#
# Usage:
#   .claude/scripts/android-test-results.sh [--root <checkout>]
#
#   --root  checkout whose results to read (default: the one holding this
#           script). Pass a worktree's path to read that worktree's run.
#
# Reads every *.xml under
#   <root>/uhabits-android/build/outputs/androidTest-results/connected/
# Gradle leaves results from earlier runs there; `rm -rf` that folder
# before a run whose results must stand alone.
#
# Output: one JSON line on stdout:
#   {"status": ..., "total": N, "failed": [...], "next": ...}
#   status      meaning                                   exit
#   green       every testcase passed                     0
#   red         some testcases failed                     0
#   no_results  no result XML (the run never reached a    0
#               device, or the folder was cleared)
#   failed  {"test": "Class#method", "message": first line of the failure}
#           per failing testcase (red only)
#   next    follow-up, present on red and no_results
#
# A red run may hold failures that predate your change: the suite is not
# green on main (#100). Rerun the failing classes on origin/main before
# blaming the change.

set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
if [ "${1:-}" = "--root" ]; then
  ROOT="${2:?--root needs a path}"
fi
DIR="$ROOT/uhabits-android/build/outputs/androidTest-results/connected"

summary="$(python3 -I - "$DIR" <<'EOF'
import glob, json, sys, xml.etree.ElementTree as ET
total, failed = 0, []
for path in sorted(glob.glob(sys.argv[1] + "/**/*.xml", recursive=True)):
    for tc in ET.parse(path).getroot().iter("testcase"):
        total += 1
        problem = tc.find("failure")
        if problem is None:
            problem = tc.find("error")
        if problem is not None:
            text = (problem.get("message") or problem.text or "").strip()
            failed.append({
                "test": tc.get("classname", "").split(".")[-1] + "#" + tc.get("name", ""),
                "message": text.splitlines()[0][:300] if text else "",
            })
print(json.dumps({"total": total, "failed": failed}))
EOF
)"

total="$(jq -r .total <<<"$summary")"
if [ "$total" = 0 ]; then
  jq -nc --arg dir "$DIR" '{status: "no_results", total: 0, failed: [],
    next: ("no result XML under " + $dir + "; run :uhabits-android:connectedDebugAndroidTest first (docs/agents/screenshot-tests.md boots the emulator)")}'
elif [ "$(jq '.failed | length' <<<"$summary")" = 0 ]; then
  jq -c '{status: "green", total, failed}' <<<"$summary"
else
  jq -c '{status: "red", total, failed,
    next: "rerun the failing classes on origin/main to tell regressions from known failures (#100); a render failure saved its image on the device, see docs/agents/screenshot-tests.md step 4"}' <<<"$summary"
fi
