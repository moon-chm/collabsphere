# CollabSphere — Audit Findings & Remaining Work

Tracking doc from a full-project security/bug audit and fix pass (Android app `Rohit_Project_Challlange` + Ktor backend `collabpshere_server`). Almost all items from the original review have been fixed. This file lists what's still open.

## Deliberately deferred (need a decision, not a bug)

- **Persistent file storage** — Render's free tier has no attached disk; uploads are wiped on every redeploy. Needs either a paid Render plan with a disk, or moving to an object store (S3/R2/Backblaze).
- **Pagination** — messages, DM history, tasks, notes, files are all unbounded fetches. Fine at current scale; revisit if usage grows.
- **Git history leak** — 9 real user files were committed to `collabpshere_server/local_files_upload/` before `.gitignore` caught it. Removed from the working tree and current HEAD, but still recoverable from old commits. A full scrub (`git filter-repo` + force-push) was declined because it rewrites shared history.

## Open — High
*(All known High severity issues have been fixed)*

## Open — Medium

- **Server**: file upload buffers the *entire* multipart body (up to the 25MB cap) into memory before checking workspace membership — any authenticated user can force that cost against a workspace they don't belong to.
- **Server**: no retry logic for Postgres `SERIALIZABLE`/`REPEATABLE_READ` conflicts under concurrent edits — surfaces as a generic 500.

## Open — Low

Server:
- `WorkspacesTable.userId` has no `.index()` (the indexing pass missed it; every other FK column got one).
- Message edit (`PUT /api/message/{id}`) checks sender ownership but not current workspace membership — a user removed from a workspace can still edit their own historical messages there.
- A channel with `userId IS NULL` (no creator) can be deleted by *any* current workspace member — confirm this is the intended permission model, not an oversight.
- `sendToUser` (DM WebSocket fan-out) silently swallows send failures with no dead-session cleanup or delivery-failure signal.
- `FileResponse.fileLocation` exposes the server's absolute filesystem path to any client with list access — unnecessary disclosure.
- No consistent JSON error envelope on server responses (mixes bare strings/booleans/ints) — would need coordinated client-side changes too, not just a server tweak.

Client:
- `MainActivity`'s `fromNotification` local (read inside `setContent`) is now always `false` because the notification-consumption fix clears that intent extra earlier in the same `onCreate` — currently harmless (same `startDestination` either way) but dead/confusing code.
- A few Compose lists/`remember` calls are missing keys (`TaskScreen.kt` assignee picker `items(members)`, `WorkspaceDetailedScreen.kt`'s `remember { mutableStateOf(initialTab) }`) — low risk given current usage patterns, but latent traps.
- `MyApplication.kt`'s `AuthTokenHolder` DataStore-sync coroutine runs in a bare `CoroutineScope(Dispatchers.IO)` with no `SupervisorJob`/exception handler — an unexpected exception there would crash the process.
- `UserRepo.loginRemote` re-hashes the password with a fresh bcrypt salt (~250ms) on every successful online login, not just when it changed — wasted CPU, no correctness impact.
- Hardcoded UI strings throughout block localization — large mechanical lift, no functional bug.
- `?: 0` nullable-ID sentinel pattern scattered across a few entities/DAOs instead of proper null handling.

## Bigger architectural items (not bugs, explicitly out of scope this pass)

- Kotlin/KSP/serialization plugin version skew (3 different versions referenced) — bumping is a real regression risk without a full build/test run.
- The six repos' delta-sync loops share near-identical boilerplate that could become one shared engine.
- `MainActivity` injects 8 repositories + 2 ViewModels and prop-drills them through navigation — a God-Activity pattern.

---
*Updated after multiple batches of security and polish fixes.*
