# project

## Gradle Version Catalog

- Versions in `gradle/libs.versions.toml` that are consumed directly by build scripts rather than a library or plugin `version.ref` must include `# @keep this version`, so catalog cleanup does not remove them. Current examples are Android SDK levels and `jdk`.
- Detekt 2.x uses the `dev.detekt` Gradle plugin and Maven group. Its Ktlint wrapper is `dev.detekt:detekt-rules-ktlint-wrapper`; Gradle reports are `checkstyle`, `html`, `sarif`, and `markdown` (the former `txt` report is removed).
- Detekt source-set baselines are stored as `detekt-baseline.xml` in each affected module. Type-resolution tasks use task-specific files such as `detekt-baseline-main-jvm.xml` and `detekt-baseline-main-android.xml`; KMP targets must not share one `main` baseline because generating one target would overwrite findings from another. Auto-correct is disabled so static checks do not rewrite source files; remove baseline entries only when the corresponding violations are intentionally fixed.

## Uploads (files)

- API type: Ktor HTTP routes. Endpoint definitions live in `CustomApi` in the `api` module.
- Non-chunked upload: `POST files/upload` (multipart). The server first writes to a local temporary file and validates the request `sha256`; it then uploads to object storage, confirms the returned object name, and stores `FileRecord.sha256` from the request parameter.
- Chunked upload: `POST files/chunk/init` -> `POST files/chunk/{id}/{index}/upload?hash=...` (per-chunk sha256 validation) -> `POST files/chunk/{id}/complete` (object storage compose merge, then write `FileRecord` and clean up chunks).
- Storage implementation: all upload paths go through `ObjectStorageService`.
  - `MEDIA_SERVICE=minio`: MinIO/S3-compatible object storage, with compose support.
  - `MEDIA_SERVICE=filesystem`: write to local filesystem, or in-memory FS, and serve reads through `GET /a_file/{path...}`.

## Resource Management

- When creating an `InputStream`/`OutputStream` and passing it to a consuming function, callers should close it explicitly with `use {}`. For example, when computing `sha256`, use `inputStream().buffered().use { input -> sha256(input.asSource().buffered()) }`.
- Utility functions that receive caller-created streams should not close them internally. The caller should wrap the function call in `use {}` when creating the stream.
- Shared `Result` transformation extensions never retain `CancellationException` as `Result.failure`: they rethrow cancellation received from the source result, thrown by a transformation block, or returned inside a nested result.

## Panel / Worker

- Worker execution records are stored in the backend `TaskRecords` table. The record type is `TaskRecordType`, and `processedId` points to the business object processed by the task.
- Worker topic moderation uses LiteRT-LM with `gemma-4-E2B-it.litertlm` from `litert-community/gemma-4-E2B-it-litert-lm`. Startup verifies a cached model in the worker home directory using SHA-256 or downloads and verifies it before launching any task; `HUGGING_FACE_HUB_TOKEN` is optional.
- `TaskRecordType.TOPIC_MODERATION` advances through topics by primary key. It reviews unencrypted community and user-space topic trees plus rooms whose `communityId` is non-null, skips private/encrypted rooms, and changes an unsafe topic author's status to `UserStatus.READ_ONLY`.
- Panel queries worker execution records through paginated `/admin/task-records`; it supports filtering by task type, and returns all records when no type is provided.
- Panel list/detail routes use Navigation 3's `ListDetailSceneStrategy`: users, communities, rooms, topics, files, and titles render side by side on wide content areas and fall back to the existing single-pane navigation when the post-drawer content area is narrow. Public and private room lists share the room detail scene. When the list pane is still the immediate parent, selecting another item replaces the current same-scene detail entry instead of growing the back stack.
- App root navigation uses Navigation 3's `ListDetailSceneStrategy`: `HomeScreen` is the list pane, while community, room, topic, and user screens share its detail scene. Wide content areas show both panes and compact content stays single-pane. Navigation originating from the Home list replaces its current immediately paired detail; navigation originating from a detail screen pushes a new entry so compact back history is preserved.

