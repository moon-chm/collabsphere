# CollabSphere — Audit Findings & Remaining Work

Tracking doc from a full-project security/bug audit and fix pass (Android app `Rohit_Project_Challlange` + Ktor backend `collabpshere_server`). Every Critical and High severity item from the original review is fixed except two deliberately deferred (noted below). This file lists what's still open.

## Deliberately deferred (need a decision, not a bug)

- **Persistent file storage** — Render's free tier has no attached disk; uploads are wiped on every redeploy. Needs either a paid Render plan with a disk, or moving to an object store (S3/R2/Backblaze).
- **Pagination** — messages, DM history, tasks, notes, files are all unbounded fetches. Fine at current scale; revisit if usage grows.
- **Git history leak** — 9 real user files were committed to `collabpshere_server/local_files_upload/` before `.gitignore` caught it. Removed from the working tree and current HEAD, but still recoverable from old commits. A full scrub (`git filter-repo` + force-push) was declined because it rewrites shared history.

## Open — High

- **Offline create-then-edit race**: if a Task/Channel/Note is created while offline (gets a negative temp id) and edited again before the create-sync succeeds, the queued update captures the stale temp id at enqueue time and still sends it once it runs — 404s forever since the server never had that id. Affects Task/Channel/Notes sync workers.

## Open — Medium

- **Server**: file download's SQL `LIKE` pattern (`url like "%/$fileNameParam"`) isn't escaped — a filename containing `%`/`_` can probe for other files' existence or produce wrong matches. Fix: use Exposed's `LikePattern.ofLiteral(...)`.
- **Server**: file upload buffers the *entire* multipart body (up to the 25MB cap) into memory before checking workspace membership — any authenticated user can force that cost against a workspace they don't belong to.
- **Server**: no retry logic for Postgres `SERIALIZABLE`/`REPEATABLE_READ` conflicts under concurrent edits — surfaces as a generic 500.
- **Client**: create-sync workers (Channel/Task/Notes/File-upload/DM-send) have no idempotency key — if the server-side create succeeds but the response is lost before `Result.success()`, WorkManager retries and creates a duplicate row.
- **Client**: `WidgetDataProvider.kt` opens a brand-new Room `AppDatabase` connection on every single widget refresh call and never closes any of them — a connection leak on every home-screen widget tick.
- **Client**: `WorkspaceViewModel`/`ChannelViewModel`/`NotesViewModel` each start their own independent `startDeltaSyncLoop` in `init{}` for the same underlying data — now more visible since `WorkspaceViewModel` is instantiated fresh per nav destination, so navigating between workspace screens can run multiple concurrent pollers hitting the same endpoint.

## Open — Low

Server:
- `WorkspacesTable.userId` has no `.index()` (the indexing pass missed it; every other FK column got one).
- Message edit (`PUT /api/message/{id}`) checks sender ownership but not current workspace membership — a user removed from a workspace can still edit their own historical messages there.
- A channel with `userId IS NULL` (no creator) can be deleted by *any* current workspace member — confirm this is the intended permission model, not an oversight.
- `sendToUser` (DM WebSocket fan-out) silently swallows send failures with no dead-session cleanup or delivery-failure signal.
- `FileResponse.fileLocation` exposes the server's absolute filesystem path to any client with list access — unnecessary disclosure.

Client:
- `MainActivity`'s `fromNotification` local (read inside `setContent`) is now always `false` because the notification-consumption fix clears that intent extra earlier in the same `onCreate` — currently harmless (same `startDestination` either way) but dead/confusing code.
- Chat auto-scroll (`MessageScreen.kt`, and pre-existing in `DMScreen.kt`) still yanks the user to the bottom on any new message even if they've scrolled up to read history — should check `firstVisibleItemIndex`/scroll position before auto-scrolling.
- A few Compose lists/`remember` calls are missing keys (`TaskScreen.kt` assignee picker `items(members)`, `WorkspaceDetailedScreen.kt`'s `remember { mutableStateOf(initialTab) }`) — low risk given current usage patterns, but latent traps.
- `MyApplication.kt`'s `AuthTokenHolder` DataStore-sync coroutine runs in a bare `CoroutineScope(Dispatchers.IO)` with no `SupervisorJob`/exception handler — an unexpected exception there would crash the process.
- `UserRepo.loginRemote` re-hashes the password with a fresh bcrypt salt (~250ms) on every successful online login, not just when it changed — wasted CPU, no correctness impact.
- No consistent JSON error envelope on server responses (mixes bare strings/booleans/ints) — would need coordinated client-side changes too, not just a server tweak.
- Hardcoded UI strings throughout block localization — large mechanical lift, no functional bug.
- `?: 0` nullable-ID sentinel pattern scattered across a few entities/DAOs instead of proper null handling.

## Bigger architectural items (not bugs, explicitly out of scope this pass)

- Kotlin/KSP/serialization plugin version skew (3 different versions referenced) — bumping is a real regression risk without a full build/test run.
- Near-zero test coverage (2 fragile server tests hitting a real dev DB, no Android unit tests at all).
- The six repos' delta-sync loops share near-identical boilerplate that could become one shared engine.
- `MainActivity` injects 8 repositories + 2 ViewModels and prop-drills them through navigation — a God-Activity pattern.

---
*Generated from a multi-pass Claude Code review session. Everything above was independently re-verified against the current code, not carried over from an earlier draft.*
