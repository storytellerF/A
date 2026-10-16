# Development

## Adaptive Home UI

Home is an independent root destination; entering a community, room, topic or user pushes normal back
history. The former root list/detail metadata and list-pane replacement tests were removed because that
navigation contract no longer applies. Home, Community and User each share one page-local pager across rail/bottom navigation.
Room and Topic detail content share a centered reading column capped at 900dp.
Settings and creation forms use AdaptiveFormLayout, capped at 1200dp, with two equal columns when
at least 800dp remains after content padding. Each setting and its divider form one layout item.
Personal favorites, comments, subscriptions and reaction records reuse AdaptiveItemGrid and its paging keys.
The topic composer uses editor/preview panes from 1000dp of available content width, capped at 1600dp;
compact view retains the preview/raw/block tabs. Keep draft and mode state outside width branches.
Keep content-width adaptation in the section or shared presentation component, after navigation and
padding are deducted. Home context belongs only to World and uses bounded unread-room, pinned-topic and joined-community
previews. Room summaries and exact unread counts use the activity endpoints described below.

Run `./gradlew :app:composeApp:jvmTest :app:composeApp:adaptiveUiTest -Pserver.flavor=dev
-Pserver.buildType=debug --console=plain` on one line. `adaptiveUiTest` runs only the runtime-free Home
layout fixtures, separately from the regular host suite and without a backend or device. It checks section,
search and scroll retention during resizing, context visibility, and result width/short-window scrolling.
Use `./gradlew validateDebugScreenshotTest -Pserver.flavor=dev -Pserver.buildType=debug --console=plain`
for presentation snapshots. Snapshot fixtures must remain independent of app runtime CompositionLocals.

## Room activity and Home context data

The protected read endpoints use the current opaque Cookie session, with no target-user parameter:

| Endpoint | Client request | Result / ordering |
| --- | --- | --- |
| `GET /users/unread/messages` | `getUnreadMessageCount()` | Exact total unread direct messages in all joined rooms, independent of paging |
| `GET /users/unread/room-activities` | `getUnreadRoomActivities(PaginationQuery(...))` | Complete joined unread set, filtered before paging; latest direct-message ID descending |
| `GET /users/pinned-topics` | `getJoinedPinnedTopics(PaginationQuery(...))` | Direct pinned topics in joined communities, topic ID descending |

Both paged endpoints accept existing next/pre tokens and page sizes 1–100. Previous pages are returned
in the same descending presentation order. Room activity cursors use `latestMessageId`, not the room ID;
this avoids returning a room again for its older unread messages. Concurrent activity may move items
between pages, so consumers refresh to obtain the current ordering; these are live queries, not snapshots.

`RoomActivityInfo` is a request-boundary projection and does not change `RoomInfo`. It contains room
metadata, a nullable latest message ID/body and an explicit `Long` unread-message count. `TopicInfo`
provides the content/type, author information and stored creation timestamp. Consumers derive a bounded
preview after decryption rather than requiring a server-generated plain summary. Empty rooms have no
latest message and a zero count. Deleted messages disappear from the projection and count. Invited
notification-room recipients keep their existing read access; these new lists deliberately cover joined
rooms, rather than changing invitation or notification semantics. Read-only joined rooms remain included.

Reverse room message lists keep a permanent 1dp latest-edge item before conditional loading rows.
It anchors new arrivals after Send while the user is at the latest edge; history keeps the visible
message key. Read markers use the maximum actual visible topic key, ignoring anchors/loading rows;
they do not assume display order or mark unseen messages read. Adaptive UI coverage checks both
latest-edge following and history retention.

Counts include all existing direct messages after the user's room read marker (or all direct messages
when no marker exists), including the user's own messages; replies are excluded. Read markers advance
with an atomic database maximum, so viewing history or receiving an older request late cannot make
already-read messages unread again. IDs are compared only
as ordered read markers, never subtracted to calculate a count. `UnreadRoomsResponse.unreadCount`
continues to count distinct unread rooms and shares the server-filtered grouped query. A page's total is
its complete eligible room/topic count, not the page length.

The server reads room/member/count/topic/author data in batches. Inaccessible or unjoined room IDs are
omitted without disclosing whether they exist. Private bodies remain encrypted on the wire, with only
the current member's encrypted key. Client requests reuse `processEncryptedTopic`; missing keys or
failed decryption preserve the existing unavailable/failure representation, never a plaintext fallback.
There is no new background polling or session renewal. Consumers request fresh data on explicit refresh
or existing feature events; after read, exit, unpin or deletion the next request reflects current state.

Run `:cloud:server:test --tests com.storyteller_f.a.cloud.server.RoomActivityTest` through the QEMU skill's
`run-testcontainers.sh`, with the usual dev/debug properties. PostgreSQL plus the existing filesystem and
Lucene RPC containers cover room-page activity ordering, totals beyond loaded pages, direct-message
counts/read markers, both cursor directions,
read-only rooms, deletion, private wire/client content, membership loss, pinned scope and request limits.

## Activity preview integration

Joined-room lists, joined-room search, community-room lists and community-room search return an
`activities` projection alongside the existing `data` and pagination. It covers eligible joined rooms from
that exact page; anonymous and unjoined rows have no private activity projection. Admin lists retain the
compatible empty default. Client request wrappers decrypt activity through the existing private-topic
boundary before delivering the page. `RoomListPreviewHost` receives those pages without making another
request and retains their ephemeral previews across page boundaries. Refresh replaces the projection;
local Room storage still persists only `RoomInfo`, never decrypted activity. Room events and lifecycle
resume refresh the existing pager, preserving its scroll owner. Both room metadata and activity arrive
from one list request, including search/community callers. The navigation badge displays the server's
complete unread-message total, not a sum of loaded rows. The legacy unread-room count keeps its meaning.

World's wide-only context independently shows at most three unread rooms and three pinned topics,
with actual source totals when the preview is partial, plus the joined-community preview. Context scrolling
is independent of the feed. Room/topic entries open existing root detail destinations; the Rooms action
opens the existing Home section. No new route-level splitting or general activity-feed contract is added.

`ActivityPreviewHost` is UI-framework-independent and uses an injected application-owned serial
coordination dispatcher. Requests, decryption and bounded text/date preparation run on the worker
dispatcher. Results are immutable, ephemeral presentation state; decrypted previews are not stored.
Superseded jobs are cancelled and cannot publish late results. Session-keyed UI collectors reset on
account changes; lifecycle stop pauses/cancels requests and resume fetches current data. Closing the
composition cancels its Host. Room create/change/read/join/exit and community membership events update
eligible data; room events do not restart pinned previews. Successful pin/unpin mutations emit the shared
topic-change event from their request boundary, including dialog and menu callers.

Pure snapshot fixtures cover compact/wide activity rows, larger text, unavailable private summaries and
context errors. Headless tests cover page-boundary retention, refresh replacement, cancellation, source
independence and event filtering. Adaptive UI tests cover preview retry/navigation, exact counts during
resizing, list refresh callbacks, and active/inactive lifecycle binding. Home's unread navigation badge
refreshes on active read/message/membership events, explicit source refresh and return to Home. Refresh
waits for an existing query to complete so an update cannot be dropped while loading; its localized label
identifies the unread state in both rail and bottom navigation. Native room cells retain their merged
content text for assistive technology; the browser test overlay keeps its room-name navigation label.

