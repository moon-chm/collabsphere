# CollabSphere — Audit Findings & Remaining Work

Tracking doc from a full-project security/bug audit and fix pass (Android app `Rohit_Project_Challlange` + Ktor backend `collabpshere_server`). Almost all items from the original review have been fixed. This file lists what's still open.

## Deliberately deferred (need a decision, not a bug)

- **Persistent file storage** — Render's free tier has no attached disk; uploads are wiped on every redeploy. Needs either a paid Render plan with a disk, or moving to an object store (S3/R2/Backblaze).
- **Pagination** — messages, DM history, tasks, notes, files are all unbounded fetches. Fine at current scale; revisit if usage grows.
- **Git history leak** — 9 real user files were committed to `collabpshere_server/local_files_upload/` before `.gitignore` caught it. Removed from the working tree and current HEAD, but still recoverable from old commits. A full scrub (`git filter-repo` + force-push) was declined because it rewrites shared history.

## Open — High
*(All known High severity issues have been fixed)*

## Open — Medium
*(All known Medium severity issues have been fixed)*

## Open — Low
*(All known Low severity issues have been fixed)*

## Bigger architectural items (not bugs, explicitly out of scope this pass)

- Kotlin/KSP/serialization plugin version skew (3 different versions referenced) — bumping is a real regression risk without a full build/test run.
- The six repos' delta-sync loops share near-identical boilerplate that could become one shared engine.
- `MainActivity` injects 8 repositories + 2 ViewModels and prop-drills them through navigation — a God-Activity pattern.

---
*Updated after multiple batches of security and polish fixes.*
