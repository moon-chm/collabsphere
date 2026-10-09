# CollabSphere — Audit Findings & Remaining Work

Tracking doc for the Android client and Ktor backend. Findings below are based on source inspection; each should be verified against the current deployment and reproduced before rollout.

## Deliberately deferred (need a decision, not a bug)

- **Persistent file storage** — Render's free tier has no attached disk; uploads are wiped on every redeploy. Needs either a paid Render plan with a disk, or moving to an object store (S3/R2/Backblaze).
- **Pagination** — messages, DM history, tasks, notes, files are all unbounded fetches. Fine at current scale; revisit if usage grows.
- **Git history leak** — 9 real user files were committed to `collabpshere_server/local_files_upload/` before `.gitignore` caught it. Removed from the working tree and current HEAD, but still recoverable from old commits. A full scrub (`git filter-repo` + force-push) was declined because it rewrites shared history.

## Confirmed open work

- **Temporary workspace ID remapping:** addressed in the current fix pass. Open workspace routes observe the stored ID mapping and replace their route after returning from nested channel/search screens, preserving the selected workspace tab and DM partner so old route ViewModels are cleared. Full offline-to-online UI acceptance coverage remains.
- **Membership removal sync:** current clients receive per-user removal tombstones through workspace delta sync; remaining members receive a workspace update and refresh their authoritative roster. A five-minute full-list reconciliation remains for pre-migration removals and repair.
- **Task reminder crash window:** a task is claimed by setting `reminderSentAt` before inserting/pushing the notification. A process crash after the claim can lose the reminder; reliable retries need an outbox and idempotent notification creation.
- **File durability:** Render is configured on the free plan with a local upload directory. Local fallback files can disappear on redeploy, and a configured Cloudinary outage silently falls back to that disk. Choose durable object storage and migrate existing files before changing this behavior.
- **Multi-instance readiness (conditional):** Redis is optional. The member cache has per-JVM entries that another instance cannot invalidate, and WebSocket delivery currently publishes to Redis only when no local session received the event. Do not scale to multiple instances until shared invalidation and fan-out are corrected and tested.
- **Link preview SSRF hardening:** the service checks DNS results before `HttpURLConnection` connects by hostname; a DNS answer can change between validation and connection. Pin the validated address while preserving TLS hostname verification, or use a vetted client with equivalent controls.
- **Unbounded result sets:** task, note, file, message, and DM history endpoints can return unbounded data. Add cursor pagination with client cursor commits only after applying the complete page.
- **Exposed database credential:** the literal was removed from the current test source, but the credential must be rotated at its provider. It remains in existing Git history; history cleanup would require a coordinated rewrite and force push.

## Already addressed in this fix pass

- Default server tests no longer include database-backed suites; DB tests require an explicit disposable database opt-in.
- Live email smoke tests and the hardcoded database connection test were removed from the default test source set.
- Workspace member refresh now prunes removed local memberships, and permanent HTTP refusals no longer create optimistic memberships.
- Removing a workspace member now stamps the workspace in the same transaction so remaining members receive a workspace delta and refresh their roster.
- Delta polling keeps its success cadence and backs off after repeated errors across the client repositories.
- Open workspace routes now resolve temporary IDs and replace the stale route when it is safe to do so.

## Broader maintainability work

- Kotlin/KSP/serialization plugin version skew (3 different versions referenced) — bumping is a real regression risk without a full build/test run.
- The six repos' delta-sync loops share near-identical boilerplate that could become one shared engine.
- `MainActivity` injects 8 repositories + 2 ViewModels and prop-drills them through navigation — a God-Activity pattern.

---
*Updated after multiple batches of security and polish fixes.*