## Topic Compose / Block Editing

- `TopicComposePage` no longer exposes the full-page `RichEditTopicPage`; rich text editing is only used as the editing capability for `ContentBlock.Paragraph` inside the block editor.
- Compose Multiplatform UI tests on the desktop/JVM target use `androidx.compose.ui.test.runComposeUiTest` (v1 API). The current Skiko desktop implementation of `v2.runComposeUiTest` throws `NotImplemented`. Test dependencies are configured as `libs.ui.test` in `commonTest` for `app/composeApp` and `client/composeCore`; Block editor UI tests should preferably live under `app/composeApp/src/headlessTest/kotlin`.

## RefCell

- `TopicRefCell`, `RoomRefCell`, `CommunityRefCell`, and `UserRefCell` do not create ViewModels directly. They obtain a `LoadingHandler` through `LocalRefCellHandlerProvider`; the default provider adapts the existing `create*ViewModel` factories.

## Media Player

- Media playlists are not resolved on click. The player UI obtains a `LoadingHandler<List<ConstPlayItem>>` through `LocalMediaPlayListHandlerProvider`, and calls `MediaPlayerService.start` after loading completes.
- The `app/desktopApp` entry point also needs to inject `LocalMediaPlayerService`; common `App()` reads this CompositionLocal at startup.

## Android App

- `app/androidApp` is the app shell module. It contains `MainActivity`, `UploadActivity`, `MediaPlayerActivity`, `BubbleActivity`, `RTCActivity`, and manifest entries. `app/composeApp` keeps reusable Compose UI, actual implementations, services, and Android helpers.
- `app/composeApp` must not depend on `app/androidApp` at compile time. When composeApp needs to start an app Activity, use a stable explicit class-name constant to create an `Intent` or `ComponentName`.
- Android WebRTC call signaling is centrally dispatched by `RTCService`'s main websocket frame collector. Peer code should not collect `frameFlow` to wait for answer; one-shot answers use `CompletableDeferred`, and repeated candidates are triggered through the Flow exposed by the peer signaling object.
- `app/desktopApp` is the Desktop JVM app shell module. It contains the Compose Desktop `main` entry point and `compose.desktop` packaging configuration. `app/composeApp` keeps shared UI and JVM actual implementations.
- `panel/desktopApp` is the Panel Desktop JVM app shell module. It contains the Compose Desktop `main` entry point and `compose.desktop` packaging configuration. `panel/composeApp` keeps shared UI and JVM actual implementations.
- `app/androidApp` and `panel/androidApp` entry classes extend `ComponentActivity`, and media services extend Media3 `MediaSessionService`. Release lint has false-positive `Instantiatable` reports for these Kotlin/Compose/Media3 components, so both app shell modules disable that lint check.
- App and Panel screenshot previews are context-free: they render only components that do not require injected runtime `CompositionLocal` dependencies such as session managers, dialog controllers, or navigation contexts. Update intentional debug baselines with `:app:androidApp:updateDebugScreenshotTest` or `:panel:androidApp:updateDebugScreenshotTest`.

## Appium

