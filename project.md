# project

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

## Panel / Worker

- Worker execution records are stored in the backend `TaskRecords` table. The record type is `TaskRecordType`, and `processedId` points to the business object processed by the task.
- Panel queries worker execution records through paginated `/admin/task-records`; it supports filtering by task type, and returns all records when no type is provided.

## Topic Compose / Block Editing

- `TopicComposePage` no longer exposes the full-page `RichEditTopicPage`; rich text editing is only used as the editing capability for `ContentBlock.Paragraph` inside the block editor.
- Compose Multiplatform UI tests on the desktop/JVM target use `androidx.compose.ui.test.runComposeUiTest` (v1 API). The current Skiko desktop implementation of `v2.runComposeUiTest` throws `NotImplemented`. Test dependencies are configured as `libs.ui.test` in `commonTest` for `app/composeApp` and `app/core`; Block editor UI tests should preferably live under `app/composeApp/src/headlessTest/kotlin`.

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

## Appium

- Appium tests are included only when `-Pappium=true` is passed. Shared test infrastructure lives in `dev/appiumCore`, and product/platform entry modules are split into `app/androidAppium`, `app/desktopAppium`, `panel/androidAppium`, and `panel/desktopAppium`.
- `app/androidApp` and `panel/androidApp` can use Robolectric to cover part of the Appium setup and launch flow. For example, writing a session JSON with the same format to `filesDir/appium-session/session.json` and calling `restoreFromStorage` can verify the non-device part of restoring login state from an injected private session.

## Gradle Tool Scripts

- `scripts/build_scripts/gradle-prune-implementations.sh` uses `./gradlew projects` to discover the modules actually included in the current build. It only processes those modules' `build.gradle.kts` files, avoiding accidental dependency removal from modules that are not included.
- The prune check runs `assemble` by default, which can be overridden with `GRADLE_PRUNE_TASK`. Script arguments are forwarded to Gradle, for example `-Pserver.flavor=...`.
- Each candidate `implementation` is verified on top of the cumulative "confirmed removable" state. On failure, only the current candidate is rolled back, preventing final deletion of dependencies that were never validated in combination.

## Account 2FA

- User sign-in used to be a single private-key signature step through `/accounts/sign-in`. After TOTP is enabled, that endpoint returns `SignInResponse.RequiresTotp` and marks the Ktor session as `UserSession.TwoFactorPending`; the client then calls `/accounts/sign-in/totp` to complete sign-in.
- User 2FA settings use the protected `CustomApi.Users.TwoFactor` API. Backend data is stored in the `UserTwoFactors` table, and Exposed `MigrationUtils.statementsRequiredForDatabaseMigration` automatically adds the table.
- The TOTP backend implementation lives in `cloud/service`. It uses JDK `SecureRandom`, Base32, and `HmacSHA1` to implement RFC 6238 directly, and generates standard `otpauth://totp/...` URIs for Google Authenticator and similar clients.

## Server Configuration

- User sign-up is controlled by the server environment variable `ENABLE_SIGN_UP`. It defaults to enabled when unset, and only rejects `/accounts/sign-up` when explicitly set to `false`.

## Cloud CLI

- `cloud/cli` commands should not use the top-level global `backend` variable. When a backend instance is needed, call `requireBackend()` directly at command execution time.
- The `:cloud:cli` `generate-preset-keys` subcommand generates Dilithium signing private keys (`p-*`) and Kyber encryption private keys (`ep-*`) for dev-data preset accounts. By default it targets `deploy/dev-data/secrets` under the project root; when run through Gradle `:cloud:cli:run`, it searches upward for `deploy/dev-data/0_preset_user.json` to avoid writing into the module directory.
- In `deploy/dev-data/0_preset_user.json`, the System, FontProvider, robot1, robot2, user1, user2, and user3 preset accounts use `algoType: DILITHIUM` and reference their corresponding `encryptionPrivateKey`.

## CI

- `Alpha Server CI` runs backend/server tests before starting the remote alpha service: `:backend:minio:test`, `:cloud:cli:test`, `:cloud:service:test`, and `:cloud:server:test`. It also enables `ENABLE_TEST_CONTAINER=true` to override the Testcontainers path.
- Test and release workflows use the same explicit Gradle dependency cache namespace: `gradle-${{ runner.os }}-release-*`. Keep the cache path limited to `~/.gradle/caches` and `~/.gradle/wrapper` so pull-request tests can restore the default-branch release cache without mixing OS-specific entries. Release jobs that share the same runner OS should run through `needs` dependencies instead of saving the same cache key concurrently. During cache key migrations, keep older runner-scoped prefixes in `restore-keys` until the new default-branch cache has been written.

## Wasm

- Wasm targets are opt-in through `-Ptarget.wasm=true`; the default in `gradle.properties` is `target.wasm=false`.
- `:app:composeApp` and `:panel:composeApp` define executable `wasmJs` browser targets. Shared modules such as `shared`, `api`, `client:core`, `client:model-storage`, `client:room`, `client:bot-lib`, and `client:ascii-parser` also define wasm targets when the property is enabled.
- `dev/core`, `dev/cli`, and `dev/server` were removed from the included build. Do not add dependencies on `projects.dev.core`, `:dev:cli`, or `:dev:server`.
- Runtime support is incomplete even after that configuration issue: wasm cryptography in `shared/src/wasmJsMain/.../Signature.wasmJs.kt` is still a stub, app image save/conversion, clipboard write, media playback, local client file access, text file save, and GPT are no-op or unsupported wasm actuals.
- Room wasm uses `androidx.sqlite:sqlite-web` with a local `sqlite-web-worker` npm package and OPFS. Both dev and production hosting need COOP/COEP headers for cross-origin isolation, otherwise OPFS/SharedArrayBuffer will fail in browsers.
