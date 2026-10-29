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

Run `:cloud:server:test --tests com.storyteller_f.a.cloud.server.RoomActivityTest` against the configured Docker-compatible backend, with the usual dev/debug properties. PostgreSQL plus the existing filesystem and
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
viewport changes target the verified test JVM's native window through Appium, with DPI conversion.

Run `:app:androidAppium:appiumTest --tests '*test activity previews*'` with the usual dev/debug properties
through the configured Docker test environment and a leased device. Temporary Android size/density,
power and locale changes must be restored. Desktop acceptance uses the same named test under
`:app:desktopAppium:appiumTest` after preparing its runtime classpath. It requires an interactive unlocked
desktop for native keyboard/Escape input; an accessibility tree alone is insufficient. Do not claim
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

Run Gradle against a Docker-compatible backend configured by the operator. Required test images
are built by Gradle through the Docker CLI available on PATH; the Docker API and published service
ports must also be reachable by the host tests. An external environment runner may provide these
connections transparently. Keep provider installation, networking implementation, private profiles
and lifecycle commands outside this repository. Do not restart a shared backend during another run.
Build contexts must continue excluding secrets through `.dockerignore`.


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

- Logback XML uses the canonical import form: an XML declaration and `<!DOCTYPE configuration>`, followed by fully qualified `<import class="..." />` declarations. Appenders, filters, rolling policies and encoders reference the imported short class names; every encoder declares `class="PatternLayoutEncoder"`.

- Server request summaries join URL, status, method, query, headers and remote-IP fields on one line without adding formatting line breaks or transforming field values.

- Log filenames use the service name: `server.log`, `worker.log`, `ws.log`, `cli.log`, `filesystem.log`, and `lucene.log`; rotated files follow `<service>.yyyy-MM-dd.log`. Existing files keep their names and remain covered by Promtail's `*.log` pattern.

- Compose overrides `LOG_PATH` to `/home/app/logs/<service>` for server, worker, ws, cli, filesystem and lucene. All six services mount the shared `service-logs` volume; Promtail mounts it read-only at `/var/log/A` and collects each service directory, including rotated `.log` files.
- The CLI entrypoint also writes its startup/readiness records to `cli.log` when `LOG_PATH` is set, including when preset initialization is disabled. Verify this path with `bash scripts/test_scripts/cli-entrypoint-test.sh`.
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

- Windows: use Appium 3 with `appium driver install --source=npm appium-desktop-driver@2.8.0`. The helper launches the JDK 21 JVM using a quoted argument file with `appium:javaSwing=false` and operates on Tao's native UIA tree. No Java accessibility agent, bytecode patch, Java Access Bridge or WinAppDriver is needed. Queries use native UIA. Controls settle to visible/stable bounds before activation. Windows uses native focus and Space to activate focusable TabItem controls. Other controls with InvokePattern use DesktopDriver's `windows: invoke` after focus where available; SelectionItemPattern uses `windows: select`. The Tao tab provider exposes InvokePattern and keyboard focus without SelectionItemPattern; focus/Space activation was checked in a real native session. Remaining controls use `windows: hover` (200 ms) followed by `windows: click` (100 ms press). These are standard native UIA/keyboard operations; failed actions are not retried and assertions remain unchanged. Windows back navigation sends native Escape through the existing keyboard interface, allowing the active dialog/navigation dispatcher to consume it first; invoking a background back button can bypass a modal scene layer. Text entry uses native focus/keys. For native non-password Edit controls with an available ValuePattern, the standard Appium extension returns that value through DesktopDriver's existing `windows: getValue` interface. DesktopDriver's TextPattern fallback can return the control caption instead of its typed value. Other controls, passwords and WebView contexts retain their original behavior. The normal Windows Appium extension maps the exact UIA `0x80040201` / `UIA_E_ELEMENTNOTAVAILABLE` code to the standard stale-element response, allowing existing waits to re-locate retired controls. The COM message about event subscribers is misleading for this UIA code; unrelated errors remain unchanged and the extension never retries commands itself. Test assertions and timeout budgets remain unchanged. Child accounts authenticate before being published to the UI or starting protected background requests. Failure/cancellation closes the unpublished account, leaves the previous account active and reports the failure through the existing dialog; publication rejects a superseded account. The account chooser closes when the active account instance changes, so a successful switch cannot leave its modal sheet intercepting the next toolbar click.
- Linux: use the repository driver with `appium driver install --source=local "$PWD/dev/appium-linux-driver"`. It uses open-source AT-SPI2 through `python3-pyatspi`, `xdotool`, `xdpyinfo`, `gdbus` and GDK 3. The driver enables `org.a11y.Status.IsEnabled` on its desktop session bus before launching the application. A process can register both GTK and AccessKit providers; queries traverse all providers owned by that PID, and element identities include the D-Bus connection plus object path. The driver rejects unavailable or zero-size displays before launching the JVM. Window-size/rectangle commands target the single visible window owned by the launched PID; ambiguous ownership fails rather than resizing the focused desktop window. Screenshots return PNG data from the active X11 display using GDK 3 (`gir1.2-gtk-3.0`). It does not depend on `libstdspalinux.so`. The package is currently tested from source; publishing it is a separate step.
- Linux driver regression tests: run `npm ci` and `npm test` in `dev/appium-linux-driver`. Element handles retain AT-SPI D-Bus connection/object-path pairs across navigation changes; Click and text-input commands preserve the saved identity; text input resolves that path directly, including when labels change. Missing or defunct targets return a stale-element error, including disappearance during a write. Text-input failures never select another editable control or use cached coordinates; non-stale editable-text failures are reported directly. Lookups respect the WebDriver implicit wait rather than adding a fixed wait. Live geometry, visibility and enabled-state queries resolve the saved identity; the shared helper waits for stable, visible controls inside its existing timeout. Merged room and activity cards expose the room, visible message/metadata and unread count as one complete accessible description. Match complete comma-separated label parts, rather than arbitrary substrings.
- `APPIUM_DESKTOP_SERVER_URL` defaults to `http://127.0.0.1:4723`; `APP_DESKTOP_TEST_JAVA` optionally selects the Java executable.
- Test JVMs use English UI labels, a JVM-local Preferences factory on Windows, and isolated database and protected-storage directories. The launcher records the JVM PID and writes application logs into each test report. Window enumeration/switching uses DesktopDriver's remote interfaces; the project Appium extension validates process/thread ownership and performs DPI-aware native-window resizing on the Appium server. Session cleanup force-closes the test JVM and deletes its PID file. Test helpers never operate on host windows using a remote process ID.

Build `:app:desktopApp:writeAppiumRuntimeClasspath` and `:panel:desktopApp:writeAppiumRuntimeClasspath` with `-Pserver.flavor=dev -Pserver.buildType=debug` before running `:app:desktopAppium:appiumTest` and `:panel:desktopAppium:appiumTest`. Run these suites sequentially on the same desktop. Docker/Testcontainers must be available for the backend services; use the operator-provided Docker endpoint.

Linux desktop applications use Tao with native X11/Wayland and AT-SPI support. Provide a graphical session, compatible system graphics libraries and WebKitGTK; a headless-only OS image is insufficient. Configure enough desktop space for the test window bounds at the active DPI before running either platform.

For Linux desktop E2E, install `xvfb`, `openbox`, `x11-utils`, `dbus`, `libsecret-tools`, `gnome-keyring`, the Linux driver dependencies above, and Appium on PATH. Authentication scenarios require an unlocked native keyring on the isolated D-Bus session; use an isolated `XDG_DATA_HOME` rather than the personal desktop keyring. Then run:

```bash
bash scripts/test_scripts/run-linux-desktop-e2e.sh -Pserver.flavor=dev -Pserver.buildType=debug
```

The runner owns a 1600×1000 virtual X11 display, a window manager, a separate D-Bus/AT-SPI session, and a loopback Appium server. `APPIUM_DESKTOP_PORT` selects its port (default 4727). It submits user and panel desktop E2E tasks in one Gradle invocation with --continue. The shared desktop BuildService serializes the suites while preparation remains parallel. Test filters and supported Test task options apply to both suites; Gradle-wide arguments apply once. It propagates failures and stops only the processes it started. Diagnostics remain under `build/linux-desktop-e2e.*`; raw logs can contain test credentials and must be sanitized before sharing. Application rendering settings, test cases, assertions, and timeouts are unchanged.

