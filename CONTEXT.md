# Loop Habit Tracker

Loop is an offline habit tracker: the user defines habits, records each day whether (or how much) they performed them, and the app turns that history into a score, streaks and charts.

## Language

### Habits

**Habit**:
Something the user wants to do regularly and tracks day by day.
_Avoid_: Task, goal, routine

**Yes-or-no habit**:
A habit whose daily entry records only whether it was done (e.g. "Exercise").
_Avoid_: Boolean habit, checkbox habit

**Measurable habit**:
A habit whose daily entry records an amount in a unit (e.g. "Run", in miles), judged against a target.
_Avoid_: Numerical habit, numeric habit, quantity habit

**Question**:
The prompt a habit asks the user each day (e.g. "Did you exercise today?"), shown under its name and in reminders.

**Description**:
Free-form notes about a habit as a whole, labelled "Notes" in the edit screen.
_Avoid_: Habit notes

**Archived habit**:
A habit the user has set aside: hidden from the main list and silenced, with its history kept so it can be unarchived.
_Avoid_: Deleted habit, inactive habit

**Position**:
A habit's place in the user's manual ordering of the main list.
_Avoid_: Index, rank

**Habit search**:
Narrowing the main list to habits whose name, question or description matches typed text.
_Avoid_: Filter

### Schedule and targets

**Frequency**:
How often a habit is meant to be done, expressed as "N times in M days" (e.g. every day, 3 times per week).
_Avoid_: Schedule, repeat, interval

**Target**:
For a measurable habit, the amount the user aims for: a target type, a value and a unit.
_Avoid_: Goal, threshold

**Target type**:
Whether a target is a minimum ("At least", e.g. 20 pages) or a maximum ("At most", e.g. 2 coffees).
_Avoid_: Goal type

**Unit**:
The free-text label for a measurable habit's amounts (e.g. "miles", "pages").

**Target period**:
A measurable habit's frequency read as the period its target covers: per day, per week or per month.

**Target period mode**:
How a weekly or monthly measurable habit lays out its target period: calendar weeks and months ("Every week"), or a rolling window of the last 7 or 30 days ("Every 7 days").

**Assume none**:
Per-habit setting on an "at most" habit: whether a closed day with nothing entered counts as met (on) or missed (off). It judges daily habits only; in a weekly or monthly period such a day adds 0 to the total either way.

### Entries

**Entry**:
The record of one habit on one day: a state (yes, no, skip, unknown, or amount), the amount when the state is amount, and optional entry notes.
_Avoid_: Checkmark (except as the UI word for a yes-or-no entry), repetition, check, record

**Entry notes**:
Free text the user attaches to a single day's entry.
_Avoid_: Description

**Yes (manual)**:
An entry where the user recorded doing a yes-or-no habit. Only these count as real completions.
_Avoid_: Checked, done

**Yes (auto)**:
A day the app shows as done because the habit's frequency was already met by manual yeses in that window.
_Avoid_: Implicit check, implicit checkmark, automatic checkmark

**No**:
An entry where the user recorded not doing the habit. Called a "lapse" in settings.
_Avoid_: Fail, miss, unchecked

**Skip**:
An entry marking the day as not applicable (e.g. sick, travelling), so it neither helps nor hurts. Only available when skip days are enabled.
_Avoid_: Excused day, rest day, freeze

**Unknown**:
A day with nothing recorded, shown as "?" when question marks are enabled and otherwise like a no.
_Avoid_: Empty, missing entry, question mark

**Amount**:
The number recorded for a measurable habit on one day, in the habit's unit.
_Avoid_: Numerical value, count

**Toggle**:
Cycling a yes-or-no entry through its values with one gesture on the main list or a widget.

