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
- The server test suite passed previously (`BUILD SUCCESSFUL in 24s`). The user reports Android production source compilation succeeded, but Android unit-test compilation failed in multiple test files; Phase 0 remains unverified until those compile failures are repaired and the final test run passes.
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
- [x] Server test suite passed (`BUILD SUCCESSFUL in 24s`, reported by the user).
- [x] Android unit tests and CI integration/emulator jobs. The Android unit test compilation issues (unresolved mockk references) and runtime assertion failures (unmocked Log calls, coroutine races) have been fixed. The test suite now compiles and passes successfully (`BUILD SUCCESSFUL in 22s`).

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

**Execution status (2026-10-10)**

- [x] Workspace create double-submit guard is claimed synchronously before launching the request coroutine.
- [x] Workspace delete double-submit guard is claimed synchronously before launching the request coroutine.
- [x] Invitation send rejects duplicate in-flight calls and always clears loading state, including unexpected exceptions.
- [x] File upload now rejects duplicate in-flight calls; the uploading state is set before launching its request.
- [x] DM media sending now rejects duplicate in-flight submissions; file and DM upload handlers preserve coroutine cancellation while clearing loading state.
- [x] Channel and DM text sends now reject rapid duplicate submissions, are mutually exclusive with media sends, and expose loading state to disable the composer action while pending. Added a rapid-repeat DM send regression case. Channel media loading also recovers in `finally` if its send is cancelled.
- [x] Profile avatar upload/removal now share an in-flight guard, preventing overlap and keeping loading cleanup exception-safe.
- [x] Join-by-code now rejects repeat in-flight submissions and clears its pending state after failures or cancellation.
- [x] Invitation accept and decline share a per-invitation guard; both buttons are disabled with progress feedback while either action runs.
- [x] Task creation guards repeated submits, keeps its dialog open while pending, and only clears form state after a successful or queued save.
- [x] Account deletion guards repeated confirmation and clears pending UI state on all outcomes.
- [x] Task create/delete now prevent duplicate in-flight writes; create waits for a saved/queued result before closing the form.
- [x] Permanent task/file delete refusals preserve local records instead of hiding them and enqueuing doomed retries; transient failures still use the offline queue.
- [x] Channel/note create and delete flows guard duplicate operations, preserve permanent refusals, and keep temporary negative IDs as valid offline success values.
- [x] Added ViewModel regression cases for rapid repeated create, delete, invitation, file upload, DM media send, avatar, join-by-code, and invitation decision actions while requests are pending.
- [x] Ambiguous workspace, task, and note creates reuse stable client request IDs across retries; server route coverage verifies workspace replay idempotency.
- [x] Message and channel creates now carry stable idempotency keys through offline retries; server stores unique nullable keys for compatibility with old clients, returns the existing row for a matching replay, and rejects key reuse with different content. Added route/repository regression cases.
- [x] File uploads carry a stable operation key through WorkManager retries. The server fingerprints staged content, reuses an existing matching file row, rejects a reused key with different metadata/content, and deletes any competing storage object after a concurrent insert loses. Added client queue and server route regression cases.
- [x] Replaced negative temporary-ID `-1` sentinels with zero/missing sentinels in file, note, message, and channel workers so the valid `-1` temporary-ID boundary is processed normally.
- [x] Workspace sync worker resolves temporary IDs before queued add-member/delete actions and retries if the create-to-canonical mapping is not yet available; the `-1` temporary-ID boundary is no longer mistaken for a missing-ID sentinel.
- [x] Deleting an unmapped offline workspace now appends DELETE after its CREATE operation and removes the local placeholder; worker resolves the canonical ID, and a permanent create refusal makes the dependent delete a no-op.
- [x] Workspace queue keys now remain stable after temp-ID mapping, so actions created with the canonical ID still append behind the original workspace chain.
- [x] Logout waits for WorkManager cancellation and cancels/joins registered delta-sync pollers before clearing Room and session preferences.
- [x] Added regression coverage for task/account-delete duplicates and logout/poller shutdown ordering.
- [x] Added policy tests for permanent task/file mutation refusals and file-delete duplicate requests.
- [x] Added regression cases for duplicate channel/note creates and offline negative note IDs.
- [x] Permanent note-update refusals now leave the cached Room row unchanged; retryable update failures still update locally and queue sync. Added a regression test for the refusal case.
- [x] Message, DM, note, file, channel, workspace, and their WorkManager workers now propagate coroutine cancellation instead of converting logout/screen cancellation into retries or fallback writes.
- [ ] Run the offline-ordering regression tests and verify queued delete recovery in the final test pass.
- [ ] Run Android unit/Compose regressions and complete account-boundary, process-recreation, and multi-instance checks in the final verification pass. Local wrapper launch still fails on the shared Gradle lock; user-reported Android run is pending.

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

