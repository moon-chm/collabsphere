# CollabSphere Master Implementation Plan

## Objective

Fix the audit findings in dependency order while preserving current single-instance behavior, existing API contracts, and offline-first data. Roll out each change behind a compatibility path or feature flag where a rollback may be needed. This plan does not assume that horizontal scaling is required today; multi-instance work is gated on that deployment decision.

## Guardrails

- Keep the current production behavior as the default until a phase passes its acceptance checks in staging.
- Use additive database migrations first. Do not drop or reinterpret existing columns until old and new paths have been compared in production.
- Keep the old client/server sync protocol working during migration; new response fields must be optional until all supported clients understand them.
- Run integration tests only with a dedicated test database and fake email, FCM, and file-storage providers. A test must fail fast if it sees production credentials or endpoints.
- For storage and distributed behavior, use dual-read or shadow verification before switching writes.
- Deploy one phase at a time, watch the listed signals, and retain a rollback path for each phase.

## Phase 0 — Establish a safe baseline

**Why first:** The current server test configuration can use real database and email configuration. Fixing tests safely must precede further test-driven changes.

**Work**

1. Add a dedicated test profile with an isolated PostgreSQL database and deterministic seed/cleanup behavior.
2. Replace direct email, FCM, Cloudinary, and GitHub calls in tests with injectable fakes. Keep any live-provider smoke test in an explicitly named, manually invoked task that is disabled by default.
3. Add a fail-fast check that blocks test startup if the selected database or provider configuration is production-like.
4. Make generated test users, workspaces, invitations, and webhook events clean up in `finally`/test teardown.
5. Record baseline build, test, request-volume, sync-latency, upload-success, and notification-delivery metrics.

**Acceptance gate**

- Default server and Android test tasks make no external network calls and leave no rows in a non-test database.
- Repeated test runs are isolated and produce the same results.
- CI can run the full suite without developer machine environment variables.

## Phase 1 — Remove exposed credentials and tighten offline workspace behavior

**Why now:** Credential exposure is urgent; workspace ID remapping and membership state affect every workspace feature.

**Work**

1. Revoke/rotate the database credential committed in `TestConnection.kt`; remove the literal and read test connection details only from a dedicated test secret. Coordinate any Git history cleanup separately so it does not unexpectedly rewrite shared history.
2. Add a single workspace ID resolver used by navigation, ViewModels, sync loops, and workers. When a temporary ID maps to a server ID, update or re-resolve the active back stack and restart loops under the canonical ID.
3. Test remapping with an open workspace containing channels, tasks, notes, messages, files, and queued work. Ensure no local rows disappear and no loop continues polling the negative ID.
4. Make member synchronization reconcile the server’s complete membership set, including removals. Ensure member add/remove operations update a membership sync version or emit a tombstone so other clients know to refresh.
5. In offline `add member`, queue only retryable connectivity/server-transient failures. Surface permanent 4xx refusals without creating phantom local memberships.

**Acceptance gate**

- An offline-created workspace remains usable while its ID is remapped online.
- A removed member disappears from Room-backed pickers on other devices after sync.
- Forbidden/not-found responses do not create local membership rows or perpetual retries.
- No source file contains a live database credential.

## Phase 2 — Reduce polling load without changing sync semantics

**Why after Phase 1:** Membership deltas must be reliable before removing the periodic full-workspace reconciliation request.

**Work**

1. Add tests for the existing PostgreSQL snapshot cursor protocol: slow concurrent transactions, deletes/tombstones, empty responses, database restore/reset, and legacy timestamp clients.
2. Replace the workspace full-list fetch on every 30-second pass with membership delta reconciliation from Phase 1. Retain an infrequent repair/full-refresh path.
3. Move polling intervals into configuration and back off after repeated network/server errors. Keep current intervals initially, then tune task polling (currently 1 second) against measured load and freshness needs.
4. Keep foreground gating and ensure each loop is uniquely keyed and cancelled when its owning screen/ViewModel is removed.
5. Add bounded pagination to delta endpoints. A page must carry a continuation cursor that cannot advance past unapplied rows; clients commit it only after the full page is applied to Room. Preserve existing headers and legacy timestamp parameters during rollout.
6. Use WebSocket events for immediate message/task changes where available, with polling retained as recovery rather than the only source of correctness.

**Acceptance gate**