The `test activity previews track reading sending and navigation` Appium scenario registers two synthetic
accounts against the isolated real backend and explicitly signs in through the UI. Messages use the
production WebSocket channel. It verifies the latest summary, exact unread count, room/topic navigation,
read-marker clearing, keyboard send, updated preview and navigation-badge clearing. The private-room
variant verifies real encrypted message delivery and client decryption. A separate refresh scenario
marks the room read through another session, then verifies that UI refresh clears rows and navigation
together. On wide Android
viewports it also verifies unread/pinned context; the desktop variant adds repeated narrow/medium/wide/
short-window resizing and draft retention. Existing room-publication E2E remains compatible. Windows
viewport changes target the verified test JVM's native window, with DPI conversion; Java-agent elements
cannot use the driver's UIA window-resize path.

Run `:app:androidAppium:appiumTest --tests '*test activity previews*'` with the usual dev/debug properties
through the configured Docker test environment and a leased device. Temporary Android size/density,
power and locale changes must be restored. Desktop acceptance uses the same named test under
`:app:desktopAppium:appiumTest` after preparing its runtime classpath. It requires an interactive unlocked
desktop for native keyboard/Escape input; an AWT accessibility tree alone is insufficient. Do not claim
native desktop or browser acceptance from Android/host results. Keep issue #60 open through review and
remaining platform checks.


The browser runner now reuses the same public/private activity and cross-session refresh scenarios.
Browser acceptance across Chrome, Edge and Firefox covers explicit sign-in, encrypted previews, exact counts and navigation badges,
unread/pinned context navigation, reading, sending and draft retention across 500/900/1600px widths
and a 500px-high window. The existing publication, registration and sign-in/sign-out restart scenarios
provide selector and authentication regression coverage. Capture the rendered page as well as the HTML
semantics overlay; new preview text/actions must use the existing Appium-only bridge without changing
native accessibility. Overlay nodes have equal stacking indices, so the driver selects the last matching
visible node to respect foreground dialog order when a background page has the same button label.
Bridge metadata updates during recomposition even when control dimensions do not change. Both activity
variants revisit the same room three additional times after sending. Failure artifacts include HTTP and
SQLite worker operation counts, without credentials, addresses, SQL or message bodies.

Firefox's native window may enforce a 500px minimum width. Browser compact coverage uses that real
viewport; desktop coverage remains 480px. Browser chrome changes height during resizing, so establish
the width before measuring its frame and verify the resulting content dimensions exactly. Verify drafts
against the current HTML input value and Compose acknowledgement after every viewport transition.
Run the activity tests with `:app:wasmAppium:appiumTest --tests '*test activity previews*'`
and the refresh test with `--tests '*test room refresh clears*'`, using the same dev/debug and Docker
prerequisites. Both `:app:wasmAppium:appiumTest` and `:panel:wasmAppium:appiumTest` run every test against
Chrome, Edge and Firefox through a JUnit parameter matrix. Reports identify each browser; variants
remain sequential. Each run uses a temporary isolated profile, retained across browser restarts and
removed after the test. Your normal browser profile is never used.

Selenium Manager first discovers a locally installed browser and resolves its matching driver. If no
installation is available, it uses its managed browser cache or downloads a browser where supported;
missing browsers fail their matrix entries rather than silently skipping coverage. Set
`WASM_E2E_CHROME_BINARY`, `WASM_E2E_EDGE_BINARY` or `WASM_E2E_FIREFOX_BINARY` to an existing executable
for nonstandard installations. These are read only when the test creates its browser. Remove an old
shared `SE_BROWSER_PATH` override before running the matrix, because it applies to every browser.
`SE_OFFLINE=true` requires all matching drivers and browsers to be available already. Filtered Gradle
`--tests` invocations still run the selected scenarios on all three browsers; do not change test
timeouts or skip missing variants to hide environment failures.


Paging clients treat server page tokens as opaque strings and retain the returned item order. Local
Room offsets belong only to the database PagingSource. Its mapped source supplies Room's centered
offset refresh policy for a new generation, and CompatPagingSource converts local keys without losing
placeholder counts. RemoteMediator REFRESH follows the returned next tokens until it covers the
previous cached range (at least initialLoadSize), then commits the replacement once. Failed or cancelled
requests do not publish a partial window or advance stored remote keys. Room replaces list records,
detail IDs and aliases in one transaction; preview Hosts publish staged pages after that commit.
There is no UI-managed request ledger, token parsing, ID-based seek or manual scroll restoration.

Collection refresh in `client/room` deletes only the requested list's named collection. Room and title
refreshes preserve their default detail caches and other lists; clearing the defaults can invalidate an
open detail page when the underlying Home list refreshes. `:client:room:jvmTest` runs shared
headless tests against real SQLite for this boundary, including detail aliases. Its temporary database
and native-driver files stay under the module build directory and use unique test scopes.

Wasm SQLite cancellation waits for pending worker responses. If an allocation cannot be delivered to
its cancelled caller, close the new connection/statement. Rollback/commit/savepoint-release preparation
and stepping must finish non-cancellably: Room's pool cleanup performs rollback before recycling its
permit, and cancelling that rollback can strand the single connection and leave later detail pages
loading forever. Normal statements still reject cancellation and no business operation is replayed.
`:client:room:wasmJsBrowserTest` covers response waiting, discarded allocation cleanup and cancelled
finalization; repeated real-browser navigation exercises the complete Room/worker boundary.

## Temporary dependency pins