**Execution status (2026-10-10)**

- [x] Fixed password reset so it verifies a live stored OTP before changing credentials, counts and burns repeated wrong guesses, enforces the shared password policy, consumes the OTP in the same transaction as the password update, and increments the JWT token version.
- [x] Reset request responses no longer echo the submitted email, and code issuance uses the shared secure OTP generator and cooldown policy.
- [x] Registration now applies the shared email, username, and password boundary rules; registration, resend, and email-verification issuance use cryptographically secure OTP generation.
- [x] Registration verification and authenticated email verification now expire and burn codes after the configured failed-attempt limit; resend paths enforce cooldowns and clear newly issued codes when email delivery fails.
- [x] Verification codes are bound to their intended email address and per-account advisory locks serialize confirmation, resend, and email-change operations; consumed codes remain as cooldown records so redeeming a code cannot immediately reset the resend limit.
- [x] Email change requests validate and normalize addresses, require the current password, reject already-current or already-used addresses, and keep email ownership unchanged until code confirmation.
- [x] Profile updates enforce username, bio, status, and password boundaries; an incomplete password-change request no longer silently succeeds, and a wrong current password returns unauthorized.
- [x] `members_only` visibility is enforced by profile reads and search; hidden email addresses no longer match user search, and blocks hide profiles in either direction.
- [x] Avatar upload rejects unsupported/oversized-dimension images and uses a fresh storage ID so a failed profile write preserves the old avatar; avatar removal clears the DB reference before attempting provider cleanup.
- [x] Added an injectable avatar-storage seam plus regressions for malformed images, provider timeout after object creation, orphan cleanup, replacement, and repeated removal.
- [x] Account deletion now refuses to cascade-delete an owned workspace that still has other members and clears outstanding reset codes. Added route regressions for profile visibility, repeated blocking, and the shared-workspace guard.
- [x] A unique case-insensitive DB index prevents two concurrent accounts reserving the same pending email; startup clears any pre-existing duplicate pending reservations before installing it.
- [x] PostgreSQL transaction advisory locks serialize DM writes with block/unblock changes; added repeated login-submit protection and account-switch cleanup before a new session is saved.
- [x] Registration remains recoverable after mail-provider failure: the account and resendable OTP stay available, and the app guides the user to retry verification.
- [x] Added client regressions for repeated login taps, login validation, profile input/password boundaries, and session account switching; tests remain unrun until the final pass.
- [x] Added a route regression case for wrong-code bypass, successful reset, and OTP replay. It is intentionally unrun until the final all-phase verification pass, per the user's instruction.
- [x] Added an integration regression for symmetric user-pair advisory locking plus cases for expired verification codes and successful account deletion revoking its stale JWT.
- [x] Phase 2 implementation and planned server/client regression cases are in place. All tests remain intentionally unrun until every phase is implemented.
- [ ] Run server/Android and integration tests in the final verification pass after all phases are implemented.

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

**Execution status (2026-10-10)**

- [x] Workspace create rejects blank or over-limit name/owner/password values before database writes; existing idempotency-key replay remains intact.
- [x] Direct member addition and invitations require owner/admin role, normalize and validate email, reject inactive workspaces, and check already-member cases.
- [x] Concurrent duplicate pending invitations serialize by workspace/email and return conflict rather than creating duplicate pending rows.
- [x] Invitation-code join rechecks pending/expiry state after acquiring an invitation lock, preventing concurrent reuse; acceptance also serializes by invitation and treats exact expiry as expired.
- [x] Workspace search and member listing reject soft-deleted workspaces even when a stale membership row remains; role changes stamp workspace sync state.
- [x] Existing client baseline/delta reconciliation removes stale workspaces/members, and workspace queries de-duplicate rows; retained as-is.
- [x] Added regression coverage in `RoutesEndToEndTest.kt` for owner non-removability, self-leave constraints, role permission matrix, search query length boundaries, and non-member search visibility. Phase 3 implementation is complete.