- Tests prove no updates are skipped across concurrent commits, reconnects, resets, or page boundaries.
- Quiet foreground screens make fewer requests; freshness remains within the agreed product target.
- Server response sizes and database rows scanned are bounded and observable.

## Phase 3 — Make uploaded files durable

**Why:** Local staging is safe for request processing, but the local fallback is not a durable production storage contract.

**Work**

1. Decide and configure the production object store. Keep local disk as a development-only fallback.
2. In production mode, fail the upload clearly if durable storage is unavailable instead of reporting success for an ephemeral local copy.
3. Inventory current local-file rows and migrate their contents to object storage. Verify size/checksum and access permissions before updating the storage key.
4. Add dual-read support for old local rows and new object-store rows. Switch new writes only after verification; retain rollback reads until migration completes.
5. Keep membership checks on upload and download, and use short-lived signed downloads where supported.

**Acceptance gate**

- Upload, download, and delete continue to work through a server restart/redeploy.
- Migrated files have matching size/checksum and remain accessible only to workspace members.
- A failed object-store upload leaves no database row pointing to a missing object.

## Phase 4 — Multi-instance readiness (only if scaling is in scope)

**Why conditional:** Redis is not needed for a single server instance. If more than one instance is deployed, shared fan-out and cache invalidation must be correct before traffic is split.

**Work**

1. Change WebSocket delivery so local sessions and remote instances both receive events. Add an event ID and origin instance ID to prevent duplicate delivery when the origin also subscribes to Redis.
2. Reconnect and monitor Redis subscriptions; report readiness as degraded when cross-instance delivery is unavailable.
3. Remove or version the per-JVM workspace-member cache so invalidation on one instance cannot leave another instance broadcasting to removed members for the full local TTL.
4. If configured for multiple instances, require Redis at startup/readiness. Preserve the existing Redis-optional single-instance mode.
5. Add integration tests with two broker instances and Redis: one local session, sessions split across instances, workspace broadcasts, member removal, Redis interruption, and reconnect.

**Acceptance gate**

- A user connected to multiple instances receives one copy of each persisted event on each active device.
- Removed users receive no new workspace broadcasts after the membership change is committed.
- Single-instance deployment still starts without Redis; multi-instance deployment cannot appear healthy without shared coordination.

## Phase 5 — Make scheduled notifications recoverable

**Why:** The 15-minute reminder poll is acceptable at current scale, but reminder claiming and notification delivery should have durable retry semantics before reliability requirements increase.

**Work**

1. Define delivery semantics (at-least-once with idempotent notification creation is recommended).
2. Add an outbox/job record created in the same transaction as the reminder claim. Use a unique key per task/reminder cycle and `FOR UPDATE SKIP LOCKED` or an equivalent atomic claim.
3. Have a worker create the in-app notification idempotently, then dispatch FCM. Retry transient failures with bounded backoff and expose permanently failed jobs.
4. Keep the existing scheduler as a producer during migration; compare produced jobs and delivery counts before disabling the old path.
5. Apply the same single-runner/lease pattern to any scheduled automation that is enabled in production before adding server instances.

**Acceptance gate**

- A process crash between claim and delivery does not lose a reminder.
- Concurrent workers do not create duplicate in-app notifications.
- Delivery failures and retry exhaustion are visible in logs/metrics.

## Phase 6 — Screen and cross-feature regression pass

**Why last:** Once data contracts and IDs are stable, verify end-to-end behavior across navigation and offline/reconnect transitions.

**Work**

1. Add Compose/UI coverage for login/registration, dashboard/workspace creation, offline workspace remap, membership removal, channel/message flows, tasks, notes, files, notifications, GitHub screens, profile, and logout.
2. Exercise each flow online, offline, reconnecting, after process recreation, and with a second account on the same device.
3. Verify loading, empty, error, and retry states; check that screen-owned polling jobs stop when screens leave composition.
4. Run release build and migration checks on a staging database before production rollout.

**Acceptance gate**

- No screen relies on stale route IDs or stale Room membership.
- Logout leaves no previous-user data, queued work, or active WebSocket session.
- Staging passes server tests, Android unit/UI tests, database migration checks, and the upload/notification smoke flows.

## Implementation status (2026-10-09)