- Appium tests are included only when `-Pappium=true` is passed. Shared test infrastructure lives in `dev/appiumCore`, and product/platform entry modules are split into `app/androidAppium`, `app/desktopAppium`, `app/wasmAppium`, `panel/androidAppium`, `panel/desktopAppium`, and `panel/wasmAppium`.
- `app/androidApp` and `panel/androidApp` can use Robolectric to cover part of the Appium setup and launch flow. For example, writing a session JSON with the same format to `filesDir/appium-session/session.json` and calling `restoreFromStorage` can verify the non-device part of restoring login state from an injected private session.
- The AsciiDoc preview Appium scenario uses a standard `asciidoc` code fence. Android and Desktop verify that the generated preview opens inside the app instead of launching an external browser.
- Appium tests combine target helpers (`AppAppiumHelper` or `PanelAppiumHelper`) with platform helpers (`AndroidAppiumHelper` or `DesktopAppiumHelper`). Target helpers provide package/runtime configuration and create the appropriate session manager; platform helpers own app launch, session injection, cleanup, diagnostics, and driver wrapping. Concrete tests delegate to shared `test*ByHelper` functions.
- Desktop Appium launch scripts are assembled line by line so each shell continuation contains exactly one trailing backslash; two backslashes make Java receive `\` as its main-class argument.
- `Modifier.appiumSemantics` emits transparent HTML overlay nodes only on Wasm; native targets use their ordinary Compose accessibility semantics, and native Appium scenarios locate visible UI text or content descriptions.
- Navigation 3 provides `LocalListDetailSceneScope` to list/detail entries only when the adaptive list-detail strategy owns the current multi-pane scene. Detail search bars use that scope to omit their duplicate self avatar in multi-pane layouts while retaining search and compact single-pane actions. UserPage exposes its own-user topic creation as a leading search-bar action; the self avatar's create button always opens the global Compose menu and never derives a creation target from `SearchScope`.
- The local Linux Appium driver retains each AT-SPI query and result index for later click/input commands. Re-finding by name loses value-only nodes and duplicate-node identity, while choosing the first editable node can target the global search field instead of the page editor.
- AndroidX's asynchronous WebWorker SQLite requests can be cancelled after SQLite executes a transaction command but before the driver records the response. The local Wasm worker reconciles `BEGIN`/`END`/`ROLLBACK` with SQLite's autocommit state, and Wasm Room is explicitly configured with a single connection pool.
- `app/wasmAppium` and `panel/wasmAppium` are enabled with `-Pappium=true`. They serve the built Compose Wasm distributions locally and run Firefox headlessly through WebDriver. Compose/Wasm renders to a canvas and has no browser-visible semantics, so Appium mode (`?appium=true`) uses transparent HTML overlay nodes emitted by `Modifier.appiumSemantics`; passive nodes do not intercept input, while action and text-input nodes call the corresponding Compose callbacks directly. Off-screen nodes are detached so Appium sees one current semantic owner per visible control.
- App preferences use AndroidX DataStore with a small Compose-facing wrapper instead of `ComposePreferences`, whose 1.1.1 Wasm artifact is not binary-compatible with the current Compose runtime. Android and JVM retain their existing `main.preferences_pb` stores; Wasm uses DataStore `WebLocalStorage` without legacy `ComposePreferences` key migration.
- App notifications use `kmpnotifier-local` 2.0.0 across Android, JVM, and Wasm. The app owns a small permission `StateFlow`; platform code initializes KMPNotifier and Android requests runtime permission through `ComponentActivity.permissionUtil()`. Wasm uses the browser Notification API rather than the incomplete Wasm provider from the archived Compose Native Notification 0.2.0.
- Browser Appium reuses the normal backend topology via `runAppiumTestEnvironment`. It restores injected sessions by writing the standard `main.S_U_<address>` and `main.session_history` localStorage entries from a same-origin bootstrap document, rather than adding a production session-injection API. The Wasm distribution server reverse-proxies backend HTTP requests so Firefox does not encounter cross-origin preflight failures. Shared room-publishing fixtures must join both the community and the room before launching the UI.

## Gradle Tool Scripts

- `scripts/build_scripts/gradle-prune-implementations.sh` uses `./gradlew projects` to discover the modules actually included in the current build. It only processes those modules' `build.gradle.kts` files, avoiding accidental dependency removal from modules that are not included.
- The prune check runs `assemble` by default, which can be overridden with `GRADLE_PRUNE_TASK`. Script arguments are forwarded to Gradle, for example `-Pserver.flavor=...`.
- Each candidate `implementation` is verified on top of the cumulative "confirmed removable" state. On failure, only the current candidate is rolled back, preventing final deletion of dependencies that were never validated in combination.
- KMP modules that add custom intermediate source sets generally call `applyDefaultHierarchyTemplate()` after target declarations and before `sourceSets {}`. Examples include `shared`, `client:room`, `client:sqlite-now`, `client:kotbase`, `client:composeCore`, `app:composeApp`, and `panel:composeApp`; custom sets such as `jvmAndroidMain`, `noJvmMain`, or `headlessTest` are then attached with explicit `dependsOn` edges.

## Account 2FA

- User sign-in used to be a single private-key signature step through `/accounts/sign-in`. After TOTP is enabled, that endpoint returns `SignInResponse.RequiresTotp` and marks the Ktor session as `UserSession.TwoFactorPending`; the client then calls `/accounts/sign-in/totp` to complete sign-in.
- User 2FA settings use the protected `CustomApi.Users.TwoFactor` API. Backend data is stored in the `UserTwoFactors` table, and Exposed `MigrationUtils.statementsRequiredForDatabaseMigration` automatically adds the table.
- The TOTP backend implementation lives in `cloud/service`. It uses JDK `SecureRandom`, Base32, and `HmacSHA1` to implement RFC 6238 directly, and generates standard `otpauth://totp/...` URIs for Google Authenticator and similar clients.