Do not use a disconnected user Xwayland session for unattended testing: the display can report 0×0 while Java and the X11 window remain alive. In the diagnosed failure, repeated thread dumps found the UI thread in Skiko `swapBuffers` and ATK waiting for UI dispatch; AT-SPI subsequently stopped exposing the application. An owned display removes that dependency rather than masking missing elements. See [Skiko's rendering properties](https://github.com/JetBrains/skiko/blob/master/skiko/src/jvmMain/kotlin/org/jetbrains/skiko/SkikoProperties.kt) for the independent rendering/vsync controls used during diagnosis.

Appium selectors use English consistently. Desktop JVMs set en/US; browser E2E variants set en-US preferences. Android 13+ fixtures capture the original app locale before clearing data, temporarily set en-US, and restore the original override after cleanup without changing the system language. Linux modal dismissal sends Escape through the dedicated `linux: dismissDialog` driver command. Desktop test navigation sends native Escape on Windows to preserve modal priority; Linux uses the application’s labeled back action through Appium. The Linux driver’s low-level back command remains an Alt+Left key action. Older Android devices must already have an English system locale. Browser lifecycle scenarios restart each browser with an isolated in-place profile so HttpOnly Cookies and encrypted IndexedDB records must survive process termination.

### Wasm browser unit tests

Browser suites use Chrome Headless; install Chrome or set `CHROME_BIN` before running the Wasm browser test tasks. Compose UI test targets declare `binaries.executable()` so the Compose plugin can bundle Skiko with the test runtime.

The `wasm-browser-test-interop` convention is applied to `shared` and `client/room`. It lowers only the `kotlinx-io` Node `createRequire` getter in generated `.import-object.mjs` files, preserving its module URL while preventing a bare `import.meta` alias from reaching Karma classic-script bundles. The loader is tracked as a test input and has regression coverage via `node --test config/webpack/wasm-node-import-meta.test.cjs`. It does not patch cached dependencies or generated files.

Browser activity resize scenarios use a strict 504px compact content viewport: Windows Chrome enforces that minimum (522px outer width with 18px frame), while Firefox permits 500px. The 504px case still exercises bottom navigation, alongside 900px and 1600px layouts; viewport equality and draft preservation assertions remain strict.

Edge E2E creates an `about:blank` target through the browser's
[DevTools HTTP endpoint](https://chromedevtools.github.io/devtools-protocol/index.html#http-endpoints)
before issuing page-scoped WebDriver commands, then closes the isolated browser's startup tabs.
Those commands would otherwise wait for Edge's external MSN new-tab navigation. Bare URLs in
ChromiumDriver args are parsed as switches, so they do not select a positional startup document.
The profile-persistence E2E verifies one blank target on fresh launch and restart, alongside Cookie
and IndexedDB retention. Normal browser windows, page loading, strict viewport checks and timeouts remain unchanged.

The `app/composeApp` browser bundle disables resolution of unused `events`, `fs`, `os` and `path` externals emitted by transitive Kotlin wrappers. These generated namespace aliases have no browser callers; do not add Node polyfills for them. Validate both the library production bundle through `assemble` and the real `app/webApp` browser entry through the three-browser E2E matrix.

Navigation tests compare `NavBackStack.toList()` with the expected history, because wrapper object equality is not the cross-platform contract for the stack contents.

Appium runners pass their target and platform helpers to the typed `AppiumTestBase`
constructor. Shared test flows extend that base and obtain both helpers and the JUnit test name
from it; concrete test methods only supply scenario options, such as `privateRoom = true`.
Wasm-only extensions require a `WasmAppiumHelper` receiver type.

### E2E navigation and readiness

Room-row native accessibility merges the room-name description with message-preview descriptions.
Keep the label on the name Text so previews and unread counts remain available to native readers.
Session-revocation and socket-reconnection scenarios prepare communities and rooms before launching
the application. A cached pager need not observe setup writes performed through another client after
Home has mounted; these scenarios test session lifecycle rather than cross-client list refresh.
Activity read/send scenarios wait for the expected server-side latest message and zero unread together;
the optimistic UI echo alone does not establish persistence or read-marker completion.

Panel drawers keep account actions below the scrolling navigation list, visible in compact windows.
Clearing authentication closes the modal drawer so it cannot cover the sign-in page.

### Local Android test initialization

Host test bases are abstract. Android host tests that access ICU or Compose resources run with Robolectric; resource tests initialize the Compose AndroidContextProvider. AsciiDoc preview tests belong to headlessTest and run on Android host, JVM and Wasm; the Javet parser test and its npm fixtures belong to jvmAndroidTest because its native Java APIs do not support Wasm. Gradle prepares the fixture dependencies with npm ci (npm must be on PATH); a missing dependency fails the test. Robolectric resolves Javet's JVM loader on its host runtime classpath, while Android production keeps the Android loader.
### Android Appium fixture lifecycle

The Android helper clears the previous test app before creating backend containers and rebinding `adb reverse`, then force-stops it after recording/log export and session shutdown, before backend teardown. `noReset` preserves app processes after a session closes; those processes must not keep requesting an old host port while the next fixture replaces its reverse target. ADB rejects a device connection to a target absent from its current reverse configuration with a fatal exit ([upstream guard](https://android.googlesource.com/platform/packages/modules/adb/%2B/HEAD/adb.cpp)). This is a fixture ownership issue, separate from environment transport. Do not bypass the guard, add connection retries, or relax test timeouts.

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

Worker uses a digest-pinned Fedora 44 base with Temurin 21 JRE from the official
[Adoptium RPM repository](https://adoptium.net/installation/linux/#centosrhelfedora-instructions)
for the glibc-linked LiteRT JNI libraries. Fedora signed repositories provide libavif, the Vulkan loader,
libstdc++, Noto CJK/mono fonts and HarfBuzz core/subset (required version 13.0.0 or newer).
No native library source build is needed. The shared Docker build stage remains JDK 21 Alpine;
keep worker package installation and user creation specific to Fedora.

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
Run Redis regression tests with `:cloud:runtime:test` against the configured Docker-compatible backend.

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
in Keychain through native Security framework calls. This avoids the `security -i` interactive parser’s 4096-byte command limit without placing secret data in process arguments. The existing Base64 payload format remains compatible. When `app.protected.storage.dir` is set, macOS account names include its hashed namespace so independent tests cannot share account history; unset production storage names remain unchanged. Linux uses Secret Service through `secret-tool`, with bounded, cancellable subprocesses.
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

The expanded host-test allowlist includes in-process Ktor fixtures and local Lucene/filesystem tests with unique temporary directories. App JVM tests already exclude device_based UI tests; their protected-history test is confined to one class. dev/appiumCore tests exercise bridge/capability helpers rather than Appium suites. Modules mixing ordinary tests with Docker integration (backend/core, backend/minio, backend/filesystem, backend/lucene, cloud/runtime, cloud/server and cloud/cli) now use the same worker-based fork capacity after reviewing per-container mapped ports and isolated storage. cloud/worker is also allowlisted after reviewing all five test classes: its LiteRT tests substitute backend factories and do not initialize a native engine or execute model inference; remaining tests cover client construction, JSON/configuration and scheduling/moderation logic. Fork capacity does not reserve VM resources: unrestricted container startup on the 4 GB/4 CPU test guest produced Elasticsearch readiness timeouts. A seven-module validation with -PunitTest.maxParallelForks=4 passed 106 tests; this is a per-run override, not the project default. Increasing the local test VM to 8 GB subsequently passed the same 106-test selection with the default 28 workers and no fork override; guest memory peaked at 6290 MiB. Backend resource sizing and private lifecycle configuration belong to the operator; keep published service-port ranges aligned with the environment transport. client/kotbase and client/sqlite-now are not included in settings.gradle.kts.

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

### Worker model resolution

KoogClientFactory accepts only exact IDs from direct-provider catalogs: OpenAI, Anthropic, Google,
Ollama, Bedrock, DeepSeek, Mistral AI and DashScope. Metadata comes from Koog without manual aliases
or fallback limits. Defaults reference SDK constants. Google, DeepSeek, Mistral AI and DashScope
modules are published as 1.3.0-beta alongside the stable Koog 1.3.0 modules.

Resolve models before allocating clients. OpenAI-compatible endpoints pass opaque model IDs without
capabilities or token limits. OpenRouter also accepts arbitrary nonblank IDs; its LLModel declares only
the completion and standard JSON-schema operations required to pass Koog's local request validation.
The remote router validates actual model support. Neither path infers context or output-token limits.
LiteRT retains local model metadata.

Google pairs GoogleModels with native GoogleLLMClient and GoogleClientSettings. Bedrock uses native
Converse to preserve standard JSON-schema output. Its factory accepts a bearer API key or the AWS
SDK default credential chain, a region and optional runtime endpoint, with one request attempt.
An explicit API key prioritizes HTTP bearer authentication over ambient AWS signing credentials.
The runtime SDK constructs and owns its default credential chain, binding it to the configured region
and closing its credential cache together with the runtime HTTP engine.
Koog's DeepSeek adapter uses JSON-object mode instead of transmitting a JSON schema. The service
retains caller system instructions and includes the schema in that provider's system prompt; the
moderation parser still validates the returned decision.
The optional region field is backward compatible with existing serialized LlmConfig records.

The migration intentionally rejects unknown direct-provider IDs. Update stored IDs to match their
catalogs or use OPENAI_COMPATIBLE for custom compatible endpoints. Google endpoint overrides must
use a native API root instead of the previous OpenAI-compatible path; see README.md for configuration.
OpenRouter IDs remain opaque and need no catalog migration.

GoogleLlmExecutionTest and OfficialProviderExecutionTest exercise native routing, authentication,
prompts, request schemas and response decoding against ephemeral loopback HTTP fixtures.
BedrockFactoryExecutionTest creates clients through KoogClientFactory and replaces only the network
transport for signed/bearer Converse execution. Synthetic JVM credentials verify API-key priority,
regional signing, endpoint routing and schema conversion. SDK provider inspection verifies default
and explicit region propagation; closing the service must close the real default credential cache
and owned HTTP engine in both authentication modes. Tests restore JVM properties and serialize their
access. HTTP fixtures use real IO dispatchers and unique ports. No live cloud account or model
invocation is needed.

Run `./gradlew :cloud:worker:test -Pserver.flavor=dev -Pserver.buildType=debug --console=plain`.

### Appium artifact reports

Every Android, desktop and Wasm helper invocation writes a report under the runner module's
`build/reports/<test task>/index.html`. Open that index to find the exact test name, platform, result,
failure stack and all collected artifacts. Per-case reports link readable backend console logs,
service log archives, application logs, screenshots/page sources, Android recordings and available
failure diagnostics. Collection errors appear separately from the original test failure.

Server integration, CLI E2E and Appium tests write raw files under
`build/test-results/<test task>/artifacts/<test class>/<test name>/`. Appium adds a platform/browser
subdirectory. Names remain readable without hashes, random suffixes or attempt counters; filesystem-invalid
characters and reserved device names are percent-encoded. Each case writes `result.properties`, logs,
screenshots and other collected files without generating HTML or scanning other cases.

The `test-artifact-reports` convention owns the two phases. Before an executing Test task, it clears
only that task's raw artifact directory. Cached and up-to-date Test tasks retain their collected results.
The `<test task>ArtifactReport` JavaExec finalizer then generates a standalone report under
`build/reports/<test task>/index.html`, including artifact copies and safe HTML viewers. It runs after
test failures too. Gradle's native JUnit XML and HTML results remain in their normal locations.
Run the finalizer directly to regenerate reports from saved raw results without running tests.
Serialize independent builds that target the same module/task output directories.
Keep the generated report tree intact when exporting it so relative artifact links work.
Private injected sessions and desktop runtime storage are removed before report generation.

Run Appium collection lifecycle tests with
`./gradlew :dev:appiumCore:test -Pserver.flavor=dev -Pserver.buildType=debug --console=plain` without
an Appium server or a device. Shared metadata, HTML viewer and generator tests live in
`:dev:testInfrastructure:test`, whose full suite also includes a real Redis startup-failure test.
The former Appium viewer tests moved there with the shared renderer; lifecycle assertions now verify raw
metadata first, then explicitly invoke aggregation to check generated pages and links.

Artifact links open static HTML viewers rather than relying on server MIME mappings. Log/text/XML/HTML
content is escaped; tar, tar.gz and zip files show an archive listing with text previews without extracting
entry paths. Images and recordings are embedded in their viewers. Original files remain available through
an explicit download link. Text previews are limited to 2 MiB per file/entry and archives to 100 entries and 8 MiB of previewed text.

Filesystem and Lucene services package their own Logback provider/configuration. Both services emit
console and rolling-file logs. Service events use SLF4J directly;
INFO goes to stdout, WARN/ERROR to stderr,
and all enabled levels go to the rolling file, matching server/worker. Promtail reads
the shared volume under `/var/log/A/<service>/*.log` with the corresponding `job` label
and pushes to Loki. `LOG_PATH` controls the file directory; without it, standalone runs
use `build/logs/filesystem` and `build/logs/lucene`. Runtime images create `/home/app/logs/<service>` owned by the non-root service
user; Compose mounts the existing `service-logs` volume there for Promtail. Appium exports both
console output and the complete filesystem/Lucene file-log directories before container shutdown.
The service-specific startup messages must appear in both outputs; SLF4J provider warnings alone
are not evidence of successful log collection. In Loki, query
`{job=~"server|worker|ws|cli|filesystem|lucene"}` for backend logs, or
`{job="filesystem"} |= "Filesystem service starting"` and
`{job="lucene"} |= "Lucene service starting"` to verify the independent services.
Confirm returned records include the matching `/var/log/A/<service>/` filename;
container stdout alone does not verify the shared-volume/Promtail/Loki chain.


### Shared Testcontainers fixtures

`dev/testInfrastructure` owns the test topology for server integration tests, Appium and CLI E2E.
`useBaseServiceFixture` creates an isolated network, PostgreSQL, Redis, filesystem and Lucene services.
Its `hostEnvironment` contains mapped host endpoints; `containerEnvironment` contains Docker-network
aliases. The full `useBackendFixture` adds preset initialization, WebSocket, HTTP server and worker.
Each invocation owns independent containers and data; no container reuse or shared database is enabled.
Ktor test-host setup and business assertions stay in server tests. Appium retains device forwarding,
browser origins and session-expiry actions. CLI E2E retains process driving and assertions.

Fixtures copy presets through the Docker API, never a host bind mount. Gradle image tasks remain
responsible for building the images before tests. The context copy normalizes shell scripts to LF
without modifying working-tree line endings. Inputs are resolved relative to the repository,
and container logging ignores the host `LOG_PATH` from test.env.

Blocking Testcontainers startup runs on Dispatchers.IO. Cancellation waits for the current native
startup call to return, then releases the registered resources with non-suspending Java IO cleanup.
The owning scope runs on Dispatchers.IO; Kotlin use retains the original failure and suppresses
close errors. Cleanup
exports console logs and application log archives before container removal, including readiness
failures inside Testcontainers. Cleanup continues after collection/removal errors and attaches them
to the original failure; without an original failure it reports the cleanup error.
Appium and CLI service logs use the raw case's `logs` directory; server integration container logs
use `logs/containers` and SQL explanations use `explain`. Fixture cleanup exports files before writing
the case result metadata, so task-level report generation never needs a live container or device.
Full-backend containers use Testcontainers' default single startup attempt. Collection errors have *.error.txt records.

Run `:dev:testInfrastructure:test :dev:appiumCore:test :cloud:server:test` through the Docker wrapper,
with `-Pserver.flavor=dev -Pserver.buildType=debug`. The infrastructure tests include a real Redis
readiness failure to verify logs survive startup cleanup. Validate the complete backend through
`:app:cliE2e:e2eTest :panel:cliE2e:e2eTest` and the existing Appium suites.

## Configured desktop test environments

Tests require a Docker-compatible backend and an Appium desktop endpoint. Set
`APPIUM_DESKTOP_SERVER_URL` and `APPIUM_DESKTOP_PLATFORM` for the target, then use the normal host command:

```bash
./gradlew :app:desktopAppium:appiumTest --tests '*test sign up' \
  -Pserver.flavor=dev -Pserver.buildType=debug --console=plain
```

`dev/test-environments/local.json`, `windows-remote.json` and `linux-remote.json` describe the project workspace
and JVM file/service roles for an external environment runner. Connection profiles, VM lifecycle,
transport setup and runner installation belong to the operator's environment, outside this project.
The remote runner must stage host-built classpath/JDK/native artifacts after compilation, provide
backend reachability, collect logs and clean private state. Neither application nor test methods
know which environment provider is used. Both app and panel use the same role configuration. Linux launches use the same quoted JVM argument-file contract as Windows, with an Appium argument array and working directory. A cross-platform runner must translate host paths/classpath separators, supply an existing target JDK and matching prebuilt native runtime JARs, and return declared output files. The Gradle test JVM and backend fixtures remain on the host; the target desktop runs only the application. Linux requires a graphical Appium session with AT-SPI and an unlocked isolated native keyring. Select `APPIUM_DESKTOP_PLATFORM=Linux` and run the two normal Gradle tasks in one invocation: `./gradlew :app:desktopAppium:appiumTest :panel:desktopAppium:appiumTest --continue -Pserver.flavor=dev -Pserver.buildType=debug --console=plain`.

`dev/test-environments/desktop-requirements.json` is the portable source of desktop runtime requirements: Java/Appium versions, normal drivers/extensions, Linux binary runtime packages, matching native classpath artifacts and desktop dimensions. Both remote environment files reference it. Connection profiles, prebuilt bundle locations and platform provisioning remain operator-owned. Prepare the target from these requirements before running the normal host Gradle tests; the application runs remotely, while tests and backend fixtures remain on the host.

For special runtime dependencies, a platform may declare `hooks.host_prepare` entries with literal `command` argv, relative `cwd` and `timeout_seconds`, or `hooks.guest_setup` entries with a relative self-contained `script`, `interpreter`, literal `arguments` and `timeout_seconds`. Host preparation may compile or package dependencies. Guest initialization may configure the OS and install prebuilt artifacts, but must not compile applications, test code or native dependencies. Guest hooks receive opaque `TEST_DESKTOP_USER` and `TEST_RUNTIME_ASSETS` values so scripts do not need provider-specific usernames or storage paths. Hooks must remain in the foreground, fail on errors and avoid logging credentials. The environment initializer preserves private logs and stops on failure/timeout; changed guest script contents require reinitialization. These are ordinary project setup scripts; they must not reference AI-plugin installation paths, VM providers or private connection profiles.

Native accessibility diagnostics are the page source returned by the active Appium session and the report's screenshots. Collection errors remain separate attachments and preserve the original test failure. Tao does not expose an AWT accessibility tree; the former attach-agent exporter and Java bridge were removed rather than reporting an empty AWT tree as native diagnostics.

Use Appium 3.2.2 with DesktopDriver 2.8.0 in an interactive logged-in Windows session. Prepare `dev/appium-windows-plugin` with prebuilt dependencies, install it through Appium's local plugin installation and activate `--use-plugins desktop-windows`. Its native dependency is pinned in package-lock.json. Standard remote window enumeration/switching stays in DesktopDriver; this extension supplies process/thread ownership checks, logical-pixel native-window resizing and complete physical-pixel screenshots. It restores the Appium thread's DPI context after native calls, keeps resized windows inside their monitor work area, and rejects dimensions that cannot fit. After a validated inspect/resize request registers the session process, screenshot capture uses DWM visible bounds and excludes invisible resize borders; it rejects reused handles and verifies that the session application is foreground before reading pixels. Other Appium drivers retain their standard screenshot behavior. This avoids the logical-pixel UIA bounds observed with Tao at 150% display scaling. Neither path calls host User32/JNA. Local desktop runs also require this extension. The full suite resizes windows up to 1600×1000 logical pixels, so the monitor work area must accommodate that size; validation uses a 1920×1080 desktop at 100% scaling. Native screenshot/resize behavior was also checked separately at 150% scaling.

macOS desktop E2E requires a qualified renderer as described in [the Skiko investigation](#macos-e2e-pause-and-skiko-investigation). The retained integration uses Appium 3 with `appium-mac2-driver@4.5.0` and a compatible full Xcode installation (Sonoma 14.8 uses Xcode 16.2). Select Xcode's developer directory, accept its license and complete first launch. Build WebDriverAgentMac and grant its runner and Xcode Helper Accessibility permission in the dedicated logged-in test account. On a qualified target, select `APPIUM_DESKTOP_PLATFORM=Mac` with the normal `APPIUM_DESKTOP_SERVER_URL`. The runner creates an ad-hoc-signed temporary application bundle using the JDK’s precompiled native launcher for Launch Services, uses Mac2 native predicate/class locators, waits for a visible, enabled click target with stable native bounds, uses native pointer clicks because an element click can report success without focusing Compose editors, clicks text containers in an area not covered by their descendants when available, and uses native keys and window adjustment (JVM text input uses `macos: keys` with value readback because XCTest `typeText` can return success without changing a Compose editor), and removes the bundle during cleanup. Applications and tests require a prebuilt macOS JDK 21 and matching native runtime libraries. Intel macOS additionally needs a matching SQLite JNI library: the official SQLite bundled 2.7.0 JVM artifact includes macOS ARM64, but no x64 binary. Prepare an x64 resource JAR on the host/designated build environment with the release's JNI sources (AndroidX commit `190a3431c064ca5e90e451b3443714ae684b8127`), SQLite 3.50.1 and its upstream compile flags, and add `natives/osx_x64/libsqliteJni.dylib` to the application runtime classpath. Validate an actual query before UI tests; do not substitute another platform binary or silently compile it in a work VM. A ready Appium endpoint alone does not verify UI automation; verify a real session, source, screenshot and click before running the suites.

For a Windows/Linux host driving a qualified Mac target, the test helper emits the standard quoted JVM argument file. The external environment provider stages host-built artifacts, translates declared paths and service roles, packages the target JDK's precompiled native launcher into an ad-hoc-signed bundle, and supplies its app path and bundle identity to Mac2. Local Mac hosts retain direct bundle packaging. The provider must collect logs, remove private inputs and bundles, and fail on transport errors. A raw argument file is not an app bundle and must not be sent directly to Mac2.

After a macOS, Xcode or Runner change, verify the current WebDriverAgentRunner and Xcode Helper have Accessibility permission and restart the test agent. A retained enabled permission entry can refer to an obsolete application identity. Check the actual `macos: adjustWindow` API and native window dimensions before the suite; a successful session or pointer-drag resize does not qualify that API. Provision application network permissions in the test environment before interpreting a blocked click as a rendering failure.

### Native desktop windows, WebView and video

Both desktop entry points use `nucleusApplication(backend = NucleusBackend.Tao)` and the shared `DesktopWindow` adapter around `DecoratedWindow`, with a title bar and the existing application content/providers. The adapter binds Escape key-down to the window composition's `NavigationEventDispatcher` through a lifecycle-owned `NavigationEventInput`, matching Compose's AWT window mediator. This preserves dialog dismissal and navigation priority without a test-only dismissal path; root navigation remains protected by its existing enabled state. They deliberately disable Nucleus's additional single-instance lock so parallel isolated processes and restart tests keep their existing lifecycle. Deep links use Nucleus's native `onDeepLink` handler and the original application callback, avoiding the AWT-only Apple Event handler on macOS. Normal packaging still uses the Compose Gradle application configuration and Java 21.

[ComposeNativeWebview 1.0.3](https://github.com/NucleusFramework/ComposeNativeWebview/releases/tag/v1.0.3) uses `dev.nucleusframework:composewebview`; shared imports use `dev.nucleusframework.webview`. Its [1.0.0 migration](https://github.com/NucleusFramework/ComposeNativeWebview/releases/tag/v1.0.0) removed the Swing/Wry desktop backend. Pin the application and Tao runtime modules to stable Nucleus 2.5.18. AsciiDoc HTML previews use a temporary/incognito WebView profile, avoiding the WebView2 default data directory beside the Java executable (which can be read-only). WebView remains an embedded native surface: WebView2 on Windows, WKWebView on macOS and WebKitGTK on Linux. Compose dialogs use scene layers inside the Tao host, preserving the native WebView parent and application composition locals. The old `desktop-native-webview` convention, checksum-guarded Wry binding transform, JNA AppKit dispatcher and transform test have been removed. A dependency migration is not evidence that the historical macOS deadlock or Skiko stall is fixed; renderer qualification below is still required.

Desktop video uses VLC's callback surface rather than `SwingPanel` or an AWT player component. `DesktopVideoHost` owns playback state and native lifetime on an injected serial dispatcher, runs native construction/commands/disposal on a worker dispatcher, and consumes a conflated stream of copied opaque BGRA frames. Stream aspect-ratio/rotation metadata is read on the worker after VLC reports readiness; the visible image keeps that ratio even when the decoder output buffer has different dimensions. The Compose adapter builds raster images off the UI thread and renders the latest frame, playback controls and loading/failure state. Source changes and composition disposal cancel the owner and release the player/factory. A compatible system VLC installation is required; portable installations can provide their native library directory through JNA's standard `jna.library.path` and VLC's `VLC_PLUGIN_PATH`. Neither runtime installation nor media playback authorizes guest compilation.

Run `:client:composeCore:jvmTest --tests '*DesktopVideoHostTest' :dev:appiumCore:test` for frame ownership, playback lifecycle and launch-capability checks. Verify actual decoded video frames, pause/resume, native accessibility queries/input, modal dismissal, window resizing, AsciiDoc preview and restart persistence on an isolated Windows/Linux desktop. Preserve the existing business assertions and timeout budgets. Run macOS integration only on a qualified accelerated desktop as described below.

Work VMs run applications and tests using host-built artifacts. Guest compilation, including native dependency install hooks, requires an explicit user request. VM/Appium setup or a test request alone does not permit it; do not add guest compilation fallbacks or enlarge VM resources for builds. Transport failures fail the test instead of launching the application on the host.

## macOS E2E pause and Skiko investigation

Status: the hard-coded macOS desktop E2E pause has been removed after an accelerated Intel macOS 15.8.1 renderer qualified on 2026-10-25. Upstream Skiko 0.150.1 and unmodified Nucleus Tao 2.5.18 and 3.0.0-dev-202610230652 visibly rendered account, sign-in, sign-up and private-key editor navigation, synthetic text input and window resizing from 800 x 600 to 900 x 700, matching native accessibility state. No retired Skiko class replacement or frame cap was used. A fresh complete acceptance run on 2026-10-25 passed all 18 original desktop E2E cases (15 application and 3 panel), with zero failures or skips and a successful runner exit. The original assertions and timeout budgets were unchanged; the native WKWebView HTML and window resizing were checked against actual screenshots. This result is separate from the historical software-uploader experiment.

Run macOS desktop E2E only after either an upstream Skiko fix has been verified against the observed software-rendering stall, or the test environment has working GPU acceleration that allows Skiko to use a hardware renderer. WHPX accelerates the virtual CPU; it does not by itself provide guest GPU acceleration. A display device, a visible desktop, or selecting an OpenGL option is insufficient evidence of a hardware renderer.

### Observed failure and evidence

The affected environment used Intel macOS 14.8, JDK 21, Skiko 0.150.1, Appium Mac2 4.5.0 and XCTest. The VM had four virtual CPUs and 4 GiB RAM, used WHPX, and lacked a usable Metal adapter. Skiko fell back to CPU rendering. These findings do not establish the same latency on physical Macs or other architectures.

Repeated stacks showed the AWT event thread spending time in `ComponentColorModel.getRGBComponent`, `OpaqueCopyAnyToArgb` and `OGLGeneralBlit`, reached from `SoftwareContextHandler.flush`. During accessibility queries, AppKit waited for AWT through `CAccessibility.invokeAndWait`. Consequently native queries and physical-key input could wait behind software frame uploads. Recorded diagnostic queries took approximately 4–28 seconds; these samples were not a controlled cross-platform benchmark.

The [upstream 0.150.1 uploader](https://github.com/JetBrains/skiko/blob/v0.150.1/skiko/src/awtMain/kotlin/org/jetbrains/skiko/context/SoftwareContextHandler.kt) reads the full native bitmap into a byte array, creates a byte raster and a `BufferedImage` using `ComponentColorModel`, then draws that image through Java2D. The native bitmap is premultiplied; the image constructor describes its raster as not premultiplied. The observed conversion path justified investigating both image representation and alpha handling, rather than extending test timeouts.

### Removed experimental patch

The experiment replaced the pinned software uploader with a host-compiled class using a cached Gradle artifact transform. The exact reviewed `skiko-awt:0.150.1` JAR had SHA-256 `bb610ada28509ebee3a623c0469aff3665689fbec73d118055f3dbf5a37211f5`. The transform rejected different bytes; it was not an upstream release or a supported extension API.

The replacement retained a native premultiplied BGRA bitmap and copied its live pixels directly into a reusable `BufferedImage.TYPE_INT_ARGB_PRE` integer buffer. Explicit little-endian interpretation preserved BGRA-to-ARGB channel order. This avoided the intermediate frame-sized byte allocation and byte-component color conversion. Completely opaque frames used `AlphaComposite.Src`; translucent frames retained source-over blending. The experiment preserved the macOS transparent-window clear and released its native bitmap and graphics resources.

A separate experiment inserted an opt-in period cap into Skiko's existing `FrameLimiter`. Its removed JVM property was `desktop.software.maxFps`: unset/zero preserved the upstream period, and values 1–240 selected a minimum period without speeding up an already slower upstream schedule. The test environment selected 10 FPS. This was an environment accommodation, not a general performance fix: it reduced animation smoothness, and the hook modified the shared limiter rather than being structurally confined to software rendering. It should not be restored as a purported software-only setting.

The runtime class replacement, Gradle convention, shared-limiter hook, replacement source, patch-specific tests and dedicated Skiko test dependencies have now been removed. Both desktop applications resolve the upstream Skiko artifact again. Experimental transformed JARs in operator archives are historical diagnostics and must not be staged for new acceptance runs. Remove the obsolete JVM property from Appium default capabilities as well as launcher options; it no longer has an implementation in the project.

### Historical validation and limits

Three patch-specific host tests covered channel order and premultiplication/compositing, replacement of previous frame contents, and limiter default/configured/slower-period behavior. A separate native scheduling probe and WebView fix addressed an independent AppKit/EDT mutual wait. Text-click targeting and asynchronous sign-out synchronization were also corrected separately.

On 2026-10-21 the combined experimental setup completed one full macOS desktop E2E run: 15 application tests and 3 panel tests, with both JUnit suites and the whole runner exiting successfully. The existing 10-minute test timeout, 15-second UI wait and original assertions remained in place. Across 1,066 element-query commands, median duration was 69 ms, P95 was 338 ms and maximum was 826 ms. Physical-key command batches had median duration approximately 6.65 seconds and maximum 11.60 seconds; batch contents and lengths varied. One native click still took 18.54 seconds.

Those results validate that combined experimental environment, not the Skiko patch in isolation. They do not certify the unpatched runtime after this rollback or certify a newly qualified hardware environment. The historical pass must not be presented as current macOS acceptance.

Before proposing an upstream Skiko change, isolate the uploader optimization in a synthetic reproducer, measure frame-upload CPU time and allocations separately, and verify opaque/translucent rendering, partial alpha, repeated frames, resize, HiDPI, transparent windows, supported CPU architectures and all desktop software-rendering backends. The extra full-frame alpha scan also needs performance evaluation. Broader renderer compatibility was not established by the three unit tests or the macOS E2E run.

The WebView AppKit run-loop adapter was an independent dependency fix, separate from Skiko. It was later removed by the ComposeNativeWebview/Tao migration above. No upstream Issue or PR has been submitted for these findings. The migration alone does not qualify the macOS renderer.

### Qualification and regression procedure

1. Record the fixed upstream Skiko version and relevant change, or evidence that the VM actually uses hardware-accelerated Metal/OpenGL instead of a software implementation. Verify visible navigation, edited text, dialog presentation and resizing against accessibility state, without restoring the local Skiko class replacement or frame cap. Library loading and first-frame rendering alone are insufficient.
2. Keep platform availability independent of provider machinery. Maintain external renderer qualification and update this status when the qualified runtime changes; do not introduce an unconditional bypass flag.
3. Build applications, tests and required native dependencies on the host or designated build machine, then stage fresh classpaths containing the intended upstream Skiko artifact. Keep private connection profiles and environment orchestration outside this project. Do not fall back to TCG or compile in a work VM without explicit authorization.
4. Verify an actual Appium session, query, input with value readback, native WebView lifecycle and screenshots, then run all 18 desktop E2E cases from clean report roots with the existing budgets and assertions. Record a new acceptance result separately from the historical experimental reports.


## Typed operation results

The business-independent `:result` KMP module defines `CustomResult<T, E>` as `Success(value)` or
`Failure(error)`. A successful nullable value is distinct from failure. HTTP response handling continues
mapping successful null to 404, Unit to 200, and other values to the existing response model; CustomResult
is not serialized into HTTP or RPC payloads.

Operation owners declare sealed error interfaces: shared crypto/content validation, backend database/search/
object storage, client requests/credentials, and each cloud business service. Signatures must name the
scenario error; do not erase it to Throwable, Any, or FailureDetails. FailureDetails exposes a diagnostic
cause for logging and terminal consumers such as Paging, loading state and HTTP response writers.

Use explicit `mapError(ScenarioError::fromError)` conversion between scenarios.
Exception classification uses `ScenarioError.from`; typed error conversion preserves authentication,
permission, input and constraint categories independently of the diagnostic exception class.
Database authentication rejections retain their authentication category through service and HTTP conversion.
Reviewer construction captures provider initialization failures at the factory boundary so the worker can
retry initialization without cancelling its sibling tasks. Pure mapping/composition does not catch exceptions.
`captureFailure` requires the boundary's error mapper, catches ordinary exceptions, and propagates coroutine
cancellation and fatal errors. Browser crypto normalizes JavaScript exceptions at the platform boundary.
Exception-based interoperability uses `getOrThrow`; new business decisions should inspect typed variants.
The old shared Kotlin Result utilities have been removed, without compatibility aliases.

`cloud/runtime` owns endpoint bindings for CustomResult and reuses endpoint4k's parameter decoder and
HTTP method binding. Import `com.storyteller_f.a.cloud.server.common.invoke` for server routes rather than
the dependency's Kotlin Result-based invocation. Error containers and platform key encodings do not change
the external API contract.

The result, crypto-boundary and request-error tests cover null success, error conversion, diagnostic causes,
ordinary failures, cancellation and the distinction between business 401 and session authentication failure.
Existing host and integration tests consume CustomResult directly; their business assertions remain applicable.

## PDF font sources and variable-font instancing

`cloud/openpdf` discovers installed font files without AWT enumeration or private JDK font APIs.
Production PDF artifacts contain no font binaries. CFF2 regressions use pinned original test-only font resources.
Install fonts before the first PDF request and restart the process after changing the installation.
Discovery reads only SFNT/TTC directories, Unicode family names and OS/2 metrics, not outline/variation
contents. The metadata layout follows the [OpenType specification](https://learn.microsoft.com/en-us/typography/opentype/spec/otff).
Selected file contents load lazily; an unused variable fallback is never fully read or instantiated.

Discovery searches these standard locations recursively (TTF/OTF/TTC/OTC):

- Windows: `%WINDIR%/Fonts` and `%LOCALAPPDATA%/Microsoft/Windows/Fonts`.
- Linux: `/usr/share/fonts`, `/usr/local/share/fonts`, `~/.fonts` and `$XDG_DATA_HOME/fonts`
  (default `~/.local/share/fonts`). Custom fontconfig-only paths require the explicit directory override below.
- macOS: `/System/Library/Fonts`, `/Library/Fonts` and `~/Library/Fonts`.

Body family preference is Noto Serif SC, Noto Serif CJK SC, then Source Han Serif SC. Monospace preference
is Noto Sans Mono, DejaVu Sans Mono, Liberation Mono, Consolas, then Menlo. Upright static Regular (400)
and Bold (700) faces take priority independently across the supported families; a variable source supplies
only missing weights. Install a body family covering the document's languages; code preserves missing
monospace glyph runs through the explicitly proportional body fallback. Unknown code glyphs fail rather
than disappearing. Bold uses real outlines and italic uses PDF obliquing. Missing required faces fail with
an installation error instead of using implicit PDF fonts. Collection face indices are preserved.

For Linux installations, use system packages such as `font-noto-all` and `font-noto-cjk` (Alpine), or
`fonts-noto-cjk` and `fonts-dejavu-core` (Ubuntu/Debian). The Docker runtime installs system font packages;
it does not copy fonts from application resources. Alpine's `font-noto-all` includes static Noto Sans Mono
Regular/Bold through `font-noto`; `font-noto-cjk` is separate. The pinned Temurin Alpine base already
includes DejaVu through its `ttf-dejavu` requirement, so the runtime stages do not redundantly request
`font-dejavu`. Font discovery still prefers Noto Sans Mono. Windows/macOS operators can install Noto Serif SC
through the OS font manager and use installed Consolas/Menlo for code.

The optional JVM property `pdf.font.directory`, set before the first PDF request, replaces discovery for
both roles. Use `serif-regular.ttf`, `serif-bold.ttf`, `mono-regular.ttf`, `mono-bold.ttf`; optional
`serif-variable.ttf` and `mono-variable.ttf` supply missing static weights through `wght`. The same basenames with `.otf` are also accepted; `.ttf` wins
when both extensions exist for one slot. Static slots must
contain static fonts. An invalid selected static face fails explicitly. Backup variable files are read
only when the requested static weight is absent; an unreadable unused backup cannot block static faces
(P2 regression). No fallback file is automatically downloaded, copied or registered.

Each PDF owns uncached OpenPDF BaseFont objects and fresh Font styles. Only immutable instance bytes
are cached (up to 16 entries), keyed by source SHA-256, collection face index, sorted axes including defaults
and engine version. Failed instancing and thrown cancellation exceptions are not published. HarfBuzz
calls are synchronous and cannot be safely interrupted by cancelling a coroutine Job. Native ownership
lasts until the call returns and cleanup finishes; the awaiting coroutine still receives cancellation.
A completed, validated immutable instance may already be cached when that cancellation is delivered.

Standalone static TrueType/OpenType fonts need no native instancing library. Variable TrueType `glyf`
fonts and static TrueType/CFF collection extraction require matching public HarfBuzz core/subset libraries **8.3.1 or newer**
and JNA. CFF2 variable fonts require **HarfBuzz 13.0.0+**: pin all axes, then use the public
`HB_SUBSET_FLAGS_DOWNGRADE_CFF2` flag to produce static CFF1 outlines compatible with OpenPDF.
The adapter preserves existing subset flags and rejects output retaining CFF2 or variation tables.
Older libraries fail explicitly before allocating CFF2 handles; TrueType and static CFF retain the 8.3.1 minimum.
Unsupported axes/ranges fail explicitly. All unrequested axes pin to their defaults. The adapter copies source memory into a DUPLICATE blob and releases every native handle
in reverse order after copying result bytes. Instantiated output must not retain `fvar`/`gvar`.

- Windows: use the [official HarfBuzz release bundle](https://github.com/harfbuzz/harfbuzz/releases), with
  `libharfbuzz-0.dll`, `libharfbuzz-subset-0.dll` and their dependencies together. Put the directory on
  `PATH`; for Gradle tests set `JAVA_TOOL_OPTIONS=-Djna.library.path=C:/tools/harfbuzz`.
- Linux: install matching `harfbuzz-subset` (Alpine), `harfbuzz` (Fedora, includes core/subset),
  or `libharfbuzz-subset0` (Debian/Ubuntu), verifying the version. Alpine runtime targets require
  `harfbuzz-subset>=13.0.0`; the Fedora worker installs `harfbuzz` from its signed repositories
  and checks that its major version is at least 13.
- macOS: install matching HarfBuzz core/subset libraries and expose their directory to JNA.
  Absolute overrides `pdf.harfbuzz.core.library` and `pdf.harfbuzz.library` are available on all platforms.

The distribution packages carry HarfBuzz's [Old MIT license](https://github.com/harfbuzz/harfbuzz/blob/14.6.0/COPYING).
Font installation and licensing remain the responsibility of the system package/font installation.
Production instancing uses no Python, subprocesses, JDK/Skiko private libraries or font registration.

Run `./gradlew :cloud:openpdf:test -Pserver.flavor=dev -Pserver.buildType=debug --console=plain` with an
installed standalone variable TrueType Noto Serif SC/CJK SC or Source Han Serif SC, mono Regular/Bold faces, and
public native libraries. CFF2 regressions read original Noto Serif SC Variable and Source Serif 4
Variable Roman/Italic OTF files from `cloud/openpdf/src/test/resources/fonts`. Their upstream
revisions, paths and SHA-256 checksums are recorded in `fonts/fixtures.properties`; loading
verifies every bundled font before use. The corresponding upstream OFL license files are
kept beside each family. These files belong only to the test classpath, with no runtime
download, system font registration or production packaging.
Optional diagnostic overrides `pdf.test.cff2.font`, `pdf.test.cff2.multiaxis.font` (Roman) and
`pdf.test.cff2.collection.font` (Italic) remain available through `JAVA_TOOL_OPTIONS`.
An invalid explicit override fails rather than falling back to a bundled font.
Both Source Serif inputs have `wght` and `opsz` axes with matching ranges.
Tests construct aligned collections in memory, compare each extracted face with its standalone source,
verify face-index cache separation, and compare optical-size-dependent advances. A separate fixed-opsz
case renders identical text at weights 400/700 using NORMAL PDF style, checks heavier actual bold outlines,
and verifies the output OS/2 weight values. To validate the regression guard, isolate a mutation that
pins all defaults but overrides only the first sorted axis (opsz). The optical-size case must still pass
while the fixed-opsz weight case fails because both renders use the default weight. Run this mutation in
a disposable worktree and restore correct sources before final test/build/static checks. Cancellation testing
uses public table callbacks to pause inside a real `hb_subset_or_fail` call, cancels the owner Job,
then releases the callback and verifies completed conversion, reverse handle release and cancelled delivery.
The synchronous C callback uses an isolated blocking coroutine bridge; test deadlines and native work
run on real dispatchers, and failure cleanup always releases the gate and joins the worker.
Keep optional external verification overrides outside source/resource trees. Preserve the bundled
originals and licenses; generate static integration inputs in memory instead of committing derived fonts. Synthetic
metadata fixtures cover discovery, collection indices and malformed/unrelated fonts without shipping
font programs. Tests also cover static priority, P2 deferred IO, mixed families, axes, native failures,
cancellation, reverse cleanup, concurrent cache publication, rendering, bilingual extraction, embedding,
mono advances, CFF2 downgrade/version gating and per-document ownership. Diagnostic PDFs/PNGs live in `cloud/openpdf/build/test-output/fonts`.
Review intended visual differences before explicitly updating existing server snapshot references;
never auto-accept comparisons.

## PDF snapshot layout and content coverage

OpenPDF snapshots use A4 pages with 48-point side margins, a bilingual snapshot title, a provenance
panel (publisher/publication time and capturer/capture time), spaced body paragraphs and heading levels,
and a footer identifying each content page. Top-panel timestamps separate date and time with a space
(for example `2026-01-02 12:30:45 UTC+00:00`), omitting fractional seconds and explicitly labelling UTC.
`PdfGenerationSpec` receives UTC calendar values from the topic creation time and `shared.utils.now()`;
the host default timezone never changes the displayed clock values.
The signature page uses the same readable UTC formatter for its publication timestamp, preserving
the original time meaning rather than substituting the signing clock. The signature flow still inserts a separate signature page;
footer numbers refer to content pages, not physical positions after signing. `SnapshotLayoutTest`
checks provenance, multi-page content retention, embedded fonts, page numbering and exact code text.
`SignedSnapshotTest` automatically checks complete-file ByteRange coverage and cryptographic signature
verification with generated test keys. Its negative case changes signed metadata bytes in memory, keeps
the PDF parseable with complete ByteRange coverage, and requires verification to fail. This verifies
integrity, not certificate trust chains or TSA trust. The layout test reads the actual signature widget
appearance (using a separately owned in-memory copy) to assert the UTC display, then renders the
original PDF. Shared fixtures always delete generated PKCS#12 files in finally.
Rendered reading samples are generated under `cloud/openpdf/build/test-output/layout`.
The provenance regression checks both UTC labels and exact clock values. Run the PDF module tests
with `-Duser.timezone=Pacific/Honolulu` in `JAVA_TOOL_OPTIONS` (alongside native/font options) to verify
that a non-UTC JVM default cannot alter the displayed UTC timestamps.

The service exports only `TopicContent.Plain`. `Encrypted`, `DecryptFailed`, `Extracted` and `Nil`
are rejected by the snapshot boundary; it does not decrypt or convert them. The PDF receives one topic's
Markdown, author/capturer and timestamps. Replies, reactions, topic identifiers and other topic metadata
are not included. Downloading `fileInfos` does not automatically embed attachments or media previews.

The current OpenPDF visitor has the following limitations (checked with synthetic export samples):

| Content | Current result |
| --- | --- |
| Body text, basic emphasis, inline code, headings | Rendered; nested styling can leave Markdown markers, and headings retain inline markers. |
| Fenced/indented code | Source text; overlapping highlights no longer duplicate characters, and unhighlighted trailing text is retained. |
| Markdown images, including standalone images | Common paragraph-contained images are ignored, even when an attachment map contains the image; alt text also disappears. |
| Inline math | Omitted. |
| Mermaid, AsciiDoc and fenced math | Source text only, without diagram/document/formula rendering. |
| HTML blocks | Omitted. |
| Tables | Body cells render, but the header can disappear; inline formatting and alignment are not interpreted. |
| Lists/tasks | Source markers can be duplicated; original ordered starts, nested structure and checked state are not faithfully rendered. |
| Links | Inline links create PDF annotations; reference links remain raw Markdown and angle-bracket autolinks can disappear. |
| Hard line breaks/entities | Breaks can collapse and entities can remain encoded. |
| Emoji and other uncovered body glyphs | Can disappear; installing a font package alone does not add body fallback or color-emoji shaping. Code glyphs outside the explicit fonts fail instead. |

These are renderer limitations, independent of static/variable font conversion. Inspect the exported
PDF when preserving rich content matters. Do not treat successful generation as proof of content parity
with the topic UI, and do not update assertions to accept silently missing content.

## Wasm media composition experiment

The experimental Wasm media path uses the UI-independent `wasm-media-host` npm package
in `client/composeCore`. Its dedicated Worker owns Mediabunny input, network reads,
demuxing, decoder iterators and an offscreen canvas. The main-thread Host owns playback
coordination, immutable snapshots, the Web Audio graph and the media clock. The Compose
adapter only owns presentation state, Skia bitmaps and adopted texture Images. It creates a deferred Host and
starts its Worker from DisposableEffect after composition commit. Its top-level
`@JsModule` factory imports the npm default export; keep that export alongside the
named JavaScript factory used by verification. Android and desktop actuals are
unchanged by this experiment.

Mediabunny is pinned to **1.62.1**, MPL-2.0, from its
[official package](https://github.com/Vanilagy/mediabunny/blob/main/package.json).
The unmodified package and exact source archive are identified in
`client/composeCore/wasm-media-host/NOTICE`; preserve upstream notices and source
availability when distributing bundles. The Wasm Yarn lock records the package integrity.
Codec availability is checked against each actual input track with `canDecode()`;
[container support does not guarantee browser codec support](https://mediabunny.dev/guide/supported-formats-and-codecs).
This implementation requires HTTPS or localhost, a module Worker, WebCodecs for compressed
tracks, Web Audio for sound, and CORS permission for cross-origin media reads.
Use finite, seekable media; live streams and browser-native control parity are outside
this experiment. No hardware-decoder selection is asserted.

### GPU texture path and the local Skiko boundary

The published Skiko 0.150.2 API does not expose the renderer context or texture table.
Newer [0.156.0 texture registration](https://github.com/JetBrains/skiko/blob/v0.156.0/skiko/src/webMain/kotlin/org/jetbrains/skiko/GLTexture.web.kt)
and `Canvas.recordingContext` APIs supply part of the boundary. However, the newer
`RenderNodeContext(Boolean, Boolean)` constructor is incompatible with Compose 1.12.2's
compiled `RenderNodeContext(Boolean)` call. Upgrading the Kotlin artifact alone also
leaves Compose's native Wasm runtime at its old version. A successful compilation is
therefore insufficient upgrade validation.

`dev/skikoMediaRuntime` is a Wasm-only included build substituted for
`org.jetbrains.skiko:skiko-wasm-js`. It recompiles the exact, SHA256-checked 0.150.2
source archive and retains the published **0.150.2 native Skia/Emscripten runtime**.
It does not substitute JVM/Android artifacts or rebuild native Skia. The upstream
Apache-2.0 license, source provenance and the upstream Kotlin 2.4-compatible
import-generator (with project headers) are preserved there. Configuration-time IO is avoided; source
verification and extraction are task inputs/actions. The three files in `patches`
are source overlays, not replacement classes loaded beside the published library:

- `WebGLMediaInterop.kt` registers the renderer's existing context and exposes a scoped,
  borrowed context token plus `pushTexture`/`unregisterTexture`. The token makes its
  own context current and restores the previous one. Registration updates both the
  Emscripten texture table and the texture object's `name`, so native GL binding
  queries round-trip the numeric handle; unregister clears both. Never close the borrowed
  `DirectContext`, create a second one, retain it beyond the callback, or suspend inside it.
- `CanvasRenderer.kt` registers/disposes that token, stops queued redraws on disposal,
  releases its surface/context, and resizes while retaining the same context.
- `SkiaLayer.web.kt` reuses the renderer for the same canvas on resize and disposes it
  on detach. This keeps an imported image associated with its original renderer.

The included build is the repository-owned temporary integration. Maintain its
`patches`, import-generator sources, `LICENSE` and `NOTICE` together; a fresh checkout
builds it through the root Gradle wrapper without a separately published fork. Generated
source extraction and compiled outputs remain under the ignored build directories.
The effective texture-name fix is already in `patches/WebGLMediaInterop.kt`.

[`upstream-patches/SKIKO-1208.patch`](dev/skikoMediaRuntime/upstream-patches/SKIKO-1208.patch)
retains the minimal fix against upstream v0.156.1 for review or forward-porting. Its
base-file SHA256 and provenance are in `NOTICE`. It is not applied by `prepareSources`:
the current 0.150.2 runtime uses the active source overlays instead. Check this patch
against a clean v0.156.1 checkout with `git apply --check` before applying it there.

Remove the temporary included build only after a compatible Compose/Skiko Kotlin and
native-runtime set has been verified in the actual Compose recording path, the
texture-name mapping is fixed upstream, and local interop call sites have been
migrated. Remove the substitution in `settings.gradle.kts` and the included-build
sources together, then repeat texture ownership, GL state, resize/disposal and release
browser checks. An upstream fix for SKIKO-1208 alone does not replace the remaining
renderer-lifetime and context-access integration.

Skiko 0.156.1 exposes `Canvas.recordingContext`, but its availability depends on the
canvas: a GPU surface canvas has a borrowed context, whereas PictureRecorder and
RenderNode recording canvases can return null. A synchronous SkiaLayer render callback
can combine it with the same DOM canvas's existing `getContext("webgl2")`; that does
not establish that a Compose drawing callback has the same access. Keep direct-layer
interop validation separate from Compose recording and ABI compatibility.

The 0.156.1 `GLTexture.web.kt` implementation ([SKIKO-1208](https://youtrack.jetbrains.com/issue/SKIKO-1208)) registers external textures in
`GL.textures` without assigning Emscripten's `texture.name`. Native
`glGetIntegerv(GL_TEXTURE_BINDING_2D)` can consequently return zero for that bound
texture even though the JavaScript binding and table entry are correct. The local
0.150.2 overlay already assigns the name. Preserve both directions of this mapping
when evaluating an upgrade; successful image rendering alone does not test native
GL state queries. A registration fix must also review repeated registration and
unregistration ownership rather than treating a table entry as the complete contract.

The default remains `rgba-copy`. Opt into `gpu-texture` through the experimental
`mediaPath=gpu-texture` query parameter. The Worker uses `VideoSampleSink`, clones and
transfers a decoder-backed `VideoFrame`, and closes its sample. The adapter imports
that frame with `texImage2D(VideoFrame)` into the **current Compose renderer's** WebGL2
context; `pushTexture` registers its handle and `Image.adoptTextureFrom` transfers
ownership to Skia. `drawImageRect` records the Image directly in the Compose scene,
without `Image.toComposeImageBitmap` or a DOM media/canvas overlay. Rotation and flip
metadata are applied with Skia transforms. The browser may perform conversion or
copies internally; this path claims removal of explicit RGBA readback and the
JavaScript-to-Skiko pixel copy, **not** zero-copy decode or guaranteed hardware decoding.
The experiment targets opaque 8-bit SDR media; alpha-video, HDR and broader color-management parity are not asserted.

The guarded GPU import creates a fresh texture and restores every GL state it
changes (unpack buffer, texture unit/binding and pixel-store values). Existing Skia
state caches therefore remain valid. The default `gpuImport=restored` uses the
already-current renderer token without redundant context activation, per-frame
flush/submit, or full GL-cache invalidation. `gpuImport=legacy` retains those former
operations in the same build for ablation. This optimization does not permit external
writes to an adopted texture or remove synchronization from a reused-resource protocol. Every frame uses a new texture, avoiding overwriting a texture
still referenced by a recorded picture. Skia owns successful imports; failed imports
unregister/delete the external texture. Superseded, late, dropped and disposed
`VideoFrame`s close at their owning boundary. Context loss is a terminal media failure:
it cancels decoding/audio and closes pending frames rather than silently switching
rendering paths. Automatic root renderer recovery is outside this experiment.

The implemented path is explicitly 8-bit sRGB `rgba-copy`: decoded samples are drawn into a Worker
`OffscreenCanvas`; `getImageData` reads RGBA pixels; a transferable buffer reaches the
adapter; `installPixelsFromArrayBuffer` copies them into Skiko linear memory; an immutable
bitmap is wrapped without conversion by `asComposeImageBitmap` and drawn by ordinary
Compose `Image`. Replacement/disposal closes the old bitmap. Avoid
`Image.toComposeImageBitmap`, which constructs another Bitmap. The short pixel handoff completes without cancellation because
Skiko allocates before awaiting its runtime Promise; the adapter then checks its owner
and immediately closes any bitmap produced after cancellation. The offscreen canvas is never inserted into the DOM. Captions,
clipping, buttons and dialogs belong to the same Compose scene. At 30fps the raw pixel
payload alone is 110.6 MB/s for 720p and 248.8 MB/s for 1080p, before the Skiko copy and
GPU upload. These are byte-volume calculations, not measured hardware guarantees.

### Playback ownership

Media timestamps are seconds. Audio samples become scheduled `AudioBufferSourceNode`s
through one gain node. AudioContext render time drives audio scheduling and starvation
checks. Video, displayed progress and end detection follow `getOutputTimestamp()` mapped
to the monotonic performance clock, capped by render progress and monotonic within a
playback generation. Until a valid output timestamp is available, reported base/output
latencies provide an estimate; missing latency values contribute zero. Silent video uses
the monotonic performance clock directly. Creation/resume of AudioContext starts
inside the user action so autoplay restrictions remain visible. Scheduling starts with
80ms lead time. The Host limits scheduled sound to roughly 500ms ahead and at most 64
nodes, and retains one pending video frame plus Mediabunny 1.62.1's four-sample decoder prefetch.
Below 40ms of remaining rendered sound it suspends the render clock; it resumes after
300ms has been scheduled, the bounded node queue is full, or the audio iterator ends.
Already-queued output and corresponding video may drain while buffering, capped by
suspended render progress. Late video frames are discarded against the output clock. Metrics describe Host dispatch timestamps and Worker readback, not speaker/display
latency or guaranteed hardware acceleration.

Pause and seek stop/disconnect every scheduled audio node and replace the decoder Worker
at the requested position. Generation checks reject late metadata, sample and resume
completions. Playback intent survives superseded seeks but is cleared by pause, end,
error and removal. Replacing the source creates a new Host; removal terminates its Worker,
clears pending reads/timers and closes AudioContext. Worker termination owns cancellation
of network reads and native decoder resources; samples copied inside the audio Worker
are also closed explicitly. The adapter disposes Skia bitmaps on replacement/removal.
Hiding the page pauses playback; returning to it requires an explicit Play action.
The existing media-item contract has no loop option; ended playback can be replayed.

### Reproduce the isolated experiment

Use JDK 21, Node 22+ and FFmpeg with VP9, H.264, Opus and AAC support. No server, account,
VM or deployment is required. From the repository root, build the fixture application:

```bash
./gradlew :dev:wasmMediaExperiment:wasmJsBrowserDevelopmentExecutableDistribution -Pserver.flavor=dev -Pserver.buildType=dev --console=plain
```

After changing npm dependencies, regenerate only the Wasm lock with
`./gradlew kotlinWasmUpgradeYarnLock -Pserver.flavor=dev -Pserver.buildType=dev --console=plain`.
The normal JS lock is independent. The root Wasm npm-install task tracks the local Host
source files and supplies Yarn's `--force` when installation is required: Yarn's copied
`file:` dependencies otherwise retain old implementation files despite a successful
Kotlin compilation. An unchanged build still reuses that task and the configuration cache.

Install disposable browser-verification tools under the ignored build directory, then
create synthetic media and run the contract tests:

```bash
npm install --prefix build/wasm-media-research/browser --no-package-lock --ignore-scripts playwright@1.64.1 esbuild@0.28.2 selenium-webdriver@4.51.0 pngjs@7.0.0
node client/composeCore/wasm-media-host/generate-fixtures.mjs
node --test client/composeCore/wasm-media-host/*.test.mjs
node client/composeCore/wasm-media-host/verify-browser.mjs
node client/composeCore/wasm-media-host/verify-worker-regressions.mjs
node client/composeCore/wasm-media-host/verify-rgba-failure.mjs
node client/composeCore/wasm-media-host/verify-bitmap-ownership.mjs
node client/composeCore/wasm-media-host/benchmark-browser.mjs
node client/composeCore/wasm-media-host/verify-compose.mjs
node client/composeCore/wasm-media-host/verify-av-sync.mjs
```

The Chromium checks use the installed Windows Chrome and Edge binaries with isolated
headless profiles. `MEDIA_CHROME_BINARY` and `MEDIA_EDGE_BINARY` can override their
installation paths. Firefox uses the installed Firefox and an existing geckodriver;
set `MEDIA_GECKODRIVER` to its executable and run
`node client/composeCore/wasm-media-host/verify-firefox.mjs` and
`node client/composeCore/wasm-media-host/verify-compose-firefox.mjs`. The latter loads
the same compiled fixture after installing diagnostics on a static same-origin bootstrap.
It checks changed/paused PNG pixels, native Skia uploads, the audio graph, menus, dialogs,
source replacement and disposal; it does not substitute a DOM media player.
No personal profile is reused.
Do not run independent builds sharing the fixture output while Compose verification is
running. `node client/composeCore/wasm-media-host/serve.mjs` prints a local fixture URL;
append `?audio=true` for pure audio or `?media=/fixture-fhd.mp4` for the larger sample.

Host checks cover video changes, the Web Audio signal path, timestamp skew, pause,
seek/resume, mute, end, error and disposal. Compose verification additionally observes
actual Skia texture upload dimensions, compares changed/paused scene pixels, exercises
an overlaid Compose button and Escape-dismissed dialog, checks a clipped corner, replaces
a playing source, and verifies that removal closes its Worker/audio graph. Its WebGL
instrumentation observes calls without replacing their results. Screenshots, JSON metrics
and benchmark results stay under `build/wasm-media-research`; do not track session reports.
The native `<video>` benchmark is a test-only comparator and is not a production fallback.
The sync fixture combines one white flash and one audio pulse each second. Its check
compares captured Compose canvas flash onset with Web Audio analyser pulses mapped to
the browser's audio-output timestamps before and after seek, using an 80ms bound. The
raw graph-onset differences remain separate diagnostics. Set
`MEDIA_SYNC_OUTPUT_DELAY_MS=200` to simulate an extra output queue in the timestamp API;
this must delay video without changing audio scheduling. Run both default and injected
delay checks with `MEDIA_RENDER_PATH=rgba-copy` and `MEDIA_RENDER_PATH=gpu-texture`.
The injection changes reported timing only, not the physical device. Canvas capture itself adds diagnostic readback cost;
its observed unique frame rate is distinct from Host dispatch and native video callbacks.
A nonzero analyser signal proves the graph carries sound, not that a physical speaker was
heard. Timestamp and headless rendering checks do not establish acoustic/display sync;
manual listening and physical-output latency measurements remain separate acceptance.

For texture verification, set `MEDIA_RENDER_PATH=gpu-texture` before running
`verify-compose.mjs`, `verify-compose-firefox.mjs` or `verify-av-sync.mjs`.
`benchmark-compose.mjs` compares both paths inside the same compiled Compose fixture,
using three interleaved rounds at 320x180, 1280x720 and 1920x1080 on Chrome and Edge.
It records real canvas frame hashes, Worker readback/bytes, CPU time spent in texture
upload calls, CDP main-thread task duration, renderer identity and resource release.
Upload call time does not measure GPU completion; canvas capture and diagnostics add
overhead to both paths. The PNG comparison checks the paused frame's color/orientation.
`verify-gpu-lifecycle.mjs` covers resize, remount, rotated media and context-loss cleanup.
Results and screenshots stay under `build/wasm-media-research`; do not turn one short
machine-specific run into a sustained performance or physical latency guarantee.

Source-built Skiko invalidates the Web release links. If concurrent links fill the
shared Kotlin compiler heap, confirm GC pressure rather than treating the stall as a
media failure. Run the complete checks sequentially with bounded workers; do not stop
shared compiler daemons or change test budgets:

```bash
./gradlew assemble -Pserver.flavor=dev -Pserver.buildType=dev --max-workers=1 --console=plain
./gradlew detekt -Pserver.flavor=dev -Pserver.buildType=dev --max-workers=1 --console=plain
./gradlew validateDebugScreenshotTest -Pserver.flavor=dev -Pserver.buildType=dev --max-workers=1 --console=plain
```

Set `MEDIA_COMPOSE_BUILD=production` for the final release-path performance comparison;
this serves the fixture's `productionExecutable` distribution. Leave it unset for fast
development checks. Record the build type alongside timings, and do not combine samples
from the two distributions in one comparison. Build the production distribution with
`:dev:wasmMediaExperiment:wasmJsBrowserDistribution` before choosing it.

Keep Wasm development and production distributions in separate Gradle invocations.
Kotlin 2.4.21 stages both through the same npm package `kotlin` directory; requesting
both graphs together can fail Gradle's implicit-dependency validation and makes the
staging directory unsuitable as an immutable test target. Serve the copied distribution
under `build/dist/wasmJs`, finish one build before the next, and never benchmark a
staging directory that another task is rewriting.

### Presentation profiling at an actual 1080p video size

`generate-benchmark-fixtures.mjs` generates 14-second 720p/1080p H.264/AAC inputs at
30 and 60fps, with an encoded 16-bit frame number and colored calibration edges.
`benchmark-fullhd.mjs` serves the release fixture with `display=1080p`, asserts DPR 1
and a 1952x1360 canvas, and checks all four video edges at the actual 1920x1080 bounds.
It compares RGBA, legacy GPU synchronization and guarded GPU import in interleaved
rounds. A dedicated Worker decodes IDs from a two-row capture strip, closes every
capture frame and reports unique media frames, skipped IDs and presentation gaps.
Canvas capture remains an observer of browser output, not a physical-display probe.
Use `MEDIA_BENCH_NO_CAPTURE=1` for companion CPU-only samples; the Worker observer is
not counted by CDP main-thread task duration. Do not interpret that duration as total
process/decoder CPU. The 30fps input is a ceiling, so use 60fps inputs to test headroom.

`MEDIA_BENCH_PROFILE=1` enables `media.gpu.*` timing entries and `media.rgba.*` handoff
marks; keep it off for final timing samples. `MEDIA_BENCH_GPU_TRACE=1` additionally
tracks real GL draws whose active shader samples the video texture. Those are submitted
draw commands, which must not be equated with observed canvas frame IDs or display output.
The diagnostic texture tracker follows actual bind calls instead of introducing an
extra per-upload `getParameter` query. `profile-presentation.mjs` separately measures
legacy full-area readback observer overhead and GL call counts.

Both decode paths read `VideoSampleSink` samples. Video RPC requests carry a media-clock
snapshot; samples already more than the existing 50ms lateness budget behind that
snapshot close before expensive canvas conversion/readback or VideoFrame transfer.
The Host retains its post-transfer check because the clock can advance while the request
is in flight. `skippedBeforeConversion` distinguishes early culling from transported
frames and Host-side late drops. This prevents an overloaded copy path from repeatedly
spending its frame budget reading pixels that the Host will immediately discard.
The raster adapter preserves the upstream opaque-canvas/Firefox alpha workaround and
uses public sample drawing for rotation/flip. No native texture pooling or mutation
of Skia-owned Images is introduced.

Report both imported frames and observed unique media IDs, with the three-round range.
The latter can be lower even when every imported texture is sampled by a real GL draw:
canvas capture/readback is another pipeline and does not prove a physical presentation
loss. The current draw tracer establishes video-texture sampling, not final screen
presentation or complete offscreen-layer lineage. A small 30fps observation difference
cannot establish import saturation. At 60fps, report main-thread time per second and per
imported frame because paths may process different frame counts. Neither measure includes
the complete Worker/decoder/GPU cost. Keep the original synchronization strategy available
for ablation; removing flush/reset is not by itself evidence of a speedup.

The draw tracer normalizes nullish framebuffer arguments: this runtime can map native
handle zero to an undefined default framebuffer, which WebGL treats as null. With tracing
enabled, the first six video draws also query the actual draw framebuffer to validate
the tracker. These diagnostic queries are absent from final timing runs.

### Headed Web presentation diagnosis on Windows

The same release fixture supports `MEDIA_BENCH_HEADED=1` in `benchmark-fullhd.mjs`.
Use installed Chrome/Edge with isolated profiles, DPR 1 and a normal test window on the
primary display. The Windows `DesktopFrameProbe.cs` helper calibrates all four video
edges at physical 1920x1080, verifies the browser process owns the foreground window,
and reads only the encoded two-row barcode during measurement. It restores its thread
DPI context and releases all native handles. It does not alter system display settings.
The desktop observer measures DWM-visible content, not optical panel scanout.

Set `MEDIA_BENCH_CASES=720-30`, `MEDIA_BENCH_CAPTURE=both`,
`MEDIA_BENCH_GPU_TRACE=1`, `MEDIA_BENCH_OBSERVER_CONTROLS=1`, and
`MEDIA_BENCH_OUTPUT=headed-study.json` for three interleaved rounds of RGBA, legacy GPU
and restored-state GPU, with capture enabled/disabled. The first round additionally
turns desktop sampling and graphics tracing off independently for the GPU path.
These controls have one sample per condition and are diagnostic, not performance
confidence estimates. The helper waits for manual activation before measuring and
rejects foreground loss. Keep the test window foreground until its browser finishes.
`MEDIA_BENCH_FOREGROUND_GATE=1` additionally pauses at the controller handshake in
`build/wasm-media-research/headed-window.json`; acknowledge the matching token in
`headed-foreground.json` only when the user is ready to activate that test window.
No keyboard/input bypass or personal browser profile is used.

Graphics capture uses an explicit CDP traceConfig, including cc/viz/gpu, benchmark,
user timing and compositor frame events, with unrelated categories excluded. Category
discovery can return an empty list; filtering the request through it silently enables
broad default tracing. Check the saved trace contains the measured start/end markers,
GPU events and PipelineReporter entries. Trace filenames include the output dataset
name to keep prototype evidence separate from later studies.

Run `analyze-headed-study.mjs headed-study.json headed-analysis.json` to correlate
source video IDs with final-canvas draw markers, containing AnimationFrame intervals,
AnimationFrame::Presentation feedback and desktop barcode observations. Preserve large
trace identifiers as strings. A shared feedback timestamp can cover multiple updates;
never count every callback as a separately visible video frame. NO_UPDATE_DESIRED is
not a drop, and whole-window compositor drops do not establish lost video content.
Only interior source IDs witnessed on the desktop, with matching non-coalesced
presentation feedback but absent from capture, count as confirmed capture omissions.
Unobserved desktop IDs remain uncertain rather than automatically becoming actual drops.

For capture-consumer isolation, set `MEDIA_BENCH_QUEUE_STUDY=1` and select
`MEDIA_BENCH_VARIANTS=gpu-restored,gpu-restored-buffer8`. This varies only the
observer's MediaStreamTrackProcessor queue capacity (1/8), records native totalFrames
and discardedFrames where supported, and times Worker copyTo calls. Do not confuse
captured frames with unique source-video IDs: a capture stream can repeat some IDs
and omit others without its consumer queue discarding frames. The supplementary
headless study is separate from the headed presentation evidence. Production media
queues and the default RGBA path are not changed by these observer controls.

`MEDIA_BENCH_FRAME_STAGES=1` records call stacks only when a transferred decoder frame
closes before any texture upload. Historical `GpuBridge.replace/releasePending` stacks
identify the old pending-slot overwrite. Keep this optional instrumentation off for
timing claims and separate intentional generation cancellation and clock-late discards
from pending-frame loss.

The GPU bridge now accepts frames through `offer`: false retains caller ownership,
true transfers it. A full pending slot rejects another frame from the same playback
generation. The Host retains that frame and stops requesting another until it is
accepted or exceeds the existing 50ms lateness budget. Texture consumption schedules
a producer wakeup after the Compose draw unwinds. Pause, seek and source replacement
may supersede an older generation; stale consumption callbacks cannot restart it.
Preview delivery waits cancellably and releases untransferred frames on failure or
cancellation. The bridge and Host each hold at most one transferred video frame.

Measurement schema 3 names canvas observations `canvasCaptureUniqueFps`,
`canvasCaptureSkippedIds` and `canvasCaptureGapMsP95/Max`; independent desktop
observations use `desktopObservedUniqueFps` (null without sampling). Host
`acceptedFrames` counts ownership transfers to the UI, not screen presentation.
`backpressureWaits` counts rejected delivery attempts, not lost source frames.
Consumers of the old `presented`, `presentedFps`, `observedUniqueFps` or generic gap
fields must use the appropriate explicit counter. The headed analyzer reads historical
`observedUniqueFps` datasets solely for comparison. None of these changes makes
captureStream lossless or changes the audio clock or lateness limit.

Schema 3 uses `canvas.captureStream()` without an explicit request rate and labels
benchmark rows `capturePolicy: "per-paint"` (`"disabled"` when capture is off). Earlier schema-2 full-HD datasets used
`captureStream(120)`; other canvas observers used a 60fps request timer. An explicit
rate selects a timer-gated listener rather than capturing every changed paint. Multiple
canvas paints can occur between video updates, so a request rate above the video's
frame rate does not establish complete observation of video IDs. Do not pool timing
samples across these capture policies. Manual `captureStream(0)` plus `requestFrame()`
after a final video draw is a separate diagnostic and has no completion acknowledgement.
Per-paint capture still has real-time delivery, queue and GPU readback limits; retain
independent desktop/presentation evidence instead of claiming lossless recording.

The worker regression check runs Chrome and Edge against the bundled decoder. It
blocks initial and pause-triggered source requests, requires a terminal error with
no surviving retries, and drains expired samples to EOF in both wire paths.
Network retry policy uses Mediabunny's pinned `getRetryDelay` option. Video EOF
responses carry `ended: true` and `skippedBeforeConversion`; Host accounts for the
skips before returning null to playback. EOF never increments decoded/accepted
frames, copied bytes or readback time. Diagnostic `videoSkips` records include EOF
so full-HD benchmark totals do not depend on a later frame being delivered.

The native-video baseline reads `requestVideoFrameCallback` metadata's
`presentedFrames`, and reports callback notifications separately: a delayed callback
can cover multiple frames. Lifecycle checks retain the original AudioContext to
verify its closed state independently of the Host clearing its context reference.

RGBA bitmap-install errors are reported through the MediaHost rendering-failure action,
which terminates decoding and audio without cancelling the Compose scene. Cancelled
imports still propagate cancellation; a superseded effect must not report a stale
failure. The RGBA failure check makes native bitmap installation return false or throw during
preview and active playback, observes error state and resource cleanup, then exercises real Compose
controls to unmount/remount and verifies that fresh playback recovers without an
uncaught browser exception. Run it against the production fixture with
`MEDIA_COMPOSE_BUILD=production`.

RGBA frames transfer from the importing coroutine to a stable bitmap owner at publication.
The owner retains both the committed frame and the latest pending frame, retires older
frames after commit, and explicitly closes every remaining frame on disposal. Ownership
does not depend on a per-bitmap DisposableEffect reaching commit. Late publications to
a disposed owner close immediately; cancelled imports still release untransferred data.
The bitmap-ownership browser check counts native installs/finalizers and defers GC cleanup
callbacks so garbage collection cannot hide a missed close. It covers committed disposal,
publication before commit followed by removal/source replacement, and active-playback
replacement/disposal. Run against the production fixture with MEDIA_COMPOSE_BUILD=production.


## Offline E2E application coverage

Add `--coverage` to `scripts/test_scripts/build-and-test.sh --e2e --plain`, or pass
`-Pe2e.coverage=true -Pserver.flavor=dev -Pserver.buildType=debug` to explicit
Appium/CLI E2E Gradle tasks. When coverage is disabled, normal distributions and release
applications do not contain the control receiver, collector or rewritten bytecode.
Acquire and renew the Android device lease around the entire command, including
installation. Desktop tests still need an interactive, unlocked desktop. Keep MSYS
graphics DLL directories out of the Windows desktop process PATH: mixing its EGL
with Nucleus's bundled ANGLE libraries can crash the native renderer at startup.
Covered Windows distribution launchers use a manifest classpath JAR, preserving
dependency order without exceeding cmd.exe's command-length limit.

JVM application distributions, desktop classpaths and backend test images use Kover
0.9.11 offline instrumentation. Android debug variants transform source-owned class
files before D8 and use the same Kover runtime. A test-only counter-store boundary
serializes snapshots with counter updates. JVM snapshots use per-process file
requests and process-owned running/shutdown markers; Android snapshots use a debuggable-only receiver protected by the system
DUMP permission. Collect snapshots before driver shutdown, force-stop or container
removal, including on failure and before application restart. Private control files
are removed with the fixture; exported binary reports remain with the case artifacts.
Desktop snapshots select the current launch PID from its launcher file. Driver
force-quit can leave an old running marker behind; retain its exported snapshot
without requesting that retired process again after restart.

All selected E2E runners prepare their immutable application artifacts before live
fixtures start. Coverage backend images use the separate `e2e-coverage` tag. Test
images still follow `distTar` -> `copyTestDockerDistribution` -> `buildTestDockerImage`.
Never rebuild shared runtime files from a second Gradle invocation during UI tests.
Linux and macOS desktop collection requires the test runner and launched application
to share the fixture filesystem, as their existing launch/session/log contract does.

Wasm coverage additionally requires a host Rust toolchain with Cargo. The pinned
`dev/wasmCoverage` tool validates and rewrites the exact Kotlin development Wasm
binary identified in webpack output. It adds stack-neutral boolean global probes
without renumbering functions, imports or types. Only repository Kotlin source lines
mapped to linked executable operators form the denominator. Compiler anchors beyond
the physical source file (including the empty EOF line) remain unmapped. Dead-code-eliminated,
unmapped, third-party and JavaScript code is outside that denominator. The collector
observes window instances; Worker instances need a separate collection channel.
This is mapped line coverage, with no source branch metric. It is exported as LCOV
and HTML separately from Kover. The test server injects its collector only in covered
distributions; every document and module instance must acknowledge a snapshot.
An acknowledged explicit boundary snapshot suppresses the redundant unload beacon.
New or unacknowledged instances retain the beacon fallback; incomplete transport remains an error.
Chrome, Edge and Firefox keep their original sequential, isolated-profile matrix.

Each case exports under its raw `build/test-results/<task>/artifacts/.../coverage`
directory. The root `e2eCoverageReport` finalizer consumes completed files only and
writes `build/reports/e2eCoverage/index.html`. JVM aggregate, individual JVM/Android
variants and Wasm reports are linked from that page; Android and JVM bytecode
variants are never combined into one percentage. Original-bytecode identities and
source fingerprints prevent stale binaries from being reported against changed
sources. Missing, empty or incomplete data makes the report task fail and leaves an
explicit error on the index. Ordinary unit-test Kover reports remain independently
available; their percentages are not added to E2E percentages. Run reporting in the
same invocation as the selected E2E tasks to retain the runner selection; a standalone
report task reads all runner artifact directories and rejects mixed builds.

### Unit and E2E union

With coverage enabled, `prepareE2eCoverageArtifacts` first runs the existing ordinary
unit-test Kover XML/HTML report tasks, before preparing or launching immutable E2E
artifacts. Ordinary backend fixtures retain freshly built `latest` images; covered
fixtures use `e2e-coverage` images built in separate immutable contexts. Every covered
E2E invocation also finalizes `coverageReport`, writing
`build/reports/coverage/index.html`, `lcov.info` and `summary.json`. The ordinary
`build/reports/kover` and independent `build/reports/e2eCoverage` outputs remain intact.
To regenerate the union from completed E2E artifacts, run:

```shell
./gradlew coverageReport -Pe2e.coverage=true -Pserver.flavor=dev -Pserver.buildType=debug --console=plain
```

This cross-platform summary unions repository-relative source paths and executable
line numbers. A line hit by either test layer counts once; the page shows unit hits,
E2E hits, overlap and the combined numerator against the union of reported executable
lines. Shared source hits on one platform do not establish coverage on every platform.
Use the independent reports for platform-specific bytecode, branch and instruction
metrics. Wasm E2E mapped lines participate through LCOV; uninstrumented Wasm unit tests
contribute no coverage. Existing ordinary Kover test selection is unchanged.
Generated/external sources outside the production source catalog are listed explicitly;
ambiguous source ownership, foreign LCOV paths and incomplete E2E data fail reporting.
No percentages or cross-platform branch counters are added together.

This integration covers the existing Android, desktop, CLI and Wasm E2E runners
and their JVM backend containers. The optional iOS projects have no E2E runner or
coverage collector in this workflow; Kotlin/Native coverage is not implemented.

Platform-protected session history tests stay under `app/composeApp/src/headlessTest/kotlin/platform_storage`.
The `platformStorageTest` source set shares them between JVM and Android device tests; Android host
Robolectric tests exclude that directory because AndroidKeyStore requires a real OS-backed provider.
All session, signature and encryption assertions are retained. The device initialization base supplies
its real instrumentation context and does not install a fake keystore or change production storage.

PDF host tests use a 1 GiB worker heap for eight concurrent cold full-size CJK font instances;
the concurrency, native cleanup checks and font assertions remain unchanged.

## Release warning diagnostics

Configure Yarn lock files only in the root build through `lockFileDirectoryProperty`
and `lockFileNameProperty`. JS keeps `kotlin-js-store/yarn.lock`; Wasm keeps
`kotlin-js-store/wasm/yarn.lock`. This API migration does not change npm dependencies
or require regenerating either lock file.

Both Compose modules configure Android device tests with
`withDeviceTestBuilder { sourceSetTreeName = "test" }`. The default hierarchy supplies
`commonTest`; do not add a cross-tree `dependsOn(commonTest)` edge.

Use `./gradlew assemble -Pserver.flavor=dev -Pserver.buildType=dev --warning-mode all
--stacktrace --console=plain --max-workers=1` on one line to attribute remaining
Gradle deprecations. Run Detekt separately on Windows. Keep warnings enabled:
a successful build does not establish Gradle 10 compatibility. Compose Hot Reload's
`HotReloadUsageType` warning originates in the plugin, not the Yarn lock configuration.

Panel view-model creation requires an owner-provided coroutine scope. Android owns
that scope for the application lifetime; iOS uses the root composition scope so
removing the controller cancels its work. Callers of `createPanelUIViewModel` must
pass their existing lifecycle scope.

The current dependency set also reports newer Material3 BottomSheet deprecations,
Wasm interop opt-ins and diagnostics in the pinned Skiko source archive. Track these
separately from the Release source-tree/Yarn/API migration; do not globally suppress
them or edit generated copies under `build/upstream`.

With the pinned plugins, `--stacktrace` attributes `Project.getProperties` to
kotlinx-rpc (`RpcPluginConstKt.isInternalDevelopment`). The Gradle 11
`Configuration.setVisible` warning is emitted by Kover, AGP, KSP, Detekt, JMH and
Compose Hot Reload configuration code. These require upstream plugin migrations;
changing application source or disabling diagnostics does not resolve them.
