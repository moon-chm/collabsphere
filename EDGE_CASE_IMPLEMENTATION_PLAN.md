# CollabSphere Edge-Case Verification Plan

## Objective

Add focused tests for edge cases that can cause data loss, duplicates, unauthorized access, crashes, stale offline state, or confusing failure behavior across the Android app and Ktor server. Preserve the existing online and offline contracts. Change production behavior only when a test demonstrates a defect or when a safety invariant is currently missing.

This plan is additive to [MASTER_IMPLEMENTATION_PLAN.md](MASTER_IMPLEMENTATION_PLAN.md). It prioritizes risk rather than attempting every possible combination of inputs.

## Guardrails

- Run server integration tests only against the guarded disposable PostgreSQL database. Use fake email, FCM, GitHub, Redis, and storage providers; never point tests at production.
- Start by writing a failing regression test for a reproduced defect. Do not rewrite a working flow solely to make tests easier.
- For offline writes, distinguish permanent server refusals from retryable failures. Preserve one operation identity across a retry after an ambiguous timeout.
- Test authorization on the server as well as UI visibility. Hiding an action in the app is not an authorization check.
- Prefer unit tests for pure policies, repository/worker tests for offline state, route tests for HTTP and authorization, and Compose tests for interaction/navigation/loading state.
- Avoid Cartesian products. Cover representative valid input, each meaningful boundary, and each distinct failure class.

## Current verification constraints

- Existing tests cover selected login validation, sync cursor/paging rules, workspace ID mapping, retry policies, workspace roles, DM rules/history, upload limits/access, and reminder outbox behavior.
- Recent workspace create idempotency and UI changes are local and unverified. The Android and server Gradle wrapper currently cannot start because the shared Gradle lock file returns “Access is denied.”
- Emulator and two-instance Redis checks require CI or dedicated fixtures. Do not mark these gates complete based only on compilation.

## Phase 0 — Make the test baseline runnable and safe

**Why first:** Edge-case tests are useful only if they run repeatably and cannot mutate real data or call live providers.

**Work**

1. Restore a usable Gradle wrapper/runtime path and record the exact commands for Android unit, Compose, server unit, and guarded PostgreSQL integration suites.
2. Confirm default test tasks blank runtime DB/provider settings; integration tasks require an explicit test-named database and opt-in.
3. Ensure integration fixtures clean up even after assertion or request failures; keep CI PostgreSQL disposable.
4. Confirm fake-provider seams exist for email, FCM, Cloudinary, GitHub, Redis, and WebSocket behavior. Add seams only where a test otherwise needs a live service.
5. Ensure all external-facing/background schedulers honor the test-provider disable switch. The GitHub weekly-digest scheduler was missing this guard and is now gated with the webhook and reminder workers.
6. Document the exact server and Android commands, including the disposable database opt-in and emulator requirement, in `collabsphere_server/README.md`.

**Acceptance**

- Repeated local/CI runs are isolated and deterministic.
- A missing or unsafe test DB fails before application DB initialization.
- Default tests make no live provider calls.

**Execution status (2026-10-10)**

- [x] Added a shared `ExternalProviderPolicy` and unit cases for the test-disable switch.
- [x] Gated GitHub digest scheduling in tests; email, FCM, webhook, and reminder code also reads the shared policy.
- [x] Documented server and Android commands and isolated PostgreSQL requirements.
- [ ] Run server unit tests, Android unit tests, and CI integration/emulator jobs. Local Gradle currently stops before configuration because it cannot access the shared wrapper lock at `%USERPROFILE%\\.gradle\\wrapper\\dists\\gradle-9.4.1-bin\\...zip.lck`; hosted CI execution is still required.

## Phase 1 — Cross-cutting action safety, retries, and lifecycle

**Why next:** These cases can duplicate or lose writes across many features.

**Work and edge cases**

1. **Double-submit/loading:** create, delete, invite, upload, and other network-backed actions cannot dispatch a second operation while the first is pending. Verify button state and completion/error recovery in Compose/ViewModel tests.
2. **Ambiguous success:** server commits, response is lost, client retries. Writes with an offline/retry path must keep the same operation ID and resolve to one server record. Start with workspace creation; extend only to actions that can actually replay.
3. **Offline queue ordering:** dependent create then update/delete; temp ID remap while a worker is pending; transient 5xx/timeout retries; permanent 4xx drops with visible error; worker process death and restart.
4. **Account boundary:** logout/account switch with queued WorkManager jobs, active poll loops, WebSockets, cached Room rows, DataStore cursors, and FCM token registration. Prove one user’s jobs/data/events never appear for another.
5. **Navigation/lifecycle:** repeated entry does not start duplicate pollers; pop/back during a request does not leave stale ViewModels; process recreation restores safe state without replaying a completed action.

**Where**

- Pure retry/ID rules: Android unit tests.
- Repository/worker ordering and remapping: Android repository/DAO/worker tests with in-memory Room and fake API.
- Duplicate server writes: Ktor/PostgreSQL route integration tests.
- Loading, navigation, logout and recreation: Compose emulator tests.