- [x] Phase 0 code: safe test profile prevents use of runtime DB/provider settings; default tests exclude DB-backed suites; guarded `integrationTest` / `checkWithIntegration` tasks, deterministic test cleanup, and CI with an ephemeral PostgreSQL service are present. Email/FCM/GitHub providers are disabled in tests and Cloudinary transport has a local HTTP fake. **Gate remaining:** the new CI integration job has not run yet; provider-side credential rotation and any coordinated Git-history cleanup remain external.
- [x] Phase 1 code: member snapshot reconciliation, permanent-HTTP refusal handling, active workspace route remapping, and per-user removal tombstones are implemented. **Gate remaining:** online/offline multi-feature acceptance and credential rotation confirmation.
- [x] Phase 2 code: client polling backoff and the five-minute workspace repair refresh are retained; workspace cursors are per user; opt-in bounded delta pages use `(sync_xid,id)` keysets (workspace pages use workspace ID), Android applies each page before final cursor commit, and low-cardinality response/row counters are implemented. Composite sync indexes are declared additively. New page-capable clients use cursor-based scans from their initial request; legacy clients retain timestamp behavior. **Gate remaining:** PostgreSQL snapshot/concurrent-commit/page-boundary integration cases, schema-index creation/plan checks, and workload measurement need a disposable/staging database. Counters record rows returned, not rows examined.
- [x] Phase 3 safeguard: production uploads fail clearly if durable storage is not configured or upload fails; local fallback remains development-only and dual-read remains supported. **Gate remaining:** provision Cloudinary/object storage secrets, inventory/migrate existing local files, and verify restart/access behavior in staging. Production uploads intentionally remain unavailable until configured.
- [x] Phase 4 readiness code: Redis requirement can be enforced from multi-instance configuration, health reflects required Redis readiness, broker events fan out across instances with origin deduplication, and required mode bypasses the process-local membership cache. **Gate remaining:** two-instance Redis integration and interruption/reconnect tests; do not enable multi-instance mode until these pass. Current single-instance mode remains Redis-optional.
- [x] Phase 5 code: transactional reminder outbox, idempotent notification creation, worker leasing/`SKIP LOCKED`, bounded retries, stable reminder keys, and a DB-backed transient-push retry/no-duplicate test are implemented. **Gate remaining:** compile/run the new integration test against disposable PostgreSQL, then collect staging evidence for delivery/retry metrics.
- [ ] Phase 6 partial: a Compose onboarding navigation/finish test is added and included in emulator CI; broad screen coverage across auth, workspaces, channels/DMs, tasks, notes, files, notifications, GitHub, profile, logout, offline/reconnect, and process recreation is still needed. Release build and staging migration/upload/notification checks remain.

**Verification (2026-10-09):** forced server `test --offline --rerun-tasks` passed after the keyset/index changes, running the then-current token/continuation tests and compiling database-backed tests. The integration task was invoked without `TEST_DATABASE_URL` and failed at its guard before database access, as intended. Android `:app:testDebugUnitTest --offline`, `:app:compileDebugAndroidTestKotlin --offline`, and `:app:assembleDebug --offline` passed. CRLF-aware `git diff --check` passes. After those successful runs, a database-backed reminder retry test and injectable suspend push sender were added. The latest server rerun is **unverified**: Gradle stopped before compilation because the shared wrapper lock under the user profile returned “Access is denied.” Database-backed tests also need a disposable PostgreSQL service. The Compose test compiles but could not be run locally: ADB cannot start and no emulator is configured; CI execution remains pending. No production database or provider credentials were passed to integration tests.

## Dependency map

```text
Safe test environment + secret rotation
                 ↓
Workspace ID resolution + membership deltas
                 ↓
Correct client sync and bounded pagination
                 ↓
Reliable file storage ──────────┐
                               ├── Cross-feature UI regression
Redis multi-instance readiness ┤
                               └── Production rollout
Reminder outbox/retries ───────────┘
```

Workspace membership and canonical workspace IDs are the shared foundation for channels, messages, tasks, notes, files, GitHub actions, notifications, and member broadcasts. PostgreSQL remains the source of truth for durable state and delta cursors; Redis is only required for shared multi-instance coordination, not for delta sync.

## Rollout and rollback

Deploy phases independently. First deploy additive schema and backward-compatible server support, then the client changes. Use feature flags for new pagination, polling intervals, and outbox processing. Monitor auth failures, membership mismatches, cursor resets, request volume, sync lag, upload errors, notification retries, and Redis subscription health. If a gate fails, disable the feature flag or roll back the app/server release while retaining additive schema; do not roll back secret rotation to an exposed credential.
