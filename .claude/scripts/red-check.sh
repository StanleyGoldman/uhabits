#!/usr/bin/env bash
# red-check.sh: prove a core test class is not vacuous. Apply one exact
# string replacement (a mutation) to a source file, run one test class in
# :uhabits-core:jvmTest, report which tests went red, restore the file.
#
# Scope: :uhabits-core:jvmTest only (the core tests). Other modules need
# their own Gradle task.
#
# Usage:
#   .claude/scripts/red-check.sh --file <source> --tests <TestClass> \
#     --old-file <path> --new-file <path>
#
#   --file      source file to mutate, relative to the repo root or absolute
#   --tests     test class name or pattern; runs `--tests '*<TestClass>'`.
#               The match is on the class name's suffix, and one test file
#               can hold several classes: ScoreListTest.kt also holds
#               NumericalAtLeastScoreListWithSkipTest, which `ScoreListTest`
#               misses. Cover a whole file with a prefix (`ScoreList*`).
#   --old-file  file holding the exact text to replace
#   --new-file  file holding the replacement text (may be empty)
#
# old and new come from files so multi-line text needs no shell quoting.
# Each file is read verbatim except that trailing newlines are dropped, so
# a file written by the Write tool or a heredoc matches mid-line text too.
# The match is literal: no regex, no glob.
#
# The source file is restored on every exit path (trap on EXIT, INT, TERM).
#
# Output: one JSON line on stdout:
#   {"status": ..., "failed": [...], "log": ..., "next": ...}
#   status       meaning                                      exit
#   red          some tests failed: the tests catch the       0
#                mutation (the outcome you want)
#   green        tests passed: they miss the mutation;        0
#                next names the follow-up
#   build_failed gradle failed with no failing testcase      0
#                (e.g. the mutation does not compile)
#   not_found    old text does not occur in --file            3
#   not_unique   old text occurs more than once in --file     3
#   usage        bad or missing arguments                     2
#   failed  "TestClass.testName[jvm]" per failing testcase (red only)
#   log     path of the gradle output (when gradle ran)
#   next    follow-up, present on every status except red

set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

emit() { # status exit-code next [failed-json] [log]
  jq -nc --arg status "$1" --arg next "$3" \
    --argjson failed "${4:-[]}" --arg log "${5:-}" \
    '{status: $status, failed: $failed}
     + (if $log == "" then {} else {log: $log} end)
     + (if $next == "" then {} else {next: $next} end)'
  exit "$2"
}

usage_next='pass --file <source> --tests <TestClass> --old-file <path> --new-file <path>; see the header of .claude/scripts/red-check.sh'

file= tests= old_file= new_file=
while [ $# -gt 0 ]; do
  case "$1" in
    --file) file="${2:-}"; shift 2 || emit usage 2 "$usage_next" ;;
    --tests) tests="${2:-}"; shift 2 || emit usage 2 "$usage_next" ;;
    --old-file) old_file="${2:-}"; shift 2 || emit usage 2 "$usage_next" ;;
    --new-file) new_file="${2:-}"; shift 2 || emit usage 2 "$usage_next" ;;
    *) emit usage 2 "unknown argument '$1'; $usage_next" ;;
  esac
done
[ -n "$file" ] && [ -n "$tests" ] && [ -n "$old_file" ] && [ -n "$new_file" ] \
  || emit usage 2 "$usage_next"
case "$file" in /*) ;; *) file="$ROOT/$file" ;; esac
for f in "$file" "$old_file" "$new_file"; do
  [ -f "$f" ] || emit usage 2 "no such file: $f"
done

old="$(cat "$old_file")"
new="$(cat "$new_file")"
[ -n "$old" ] || emit usage 2 "--old-file is empty"

# Read the source verbatim, trailing newlines included.
content="$(cat "$file"; printf x)"
content="${content%x}"
stripped="${content//"$old"/}"
count=$(( (${#content} - ${#stripped}) / ${#old} ))
[ "$count" -eq 0 ] && emit not_found 3 "copy the old text exactly from $file, whitespace included"
[ "$count" -gt 1 ] && emit not_unique 3 "old text occurs $count times in $file; widen it with surrounding lines until it is unique"

backup="$(mktemp)"
cp -p "$file" "$backup"
restore() { cp -p "$backup" "$file" && rm -f "$backup"; }
trap restore EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

printf '%s' "${content/"$old"/"$new"}" > "$file"

results="$ROOT/uhabits-core/build/test-results/jvmTest"
rm -f "$results"/*.xml
log="$(mktemp --suffix=.red-check.log)"
(cd "$ROOT" && ./gradlew -q :uhabits-core:jvmTest --tests "*$tests") > "$log" 2>&1
code=$?

failed="$(cat "$results"/*.xml 2>/dev/null | awk '
  /<testcase / {
    name = ""; cls = ""
    if (match($0, /name="[^"]*"/)) name = substr($0, RSTART + 6, RLENGTH - 7)
    if (match($0, /classname="[^"]*"/)) cls = substr($0, RSTART + 11, RLENGTH - 12)
    sub(/.*\./, "", cls)
  }
  /<failure/ { if (name != "") print cls "." name; name = "" }
' | jq -Rsc 'split("\n") | map(select(length > 0))')"

if [ "$failed" != "[]" ]; then
  emit red 0 "" "$failed" "$log"
elif [ "$code" -eq 0 ]; then
  emit green 0 "the tests in *$tests miss this mutation: add or tighten an assertion that fails on it, then rerun" "[]" "$log"
else
  emit build_failed 0 "gradle exited $code with no failing testcase; read the log (often the mutation does not compile, or no test matched *$tests)" "[]" "$log"
fi
