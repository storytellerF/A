# Project Development Rules

## Global Principles
- Use JDK 21. All modules must target Kotlin/JVM 21.
- Prefer KMP-shareable code. Handle platform differences with `expect`/`actual` or platform source directories.
- Keep the Gradle configuration cache enabled (`org.gradle.configuration-cache=true`) and avoid side effects or IO during configuration.
- Logging: use Napier in shared/app code. Initialize a logging tree only when needed for tests or debugging.
- Static analysis: run Detekt with `config/detekt/detekt.yml`. Do not create or use Detekt baseline files; every active finding must be fixed in source code. Do not add or broaden suppressions to bypass findings. When Detekt rules conflict, disable only the conflicting rule or narrowly scoped configuration necessary to resolve the conflict.
- Detekt permits public library entities and public data classes only in the configured `api` and `shared.model` protocol/model scopes and the `client/core` request boundary; keep these exceptions narrowly scoped in `config/detekt/detekt.yml`.
- New Kotlin source files must start with the private-project license header configured by Detekt.
- Coverage: generate Kover reports when needed.
- Environment/secrets: `app/composeApp` and `panel/composeApp` inject values from `deploy/{flavor}.env` through BuildKonfig. `app/androidApp` reads the same env file for deep links. `app/androidApp` and `panel/androidApp` use flavor/buildType values to configure application IDs and build types. Always pass `-Pserver.flavor` and `-Pserver.buildType`.
- Commit messages: start with a verb in English or Chinese and include the module/scope, for example `client/core: add getAllUsers request`.
- Do not use Chinese method names.
- Do not use bare `InputStream`/`OutputStream`; use `BufferedInputStream`/`BufferedOutputStream`.
- Do not use `println` or `System.out.println` in code.
- When editing files, preserve the existing line endings.
- When adding dependencies, verify the correct group and artifact from GitHub or the upstream repository. If the dependency cannot be found, do not downgrade versions.