## Server Configuration

- User sign-up is controlled by the server environment variable `ENABLE_SIGN_UP`. It defaults to enabled when unset, and only rejects `/accounts/sign-up` when explicitly set to `false`.

## Cloud CLI

- `cloud/cli` commands should not use the top-level global `backend` variable. When a backend instance is needed, call `requireBackend()` directly at command execution time.
- The `:cloud:cli` `generate-preset-keys` subcommand generates Dilithium signing private keys (`p-*`) and Kyber encryption private keys (`ep-*`) for dev-data preset accounts. By default it targets `deploy/dev-data/secrets` under the project root; when run through Gradle `:cloud:cli:run`, it searches upward for `deploy/dev-data/0_preset_user.json` to avoid writing into the module directory.
- In `deploy/dev-data/0_preset_user.json`, the System, FontProvider, robot1, robot2, user1, and user2 preset accounts use `algoType: DILITHIUM` and reference their corresponding `encryptionPrivateKey`. The dev preset keeps only communities and rooms referenced by topic data, and omits optional font imports to keep initialization fast.

## CI

- `Alpha Server CI` runs backend/server tests before starting the remote alpha service: `:backend:minio:test`, `:cloud:cli:test`, `:cloud:service:test`, and `:cloud:server:test`. It also enables `ENABLE_TEST_CONTAINER=true` to override the Testcontainers path.
- Test and release workflows use `gradle/actions/setup-gradle@v4` instead of a hand-written `actions/cache` Gradle User Home cache. PR test jobs should set `cache-read-only: true`; release/main jobs should keep the default write behavior and `cache-cleanup: on-success` so PR checks can restore default-branch Gradle cache without trying to save large merge-ref caches.
- PR compile checks run `./gradlew compileAllNoRelease --console=plain`, a root aggregation task that compiles included modules while excluding Android release and benchmark variants.
- `client/composeCore` shares JVM/Android actual sources through a custom `jvmAndroidMain` source set with explicit `dependsOn` edges from `jvmMain` and `androidMain`; this keeps the shared `compose-pdf` and `m3u-parser` actual implementations out of wasm.

## Wasm