**Acceptance**

- A replayed request produces at most one durable write.
- Queued work belongs to its original user and resolves dependencies in order.
- Pending UI state always clears on success, refusal, exception, or cancellation.

## Phase 2 — Authentication, profile, and account security

**Why after Phase 1:** Session ownership and cleanup are prerequisites for safely testing account actions.

**Actions and edge cases**

- Register/login: blank, malformed, whitespace-only and duplicate email; wrong password; unverified account; simultaneous registration; expired/revoked JWT; repeated submit.
- OTP and reset: wrong, expired, reused, superseded, or rate-limited codes; resend invalidates old code; concurrent confirm/resend; response lost after successful password reset.
- Profile/avatar/email: maximum/blank fields; malformed image; upload succeeds but profile save fails; remove avatar during pending upload; duplicate email; wrong current password; expired email verification.
- Privacy/blocking: self-block; repeated block/unblock; blocked user search/profile visibility; private profile viewed by nonmember; concurrent block and DM send.
- Logout/account deletion: stale JWT rejected; FCM device token detached; all user data and queued work cleared; deletion with owned/shared workspaces; retries after deletion.

**Where:** `SecurityRoutesTest.kt` and authenticated route tests; Login/Profile/SessionManager ViewModel tests; focused Compose tests for validation, pending, error, and logout navigation.

**Acceptance:** authorization uses the authenticated principal, one-time tokens cannot be reused, and no old-account state survives logout or account switch.

## Phase 3 — Workspace, membership, invitations, search, and presence

**Why after Phase 2:** Every workspace-scoped feature relies on correct membership and identity.

**Actions and edge cases**

- Create/list/delete: empty/long values; repeated tap; same-key retry; create response lost; mismatched owner/password; delete wrong ID; delete already deleted; sync tombstone after delete; local temporary ID remapped while a nested route is open.
- Join/invite: invalid/expired/reused code; duplicate pending invite; invitee already member; unknown email; accept/decline twice; role changes before accept; case/whitespace normalization.
- Membership/roles: owner cannot be removed/demoted; admin/member permission matrix; self-leave rules; concurrent role change/removal; removed user loses access immediately; remaining clients refresh roster; empty roster reconciliation.
- Search/presence: blank/long/special-character query; zero results; private or nonmember target; presence request for another workspace; stale/disconnected presence.
- Dashboard: stable unique rows during baseline fetch plus delta sync; duplicate member joins do not duplicate workspaces; negative temporary IDs remain safe for rendering.

**Where:** `RoutesEndToEndTest.kt`, workspace route/integration tests; WorkspaceRepo/DAO/ViewModel tests; dashboard and delete/create Compose tests.

**Acceptance:** exactly one visible row per canonical workspace ID; removed/deleted workspaces disappear locally and cannot be recovered by stale sync; role checks pass on the server.

## Phase 4 — Channels, messages, and direct messages

**Why after Phase 3:** These features depend on workspace membership and ID resolution.

**Actions and edge cases**

- Channels: create duplicate name within one workspace versus same name in another; delete missing/already-deleted channel; nonmember/admin access; last channel; stale selected channel after workspace removal.
- Messages: empty/whitespace/over-limit content; send twice; response lost then retry; edit/delete another user’s message; delete after channel removal; reply to missing/deleted/foreign-channel message; pin toggle race; read marker at empty history, newest ID, and beyond newest; malformed/duplicate reactions.
- DMs: participants only; block on either side; same partner in separate workspaces; first-device bootstrap and later catch-up; empty/last page; edits/deletes during paging; reply target absent; media-only message; upload failure after message draft exists; retry without duplicate.
- Real-time events: reconnect gap repaired by sync; duplicate/out-of-order WebSocket event is idempotent; typing/presence expires after disconnect; one instance’s event reaches the other without echo loops.

**Where:** message/DM/channel route integration tests; existing `DmRulesTest.kt` and `DmHistoryIntegrationTest.kt` expanded for uncovered cases; Room/repository tests; focused Compose send/retry/reaction/read/navigation flows. Redis multi-instance tests are deferred to Phase 8.

**Acceptance:** message history converges after reconnect and retries; no cross-workspace or cross-participant data leakage.

## Phase 5 — Tasks, notes, reminders, and GitHub task links

**Why after Phase 4:** Task assignment and reminders rely on users, membership, notification delivery, and sync.

**Actions and edge cases**

- Tasks: blank/over-limit title; invalid priority/status; assignee is nonmember or removed; due date null/past/boundary/time-zone; repeated create/update; update racing delete; completion while reminder is queued; offline create then edit/assign/delete before server ID arrives.
- Checklist/labels: empty list, duplicates, normalization, maximum item count/length, malformed serialized legacy value, concurrent edit.
- Reminders: due-window boundaries; due date/assignee changes after enqueue; task deleted/completed; two workers claim simultaneously; worker crash after claim; lease expiry; transient versus permanent FCM failure; retry limit; no push token; notification dedupe after retry.
- Notes: blank/maximum fields; pin/unpin repeated; edit/delete racing offline replay; tombstone versus late update; two devices editing around a sync page boundary.
- GitHub task links: wrong workspace/repository, duplicate attach, unlink twice, deleted task, missing permission, provider throttling/partial failure.