## Module Responsibilities
- **api**: Defines REST API endpoints, query/path models, and the admin `AdminApi` (`/admin/*`).
- **client/core**: Session management and request wrappers using unified `serviceCatching`.
- **client/composeCore**: Shared Compose UI components, media helpers, cross-platform UI utilities, and related actual implementations.
- **client/asciidoc-parser**: AsciiDoc preview HTML generation used by markdown rendering.
- **client/model-storage**: Abstract storage interfaces and collection identifiers.
- **client/room**: Room-based storage implementation and aggregation entry points.
- **shared**: Cross-platform business models and utilities.
- **app/composeApp**: Shared user-facing Compose UI, actual implementations, and services.
- **app/androidApp** / **app/desktopApp** / **app/cliApp**: User-facing platform entry points.
- **panel/composeApp**: Shared Compose UI for the admin panel.
- **panel/androidApp** / **panel/desktopApp** / **panel/cliApp**: Admin panel platform entry points.
- **backend/***: Backend core functionality, storage, search, and related services.
- **cloud/***: Core service modules that integrate backend functionality.
- **bot/***: Bot modules.

## Code Style And Practices
- **Kotlin**: Do not expose mutable collections in public APIs. Prefer `data class`, `sealed interface`/`sealed class`, and `suspend` + `Result<T>`. Use verb-object naming for functions.
- **Compose**: Name `@Composable` functions in PascalCase. The general lowercase function-naming checks exclude `composeApp` source trees because Compose uses a different naming convention.
- **Architecture**: UI -> `SessionManager` request extensions -> API endpoint. Add local storage and paging when appropriate.
- **GlobalDialog vs GlobalTask**: Use `GlobalDialog` for user-initiated actions where success or failure is important to the user (e.g., sign-in, publishing content, profile updates). Use `GlobalTask` for user-initiated actions where failure can be tolerated but the operation must not be cancelled by scrolling or page switching (e.g., adding reactions, toggling favorites). When implementing client UI handlers, consider adding `GlobalDialog` or `GlobalTask` as fields in the handler/controller class.
- **Paging**: Store cursors consistently in `RemoteKeyStorage` (`PRE_COLLECTION`/`NEXT_COLLECTION`).
- **Logging**: Instrument external network requests through `serviceCatching`. Avoid noisy logging in hot paths.
- **Tests**: Write tests whenever possible. Client-side non-UI tests go under `src/headlessTest/kotlin`. Compose tests should follow https://kotlinlang.org/docs/multiplatform/compose-test.html. End-to-end tests use `scripts/test_scripts/build-and-test.sh --e2e`, which runs the Appium runner modules' explicit `appiumTest` tasks and CLI E2E tasks; no Gradle property is required.

## Static Checks
- After code changes, run `./gradlew assemble --console=plain` to check for compilation errors.
- After compilation checks, run `./gradlew detekt --console=plain` for static code style checks.
  - On Windows, run this through Git Bash.
- Detekt allows function names up to 50 characters. Appium and CLI E2E source sets are exempt from function naming and length rules so backtick test names can remain behavior-descriptive. Keep production function names concise even when the configured limit permits longer names.

## Tests
- Actively test affected modules.
    * For small changes, use `./gradlew :module:test --console=plain`.
    * For the full regular unit test suite, use `./gradlew check --console=plain` (excluding device-dependent Compose and Appium tests).
    * For end-to-end tests, use `./scripts/test_scripts/build-and-test.sh --e2e --plain`; this runs Appium and CLI E2E suites.
    * For Compose common tests under `device_based`, use a real device with `./gradlew :module:connectedAndroidTest` or run `./gradlew :module:jvmTest`.
    * For Compose UI changes, run `./gradlew validateDebugScreenshotTest` for snapshot tests.
- Keep Compose screenshot tests context-free: do not inject app runtime `CompositionLocal` dependencies such as session managers, dialog controllers, or navigation contexts. Cover components that require those dependencies in another test layer.
- Tests are meant to reveal problems. If a test finds a problem, fix the problem instead of working around it in the test.
- Do not add extra test steps into a test case unless there is an actual dependency between the steps.
- Extract repeated test steps into helper methods.
- Android Appium tests require a real device or emulator and cannot run concurrently on the same device. The caller must keep the device-side lock leased for the entire `--e2e` Appium sequence and refresh its expiry until release; `build-and-test.sh` does not acquire or release that lock.
- `build-and-test.sh` only orchestrates device/UI and end-to-end tests through `--android`, `--desktop`, `--compose`, and `--e2e`; run ordinary unit tests directly with Gradle.
- Appium sources and runners belong to the independent `*Appium` modules under `src/appiumTest/kotlin`. They depend on built or installed application artifacts, not application implementation code.
- Appium `appiumTest` tasks must disable Gradle up-to-date reuse so every explicit E2E invocation drives the target again.
- Appium tests compose a target helper (`AppAppiumHelper` or `PanelAppiumHelper`) with a platform helper (`AndroidAppiumHelper` or `DesktopAppiumHelper`). Keep concrete test methods as calls to shared `test*ByHelper` functions; target helpers create sessions and platform helpers own launch, cleanup, and log collection.
- Cloud server integration tests and Appium E2E tests use PostgreSQL with dedicated filesystem RPC and Lucene RPC containers. Do not reintroduce H2, MinIO, or Elasticsearch into these test topologies.
- Keep filesystem and Lucene RPC services business-independent. Translate business search models in backend adapters; clients download object contents directly from filesystem HTTP rather than through server.
- Define filesystem and Lucene RPC contracts in their respective `services/*-protocol` modules. Service runtime code must not depend on `backend/core`, `shared`, or `api`; map business storage/search types in backend adapters. Keep service configuration independent of flavor env files.
- Lucene RPC uses explicit named fields with independent indexing, storage and DocValues settings, not business JSON payloads. Do not inject fixed ID fields or expose ID-specific RPC operations; adapters declare business identifiers, queries and sort fields. Keep `Lucene*Document` conversion classes in backend adapters.
- Gradle owns test image construction: `distTar` -> `copyTestDockerDistribution` -> `buildTestDockerImage`, using the root Dockerfile. Testcontainers only starts the resulting images. Keep image tasks in the shared convention plugin, and use the QEMU skill's Docker CLI proxy on Windows rather than a separate test Dockerfile.
- Do not reintroduce the removed H2 service or Docker target; `r2dbc-h2` supports only file and memory protocols, not the remote database topology required here.

## Additional AI Collaboration Rules
- Deploy through `scripts/service_scripts/start-service-in-docker.sh`; compile deployment artifacts inside Docker, not on the host.
- Make the smallest necessary changes. Prefer extending `client/core` and `api`; do not casually change public models.
- Do not create files outside the repository. Do not expose sensitive values from `deploy/*.env` in logs, documentation, or commits. Source-transfer archives must also exclude `dev/preset/secrets`, credential files and private-key contents; inspect the final archive before transfer and preserve Git executable modes.
- For changes involving the configuration cache, avoid reading external environment state during Gradle configuration.
- Configure shared Kotlin/JS and Wasm Yarn root extensions only in the root build; subprojects must not override lockfile locations. Keep JS and Wasm lockfiles separate under `kotlin-js-store` and regenerate the affected lockfile after npm dependency changes.
- Keep existing tests compatible. If tests must be updated, explain the migration reason and steps.
- Save rule-related knowledge learned while coding or scanning the project to this file.

- Android KMP device tests must include `commonTest` when exercising shared Compose tests and declare their test-only host activity in `src/androidDeviceTest/AndroidManifest.xml`. Keep the device awake during UI testing and restore temporary power settings before releasing its lease.

## Troubleshooting Quick Reference
- Network errors: check `client/core/*Request.kt` `serviceCatching` logs and `AdminApi`/`CustomApi` routes.
- Paging errors: check how `RemoteKeyStorage`/`RemoteKeyRoomStorage` saves and reads cursors.
- Data not persisted: check `*RoomStorage.save`/`observeData` implementations and `commonJson` serialization.

- Desktop Appium suites must run sequentially on one desktop. Windows uses DesktopDriver with the Java accessibility agent and a JVM-local Preferences factory; do not rely on java.util.prefs.userRoot to isolate Windows test state. Preserve the agent discovery temp directory until premain completes, then isolate application data. Set app.protected.storage.dir inside the test runtime directory; the Preferences factory does not isolate protected-value files.
- Linux Appium element handles must retain stable AT-SPI object paths. When a target disappears during navigation, return a stale-element error and re-locate the control; never fall back to cached coordinates for a disappeared target. Text input must pass and resolve the saved object path directly; never reselect by result index or fall back to another editable control.
- Shared Wasm browser test configuration belongs to the wasm-browser-test-interop convention. A later useKarma call replaces the framework configuration, so do not override it in consuming modules; track loader files as task inputs and preserve the configuration cache.

- JUnit host test initialization bases must be abstract so test discovery does not run the base itself. Keep the two conflicting Detekt abstract-class rule exceptions scoped to PlatformHeadlessTest files.

- Diagnose QEMU forwarding with guest/host application probes and the installed binary's polling capacity. Listener binding or successful TCP connect alone does not prove forwarding; do not report SSH bridging as a fix for direct-forward failures.
- For intermittent connection-refused/reset failures, compare guest health with concurrent host connection probes. Serial health or passing focused reruns alone does not establish cross-test state interference; distinguish per-listener backlog from total polling capacity.

- The QEMU plugin now uses verified upstream gvproxy over loopback TCP. Keep Docker automatic publish ranges aligned with explicit gvproxy rules, use gvproxy DNS for guest/containers, and coordinate both processes during recovery. Confirm the shared VM is idle before migration/restart. Never use SSH networking bridges, diagnostic DLL replacement, added connection retries, skipped tests or relaxed timeouts as a permanent forwarding fix.

- Preserve Git executable modes when copying Windows sources into an isolated Linux test checkout; ordinary Windows archive metadata can remove launcher execution permissions.
- Keep Android test app shutdown and reverse-port replacement in the fixture order documented in DEVELOPMENT.md; noReset must not leave an old app requesting removed host targets.

- Run unattended Linux desktop E2E through the isolated display runner documented in DEVELOPMENT.md. Do not use a zero-size/disconnected Xwayland session or change render settings, retries, skips, or timeouts to hide an unresponsive accessibility tree.

- Resumable upload sessions require real SHA256 and CRC32C values. Keep generic file-operation quota locks separate; never fill required digest fields with dummy values. OSS compose owns backend-specific verification; follow DEVELOPMENT.md for MinIO's verified-source CRC32C combination and filesystem's streaming SHA256 check.

- RPC adapters acquire a valid connection once per business operation. Pin upload/download sequences to that proxy, preserve caller cancellation, and never automatically replay failed operations. Recovery tests must reuse the same adapter across independent service restarts.

- Authentication uses Redis-backed opaque Cookie sessions. Never restore revoked/expired sessions by silently
  signing in with saved keys, replay mutations, or renew activity from background polling/WS heartbeats.
  Keep user and panel session namespaces separate and use protected native Cookie storage.

- Persist GUI private keys/account history through the protected-value boundary. Do not read or migrate old
  Settings history; new records must verify encrypted writes before reporting success. Never
  downgrade to plaintext when an OS secret store is unavailable. Browser private-key encryption does not
  replace the HttpOnly Cookie boundary. Run Windows Detekt separately from compilation, with one worker.

- Provide sign-in storage through backend/core's SigninSessionServiceFactory registration. Keep driver
  dependencies and the Redis implementation in backend/redis; cloud/runtime owns only the Ktor adapter.

- Keep host E2E runtime JARs immutable for the entire test process. Finish compilation before UI execution; do not rebuild shared fixtures from another command while tests run in the same checkout.

- Keep host-test fork increases scoped to the reviewed allowlist in root build.gradle.kts. Preserve shared-device/desktop serialization. Test image contexts must be isolated per module and immutable during each build; keep source-build shared writable cache locks. Detekt 2.0.0-alpha.6 Worker API cannot configure its process heap and fails Compose analysis; keep it disabled until that upstream limitation is resolved.

- Before expanding host-test concurrency, verify temporary paths, ports and persistent namespaces are isolated. A module with a pure test and Docker/native-model integration tests is not a pure-test module; do not expand its entire test task without reviewing those fixtures. Inactive modules excluded by settings.gradle.kts do not need concurrency configuration.

- Compare build concurrency using the same explicit tasks and rerun/cache policy. Two-module compilation, six-module compilation and full assemble timings are different workloads and cannot establish a parallelism speedup or regression.

- Verify VM state with sufficient read permissions before concluding it is stopped; sandbox probes can report inaccessible services. When applying skill templates to a legacy idle guest, normalize generated Linux scripts to LF and preserve the default Docker publish pool while restoring disjoint outbound ports. Never infer published plugin versions from a dirty local checkout; check the configured marketplace origin and branch.

- Create upload and chunk staging files atomically with unique names per request; client filenames and upload IDs are not unique across processes. Keep body reception inside the cleanup boundary. Real RPC shutdown waits invoked from runTest must use a real dispatcher so virtual time cannot expire before IO completes; preserve the existing timeout duration.

- Linux AT-SPI can append comma-separated badge suffixes to accessible labels. Match whole labels within the merged name, not arbitrary substrings. Authentication E2E needs secret-tool and an unlocked native keyring on the isolated test D-Bus session with isolated data directories.

- Keep E2E selector text in English and configure test locales explicitly. Restore temporary app-language overrides; preserve product translations. Capture Android app locales before clearing data, because pm clear also removes the override.

- Never pop the last root navigation entry. Dismiss modals through platform back/Escape; do not make an entire dialog surface clickable solely for tests, because the Wasm overlay can intercept child controls.

- Account-menu confirmations must replace the current modal instead of nesting another Dialog inside it. Keep sign-out request ownership outside the dismissed modal; clear protected active-session history only after server sign-out succeeds.

- Keep private deployment host aliases, addresses, usernames, absolute host paths and session-specific verification records out of tracked files, commit messages and public PR text. Record reusable findings with generic environment names and placeholders.

- Preserve raw ML-DSA/ML-KEM key bodies and ciphertext layout during crypto changes.
  JVM KEM uses Java 21 `javax.crypto.KEM`; Android uses BC `KeyGenerator` with
  `withNoKdf()` because its SDK lacks that API. Keep cross-platform compatibility
  coverage in shared headless tests; do not silently introduce KDF or DER encoding changes.

- Shared Android device tests reuse headless tests through the test source-set tree;
  configure `withDeviceTestBuilder { sourceSetTreeName = "test" }` and provide
  a device-specific abstract PlatformHeadlessTest actual without Robolectric.

- Read-only users retain authentication and read access. Enforce write restrictions for HTTP mutations and WebSocket topic creation; notification-room recipients remain invited, with disabled message input.

- Snapshot PKCS#12 Base64 is secret configuration. Preserve existing key bytes during migration, validate supplied credentials without a file fallback or replacement-key generation, and keep decoded Base64 key material in memory.

- Keep crypto tests self-contained with synthetic vectors or generated keys; missing external preset directories must not silently pass a test. Legacy CryptoJvm ECIES/CBC and shared P256 encryption use different ciphertext formats, so verify each format independently unless an explicit compatibility contract exists.

- Adaptive Home uses one section pager across rail/bottom navigation. Measure shared lists and search
  results against their own content constraints, and preserve mounted reading state across resizes.
  Keep Home context out of shared lists. Run `:app:composeApp:adaptiveUiTest` for isolated layout/state
  fixtures; regular host tests continue excluding device-based UI tests.

- On Windows, run assembly, Detekt and screenshot/host UI verification sequentially when they share
  Android runtime JARs. R8 and preview renderers can hold those files open during another build.

- Room activity is an API projection, not a RoomInfo schema extension. Filter joined membership before
  grouped unread pagination; apply activity cursors to the per-room maximum message ID, not individual
  message rows. Preserve unread-room versus unread-message count meanings and existing private-topic
  decryption. See DEVELOPMENT.md for the data contract and integration checks.

- Activity previews must remain ephemeral and session-keyed. Room lists/search return activity with
  the same page; never add a second viewport/batch fetch or persist decrypted previews. Preserve local
  RoomInfo caching and route successful pin/read changes through the event bus. Total unread messages
  come from the backend's complete joined-room aggregate, never a client sum of loaded pages.

- Keep a permanent latest-edge anchor before conditional room loading rows, so new messages remain
  visible at the latest edge while history keeps its message-key anchor.
- Update room read markers from the maximum visible topic item key, not LazyColumn indices or the
  first displayed row; paging spacers and loading rows occupy indices. New messages can replace the
  visible topic without changing the scroll index.
- Verify an interactive, unlocked foreground desktop before Windows Appium keyboard tests. An accessible
  AWT tree does not prove OS input is available; do not bypass empty-editor failures with fake content.
- Refresh the Home unread navigation badge on read/message/membership events and lifecycle resume;
  keep badge labels localized and test their presence/absence alongside row counts.
- Keep read-marker upserts monotonic and atomic; history navigation and late requests must not rewind
  the stored marker. Verify both exact counts and the legacy unread-room aggregate.
- Native room-cell accessibility must preserve merged visible content; keep platform-specific browser
  test-overlay labels separate. Resize Windows Java-agent test windows through their verified native
  handle and convert viewport dimensions for window DPI.

- Browser Appium previews need explicit text/action bridge nodes; keep the bridge disabled outside test
  mode and preserve native merged accessibility. Select equal-stack overlay nodes in foreground paint
  order so dialog controls win over identically labelled background controls.
- Measure actual browser content dimensions after responsive chrome settles. Native Firefox compact
  windows may be limited to 500px; keep strict viewport/draft assertions and document the tested sizes.

- Collection refresh must clean the requested named collection, never its default detail cache. Verify
  selected-list deletion while preserving detail IDs/aliases and unrelated lists using real SQLite.
  Keep host-test database scopes unique and temporary storage under the module build directory.

- Browser semantics must publish changed labels even when geometry is unchanged.
- Async SQLite transaction finalizers must complete under cancellation so Room can recycle its pool
  permit. Close allocated connections/statements when cancellation prevents delivery; new operations
  still honor cancellation, and cleanup must not replay business writes.

- Treat server paging tokens as opaque and preserve server item order. Keep database offset keys
  separate from network tokens. Refresh through the existing Pager/RemoteMediator, retain the loaded
  range and placeholder counts, and atomically replace room rows before publishing preview state.
  Anchor-preserving mapped PagingSources calculate refresh keys from previous-generation pages
  without relying on per-instance caches. Verify multi-page event refresh with shuffled IDs and opaque tokens.

- Fingerprint deployed Wasm entry scripts and require cache validation for HTML. During
  upgrade acceptance, compare browser-loaded Wasm filenames with the image; a successful
  fresh HTTP probe does not prove an existing tab has loaded the new client.

- Isolated deployment checkouts must reconnect existing filesystem/Lucene bind mounts
  before startup. Reload BunkerWeb upstream resolution after container replacement;
  container health alone cannot establish correct domain routing or data continuity.

- Version Compose web resource URLs as well as the entry script. Cached translation
  files paired with new generated byte offsets can display unrelated strings or corrupted text.

- Pin external Dockerfile base images and build frontends by verified manifest digest.
  Treat transient Docker Hub failures with bounded retries before changing build configuration.