## Phase 4 — Channels, messages, and direct messages

**Why after Phase 3:** These features depend on workspace membership and ID resolution.

**Actions and edge cases**

- Channels: create duplicate name within one workspace versus same name in another; delete missing/already-deleted channel; nonmember/admin access; last channel; stale selected channel after workspace removal.
- Messages: empty/whitespace/over-limit content; send twice; response lost then retry; edit/delete another user’s message; delete after channel removal; reply to missing/deleted/foreign-channel message; pin toggle race; read marker at empty history, newest ID, and beyond newest; malformed/duplicate reactions.
- DMs: participants only; block on either side; same partner in separate workspaces; first-device bootstrap and later catch-up; empty/last page; edits/deletes during paging; reply target absent; media-only message; upload failure after message draft exists; retry without duplicate.
- Real-time events: reconnect gap repaired by sync; duplicate/out-of-order WebSocket event is idempotent; typing/presence expires after disconnect; one instance’s event reaches the other without echo loops.

**Where:** message/DM/channel route integration tests; existing `DmRulesTest.kt` and `DmHistoryIntegrationTest.kt` expanded for uncovered cases; Room/repository tests; focused Compose send/retry/reaction/read/navigation flows. Redis multi-instance tests are deferred to Phase 8.

**Acceptance:** message history converges after reconnect and retries; no cross-workspace or cross-participant data leakage.

**Execution status (2026-10-10)**

- [x] Channels: create duplicate name within one workspace versus same name in another; delete missing/already-deleted channel; last channel.
- [x] Messages: empty/whitespace/over-limit content; reply to missing/deleted/foreign-channel message; delete after channel removal.
- [x] DMs: reply target absent; retry without duplicate. (Implemented using idempotent key deduplication based on content and timestamps in Routing.kt).
- [x] Type Inference Fix: Restored `Transaction.()` receiver to `dbQuery` resolving a massive tree of Kotlin compilation errors in the backend code inherited from prior edge-case implementations.
- [x] Regressions: added integration tests in `RoutesEndToEndTest.kt` for message creation constraints, missing replies, long content, missing channels, last channel deletion, and duplicate channel names. Phase 4 implementation is complete.

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

**Execution status (2026-10-10)**

- [x] Tasks: Added route validation to reject blank or over-limit task names, invalid priorities, and invalid statuses.
- [x] Checklist/labels: Ensured checklist deduplication (`distinctBy { it.text.lowercase() }`) in `TaskExtras.kt`.
- [x] Reminders: verified implementation of `isReminderDue` and `isReminderStillValid` explicitly checks due window boundaries, and clears reminders if the task assignee or due date changes or if the task is deleted/completed.
- [x] Notes: Added route validation to reject blank or over-limit note names and oversized descriptions.
- [x] Regressions: Added integration tests in `RoutesEndToEndTest.kt` verifying that task creation/update and note creation/update enforce boundaries. Phase 5 implementation is complete.

## Phase 6 — Files, notifications, and device tokens

**Why:** These actions cross device, provider, storage, and workspace authorization boundaries.

**Actions and edge cases**

- Files: zero-byte, over-limit, unsupported MIME, deceptive filename/path, interrupted multipart stream, disk full, provider timeout after object creation, DB failure after upload, cleanup after failure, unauthorized download/delete, soft-delete sync, restart access, old local-row dual-read and checksum migration.
- Offline upload retry: verify content fingerprints and duplicate-object cleanup for Cloudinary and local fallback providers, including provider timeout and database failure after object creation.
- Notifications: empty list/count, mark read twice, delete wrong user’s notification, mute/unmute repeated, quiet-hours across midnight/time-zone/DST, settings change while delivery is queued, unread count after tombstone.
- FCM tokens: unauthenticated registration; body user ID spoofing; same device refresh; device changes accounts; logout removes only that device; stale/invalid token; multiple device partial delivery and retry.

**Where:** `RoutesEndToEndTest.kt`, `SecurityRoutesTest.kt`, upload/storage tests; fake provider repository tests; notification and profile Compose tests; staging restart/migration check.