**Where:** task/notes pure and client repository tests; server route/PostgreSQL reminder tests; fake FCM and GitHub clients; Compose task/editor/notification actions.

**Acceptance:** task, note, and reminder state converges with no duplicate notifications or stale resurrection.

## Phase 6 — Files, notifications, and device tokens

**Why:** These actions cross device, provider, storage, and workspace authorization boundaries.

**Actions and edge cases**

- Files: zero-byte, over-limit, unsupported MIME, deceptive filename/path, interrupted multipart stream, disk full, provider timeout after object creation, DB failure after upload, cleanup after failure, unauthorized download/delete, soft-delete sync, restart access, old local-row dual-read and checksum migration.
- Notifications: empty list/count, mark read twice, delete wrong user’s notification, mute/unmute repeated, quiet-hours across midnight/time-zone/DST, settings change while delivery is queued, unread count after tombstone.
- FCM tokens: unauthenticated registration; body user ID spoofing; same device refresh; device changes accounts; logout removes only that device; stale/invalid token; multiple device partial delivery and retry.

**Where:** `RoutesEndToEndTest.kt`, `SecurityRoutesTest.kt`, upload/storage tests; fake provider repository tests; notification and profile Compose tests; staging restart/migration check.

**Acceptance:** no unauthorized file/notification access; failed uploads leave no dangling row/object; token lifecycle is account-safe.

## Phase 7 — GitHub OAuth, webhooks, automation, and external links

**Why late:** These flows require stable core task/workspace behavior and rely on external-provider fakes.

**Actions and edge cases**

- OAuth: invalid/missing/replayed/expired state; account disconnect while callback is pending; provider denial; token refresh failure.
- Webhooks: invalid signature; duplicate delivery; out-of-order event; malformed/oversized payload; unavailable provider; retry after partial processing; idempotent task/comment creation.
- Repository/PR/issue actions: no installation/repository; access revoked; duplicate link; rate limit; deleted task; partial API data; sync cancellation.
- Unfurl/link preview: malformed URL; redirect to private IP; DNS rebinding; IPv4/IPv6/local host; oversized/slow response; invalid HTML; unsupported scheme.

**Where:** pure state/parser/security tests; route tests with fake HTTP/provider clients; DB integration for webhook dedupe; no live GitHub credentials in CI.

**Acceptance:** replayed webhook/OAuth state cannot duplicate or cross-link data; outbound URL handling blocks private targets after redirects and DNS changes.

## Phase 8 — Cross-screen acceptance, Redis, and rollout evidence

**Why last:** This validates combinations after their individual contracts are stable.

**Work**

1. Compose journeys for register/login, workspace create/delete/member removal, channel/DM send/retry, task/reminder, notes, file upload/download, notifications, profile/logout.
2. Run journeys online, offline-to-online, after process death, after account switch, and with a second user where permissions differ.
3. Run delta endpoint matrix for workspaces, channels, tasks, notes, messages, files, and DMs: initial cursor, exact page boundary, continuation, empty page, concurrent commit, tombstone, stale/reset cursor, and legacy timestamp client.
4. Run two-server/two-Redis-client tests for publish/fanout, origin dedupe, cache invalidation, Redis disconnect/reconnect, and required-mode readiness.
5. In staging, verify schema migration, upload/download/delete across restart, reminder delivery/retry, query plans/page sizes, and release build.

**Acceptance**

- All target platform/server tests pass in CI; PostgreSQL, emulator, and Redis jobs ran (not merely compiled).
- No production provider or database is used by tests.
- Staging evidence exists for migration, storage, notifications, sync boundaries, and multi-instance behavior before enabling those deployment modes.

## Cases that do not need their own edge-case test

- Static text/icon/color/layout variations: cover important screen states with a small Compose smoke set; no separate test for each decorative element.
- Shared authentication/header plumbing: test the common authentication mechanism and one representative route per distinct authorization policy. Add route-specific tests when ownership, membership, or role checks differ.
- Every combination of every field: use boundary classes and pairwise combinations where interactions matter; exhaustive Cartesian testing is not practical and adds little confidence.
- Pure helpers already tested (paging token parsing, retry backoff, role rules, task references): extend the existing test rather than duplicate the same assertion at every call site.

## Execution order and reporting

Execute phases in order. Within a phase, add a test, reproduce/fix the issue if it fails, run the narrow test, then run the relevant suite. Mark a case **covered** only after the test executed successfully; compilation alone is not execution. Mark provider, emulator, PostgreSQL, and Redis gates **blocked** when their required fixture is unavailable, and retain them as open work.

For each phase, record: edge case, test file, result, implementation change (if any), and remaining environment gate. Do not delete user data or auto-merge duplicate workspaces as part of the test pass; report and ask before any cleanup that could discard user content.
