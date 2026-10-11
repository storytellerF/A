# Development

## Backend test images

Each backend service applies `test-docker-image`. Its `buildTestDockerImage` task depends on
`copyTestDockerDistribution`, which copies the `distTar` task output to `deploy/build`.
Integration and E2E tasks depend on the image tasks they require; ordinary assembly does not.
Images use the root Dockerfile with `BUILD_ON=local`, so tests package host-built distributions
without recompiling them inside Docker. Testcontainers starts these images without building them.
The copy and image tasks share one Gradle build-service permit: context writes cannot overlap
another image's archive, and image builds run sequentially even with parallel Gradle enabled.

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
Keep the Docker automatic range and registered same-port range consistent. The current
Windows pool remains `20000–20015`. Recovery coordinates QEMU/gvproxy stop and start
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
- Linux driver regression tests: run `npm ci` and `npm test` in `dev/appium-linux-driver`. Element handles retain AT-SPI object paths across navigation changes; disappeared elements return a stale-element error and are relocated by the desktop helper instead of clicking cached screen coordinates. Lookups respect the WebDriver implicit wait rather than adding a fixed wait.
- `APPIUM_DESKTOP_SERVER_URL` defaults to `http://127.0.0.1:4723`; `APP_DESKTOP_TEST_JAVA` optionally selects the Java executable.
- Test JVMs use English UI labels, a JVM-local Preferences factory on Windows, and isolated database directories. Windows app logs go to the same per-test desktop log files as Linux. Desktop accessibility maps the existing `appiumSemantics` labels/actions so image descriptions do not hide account and topic text. Android retains its native semantics. The Windows launcher preserves the system temporary directory during Java-agent initialization before switching the application to its isolated directory. Gradle also builds a test-only compatibility agent: DesktopDriver 2.8.0 calls the removed Component.getPeer() method on JDK 21, so this agent replaces its getHwnd lookup with the JDK 21 AWT peer field lookup and dispatches accessibility read queries on Swing EDT (mutating commands retain the driver's existing EDT dispatch). It opens the two required java.desktop packages only to the test agent module. The agent verifies the supported bridge bytecode before installing transformations; unsupported bridge versions or method signatures fail explicitly; installed Appium files are not patched. The helper records the launched JVM PID and switches between that process's native frame and dialogs. Session cleanup force-closes the test JVM and deletes its PID file.

Build `:app:desktopApp:writeAppiumRuntimeClasspath` and `:panel:desktopApp:writeAppiumRuntimeClasspath` with `-Pserver.flavor=dev -Pserver.buildType=debug` before running `:app:desktopAppium:appiumTest` and `:panel:desktopAppium:appiumTest`. Run these suites sequentially on the same desktop. Docker/Testcontainers must be available for the backend services; on Windows use the QEMU Docker proxy described above.

For Linux desktop E2E, install `xvfb`, `openbox`, `x11-utils`, `dbus`, the Linux driver dependencies above, and Appium on PATH, then run:

```bash
bash scripts/test_scripts/run-linux-desktop-e2e.sh --max-workers=2 -Pserver.flavor=dev -Pserver.buildType=debug
```

The runner owns a 1600×1000 virtual X11 display, a window manager, a separate D-Bus/AT-SPI session, and a loopback Appium server. `APPIUM_DESKTOP_PORT` selects its port (default 4727). It runs every user and panel desktop E2E sequentially, propagates failures, and stops only the processes it started. Diagnostics remain under `build/linux-desktop-e2e.*`; raw logs can contain test credentials and must be sanitized before sharing. Application rendering settings, test cases, assertions, and timeouts are unchanged.

Do not use a disconnected user Xwayland session for unattended testing: the display can report 0×0 while Java and the X11 window remain alive. In the diagnosed failure, repeated thread dumps found the UI thread in Skiko `swapBuffers` and ATK waiting for UI dispatch; AT-SPI subsequently stopped exposing the application. An owned display removes that dependency rather than masking missing elements. See [Skiko's rendering properties](https://github.com/JetBrains/skiko/blob/master/skiko/src/jvmMain/kotlin/org/jetbrains/skiko/SkikoProperties.kt) for the independent rendering/vsync controls used during diagnosis.

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