PostgreSQL R2DBC is pinned to `1.1.2.RELEASE` while
[upstream issue #756](https://github.com/pgjdbc/r2dbc-postgresql/issues/756)
(DNS UDP socket retention in 1.1.3) is unresolved. Dependabot ignores
`org.postgresql:r2dbc-postgresql` specifically, including grouped version updates.
After verifying an upstream fix, update the catalog version and remove the matching
`ignore` entry from `.github/dependabot.yml`.
The ignore rule takes effect when this configuration reaches the repository default branch.

## Post-quantum cryptography

JVM and Android share JCA `Signature` and `KeyFactory` operations through the `BC`
provider for ML-DSA-65 and ML-KEM-768. KEM operations use Java 21 `javax.crypto.KEM`
on JVM and `KeyGenerator` with BC's `KEMGenerateSpec`/`KEMExtractSpec` on Android,
whose SDK does not expose `javax.crypto.KEM`. Android explicitly disables the default
KDF so both platforms retain the same raw 32-byte shared secret used by Wasm.
Stored PEM bodies remain long-form raw keys; they are not PKCS#8/X.509 encodings.
Ciphertexts remain the ML-KEM encapsulation followed by the AES key XOR shared secret.

Run `./gradlew :shared:jvmTest :shared:testAndroidHostTest -Pserver.flavor=dev -Pserver.buildType=debug --console=plain`
for shared JVM/Android compatibility tests. These compare the JCA implementations
with the previous lightweight API in both directions and verify fixed synthetic
vectors generated by the pinned `@noble/post-quantum` 0.6.1 used on Wasm.
Regenerate the vectors after Gradle installs Wasm dependencies with
`node shared/ml-crypto/generate-compatibility-vectors.mjs build/wasm`.
The generator uses fixed synthetic seeds and deterministic signatures.
Android host tests run under Robolectric. For device verification, run
`./gradlew :shared:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.storyteller_f.shared.PostQuantumCompatibilityTest -Pserver.flavor=dev -Pserver.buildType=debug --console=plain`
with a connected Android device and its device lease held. The device source set
reuses the headless compatibility tests and their fixed vectors.

## Backend test images

Each backend service applies `test-docker-image`. Its `buildTestDockerImage` task depends on
`copyTestDockerDistribution`, a Sync task preparing this module's `build/test-docker-context`.
The context contains only its distTar output, a copy of the canonical root Dockerfile and
necessary staging/CLI helper scripts. Preparation finishes before its image build, and other
modules never mutate that context. Image tasks have no global build-service permit and may run concurrently.
Integration and E2E tasks depend on the images they require; ordinary assembly does not.
Tests select `BUILDER_STAGE=prebuilt-builder`, which only extracts the distribution and does not
mount shared Gradle caches. The root Dockerfile defaults to `source-builder` for deployment;
source compilation remains inside Docker with shared writable cache locks. Testcontainers only starts images.

With native Docker, run Gradle normally. With QEMU on Windows, compile the QEMU skill's generic
Docker proxy once with `build-docker-proxy.sh`, then run Gradle through `run-testcontainers.sh`.
The wrapper places the proxy on PATH. It applies Docker ignore rules to the local build context
and streams the archive over SSH to guest `docker buildx build --load`. No shared host mount or
project-specific VM image list is required; secrets must remain excluded by `.dockerignore`.

### Windows gvproxy networking and legacy slirp diagnosis

The current plugin backend is upstream gvproxy v0.9.0 over a loopback TCP QEMU
socket, using guest MAC `5a:94:ef:e4:0c:ee`, guest `192.168.127.2`, and gateway/DNS
`192.168.127.1`. The plugin registers SSH, Docker API and published-port forwards
through the loopback services API; gvproxy does not discover Docker mappings itself.
Guest and container DNS use gvproxy directly; Unbound and forced TCP DNS are removed.
Keep the Docker automatic range and registered same-port range consistent. The updated AgentStart1/me.codex QEMU 0.5.0 defaults to `20000–20127` on both platforms; the persistent test guest has been migrated to that 128-port pool. Always use the active skill profile's resolved range. Recovery coordinates QEMU/gvproxy stop and start
and re-registers rules; upstream accepts one QEMU transport connection per process.
Do not restart a VM used by another session, add connection-failure retries, or use
SSH networking bridges to hide direct-forward failures.

The installed qemu-alpine-docker 0.4.1 plugin includes the gvproxy lifecycle scripts. Resolve its current installation path from the loaded skill and use those scripts consistently. Do not mix older cached slirp lifecycle scripts with the gvproxy VM.

The following capacity evidence describes the legacy `-netdev user` backend:


The installed MSYS2 QEMU 11.0.1 Windows main loop uses a 64-socket `fd_set` for slirp
polling. Forward listeners and active connections share that capacity. Socket binding can
succeed even when the socket is omitted from polling, so a successful TCP connect does
not prove that forwarded application traffic works. A controlled same-service probe
passed all 48 forwards; with 64 forwards plus SSH, Docker and one dummy listener, the
last four HTTP forwards failed. Reversing rule order moved the failure to the other four
ports while guest HTTP stayed healthy. The installed executable contains the same
64-entry checks as upstream `util/main-loop.c` and MinGW's `FD_SET` implementation.

For the legacy slirp backend, direct-forward ranges must reserve space for active connections;
large static ranges require a QEMU build with a larger `FD_SETSIZE` or a polling
implementation that supports the required socket count. Merely widening a profile
cannot change the executable's compiled capacity. SSH forwarding is a diagnostic
alternative, with different performance: the local PostgreSQL probe measured about
3.4 seconds for 100 queries over SSH versus 20–23 milliseconds through direct QEMU
forwarding. Do not treat successful SSH probes as a fix for QEMU forwarding.

Docker caches its automatic publish range when the daemon initializes. Match that
range to the VM's same-port forwards: set guest `net.ipv4.ip_local_port_range` before
Docker starts, then restore the kernel range to `32768 60999` after startup so outbound
connections do not compete with published ports. A persistent sysctl file can establish
the initialization range at boot, but restoration must follow Docker startup. After any
Docker/VM restart, verify assigned ports and guest/host health requests before testing.
Do not restart a shared daemon while others use its containers.

Use the skill's existing Docker CLI proxy and `run-testcontainers.sh`, with JDK 21 and
`-Pserver.flavor=dev -Pserver.buildType=debug`. Let Gradle build required test images.

A device-independent E2E smoke test is `:app:cliE2e:e2eTest --tests '*CliE2eTest'` (also pass
`-Pserver.flavor=dev -Pserver.buildType=debug`). Filter by class: Kotlin `internal` test
methods acquire module-specific JVM name suffixes, so an exact source-method filter will not match.

## Wasm Nginx configuration

Compose mounts `deploy/docker-compose/app-wasm.nginx.conf` read-only into both Wasm sites
at `/etc/nginx/conf.d/default.conf`. The Wasm images contain the built site, not this
configuration. After editing the host configuration, reload Nginx or recreate the containers;
no image rebuild is required. Standalone image runs must supply the same configuration to
enable the required COOP/COEP headers.

Docker builds rename each Wasm entry script with its SHA256 content prefix and update
its index reference. A separate content digest versions Compose resource URLs through
`configureWebResources`, keeping translated strings and generated byte offsets in sync.
Keep HTML and mutable resource URLs subject to cache validation
(`Cache-Control: no-cache`); otherwise a browser can retain an old entry script across
deployments and load a different client than fresh HTTP probes. Verify the browser's
loaded Wasm filenames against the deployed image when accepting an upgrade.

## Adminer reverse proxy

The Bunker Adminer site uses the regex location `~ ^/` instead of the ordinary `/` location.
Bunker adds `X-Forwarded-Prefix: /` to ordinary root locations; Adminer then creates session
cookies with `Path=//`, preventing login at `/`. Regex routing preserves the request URI without
adding this prefix. When checking Adminer deployment, test an actual PostgreSQL login through
its HTTPS domain, not only the login page response, and verify session cookies have `Path=/`.

## Independent filesystem and Lucene services

The services share this repository's build tooling, but their runtime dependency graphs do not
include A's `backend/core`, `shared`, or `api` business modules.

- `services/filesystem-protocol` defines the filesystem RPC contract and object metadata.
- `services/lucene-protocol` defines generic index documents, queries, and the Lucene RPC contract.
  Documents are lists of named text/long fields, allowing repeated names. Indexing modes
  (`NONE`, `TEXT`, `EXACT`), storage, and DocValues (`NONE`, `NUMERIC`, `SORTED`) are independent.
  Reads return stored values grouped by field name, preserving multiple values; there is no ID property.
- `cloud/filesystem-service` implements object storage and direct HTTP downloads.
- `cloud/lucene-service` implements generic indexing and search.
- `backend/filesystem` and `backend/lucene` adapt A's business interfaces to these protocols.
  Filesystem metadata is explicitly converted at this boundary; the service does not implement
  the business-side `ObjectStorageService` interface.

`LuceneUserDocument` and the other backend conversion classes map business documents to these
generic fields and restore search results without JSON payloads or business serializers.
The service does not create or reserve `id1`, `id2`, or any other application fields.
Queries declare their field names; sorting declares field names, LONG/STRING types, and directions.
RPC has no `get(ids)` or `sortByIdDescending`. A's adapters explicitly declare their own stored,
exact-indexed `id` field with numeric DocValues, and implement topic lookup through a normal query.
The RPC contract changed: deploy the service and its backend clients together. The generic service
returns legacy stored fields under their original names without translating them. A's old `id1` or
`_payload` indexes need a rebuild from source data to match the adapters' new field declarations.
This change does not delete or automatically migrate index data.

Build distributions and run service tests without starting A's application services or database:

```bash
./gradlew :cloud:filesystem-service:distTar :cloud:lucene-service:distTar \
  :cloud:filesystem-service:test :cloud:lucene-service:test \
  -Pserver.flavor=dev -Pserver.buildType=debug --console=plain
```

The flavor/build-type properties are required by the repository's application configuration,
not by these services. Extract each distribution into its own directory and run
`bin/filesystem-service` or `bin/lucene-service`. Both require JDK 21; the distributions also
work through the existing Dockerfile targets. They do not read `deploy/{flavor}.env`.

Filesystem configuration:

| Variable | Default | Purpose |
| --- | --- | --- |
| `FILESYSTEM_RPC_PORT` | `8820` | Internal RPC listener |
| `FILESYSTEM_HTTP_PORT` | `8822` | Direct object-download listener |
| `FILE_SYSTEM_MEDIA_PATH` | `/data` | Object storage directory |
| `FILESYSTEM_PUBLIC_URL` | Required | Externally reachable HTTP origin |

Lucene configuration:

| Variable | Default | Purpose |
| --- | --- | --- |
| `LUCENE_RPC_PORT` | `8821` | Internal RPC listener |
| `LUCENE_BASE_PATH` | `/data` | Index storage directory |

Existing configuration names remain supported to avoid a deployment migration. Neither service
reads business selectors, database settings, user credentials, or alpha/bunker domain rules.
RPC endpoints must stay on a trusted internal network; this separation does not introduce RPC
authentication or signed download URLs. Filesystem HTTP currently serves objects publicly to
anyone who can reach their URLs. Business authorization is not implemented by either service.

Clients and servers must be rebuilt together after this protocol extraction: RPC interfaces now
use the independent `com.storyteller_f.services.*.api` namespaces.

## Service log collection

- Compose overrides `LOG_PATH` to `/home/app/logs/<service>` for server, worker, ws and cli. All four services mount the shared `service-logs` volume; Promtail mounts it read-only at `/var/log/A` and collects each service directory, including rotated `.log` files.
- Runtime images create the log directory owned by the application user. Container logs no longer depend on a host `LOG_PATH` bind mount. Recreate the application services and Promtail together to apply the volume/path changes; existing host log files are not automatically migrated.

## Windows AVIF native library

Set `AVIF_LIBRARY_PATH` in the environment of the JVM process to the directory containing
libavif and its dependent DLLs. For a PowerShell development or test session:

```powershell
$env:AVIF_LIBRARY_PATH = 'C:\tools\msys64\ucrt64\bin'
```

`loadAvif()` uses this value as `jna.library.path` on Windows and reports a configuration
error when it is missing or blank. There is no fixed installation directory or vcpkg detection.
DLL naming and dependency installation remain the caller's responsibility.
### Desktop Appium on Windows and Linux

Desktop E2E helpers select the host platform by default. `APPIUM_DESKTOP_PLATFORM` can explicitly select `Windows` or `Linux`; the Appium server must run on that same platform. Other platforms fail with an explicit error.

- Windows: use Appium 3 with `appium driver install --source=npm appium-desktop-driver@2.8.0` (verified with DesktopDriver 2.8.0). The helper launches the JDK 21 JVM with `appium:javaSwing=true`, uses a JVM argument file for long classpaths, and operates on the Java accessibility tree. No WinAppDriver or Java Access Bridge installation is required for this driver.
- Linux: use the repository driver with `appium driver install --source=local "$PWD/dev/appium-linux-driver"`. It uses open-source AT-SPI2 through `python3-pyatspi`, `xdotool`, `xdpyinfo`, and `/usr/share/java/java-atk-wrapper.jar`. The driver rejects unavailable or zero-size displays before launching the JVM. It does not depend on `libstdspalinux.so`. The package is currently tested from source; publishing it is a separate step.
- Linux driver regression tests: run `npm ci` and `npm test` in `dev/appium-linux-driver`. Element handles retain AT-SPI object paths across navigation changes; Click and text-input commands preserve the saved identity; text input resolves that path directly, including when labels change. Missing or defunct targets return a stale-element error, including disappearance during a write. Text-input failures never select another editable control or use cached coordinates; non-stale editable-text failures are reported directly. Lookups respect the WebDriver implicit wait rather than adding a fixed wait.
- `APPIUM_DESKTOP_SERVER_URL` defaults to `http://127.0.0.1:4723`; `APP_DESKTOP_TEST_JAVA` optionally selects the Java executable.
- Test JVMs use English UI labels, a JVM-local Preferences factory on Windows, and isolated database and protected-storage directories. Windows app logs go to the same per-test desktop log files as Linux. Desktop accessibility maps the existing `appiumSemantics` labels/actions so image descriptions do not hide account and topic text. Android retains its native semantics. The Windows launcher preserves the system temporary directory during Java-agent initialization before switching the application to its isolated directory. Gradle also builds a test-only compatibility agent: DesktopDriver 2.8.0 calls the removed Component.getPeer() method on JDK 21, so this agent replaces its getHwnd lookup with the JDK 21 AWT peer field lookup and dispatches accessibility read queries on Swing EDT (mutating commands retain the driver's existing EDT dispatch). It opens the two required java.desktop packages only to the test agent module. The agent verifies the supported bridge bytecode before installing transformations; unsupported bridge versions or method signatures fail explicitly; installed Appium files are not patched. The helper records the launched JVM PID and switches between that process's native frame and dialogs. Session cleanup force-closes the test JVM and deletes its PID file.

Build `:app:desktopApp:writeAppiumRuntimeClasspath` and `:panel:desktopApp:writeAppiumRuntimeClasspath` with `-Pserver.flavor=dev -Pserver.buildType=debug` before running `:app:desktopAppium:appiumTest` and `:panel:desktopAppium:appiumTest`. Run these suites sequentially on the same desktop. Docker/Testcontainers must be available for the backend services; on Windows use the QEMU Docker proxy described above.

For Linux desktop E2E, install `xvfb`, `openbox`, `x11-utils`, `dbus`, `libsecret-tools`, `gnome-keyring`, the Linux driver dependencies above, and Appium on PATH. Authentication scenarios require an unlocked native keyring on the isolated D-Bus session; use an isolated `XDG_DATA_HOME` rather than the personal desktop keyring. Then run:

```bash
bash scripts/test_scripts/run-linux-desktop-e2e.sh -Pserver.flavor=dev -Pserver.buildType=debug
```

The runner owns a 1600×1000 virtual X11 display, a window manager, a separate D-Bus/AT-SPI session, and a loopback Appium server. `APPIUM_DESKTOP_PORT` selects its port (default 4727). It submits user and panel desktop E2E tasks in one Gradle invocation with --continue. The shared desktop BuildService serializes the suites while preparation remains parallel. Test filters and supported Test task options apply to both suites; Gradle-wide arguments apply once. It propagates failures and stops only the processes it started. Diagnostics remain under `build/linux-desktop-e2e.*`; raw logs can contain test credentials and must be sanitized before sharing. Application rendering settings, test cases, assertions, and timeouts are unchanged.

Do not use a disconnected user Xwayland session for unattended testing: the display can report 0×0 while Java and the X11 window remain alive. In the diagnosed failure, repeated thread dumps found the UI thread in Skiko `swapBuffers` and ATK waiting for UI dispatch; AT-SPI subsequently stopped exposing the application. An owned display removes that dependency rather than masking missing elements. See [Skiko's rendering properties](https://github.com/JetBrains/skiko/blob/master/skiko/src/jvmMain/kotlin/org/jetbrains/skiko/SkikoProperties.kt) for the independent rendering/vsync controls used during diagnosis.

Appium selectors use English consistently. Desktop JVMs set en/US; browser E2E variants set en-US preferences. Android 13+ fixtures capture the original app locale before clearing data, temporarily set en-US, and restore the original override after cleanup without changing the system language. Linux modal dismissal sends Escape through the dedicated `linux: dismissDialog` driver command; page back retains Alt+Left. Older Android devices must already have an English system locale. Browser lifecycle scenarios restart each browser with an isolated in-place profile so HttpOnly Cookies and encrypted IndexedDB records must survive process termination.

### Wasm browser unit tests

Browser suites use Chrome Headless; install Chrome or set `CHROME_BIN` before running the Wasm browser test tasks. Compose UI test targets declare `binaries.executable()` so the Compose plugin can bundle Skiko with the test runtime.

The `wasm-browser-test-interop` convention is applied to `shared` and `client/room`. It lowers only the `kotlinx-io` Node `createRequire` getter in generated `.import-object.mjs` files, preserving its module URL while preventing a bare `import.meta` alias from reaching Karma classic-script bundles. The loader is tracked as a test input and has regression coverage via `node --test config/webpack/wasm-node-import-meta.test.cjs`. It does not patch cached dependencies or generated files.

Navigation tests compare `NavBackStack.toList()` with the expected history, because wrapper object equality is not the cross-platform contract for the stack contents.

### Local Android test initialization

Host test bases are abstract. Android host tests that access ICU or Compose resources run with Robolectric; resource tests initialize the Compose AndroidContextProvider. AsciiDoc preview tests belong to headlessTest and run on Android host, JVM and Wasm; the Javet parser test and its npm fixtures belong to jvmAndroidTest because its native Java APIs do not support Wasm. Gradle prepares the fixture dependencies with npm ci (npm must be on PATH); a missing dependency fails the test. Robolectric resolves Javet's JVM loader on its host runtime classpath, while Android production keeps the Android loader.
### Android Appium fixture lifecycle

The Android helper clears the previous test app before creating backend containers and rebinding `adb reverse`, then force-stops it after recording/log export and session shutdown, before backend teardown. `noReset` preserves app processes after a session closes; those processes must not keep requesting an old host port while the next fixture replaces its reverse target. ADB rejects a device connection to a target absent from its current reverse configuration with a fatal exit ([upstream guard](https://android.googlesource.com/platform/packages/modules/adb/%2B/HEAD/adb.cpp)). This is a fixture ownership issue, separate from QEMU forwarding. Do not bypass the guard, add connection retries, or relax test timeouts.

### File upload integrity

Resumable uploads require both raw-file SHA256 (64 lowercase hexadecimal characters) and whole-file CRC32C (four big-endian bytes encoded as canonical Base64). The client computes both in one source pass, stores both with its resumable state, and sends both in `Files.Chunks.InitBody`. `UploadRecords.sha256` and `UploadRecords.crc32c` are NOT NULL and have no defaults. The backend model validates both encodings. The OSS `compose` boundary owns verification: filesystem uses SHA256; MinIO uses CRC32C. The existing final-file SHA256 field remains unchanged.

Filesystem updates its SHA256 digest while copying the ordered sources into a temporary file in the target directory. It publishes the target by atomic rename only after verification and removes temporary output on failure. Existing targets and source parts survive a failed verification; the upload coordinator separately marks the session failed, releases quota and removes its chunks.

MinIO uploads source objects through a buffered stream with CRC32C and FULL_OBJECT checksums, computing the SDK checksum as it reads the upload stream. During compose, OSS requests each source's native checksum metadata, requires FULL_OBJECT CRC32C, and combines the verified CRCs with the actual source lengths in source order. CRC32C linearization gives the checksum of the concatenated bytes, not a composite hash-of-hashes. It compares that value with the expected CRC32C before copying and pins each source with its ETag during server-side compose. It does not download object bodies, reread source files or trust user metadata as checksum evidence. MinIO's multipart-copy handler does not feed its checksum reader, so requesting a checksum directly on that path fails; see the [MinIO handler](https://github.com/minio/minio/blob/RELEASE.2025-07-23T15-54-02Z/cmd/object-multipart-handlers.go). The composed object need not expose a native whole-object CRC32C header; verification is performed by the OSS boundary using immutable source snapshots.

Normal batch uploads and file copies use independent quota-operation locks rather than checksum-less upload-session rows. The admin upload-record list now represents resumable sessions; its test initializes a session and checks both digest values round-trip through PostgreSQL and the API. Direct-upload file-list coverage remains in place.

This alpha protocol has no legacy fallback. Recreate an existing alpha database from the updated schema and clear old resumable-upload state before using the new client and server together. Do not populate old rows with dummy checksums. No running database is reset by the implementation. MinIO checksum integration tests use RELEASE.2025-07-23T15-54-02Z; the verified cached upstream image can be imported when the registry refuses anonymous pulls. Cloud server integration tests keep the PostgreSQL, filesystem RPC and Lucene RPC topology; MinIO tests run separately in `backend/minio`.

Linux server image tests also require the native libavif shared library and its codec dependencies; expose their directory through LD_LIBRARY_PATH when they are supplied in an isolated runtime rather than installed system-wide.

### Deployment and RPC lifecycle verification

Application backend environment overrides are generated only for enabled cli/server/worker/ws services. Container server and WebSocket listeners use port 80; published host ports remain configurable. Filesystem public URLs come from the flavor file and the same Compose-resolved value is passed to filesystem and applications. Bunker credentials and domain-prefixed settings use the final variable names in deploy/bunker.env.example, injected only into BunkerWeb through raw env_file (Compose 2.30+). Existing external files using BUNKER_* placeholders must be rewritten using the example; they are not automatically migrated or overwritten.

Run scripts/test_scripts/compose-service-test.sh for generated configuration regressions. Validate real Compose combinations with non-secret fixture environment files before deployment. MinIO runtime images install curl and initialize /data for UID 1000 so a fresh named volume works. File search indexes both the original analyzed name and an unstored lowercase full-name field; queries search both fields, while responses preserve the original name. Filesystem RPC upload state belongs to one WebSocket connection, and session completion removes unfinished uploads after its calls stop. The disconnect regression closes one real RPC WebSocket and verifies its temporary files disappear without affecting another connection.

Worker uses a Temurin 21 JRE on Ubuntu Jammy for the glibc-linked LiteRT JNI libraries. The shared Docker build stage remains JDK 21 Alpine; keep Worker runtime dependency installation and user creation specific to Ubuntu.

### RPC adapter recovery

Lucene and filesystem adapters retain an RpcServiceProvider rather than a permanent RPC proxy. ManagedRpcService serializes transport establishment with a coroutine Mutex and performs one health call before publishing a new connection. Each business operation acquires once; filesystem upload batches and chunked downloads keep that proxy for the entire operation. A disconnected connection is replaced only for a subsequent operation. Failed reads or writes are never replayed automatically, and a partial upload is never continued on another WebSocket. Caller cancellation propagates; failed or cancelled establishment closes its candidate and does not populate the cache. WebSocket completion closes the corresponding client resources.

Loopback recovery tests restart a real CIO RPC listener on the same port while keeping the same adapter. They check shared concurrent reconnection, interrupted writes, uploads, caller cancellation, cancelled connection establishment and cancelled Mutex waiters. Core container tests replace the independent Lucene/filesystem container with its original published port explicitly bound, without rebuilding the adapter. Docker randomly allocated published ports may change on a plain container restart. Ktor loopback fixture startup calls the top-level embeddedServer explicitly, preventing the CoroutineScope overload from making the startup scope wait for the entire server lifetime.

Environment selection is shared in scripts/service_scripts/service-env.sh. It resolves a name or file path without sourcing dotenv contents as shell code, exports APP_ENV_FILE for all application env_file entries, and resolves BUNKER_ENV_FILE from the selected file relative to its directory. Validate explicit paths (including spaces), relative Bunker paths and name fallback through compose-service-test.sh and real Docker Compose config.
When a fresh Docker Wasm build rejects the lock while an incremental host build accepts it, compare generated package.json manifests before changing the lock. Old transitive npm entries can remain in generated application manifests; regenerate those manifests after dependency changes and validate strict lock storage on the actual deployment platform.

Deployment browser checks must load full Wasm responses and exercise actual rendering and authenticated operations. App and panel Permissions-Policy must allow cross-origin-isolated=(self) alongside COOP/COEP. Media CORS belongs to BunkerWeb: enable USE_CORS with an app/panel origin allowlist so OPTIONS returns 204 rather than reaching the object handler and triggering bad-behavior bans.
For configuration-only refreshes of existing Docker-built artifacts, use scripts/service_scripts/start-service-in-docker.sh <environment> --no-build. The default command still builds artifacts inside Docker.
Panel login uses the selected signing algorithm and its encryption key when required. Supply an explicit ViewModel initializer on Wasm; its default factory cannot construct the login ViewModel. PanelInputHost accepts coordination/worker dispatchers and owns input state until its parent lifecycle ends.

### Redis sign-in authentication

App sign-out replaces the account modal with its confirmation; avoid nesting a confirmation Dialog inside another Dialog.
PanelSignoutHost owns confirmation on the injected serial coordinator; the account GlobalDialog owns the request, protected-history exit and success-only account reset.

Bunker deployments attach signin-redis to the same bw-services network as Server and WebSocket.
When upgrading an existing bunker.env, retain X-Account-Id in the API CORS_ALLOW_HEADERS list from deploy/bunker.env.example; authenticated browser requests require it.

`cloud/runtime` registers independent user and panel session providers (`user_session`, `panel_session`).
Cookies contain random session IDs; Redis keys hash the credentials and isolate the two namespaces.
Pending sign-in/2FA challenges last five minutes. Completing sign-in rotates the credential. Successful
sessions have 30-day idle and 90-day absolute limits. Redis TIME and atomic Lua operations own expiry,
activity updates and revocation; ordinary reads never renew the session. Account indexes support all-device
revocation. Redis must use persistent, non-evicting storage; restoring an older backup can restore revoked
sessions, so disaster recovery must invalidate restored authentication data before accepting traffic.

Native GUI session storage is separate from key history and is shared by HTTP and WS. Browser cookies
are managed by the browser rather than the Ktor in-memory jar. Client 401 handling ends the account scope
and requires explicit login; 503/network failures retain credentials. Mutations are never automatically
replayed. Browser Origin values must appear in `SIGNIN_ALLOWED_ORIGINS`; missing Origin is allowed for
native clients. Production Cookies use Secure, HttpOnly and SameSite=Strict. Test/dev HTTP is explicit
through BUILD_TYPE; production requires HTTPS/WSS and same-site browser hosting.

WS rechecks Redis before each business message and every five seconds while idle; these checks do not
renew activity, while explicit outgoing business messages do. A Redis outage closes the WS temporarily.
The client opens WS directly with its Cookie. Only a failed handshake probes the HTTPS session endpoint
because browser handshake errors do not expose reliable HTTP status. Policy closes invalidate local login. Persistent handshake failures stop after three attempts and expose an explicit room-input refresh action; temporary HTTPS
failures back off to one minute. No outgoing operation is replayed.

Pointer/key input is reported through authenticated activity endpoints and updates are coalesced to
five minutes. User/panel sign-out-all endpoints delete that account's Redis sessions atomically.
`cloud/runtime` tests use an isolated Redis container; server integration fixtures also include Redis
alongside PostgreSQL and the filesystem/Lucene RPC services. Run client host tests with
`./gradlew :client:core:jvmTest :client:composeCore:jvmTest -Pserver.flavor=sample -Pserver.buildType=test`.
Run Redis regression tests with `:cloud:runtime:test` through the QEMU Testcontainers wrapper on Windows.

Authenticated Appium fixtures now inject the Cookie obtained by an explicit setup login along with the
synthetic account key. Native fixtures load that Cookie into an isolated memory jar; browser fixtures
install it through WebDriver on the owned bootstrap origin. The Wasm distribution server starts before
the backend so its exact origin can be allowlisted. This replaces the old saved-key auto-login assumption
without granting a production authentication bypass.

Restored account addresses and the public X-Account-Id assertion prevent a shared browser Cookie from
silently changing the identity of an existing page. Add X-Account-Id to the gateway's CORS allowed headers
when upgrading. The assertion is never accepted as authentication and is only sent to the API origin.
WS carries the public expected account ID in its URL and verifies it before registering subscriptions.

Explicit sign-in obtains a fresh server-generated UUID challenge through get-data. Client timestamps
are not accepted as authentication challenges. Starting reauthentication invalidates the current role's
session; every get-data request requires X-Requested-With: A-client, preventing ordinary browser
navigations from issuing a pending Cookie that overwrites an existing login. Old login
signatures cannot mint another session using a caller-chosen timestamp. Normal 401 challenges never
invalidate a different tab's valid session merely because its expected account ID no longer matches.


### Protected client account history

SessionHistoryManager operations are suspending. Account history, including all private keys and the
selected account, uses the protected-value expect/actual boundary also used by native Cookies. The
Settings dependency and AndroidKeyStoreUserPass are removed. Old Settings history is neither read,
migrated nor deleted; users import their account keys again. New protected writes are read back and
verified before reporting success. User and panel history have separate storage keys. Do not use a saved key to silently
reauthenticate after Redis expiry/revocation.

Android encrypts records with AES-GCM and an Android Keystore key, committing ciphertext to private
SharedPreferences `protected-data`, excluded from cloud backup and device transfer in both Android
apps because the Keystore key cannot be transferred with it. Windows stores DPAPI ciphertext under `~/.a/protected-storage/`
using atomic file replacement, avoiding Java Preferences' per-value size limit. macOS stores secrets
in Keychain; Linux uses Secret Service through `secret-tool`, with bounded, cancellable subprocesses.
Account history persistence fails if protected storage is unavailable; Cookie persistence may retain
an in-memory login but never falls back to plaintext. Browser account records and non-extractable
AES-GCM keys use IndexedDB. Record names are authenticated as additional data. This protects stored
key bytes, but scripts executing in that origin can still request decryption; it does not provide the
HttpOnly boundary of the login Cookie. HTTPS or localhost is required for WebCrypto.

Run the browser storage boundary regression with
`python scripts/test_scripts/test-browser-protected-storage.py --browser <Chromium-or-Edge-executable>`.
It uses an isolated headless profile under build/, checking durable reads, namespacing, key export
rejection and authenticated ciphertext modification. Client host tests also cover protected history restoration
and preservation of saved state on failed/unverifiable writes. Existing history tests use an isolated
protected namespace and `app.protected.storage.dir` under the module build directory, avoiding real
account storage and Java Preferences.

Sign-in proofs are atomically consumed in Redis before account creation/first-factor sign-in and after
successful TOTP verification. First-factor and TOTP transitions rotate Cookies; concurrent replay cannot
issue another session from one proof. Anonymous public/401 requests do not create pending Cookies,
preventing an older background response from overwriting a freshly signed-in browser Cookie.


Sign-in storage uses `backend/core`'s SigninSessionService / SigninSessionServiceFactory contracts and the
same ServiceLoader registration pattern as other backend services. `backend/redis` registers the Redis
provider under META-INF/services. `SIGNIN_SESSION_SERVICE` defaults to `redis`; unknown providers or a
missing endpoint fail at startup. `cloud/runtime` only adapts the service to Ktor SessionStorage and
closes the registered services when the application stops. It does not construct a Redis driver client.
The existing service-file merge includes this provider in server/WS distributions. Regression tests
build the registered provider, exercising registration along with the Redis storage behavior.

Gradle enables parallel project execution by default. Detekt Worker API remains disabled because Detekt 2.0.0-alpha.6 starts process-isolated workers without a configurable heap, and Compose analysis exhausts that heap. Detekt runs within the 6 GB Gradle daemon instead; its internal parallel AST analysis remains enabled. Worker capacity follows Gradle defaults; ordinary compilation should not force --max-workers=1. Run compilation and auto-correcting Detekt in separate invocations. Docker builds inherit the same project settings.

Remaining concurrency boundaries: Appium tasks use one test fork and platform-specific single-use Gradle build services (android, desktop, browser), serializing App and Panel tests within each platform while allowing different platforms to run concurrently in one Gradle invocation. These services do not coordinate separate Gradle processes; shared-device leases remain required; the standard --e2e runner submits six Appium and two CLI targets together in one Gradle invocation with --continue, allowing independent platforms to proceed after a test failure. Test filters are applied to every target. CLI E2E tasks use the global Gradle worker count as fork capacity; App and Panel tasks and independent test classes may run concurrently. Each currently has one class containing one scenario, so the existing suites can run at most one process per target. Test Docker images use independent immutable contexts and build concurrently. Deployment submits all selected targets in one Compose build; the standalone image helper starts its four builds concurrently and waits for all results; BuildKit sharing=locked serializes builders sharing Gradle caches. CI concurrency cancels obsolete runs for the same workflow/ref and does not limit workers inside a build. Unit-test tasks outside the reviewed host-test allowlist retain Gradle's default single fork; independent project tasks can run concurrently.

Reviewed host-test tasks in api, shared, client/core, client/composeCore, client/asciidoc-parser, backend/redis, app/composeApp, panel/composeApp, cloud/service, services/lucene-protocol, cloud/filesystem-service, cloud/lucene-service, dev/appiumCore and cloud/worker use Gradle's maximum worker count as their default fork capacity, without a separate fixed cap. The reviewed container-backed test tasks in backend/core, backend/filesystem, backend/lucene, backend/minio, cloud/runtime, cloud/server and cloud/cli also use the default worker capacity. Set -PunitTest.maxParallelForks=1 (or another positive count) to override these host-test tasks. UI, other persistence and Appium tasks retain their existing fork settings; all forks remain subject to Gradle worker capacity.

Wasm compilation tasks have no dedicated concurrency cap and follow the global Gradle worker budget. Six production compilation tasks explicitly rerun together passed with the 8 GB Kotlin daemon in 1m29s. Heap sampling approached capacity with substantial full-GC activity, so this validates the current six-task graph rather than arbitrary future workloads.

The default Kotlin daemon maximum heap is 8 GB (gradle.properties), raised from 6 GB after observing Wasm heap pressure on a 32 GB host. This is a maximum heap, not a reservation; keep enough memory for Gradle, test forks and native tools.

The Gradle daemon maximum heap is 6 GB, raised from 4 GB to give in-process Compose Detekt analysis more headroom. Kotlin daemon heap remains independently configured at 8 GB.

The expanded host-test allowlist includes in-process Ktor fixtures and local Lucene/filesystem tests with unique temporary directories. App JVM tests already exclude device_based UI tests; their protected-history test is confined to one class. dev/appiumCore tests exercise bridge/capability helpers rather than Appium suites. Modules mixing ordinary tests with Docker integration (backend/core, backend/minio, backend/filesystem, backend/lucene, cloud/runtime, cloud/server and cloud/cli) now use the same worker-based fork capacity after reviewing per-container mapped ports and isolated storage. cloud/worker is also allowlisted after reviewing all five test classes: its LiteRT tests substitute backend factories and do not initialize a native engine or execute model inference; remaining tests cover client construction, JSON/configuration and scheduling/moderation logic. Fork capacity does not reserve VM resources: unrestricted container startup on the 4 GB/4 CPU test guest produced Elasticsearch readiness timeouts. A seven-module validation with -PunitTest.maxParallelForks=4 passed 106 tests; this is a per-run override, not the project default. Increasing the local test VM to 8 GB subsequently passed the same 106-test selection with the default 28 workers and no fork override; guest memory peaked at 6290 MiB. To reproduce that resource setup, copy the installed skill dev.profile into an untracked local profile, set VM_MEMORY=8192, and pass that profile to lifecycle scripts and run-testcontainers.sh with --profile. Keep Docker and gvproxy port ranges aligned. client/kotbase and client/sqlite-now are not included in settings.gradle.kts.

Signin lifecycle regression coverage is in cloud/runtime RedisSessionStorageTest and cloud/server SigninLifecycleTest. The latter uses the existing PostgreSQL/Redis/filesystem/Lucene fixtures and checks real HTTP/WS cookie sharing, policy-close after revocation and panel session restore/revoke. Administrator signup must issue the success Cookie immediately; signout is idempotent after expiry, while signout-all requires an authenticated account.

Run the three-browser WebSocket regressions with `./gradlew :app:wasmAppium:appiumTest --tests '*test browser socket*' -Pserver.flavor=dev -Pserver.buildType=debug --console=plain` in the configured Docker test environment. These use explicit UI sign-in and a test-only observer of native WebSocket connection and message counts. They cover transport disconnection and recovery without message replay, authenticated browser restart, sign-out, removed/expired browser Cookies, server revocation and expiry, replay of revoked/expired Cookies and rejection of an unapproved browser Origin. The observer stores no credentials, URLs or message bodies. The server-expiry test moves the isolated Redis session deadline into the past while keeping the browser Cookie and stored key; it does not shorten production timeouts. Redis idle/absolute expiry also has storage-level coverage.

Snapshot signing configuration is loaded at the cloud/server boundary into an in-memory Base64 PKCS#12 value shared with the PDF adapters. Both OpenPDF and PdfBox read buffered in-memory streams. The legacy file configuration is converted at startup; Base64 configuration never writes a decoded private-key file. SnapshotKeyStoreTest covers source precedence, legacy input, malformed Base64, missing/wrong passwords and redacted diagnostics. SnapshotTest exercises signed PDF generation using Base64 input.

When deploying from an isolated source checkout, reconnect the existing filesystem
and Lucene bind-mount directories before the first `up`; they resolve relative to
that checkout, unlike named PostgreSQL volumes. Preserve newly created directories
separately while correcting a mistaken mount. The Compose wrapper reloads BunkerWeb
Nginx after `up` to resolve current service addresses after container replacement.

Dockerfile base images and build frontends retain readable version tags and pin their
manifest digests. For upgrades, resolve each upstream manifest with
`docker buildx imagetools inspect <image>:<version>` and update its digest explicitly.
Retry transient registry connection failures a few times before investigating further.

## Cross-platform RTC

The shared `RtcCallHost` owns call state independently of Compose. Each remote user has an
independent peer job and buffered signaling queue. ICE is queued until the remote SDP is ready;
peer departure cannot clear another peer's state. Native callbacks carry peer identities so a
late callback cannot change a replacement peer. Acquisition and shutdown retain ownership
through cancellation. The screen owns the call lifetime.
The shared WebSocket dispatcher translates recipient channel cancellation into a delivery
failure while preserving its own cancellation, so a disconnect cannot stop other rooms'
negotiation loop.

Media uses Ktor 3.6.0's experimental `ktor-client-webrtc` API on Android, JVM, Wasm and iOS.
Keep platform engines, permission prompts, renderers and native cleanup behind `RtcEngine`.
Ktor's JVM engine uses webrtc-java 0.16.0; detach sender tracks before connection shutdown,
remove video sinks before disposing tracks, and explicitly close the media factory and local
audio track. Remote JVM tracks are borrowed from their connection. Frame conversion runs on
a worker and balances retained native frames. Android's EGL/media factory is application-owned.
Browser audio belongs to the peer rather than a lazy video tile so scrolling cannot mute audio.
Browser empty-string ICE candidates signal the end of gathering; filter them at the shared
RTC boundary because native adapters reject them. Non-empty candidate errors still propagate.
The native negotiation regression checks decoded frames after receiving these markers.

Ktor 3.6.0 retains calls to the removed `js.core.JsPrimitives` conversion ABI.
A Wasm-only internal compatibility object forwards its four required conversions to Kotlin's
standard JS interop. Keep newer Wrappers for Kodio's URL API rather than downgrading the
wrapper graph. Remove the object after Ktor rebuilds against the newer ABI.
The web executable treats partial-linkage diagnostics as errors. Browser tests cover real
constraint conversion, synthetic audio/video capture, SDP generation and track cleanup.

Browser media capture normalizes Wasm `JsException` failures into Ktor device exceptions.
A missing capture device must return the call to its failed ready state; DOM exceptions can lack
JavaScript stacks, so do not retain them as causes in Kotlin-formatted diagnostic exceptions.

Run `./gradlew :app:composeApp:jvmTest --tests "*Rtc*Test" :app:composeApp:testAndroidHostTest --tests "*Rtc*Test" -Pserver.flavor=dev -Pserver.buildType=debug --console=plain`
for shared state/signaling and native SDP/ICE/cleanup coverage. The native JVM fixture uses
synthetic audio/video sources and real time. Compile Android, desktop and Wasm adapters;
use macOS with `-Ptarget.ios=true` for iOS, whose Xcode target links WebRTC 137.7151.04.
Real multi-client checks must cover permissions, media, joining/leaving, mute state, camera
switching and scrolling beyond four participants. Pure audio joins negotiate audio tracks.
Use `requestVideoFrameCallback` and increasing `presentedFrames` for browser presentation
checks. Firefox can leave `getVideoPlaybackQuality().totalVideoFrames` at zero for a live
MediaStream even as frames reach the compositor. Verify inbound audio packets separately.

The browser origin needs HTTPS and `Permissions-Policy` allowing camera, microphone and
autoplay for `self`; keep unrelated origins restricted. After deployment compare the browser's
loaded Wasm entry fingerprint with the running image, including versioned Compose resources.

The static client Nginx configuration compresses Wasm, JavaScript, CSS and SVG responses.
After deployment, request an entry Wasm asset with `Accept-Encoding: gzip`, verify
`Content-Encoding`/`Vary`, and compare decompressed bytes with the image. Keep HTML cache
validation enabled. Configuration-only releases must recreate the static containers to
reattach replaced bind-mounted configuration files, then reload proxy upstream resolution.