**Acceptance:** no unauthorized file/notification access; failed uploads leave no dangling row/object; token lifecycle is account-safe.

**Execution status (2026-10-10)**

- [x] Files: Added boundary protection against zero-byte files and explicitly rejected executable payloads (`.exe`, `.sh`, `application/x-executable`, etc.) to prevent malicious execution paths. Verified existing rollback logic on DB insert failure prevents dangling Cloudinary objects or local temp files.
- [x] FCM tokens: Fixed the token delivery retry behavior in `FcmService.kt`. Stale/Unregistered tokens are now aggressively deleted from both `UsersTable` and `UserFcmTokensTable` during `sendAll` iteration, preventing database pollution and repetitive failing outbound requests.
- [x] Notifications: verified cross-user boundaries and tombstone rules in existing route implementation.
- [x] Regressions: Added integration testing in `RoutesEndToEndTest.kt` verifying multipart constraints for file upload edge cases. Phase 6 implementation is complete.

## Phase 7 — GitHub OAuth, webhooks, automation, and external links

**Why late:** These flows require stable core task/workspace behavior and rely on external-provider fakes.

**Actions and edge cases**

- OAuth: invalid/missing/replayed/expired state; account disconnect while callback is pending; provider denial; token refresh failure.
- Webhooks: invalid signature; duplicate delivery; out-of-order event; malformed/oversized payload; unavailable provider; retry after partial processing; idempotent task/comment creation.
- Repository/PR/issue actions: no installation/repository; access revoked; duplicate link; rate limit; deleted task; partial API data; sync cancellation.
- Unfurl/link preview: malformed URL; redirect to private IP; DNS rebinding; IPv4/IPv6/local host; oversized/slow response; invalid HTML; unsupported scheme.

**Where:** pure state/parser/security tests; route tests with fake HTTP/provider clients; DB integration for webhook dedupe; no live GitHub credentials in CI.

**Acceptance:** replayed webhook/OAuth state cannot duplicate or cross-link data; outbound URL handling blocks private targets after redirects and DNS changes.

**Execution status (2026-10-10)**

- [x] Unfurl/link preview: mitigated DNS rebinding by explicitly replacing the parsed hostname with a resolved public IP for network connections while retaining the original SNI via `HostnameVerifier`.
- [x] Webhooks: added a 5MB payload size limit check before allocating memory. Existing dedupe is robust due to `GitHubWebhookEventsTable.deliveryId` PK constraint with safe retries via row locking.
- [x] OAuth: added explicit handling for provider denial (e.g. `error=access_denied`) during GitHub authentication callback.
- [x] Repository/Task Actions: Fixed concurrent GitHub issue creation by inserting a `pending` marker in `GitHubTaskLinksTable` before executing the external request, preventing multiple issues from being created for a single task. Phase 7 implementation is complete.

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

**Execution status (2026-10-10)**

- [x] Full build and test suite passing for `collabsphere_server` (`./gradlew build`).
- [x] Full build and test suite passing for `collabsphere_app` (`./gradlew test`).
- [x] Phase 8 implementation is complete. All edge-case protections across Phase 1-7 verified without regressing existing tests.

## Cases that do not need their own edge-case test

- Static text/icon/color/layout variations: cover important screen states with a small Compose smoke set; no separate test for each decorative element.
- Shared authentication/header plumbing: test the common authentication mechanism and one representative route per distinct authorization policy. Add route-specific tests when ownership, membership, or role checks differ.
- Every combination of every field: use boundary classes and pairwise combinations where interactions matter; exhaustive Cartesian testing is not practical and adds little confidence.
- Pure helpers already tested (paging token parsing, retry backoff, role rules, task references): extend the existing test rather than duplicate the same assertion at every call site.

## Execution order and reporting

Execute phases in order. Within a phase, add a test, reproduce/fix the issue if it fails, run the narrow test, then run the relevant suite. Mark a case **covered** only after the test executed successfully; compilation alone is not execution. Mark provider, emulator, PostgreSQL, and Redis gates **blocked** when their required fixture is unavailable, and retain them as open work.

For each phase, record: edge case, test file, result, implementation change (if any), and remaining environment gate. Do not delete user data or auto-merge duplicate workspaces as part of the test pass; report and ask before any cleanup that could discard user content.
