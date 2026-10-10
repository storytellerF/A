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
- Linux: retain the `linux` Appium driver, a graphical session, and `/usr/share/java/java-atk-wrapper.jar`.
- `APPIUM_DESKTOP_SERVER_URL` defaults to `http://127.0.0.1:4723`; `APP_DESKTOP_TEST_JAVA` optionally selects the Java executable.
- Test JVMs use English UI labels, a JVM-local Preferences factory on Windows, and isolated database directories. Windows app logs go to the same per-test desktop log files as Linux. Desktop accessibility maps the existing `appiumSemantics` labels/actions so image descriptions do not hide account and topic text. Android retains its native semantics. The Windows launcher preserves the system temporary directory during Java-agent initialization before switching the application to its isolated directory. Gradle also builds a test-only compatibility agent: DesktopDriver 2.8.0 calls the removed Component.getPeer() method on JDK 21, so this agent replaces its getHwnd lookup with the JDK 21 AWT peer field lookup and dispatches accessibility read queries on Swing EDT (mutating commands retain the driver's existing EDT dispatch). It opens the two required java.desktop packages only to the test agent module. The agent verifies the supported bridge bytecode before installing transformations; unsupported bridge versions or method signatures fail explicitly; installed Appium files are not patched. The helper records the launched JVM PID and switches between that process's native frame and dialogs. Session cleanup force-closes the test JVM and deletes its PID file.

Build `:app:desktopApp:writeAppiumRuntimeClasspath` and `:panel:desktopApp:writeAppiumRuntimeClasspath` with `-Pserver.flavor=dev -Pserver.buildType=debug` before running `:app:desktopAppium:appiumTest` and `:panel:desktopAppium:appiumTest`. Run these suites sequentially on the same desktop. Docker/Testcontainers must be available for the backend services; on Windows use the QEMU Docker proxy described above.
