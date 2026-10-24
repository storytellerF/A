# Project Development Rules

## Global Principles
- Use JDK 21. All modules must target Kotlin/JVM 21.
- Prefer KMP-shareable code. Handle platform differences with `expect`/`actual` or platform source directories.
- Keep the Gradle configuration cache enabled (`org.gradle.configuration-cache=true`) and avoid side effects or IO during configuration.
- Logging: use Napier in shared/app code. Initialize a logging tree only when needed for tests or debugging.
- Use the canonical imported-class Logback XML form documented in DEVELOPMENT.md.
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
- **result**: Business-independent KMP result container and error diagnostic contracts.
- **shared**: Cross-platform business models and utilities.
- **app/composeApp**: Shared user-facing Compose UI, actual implementations, and services.
- **app/androidApp** / **app/desktopApp** / **app/cliApp**: User-facing platform entry points.
- **panel/composeApp**: Shared Compose UI for the admin panel.
- **panel/androidApp** / **panel/desktopApp** / **panel/cliApp**: Admin panel platform entry points.
- **backend/***: Backend core functionality, storage, search, and related services.
- **cloud/***: Core service modules that integrate backend functionality.
- **bot/***: Bot modules.

## Code Style And Practices
- **Kotlin**: Do not expose mutable collections in public APIs. Prefer `data class`, `sealed interface`/`sealed class`, and `suspend` + `CustomResult<T, ScenarioError>`. Use verb-object naming for functions.
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
- Appium tests pass target/platform helpers to the typed `AppiumTestBase` constructor. Shared test flows are base extension functions that read its helpers and JUnit test name; concrete test methods pass only scenario options. Target helpers create sessions and platform helpers own launch, cleanup, and log collection.
- Cloud server integration tests and Appium E2E tests use PostgreSQL with dedicated filesystem RPC and Lucene RPC containers. Do not reintroduce H2, MinIO, or Elasticsearch into these test topologies.
- Keep filesystem and Lucene RPC services business-independent. Translate business search models in backend adapters; clients download object contents directly from filesystem HTTP rather than through server.
- Define filesystem and Lucene RPC contracts in their respective `services/*-protocol` modules. Service runtime code must not depend on `backend/core`, `shared`, or `api`; map business storage/search types in backend adapters. Keep service configuration independent of flavor env files.
- Lucene RPC uses explicit named fields with independent indexing, storage and DocValues settings, not business JSON payloads. Do not inject fixed ID fields or expose ID-specific RPC operations; adapters declare business identifiers, queries and sort fields. Keep `Lucene*Document` conversion classes in backend adapters.
- Gradle owns test image construction: `distTar` -> `copyTestDockerDistribution` -> `buildTestDockerImage`, using the root Dockerfile. Testcontainers only starts the resulting images. Keep image tasks in the shared convention plugin, and use the configured Docker-compatible backend rather than a separate test Dockerfile.
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

- Desktop Appium suites must run sequentially on one desktop. Windows uses DesktopDriver native UIA (`javaSwing=false`) and a JVM-local Preferences factory; do not rely on java.util.prefs.userRoot to isolate Windows test state. Do not reintroduce the removed AWT agent/bytecode compatibility patch. Set app.protected.storage.dir inside the test runtime directory; the Preferences factory does not isolate protected-value files.
- Linux Appium must enable session accessibility and query every AT-SPI provider owned by the launched PID. Element handles must retain the D-Bus connection and stable object path; object paths alone can collide across providers. When a target disappears during navigation, return a stale-element error and re-locate the control; never fall back to cached coordinates for a disappeared target. Text input must pass and resolve the saved object path directly; never reselect by result index or fall back to another editable control.
- Shared Wasm browser test configuration belongs to the wasm-browser-test-interop convention. A later useKarma call replaces the framework configuration, so do not override it in consuming modules; track loader files as task inputs and preserve the configuration cache.

- JUnit host test initialization bases must be abstract so test discovery does not run the base itself. Keep the two conflicting Detekt abstract-class rule exceptions scoped to PlatformHeadlessTest files.

- Diagnose remote service forwarding with target/host application probes and the installed transport's polling capacity. Listener binding or successful TCP connect alone does not prove forwarding; do not report SSH bridging as a fix for direct-forward failures.
- For intermittent connection-refused/reset failures, compare guest health with concurrent host connection probes. Serial health or passing focused reruns alone does not establish cross-test state interference; distinguish per-listener backlog from total polling capacity.

- When an external Docker backend uses gvproxy over loopback TCP, Keep Docker automatic publish ranges aligned with explicit gvproxy rules, use gvproxy DNS for guest/containers, and coordinate both processes during recovery. Confirm the shared VM is idle before migration/restart. Never use SSH networking bridges, diagnostic DLL replacement, added connection retries, skipped tests or relaxed timeouts as a permanent forwarding fix.

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

- Verify VM state with sufficient read permissions before concluding it is stopped; sandbox probes can report inaccessible services. When applying skill templates to a legacy idle guest, normalize generated Linux scripts to LF and preserve the default Docker publish pool while restoring disjoint outbound ports. Verify the installed environment tooling independently of project configuration.

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
  native tree does not prove OS input is available; do not bypass empty-editor failures with fake content.
- Refresh the Home unread navigation badge on read/message/membership events and lifecycle resume;
  keep badge labels localized and test their presence/absence alongside row counts.
- Keep read-marker upserts monotonic and atomic; history navigation and late requests must not rewind
  the stored marker. Verify both exact counts and the legacy unread-room aggregate.
- Native room-cell accessibility must preserve merged visible content. Room names must contribute
  to merged native descriptions when previews supply descriptions; keep browser-only labels separate.
  Resize Windows native test windows through their verified native handle and convert viewport
  dimensions for window DPI.

- Browser Appium previews need explicit text/action bridge nodes; keep the bridge disabled outside test
  mode and preserve native merged accessibility. Select equal-stack overlay nodes in foreground paint
  order so dialog controls win over identically labelled background controls.
- Measure actual browser content dimensions after responsive chrome settles. Native Firefox compact
  windows may be limited to 500px; keep strict viewport/draft assertions and document the tested sizes.

- Collection refresh must clean the requested named collection, never its default detail cache. Verify
  selected-list deletion while preserving detail IDs/aliases and unrelated lists using real SQLite.
  Keep host-test database scopes unique and temporary storage under the module build directory.

- Browser semantics must publish changed labels even when geometry is unchanged.
- Wasm E2E runs every scenario on Chrome, Edge and Firefox sequentially with isolated profiles.
  Prefer installed browsers through Selenium Manager discovery; keep per-browser binary overrides
  separate and never reuse a personal browser profile or skip an unavailable matrix entry.
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

- RTC ICE candidates must remain queued until their peer remote description is ready. Keep peer
  queues independent, and guard completion cleanup against replacing a newer connection for the same user.

- Ktor RTC JVM shutdown must detach sender tracks before closing the connection and disposing
  local media. Remote JVM tracks are borrowed; video renderers must remove sinks before
  track disposal. Keep native negotiation tests on a real dispatcher, including cancellation cleanup.
- Android Ktor remote tracks are owned by native receivers. Remove renderer sinks before
  PeerConnection disposal and avoid separately disposing those receiver-owned tracks.

- Normalize browser RTC capture `JsException` at the platform boundary; it is not an `Exception`.
  DOM failures can lack JavaScript stacks, so preserve their message without retaining an unsafe
  JS cause. Verify missing-device errors exit the joining state and cancellation remains cancellation.

- Verify compression for cold Wasm client loads before diagnosing a blank page as RTC failure.
  Browser session fixtures must populate storage on a static same-origin bootstrap document
  before opening the app, so setup never aborts its first Wasm download with a reload.

- Check Wasm runtime dependency resolution as well as compile classpaths. Ktor 3.6.0's browser
  adapter retains the Wrappers 2026.1.9 ABI; newer transitive wrappers can remove inlined
  symbols while compilation still succeeds. Preserve the recorder's newer URL API when resolving
  primitive ABI compatibility. Exercise real browser constraint conversion,
  synthetic track capture, SDP creation and cleanup when changing RTC dependencies.

- Browser ICE events can contain an empty candidate string before the final null event. Treat
  this as an end-of-candidates marker at the RTC boundary; native Ktor adapters reject it.
  Preserve failures for non-empty invalid candidates and verify media still flows after the marker.

- Shared RTC dispatch must distinguish a recipient channel cancellation from caller cancellation.
  Check the caller context before propagating cancellation; one closed WebSocket must not
  cancel the global signaling or negotiation jobs.

- Verify browser WebRTC presentation with requestVideoFrameCallback presentedFrames. Firefox can
  report zero VideoPlaybackQuality totalVideoFrames for a MediaStream while actual frames
  are being presented; do not infer a frozen stream from that counter alone.

- Adaptive form fields must emit one layout root per item, including their dividers. Keep editor draft
  and mode state outside responsive width branches; measure breakpoints after pane padding.

- Resolve direct-provider Koog model names only by exact catalog IDs through modelsById();
  reject unlisted IDs and never add manual aliases, model substitutions or metadata fallbacks.
  OpenRouter is a router exception: accept nonblank IDs through LLModel without catalog lookup
  or guessed token limits, and let the remote API validate model support.

- OpenAI-compatible endpoints share only the HTTP protocol. Treat model IDs as opaque strings;
  never resolve them through the OpenAI catalog or infer OpenAI model metadata.

- Pair GoogleModels with native GoogleLLMClient and a native API root; Google definitions lack
  OpenAI endpoint capabilities. Verify request execution, not only model lookup or client construction.

- Use native Bedrock Converse for schema-based generation. DeepSeek JSON-object mode must receive
  the response schema in its system prompt because the native adapter omits it from the API payload.

- Bedrock API keys must take priority over ambient AWS signing credentials. Let the runtime SDK
  construct and own its default credential chain so credential requests inherit the configured region
  and service shutdown closes the credential cache and HTTP engine. Exercise the production factory
  in authentication, region propagation and resource-lifecycle tests.

- Appium artifacts belong to the case's raw `AppiumTestReport` directory. Let Gradle clear task outputs,
  preserve the original test failure during collection, and register private runtime paths for cleanup
  before raw result metadata is written. Link artifacts through static HTML viewers so server MIME defaults cannot force
  downloads. See DEVELOPMENT.md for the report entry point and isolation contract.

- Filesystem/Lucene use SLF4J directly, without Napier or shared/business dependencies. Keep their Logback
  provider, container-owned `LOG_PATH` directories and Appium file-log archives aligned; validate
  service startup records rather than treating nonempty provider-warning output as collected logs.

- Appium 11 does not transitively provide Selenium support utilities. Declare selenium-support explicitly
  when using Selenium waits; Appium elements are AppiumWebElement, not RemoteWebElement. Treat a missing
  page source as a diagnostic collection failure instead of writing an empty artifact.

- Keep server/Appium/CLI Testcontainers topology in dev/testInfrastructure. Callers own Ktor test-host,
  platform forwarding and business assertions; use explicit host versus container environments.
  Register resources before startup, export logs before Testcontainers removes failed containers,
  and preserve the original failure while completing cancellation-safe cleanup.

- Normalize Linux test-image shell scripts to LF in copyTestDockerDistribution, preserving source
  line endings so Windows checkouts cannot break container entrypoints. Track the filtering policy
  as a task input so existing Sync contexts are regenerated when the policy changes.

- JVM coroutine stack-trace recovery can copy exceptions across dispatchers; verify the original
  failure through its cause chain and check that cleanup failures remain suppressed on it.

- Initialize Edge with a browser-level DevTools blank target and close its isolated startup tabs
  before page-scoped WebDriver commands, which can wait on the external MSN new-tab navigation.
  A bare about:blank arg is parsed as a switch. Verify one blank target on fresh launch and restart;
  preserve normal browser windows, viewport checks and page-load timeouts.
- Current Windows Chrome can enforce a 504px content minimum. Browser activity resize tests use that explicit compact width across the matrix and retain exact viewport/draft assertions; see DEVELOPMENT.md.

- Keep raw Appium/CLI/server artifacts under build/test-results/<task>/artifacts/<class>/<test>;
  Appium adds its platform. The test-artifact-reports convention clears raw files before Test executes
  and finalizes each task with one reports/<task>/index.html. Do not scan or render reports inside cases.
  Keep report generation independent of live fixtures and serialize independent builds sharing outputs.
  Do not add hashes, counters, random suffixes or container startup retries. See DEVELOPMENT.md.

- E2E read completion must identify the expected persisted message as well as zero unread;
  optimistic local echoes can precede server persistence.

- Close the panel modal navigation drawer when authentication is cleared so sign-out or
  revocation exposes the sign-in page rather than leaving an anonymous navigation overlay. Keep
  its account actions outside the scrolling navigation list so compact windows expose sign-out.

- Prepare authentication/socket E2E community and room fixtures before application launch;
  cached pagers need not observe setup writes performed through another client after Home mounts.

- Desktop window discovery/focus must use remote Appium interfaces. Keep process/thread ownership, DPI-aware resizing and physical-pixel screenshot capture in dev/appium-windows-plugin on the Appium host; do not restore host User32/JNA calls. Remote diagnostics must never attach to a guest PID on the host. Keep environment-provider installation and private connection profiles outside the project. Project JSON declares artifact/service roles only; the external environment runner owns staging, forwards and private-session cleanup. Collect native page source and screenshots through Appium; keep test methods free of SSH/VM transport code. See DEVELOPMENT.md for the environment contract.

- Work VMs are test/runtime environments. Unless the user explicitly requests guest compilation, build applications, test code and dependencies on the host or the existing designated build environment, then stage prebuilt artifacts. Installing Appium/VMs or requesting E2E tests does not authorize guest builds, including native dependency install hooks. Do not silently fall back to guest compilation or enlarge VM resources for it.

- macOS desktop E2E is paused until an upstream Skiko fix or working VM GPU acceleration has been verified. Do not launch macOS E2E, bypass its preflight guard, restore local Skiko class replacements/frame caps, or treat historical patched-runtime reports as current acceptance. Keep the original test budgets and assertions. See DEVELOPMENT.md#macos-e2e-pause-and-skiko-investigation for evidence and resumption criteria.
- Mac2 coordinate clicks must wait for visible, enabled controls with stable bounds during Compose navigation. For text containers, prefer their own uncovered area over a center point inside a nested interactive control; keep this geometry covered by unit tests.
- Desktop WebView requires the pinned Nucleus Tao runtime and native window entry points. Do not restore the removed Swing/Wry transform or embed SwingPanel in a Tao window. Keep video native lifetime in DesktopVideoHost and copied-frame rendering in the Compose adapter. Observe asynchronous sign-out completion before testing restart persistence; do not extend UI timeouts or remove post-restart assertions.

- Linux desktop E2E requires graphical Tao/X11 and AT-SPI runtime libraries. Window geometry commands must target the launched PID and reject ambiguous visible windows, never the focused desktop window. Preserve native screenshot failures as collection errors; see DEVELOPMENT.md for driver dependencies.

- Tao desktop windows must bind Escape to the composition's NavigationEventDispatcher with a lifecycle-owned NavigationEventInput, matching Compose's AWT mediator. Preserve overlay priority and disabled root navigation; do not replace dialog dismissal with test-only UI controls.
- Merged clickable activity/room cards must retain message previews and unread counts in their accessible description. Native providers may expose only the first merged contentDescription; provide a complete row description and match whole label parts in automation.

- Windows and Linux desktop E2E share the quoted JVM argument-file launch contract. Keep Linux Appium arguments as an array and honor its working directory. Remote environment runners translate paths and provide prebuilt target JDK/native dependencies; keep Gradle tests and backend fixtures on the host and provider connections outside the project.

- Declare desktop runtime dependencies and optional project setup hooks in dev/test-environments/desktop-requirements.json. Keep VM hardware, credentials and provider launch commands outside the project. Host preparation may build; guest initialization hooks remain limited to runtime configuration and prebuilt dependency installation.

- Return `CustomResult<T, ScenarioError>` with a scenario-owned sealed error interface. Convert errors explicitly
  when crossing scenarios; `FailureDetails` is only a diagnostic/terminal-consumer contract, never a business return type.
  Capture exceptions at explicit boundaries, propagate cancellation, and do not restore the removed Kotlin Result helpers.
- Endpoint server bindings are owned by cloud/runtime's `EndpointResultRouting.kt`; use its `invoke` extension
  so typed failures stay intact without passing through endpoint4k's Kotlin Result-based invocation overloads.

- Preserve typed failure categories through `fromError` conversions; do not infer authentication or permission
  failures solely from the diagnostic exception class. Host PDF tests need JVM-visible Noto Serif and Noto Sans Mono Regular.