- Wasm targets are always enabled; `target.wasm` is no longer a project property.
- Browser deployments serve the app and API from different origins. The Wasm Ktor client must configure Fetch with `credentials: include` only for requests whose origin matches the configured API origin; cross-origin media requests keep the browser default and do not send credentials. The API edge must allow credentials for explicit app/panel origins and expose `WWW-Authenticate` so `SingleFlightCustomAuthPlugin` can read the custom authentication challenge. Credentialed CORS must not use a wildcard origin.
- App and Panel load a bundled Noto Sans SC font into Compose typography on Wasm so Canvas/Skia rendering does not depend on browser CSS font fallback. On secure Chromium contexts with Local Font Access, the `client/composeCore/local-font-access` bridge waits for the first pointer or keyboard interaction, requests permission, and replaces the bundled fallback with the first preferred installed CJK font. Production response headers must allow `local-fonts=(self)` in the Permissions Policy. Firefox and denied/unsupported Local Font Access paths continue using the bundled font.
- The cacheable `downloadNotoWasmFont` task downloads Noto Sans SC Regular and its SIL OFL license from the pinned Noto CJK `Sans2.004` release, verifies both SHA-256 hashes, and exposes them as generated `wasmJsMain` Compose resources. Do not commit the 8.3 MB generated font into source control.
- `:app:webApp` and `:panel:webApp` define the executable `wasmJs` browser targets. `:app:composeApp` and `:panel:composeApp` retain non-executable Wasm targets for shared UI and platform actuals. Shared modules such as `shared`, `api`, `client:core`, `client:model-storage`, `client:room`, `client:bot-lib`, and `client:asciidoc-parser` also define Wasm targets.
- `dev/core`, `dev/cli`, and `dev/server` were removed from the included build. Do not add dependencies on `projects.dev.core`, `:dev:cli`, or `:dev:server`.
- Runtime support remains incomplete for app image save/conversion, clipboard write, media playback, local client file access, text file save, and GPT, which are no-op or unsupported wasm actuals.
- `shared` wasm supports the P-256 path through WebCrypto (ECDSA signing/verification, ECDH + HKDF + AES-GCM key encapsulation, AES-CBC content encryption, and deriving a DER public key from a PEM private key) and the standard PQC path through `@noble/post-quantum`: ML-DSA-65 signing and ML-KEM-768 encapsulation. Firefox WebCrypto does not implement MD5 or RIPEMD-160, even though cryptography-kotlin declares their common algorithm IDs, so Wasm uses `@noble/hashes/legacy` for those compatibility hashes; JVM continues through its cryptography provider. JVM uses Bouncy Castle's matching standard asymmetric algorithms. PQC raw keys are carried in the existing Base64 PEM container methods; legacy Dilithium/Kyber accounts must migrate to newly generated ML-DSA/ML-KEM keys.
- `shared/ml-crypto` is a private local npm module that provides the bridge for shared Wasm `@JsModule` imports. Declaring it as a `shared` `wasmJsMain` npm dependency makes the bridge available transitively to executable WebApp modules and Wasm tests without manually copying resources.
- `client:asciidoc-parser` uses the external npm module `@asciidoctor/core@3.0.4` for its Wasm `actual`, matching the bundled 3.0.4 script used by JVM/Android. Its browser tests use Firefox.
- Room wasm uses `androidx.sqlite:sqlite-web` with a local `sqlite-web-worker` npm package and OPFS. Both dev and production hosting need COOP/COEP headers for cross-origin isolation, otherwise OPFS/SharedArrayBuffer will fail in browsers.
- Adding `app` to a flavor's `COMPOSE_FILE_LIST` includes `deploy/docker-compose/docker-compose.app.yml`, which serves `app-wasm` and `panel-wasm` from their WebApp distributions with COOP/COEP headers. `start-service-in-local.sh` detects this profile and builds both Wasm distributions before Compose startup; without it, neither Wasm build nor service is included. The `sample` profile intentionally uses the named Elasticsearch certificate volume from the `elastic` profile instead of `certs_bind`, which is only for host-local development.
- Elasticsearch setup assigns its certificate volume to UID/GID 1000, the non-root user used by Server, WebSocket server, and CLI containers.
- The Compose Wasm distribution can exceed a 3 GiB Kotlin compiler heap while lowering Material3 IR. Keep `kotlin.daemon.jvmargs=-Xmx6G` in `gradle.properties`; this is separate from `org.gradle.jvmargs` and requires enough host memory for both daemons.
- The `sample` Compose profile builds Server, WebSocket server, and CLI on the host before Docker starts. It sets `BUILD_ON=local`, so the Dockerfiles reuse the resulting packages under `deploy/build` instead of invoking Gradle inside image builds.
