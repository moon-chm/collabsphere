# CollabSphere — Audit Findings & Remaining Work

Tracking doc for the Android client and Ktor backend. Findings below are based on source inspection; each should be verified against the current deployment and reproduced before rollout.

## External rollout work and remaining risks

- **Persistent file storage** — production upload now fails closed unless Cloudinary is configured and available. Configure the provider secrets, migrate/inventory old local files, and verify restart durability in staging before enabling production uploads.
- **Database credential exposure** — the hardcoded probe was removed from the current source, but provider-side credential rotation and any Git history cleanup are still required. History rewriting needs coordination because it affects shared clones.
- **Historical uploaded files** — prior commits may contain real user files under `collabpshere_server/local_files_upload/`. Confirm the current repository history and coordinate a history rewrite if required by the data owner/security policy.
- **Delta scan telemetry** — opt-in page-capable sync now uses keyset continuation on `(sync_xid,id)` for entities and workspace ID for workspace changes, with supporting composite indexes declared in the schema. Rows returned are bounded per request. Verify generated indexes/query plans against disposable PostgreSQL; current metrics count returned rows, not rows examined, and production workload tuning remains.
- **Multi-instance Redis** — required-mode health and cross-instance fan-out are implemented, but do not enable multi-instance deployment until two-broker Redis integration tests pass.
- **Phase 6 UI/staging acceptance** — broad Compose online/offline, second-account, process-recreation, release build, schema migration, upload, and notification checks remain.

## Confirmed open work

- **Temporary workspace ID remapping:** addressed in the current fix pass. Open workspace routes observe the stored ID mapping and replace their route after returning from nested channel/search screens, preserving the selected workspace tab and DM partner so old route ViewModels are cleared. Full offline-to-online UI acceptance coverage remains.
- **Membership removal sync:** current clients receive per-user removal tombstones through workspace delta sync; remaining members receive a workspace update and refresh their authoritative roster. A five-minute full-list reconciliation remains for pre-migration removals and repair.
- **Task reminders:** transactional outbox, idempotent notification creation, and bounded retry/lease behavior are implemented. DB-backed crash/retry and FCM integration cases still need a disposable test environment.
- **File durability:** production no longer silently falls back to local disk; provider provisioning and old-file migration remain external work.
- **Multi-instance readiness (conditional):** Redis-required mode, shared fan-out, origin deduplication, reconnect attempts, and local-cache bypass are implemented. Integration validation across two instances remains.
- **Link preview SSRF hardening:** the service checks DNS results before `HttpURLConnection` connects by hostname; a DNS answer can change between validation and connection. Pin the validated address while preserving TLS hostname verification, or use a vetted client with equivalent controls.
- **Delta response size:** opt-in continuation paging now covers synced entities; Android applies each page before committing the final snapshot cursor. The snapshot/page-boundary cases still require PostgreSQL integration tests.

## Already addressed in this fix pass

- Default server tests no longer include database-backed suites; DB tests require an explicit disposable database opt-in.
- Live email smoke tests and the hardcoded database connection test were removed from the default test source set.
- Workspace member refresh now prunes removed local memberships, and permanent HTTP refusals no longer create optimistic memberships.
- Removing a workspace member now stamps the workspace in the same transaction so remaining members receive a workspace delta and refresh their roster.
- Delta polling keeps its success cadence and backs off after repeated errors across the client repositories.
- Open workspace routes now resolve temporary IDs and replace the stale route when it is safe to do so.
- Delta endpoints support optional bounded pages; Android commits the cursor only after all pages are applied. Sync response and row counters are exposed through the existing metrics endpoint.
- Production uploads fail closed when durable storage is unavailable; local storage remains available for development.
- Reminder claims and outbox records are transactional, with idempotent notification creation and worker retry leases.
- Redis-required readiness and cross-instance WebSocket fan-out are available behind multi-instance configuration; single-instance mode stays Redis-optional.
- CI workflows and pure tests for paging helpers/policies were added. Server unit tests and Android unit tests passed in the local workspace.
- Server CI now provisions an isolated PostgreSQL service and runs the guarded integration suite; the workflow itself still needs a hosted Actions run to provide execution evidence.
- Delta continuation no longer uses unbounded offsets. A fresh page-capable sync starts with the transaction-ID cursor; old clients without `pageSize` retain the timestamp path.
- A Compose onboarding test covers moving through all onboarding pages and invoking the finish action; it is compiled and wired to emulator CI but still needs an emulator run.

## Broader maintainability work

- Kotlin/KSP/serialization plugin version skew (3 different versions referenced) — bumping is a real regression risk without a full build/test run.
- The six repos' delta-sync loops share near-identical boilerplate that could become one shared engine.
- `MainActivity` injects 8 repositories + 2 ViewModels and prop-drills them through navigation — a God-Activity pattern.

---
*Updated after multiple batches of security and polish fixes.*