**Day status**:
How a habit stands on a day: **open** (still undecided), **met** or **missed**. A yes-or-no day is met by a yes (manual or auto) or a skip, and missed by a no or by closing unknown. A daily measurable day is met by a skip or by reaching an "at least" target, and missed by going over an "at most" cap; otherwise it stays open until the day closes. A weekly or monthly measurable habit takes each day's status from its target period: an "at least" period is met once its total reaches the target and missed if it closes below; an "at most" period is missed once its total goes over the cap and met only when it closes at or under it. Nothing entered never meets an "at least" target, even a target of 0. Skipped days drop out of the period and the target scales to the days left; a period of only skips has no verdict, and today counts it as met, like a skipped day. "Hide completed" hides habits met today, and sort by status puts them first.
_Avoid_: Completed, done, checked

**Entered (today)**:
A habit for which the user recorded something today: a yes, a no, a skip or an amount. Unknown and Yes (auto) are not entered. Used by "Hide entered".

**Grace period**:
The time a toggled row stays on the main list although "Hide completed" or "Hide entered" would hide it: about 2 s after the last toggle on any of its days, or until its long-press popup or number dialog closes.

### Progress

**Score**:
A 0–100% measure of how established a habit is, weighted toward recent days, so one missed day after a long run only dents it.
_Avoid_: Habit strength, progress, percentage

**Streak**:
Consecutive met days, counted in days. A weekly or monthly measurable habit joins its streak a whole target period at a time: an open period is pending, neither extending nor breaking the streak, and joins once it is met; a missed period ends the streak at the period before it; a period of only skips bridges a running streak, adding its days but not counting as met. In calendar mode the streak also counts its met weeks or months ("35 · 5 weeks"). In rolling mode each day joins by its own window. A measurable streak never reaches past the day or period holding today.
_Avoid_: Chain, run

**Today**:
The day the app treats as current, which can be set to start at 3:00 AM instead of midnight.
_Avoid_: Current date

**First day of the week**:
The user's chosen start of the week, used to lay out the calendar, group days into weeks, and start the calendar weeks a weekly measurable habit is judged by.

### Habit screen views

**Overview**:
A yes-or-no habit's summary card: its current score, how that changed over the past month and year, and its total manual yeses.

**Calendar**:
The grid of a habit's daily entries on its screen. The widget showing the same grid is named "History".
_Avoid_: History, heatmap

**Bar chart**:
A habit's totals per period, from day to year: manual yeses for a yes-or-no habit, amounts for a measurable one. Its card is titled "History".
_Avoid_: Repetitions chart, History

**Frequency chart**:
How often a habit was done on each weekday, month by month; not the habit's frequency setting.

**Target progress**:
A measurable habit's amount so far in the current day, week, month, quarter and year, each against the target scaled to that period.

### Reminders

**Reminder**:
A notification for a habit at a chosen time on chosen days, asking its question. It fires only while today's day status is open, so it stays quiet once the day, or a measurable habit's target period, is met or missed.
_Avoid_: Alarm, notification

**Skip if logged**:
A per-habit setting on a measurable habit's reminder ("Skip if I've logged anything today"): when on, the reminder stays quiet on any day the habit is **entered (today)**, i.e. has an amount or a skip, even while its target period is open. Off by default.

**Snooze**:
Postponing a shown reminder ("Later") by a chosen delay.

### Data in and out

**Full backup**:
A file holding all habits and entries that the app can import again. The app also makes automatic backups.
_Avoid_: Database export, db file

**CSV export**:
A zip of spreadsheet files of habits, entries and scores for use elsewhere; it cannot be imported back.

**Import**:
Loading habits and entries from a full backup or from another habit app's export (Tickmate, HabitBull, Rewire).

### Widgets and automation

**Widget**:
A home-screen view of one habit, in one of several kinds: checkmark, history, score, streaks, frequency or target.
_Avoid_: Home-screen shortcut

**Automation action**:
An action another app (Tasker, Locale and compatible tools) performs on a habit for today: check, uncheck or toggle a yes-or-no habit, or increment or decrement a measurable one.
_Avoid_: Plugin command
