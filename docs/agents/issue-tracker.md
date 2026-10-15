# Issue tracker: GitHub

Issues and specs for this repo live as GitHub issues on **StanleyGoldman/uhabits**. Use the `gh` CLI for all operations.

This repo is a maintained fork of iSoron/uhabits, which has stopped shipping releases. `origin` (StanleyGoldman/uhabits) is where all work happens. The `upstream` remote (iSoron/uhabits) is kept only in case changes are ever contributed back; never create issues, comments, labels or PRs there.

Always pass `--repo StanleyGoldman/uhabits` (or use `repos/StanleyGoldman/uhabits/...` in `gh api` paths) rather than relying on `gh`'s inferred default, since a clone with an `upstream` remote can resolve to the upstream repo.

## Conventions

- **Create an issue**: `gh issue create --repo StanleyGoldman/uhabits --title "..." --body "..."`. Use a heredoc for multi-line bodies.
- **Read an issue**: `gh issue view <number> --repo StanleyGoldman/uhabits --comments`, filtering comments by `jq` and also fetching labels.
- **List issues**: `gh issue list --repo StanleyGoldman/uhabits --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'` with appropriate `--label` and `--state` filters.
- **Comment on an issue**: `gh issue comment <number> --repo StanleyGoldman/uhabits --body "..."`
- **Apply / remove labels**: `gh issue edit <number> --repo StanleyGoldman/uhabits --add-label "..."` / `--remove-label "..."`
- **Close**: `gh issue close <number> --repo StanleyGoldman/uhabits --comment "..."`

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as feature requests; `/triage` reads this flag.)_

When set to `yes`, PRs run through the same labels and states as issues, using the `gh pr` equivalents (always with `--repo StanleyGoldman/uhabits`):

- **Read a PR**: `gh pr view <number> --comments` and `gh pr diff <number>` for the diff.
- **List external PRs for triage**: `gh pr list --state open --json number,title,body,labels,author,authorAssociation,comments` then keep only `authorAssociation` of `CONTRIBUTOR`, `FIRST_TIME_CONTRIBUTOR`, or `NONE` (drop `OWNER`/`MEMBER`/`COLLABORATOR`).
- **Comment / label / close**: `gh pr comment`, `gh pr edit --add-label`/`--remove-label`, `gh pr close`.

GitHub shares one number space across issues and PRs, so a bare `#42` may be either: resolve with `gh pr view 42` and fall back to `gh issue view 42`.

## When a skill says "publish to the issue tracker"

Create a GitHub issue on StanleyGoldman/uhabits.

## Spec tickets

Used by `/to-tickets`. Publish each ticket as a **sub-issue of its spec issue**: `gh api --method POST repos/StanleyGoldman/uhabits/issues/<spec>/sub_issues -F sub_issue_id=<ticket-db-id>`, with the database id from `gh api repos/StanleyGoldman/uhabits/issues/<n> --jq .id`. Blocking edges between tickets use native issue dependencies, as under **Blocking** in Wayfinding operations below.

## When a skill says "fetch the relevant ticket"

Run `gh issue view <number> --repo StanleyGoldman/uhabits --comments`.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a single issue with **child** issues as tickets. All commands target `--repo StanleyGoldman/uhabits`.

- **Map**: a single issue labelled `wayfinder:map`, holding the Notes / Decisions-so-far / Fog body. `gh issue create --label wayfinder:map`.
- **Child ticket**: an issue linked to the map as a GitHub sub-issue (`gh api` on the sub-issues endpoint). Where sub-issues aren't enabled, add the child to a task list in the map body and put `Part of #<map>` at the top of the child body. Labels: `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`). Once claimed, the ticket is assigned to the driving dev.
- **Blocking**: GitHub's **native issue dependencies**, the canonical, UI-visible representation. Add an edge with `gh api --method POST repos/StanleyGoldman/uhabits/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`, where `<blocker-db-id>` is the blocker's numeric **database id** (`gh api repos/StanleyGoldman/uhabits/issues/<n> --jq .id`, _not_ the `#number` or `node_id`). GitHub reports `issue_dependencies_summary.blocked_by` (open blockers only, the live gate). Where dependencies aren't available, fall back to a `Blocked by: #<n>, #<n>` line at the top of the child body. A ticket is unblocked when every blocker is closed.
- **Frontier query**: list the map's open children (`gh issue list --state open`, scoped to the map's sub-issues / task list), drop any with an open blocker (`issue_dependencies_summary.blocked_by > 0`, or an open issue in the `Blocked by` line) or an assignee; first in map order wins.
- **Claim**: `gh issue edit <n> --add-assignee @me`, the session's first write.
- **Resolve**: `gh issue comment <n> --body "<answer>"`, then `gh issue close <n>`, then append a context pointer (gist + link) to the map's Decisions-so-far.
- **Prototype PR**: a `prototype` ticket's branch (`prototype/<name>`) gets a draft PR with `Refs #<ticket>`. When the ticket resolves, close that PR unmerged: `gh pr close <n> --comment "..."`. The closed PR and its branch stay as the primary source.
