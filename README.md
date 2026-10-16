# A

A Kotlin Multiplatform application for Android, Desktop, CLI, and Web, with cloud services and an
administration panel.

For build, test, and independent service setup instructions, see [DEVELOPMENT.md](DEVELOPMENT.md).

[Download the latest alpha build](https://nightly.link/storytellerF/A/workflows/alpha/alpha?preview)

## Home on larger screens

Home keeps World, Communities and Rooms as local sections. On larger windows, World keeps a bounded
single reading flow with a joined-community preview, Communities uses more columns, and Rooms shows
additional existing metadata. Community and member search results adapt their column count to the
available content area independently of the input.
Resizing preserves the selected section and its reading state. Wide lists provide an explicit Refresh action.
Community and user detail sections preserve selection when resizing; room and topic details keep their
reading content and message input aligned in a bounded column.
Settings and creation forms expand to two columns on wide windows. Favorites, subscriptions, comments
and reaction history use adaptive grids. The topic editor shows a live preview alongside the raw or
block editor when space permits, and keeps the draft when the window is resized.
Joined room rows include the latest message preview, author/time and an exact unread-message count.
The Rooms navigation badge shows the total unread messages across all joined rooms.
Wide World's context includes bounded unread-room and joined-community pinned-topic previews, each
with its own refresh and loading/error state. Unavailable private messages show a neutral unavailable
label; previews stay in memory and are not saved as plaintext.

## Storage and Search Availability

After a filesystem or Lucene service restart, later requests reconnect automatically. An interrupted
request still reports failure; failed operations and partial uploads are not replayed automatically.

## Worker Configuration

Worker task settings are stored per task type. A task without a persisted configuration does not run.
The CLI `add` command accepts a `taskConfig` preset whose `taskConfigData` entries contain `type`,
`isEnabled`, `fetchSize`, and `waitDurationMillis`; see `deploy/dev-data/7_preset_task_config.json`.
Administrators can edit the same values from the Panel's **Worker task configurations** page. Workers
reload the setting before every iteration, so enabling, disabling, or tuning a task does not require a restart.

### Topic Moderation

When the persisted `TOPIC_MODERATION` task configuration is enabled, the worker loads the active LLM
configuration from backend storage. Apply one of the `cloud/worker/llm-config.*-example.json` presets with
the Cloud CLI `add` command. For OpenRouter, set a valid API key and model identifier in
`cloud/worker/llm-config.openrouter-example.json`; its official Koog client uses OpenRouter's default API
endpoint and ignores `baseUrl`. For LiteRT-LM, set `modelPath` in
`cloud/worker/llm-config.litert-example.json` to an existing local `.litertlm` model before applying it;
the worker does not download the model.

OpenAI, Anthropic, Google, Ollama, Bedrock, DeepSeek, Mistral AI and DashScope require exact,
case-sensitive IDs from Koog's model catalog. Unknown IDs, unlisted dated versions and aliases
are rejected. Their default model IDs are:

| Provider | Default model ID |
| --- | --- |
| `OPENAI` | `gpt-4o` |
| `ANTHROPIC` | `claude-sonnet-4-0` |
| `GOOGLE` | `gemini-2.5-flash` |
| `OLLAMA` | `llama3.2:latest` |
| `BEDROCK` | `openai.gpt-oss-120b-1:0` |
| `DEEPSEEK` | `deepseek-v4-flash` |
| `MISTRAL_AI` | `mistral-medium-latest` |
| `DASHSCOPE` | `qwen-plus` |

OpenRouter requires an explicit, nonblank ID and accepts models outside Koog's catalog unchanged.
It uses the router's model support rather than guessed context or output-token limits.

Google uses the native Gemini API. Its default API root is `https://generativelanguage.googleapis.com`.
For a custom Google endpoint, set `baseUrl` to its native API root; the client appends
`v1beta/models/{id}:generateContent`. Existing Google configurations pointing to an OpenAI-compatible
`/v1beta/openai` endpoint must switch to the native root, or use `OPENAI_COMPATIBLE` to retain that protocol.

OpenAI-compatible model IDs are opaque strings passed directly to the API, with no OpenAI catalog
lookup or inferred metadata; their existing default is `gpt-3.5-turbo`. For direct-provider configurations,
replace unlisted IDs with exact catalog IDs. Custom deployments using the compatible HTTP protocol
can use `OPENAI_COMPATIBLE` with their endpoint and model name. LiteRT keeps local model IDs.

DeepSeek, Mistral AI and DashScope use `apiKey` and optionally a native API root in `baseUrl`;
DashScope defaults to the international endpoint. Use the corresponding `llm-config.*-example.json`
presets. Bedrock uses the native AWS Converse API. Its optional `region` defaults to the Koog SDK's
`us-west-2`; `baseUrl` optionally overrides the runtime endpoint. Without `apiKey`, AWS credentials
come from the SDK default chain (for example a workload role, environment or shared profile).
Alternatively, `apiKey` can contain a Bedrock bearer API key. Never store AWS access/secret keys in
that field. The Bedrock preset omits credentials so workload credentials can be supplied at runtime.

All providers return the same structured moderation decision. The LiteRT-LM client uses native JSON-schema
constrained output, tries the GPU backend first, and falls back to CPU if GPU initialization fails. Set
`cachePath` in the LiteRT-LM preset to a directory writable by the worker. Existing configurations without
`cachePath` use a model-specific directory under the JVM temporary directory.

LiteRT-LM's prebuilt Linux x86_64 library requires AVX. If the host cannot run the selected provider,
disable the persisted `TOPIC_MODERATION` task configuration; topic content is then not reviewed and
harmful-topic authors are not automatically marked `READ_ONLY`.

Moderation covers topics and comments in communities, user spaces, and public rooms that belong to a
community. Encrypted private and notification-room topics are excluded. Harmful content such as abusive
profanity, threats or graphic violence, pornography, hate, self-harm, exploitation, or promoted illegal
activity causes the author account to be marked `READ_ONLY`.

### Configure Windows GPU DLLs for LiteRT-LM

Windows GPU inference requires 64-bit `dxil.dll` and `dxcompiler.dll` from Microsoft's
[DirectX Shader Compiler](https://github.com/microsoft/DirectXShaderCompiler/releases). The
`com.google.ai.edge.litertlm:litertlm-jvm` artifact does not include these DLLs. LiteRT-LM loads them
through the native Direct3D 12 runtime, so adding their directory to `PATH` alone is not sufficient for
the current worker. Put both DLLs beside the `java.exe` that actually starts the worker.

The following PowerShell example installs the DXC version currently pinned for worker deployment. Use an
application-owned Java 21 runtime when possible instead of modifying a shared system JDK. Set
`JAVA_HOME` to that runtime before running these commands.

```powershell
$dxcArchive = Join-Path $PWD "dxc_2026_07_29.zip"
$dxcDirectory = Join-Path $PWD "dxc-v1.9.2607"
$expectedDxcHash = "A1DFB116BA3EEAE6A1582291B53A8E7BF65AD760676BD3194685C8F7367CD241"

if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    throw "Set JAVA_HOME to the Java 21 runtime used by the worker"
}

$workerJavaDirectory = Join-Path $env:JAVA_HOME "bin"
$workerJavaExecutable = Join-Path $workerJavaDirectory "java.exe"
$dxilDestination = Join-Path $workerJavaDirectory "dxil.dll"
$dxCompilerDestination = Join-Path $workerJavaDirectory "dxcompiler.dll"

if (-not (Test-Path -LiteralPath $workerJavaExecutable)) {
    throw "Worker Java executable not found: $workerJavaExecutable"
}
if (Test-Path -LiteralPath $dxcDirectory) {
    throw "DXC extraction directory already exists: $dxcDirectory"
}
if ((Test-Path -LiteralPath $dxilDestination) -or (Test-Path -LiteralPath $dxCompilerDestination)) {
    throw "DXC DLLs already exist in $workerJavaDirectory; verify or back them up before replacing them"
}

Invoke-WebRequest `
    -Uri "https://github.com/microsoft/DirectXShaderCompiler/releases/download/v1.9.2607/dxc_2026_07_29.zip" `
    -OutFile $dxcArchive

$actualDxcHash = (Get-FileHash -LiteralPath $dxcArchive -Algorithm SHA256).Hash
if ($actualDxcHash -ne $expectedDxcHash) {
    throw "DXC SHA-256 mismatch: $actualDxcHash"
}

Expand-Archive -LiteralPath $dxcArchive -DestinationPath $dxcDirectory
Copy-Item -LiteralPath (Join-Path $dxcDirectory "bin\x64\dxil.dll") -Destination $dxilDestination
Copy-Item -LiteralPath (Join-Path $dxcDirectory "bin\x64\dxcompiler.dll") -Destination $dxCompilerDestination
Get-Item -LiteralPath $workerJavaExecutable, $dxilDestination, $dxCompilerDestination
```

Restart the worker after installing the DLLs. A successful GPU startup logs both messages:

```text
Initializing LiteRT LLM client with GPU backend
Initialized LiteRT LLM client with GPU backend
```

If GPU initialization fails, the worker remains available and logs the fallback before initializing a
new CPU engine:

```text
LiteRT GPU initialization failed; falling back to CPU
Initialized LiteRT LLM client with CPU backend
```

A warning about missing `libLiteRtTopKWebGpuSampler.dll` concerns LiteRT-LM's optional WebGPU sampler.
Version `0.16.1` does not ship that DLL in its JVM artifact and uses its built-in C sampler instead; this
warning does not by itself mean that the main model fell back from GPU. Use the backend initialization
messages above as the source of truth.

## Build and Run

### Requirements

- Java 21 (Eclipse Temurin)
- The included Gradle wrapper (`./gradlew`)
- Git Bash on Windows for repository shell scripts
- Docker with Compose support for the local service stack
- Desktop AsciiDoc previews use the system WebView engine: WebView2 on Windows, WKWebView on macOS, and WebKitGTK on Linux.

### Build Commands

```bash
# Assemble included modules
./gradlew assemble -Pserver.flavor=sample -Pserver.buildType=prod

# Build the Cloud server
./gradlew :cloud:server:assemble -Pserver.flavor=sample -Pserver.buildType=prod

# Build the Android app
./gradlew :app:androidApp:assembleDebug -Pserver.flavor=sample -Pserver.buildType=prod

# Build the Desktop app
./gradlew :app:desktopApp:packageDistributionForCurrentOS -Pserver.flavor=sample -Pserver.buildType=prod
```

### Compose Stack With Wasm Apps

Add `app` to a flavor's `COMPOSE_FILE_LIST` to deploy the user app and admin panel as Wasm sites.
Compose builds both sites in the JDK 21 builder stage of `wasm.Dockerfile`, passes only the required
flavor, build type, and server URLs as build arguments, and copies each distribution into its own
Nginx runtime image. Deployment compiles backend and Wasm distributions inside Docker; the host
does not need a local JDK or prebuilt distributions. Images are built sequentially before startup:

```bash
./scripts/service_scripts/start-service-in-docker.sh sample
```

To refresh configuration using existing Docker-built images, run `./scripts/service_scripts/start-service-in-docker.sh sample --no-build`.

Open the user app at `http://localhost:8080` and the panel at `http://localhost:8081` after the containers become healthy. Omit `app` to skip both Wasm builds and services.

Both `compose-service.sh` and `start-service-in-docker.sh` accept an environment name
(`alpha` or `alpha.env`) or a file path (`~/deploy/alpha.env`). Names resolve to
`~/deploy/<name>.env` first, then `deploy/<name>.env`. Use `deploy/sample.env` as the reference;
`BUILD_TYPE` is required, and `COMPOSE_FILE_LIST` selects which services are included in the stack.
Server, Worker, WebSocket server, and CLI images share the JDK 21 builder in the root `Dockerfile` and
are packaged as the separate `server`, `worker`, `ws`, and `cli` targets.

When `COMPOSE_FILE_LIST` includes `bunker`, copy `deploy/bunker.env.example` to
`~/deploy/bunker.env` outside the project and replace its example domains and credentials.
Set `BUNKER_ENV_FILE=/home/<user>/deploy/bunker.env` inside the selected application env file
to use another path; a relative path is resolved beside that env file. A shell override takes precedence. The file is injected only into the BunkerWeb
container using `env_file`; it does not participate in Compose interpolation or override the flavor
configuration. Docker Compose 2.30 or newer is required for the raw env-file format, which preserves
literal credential values without interpolation. It contains final BunkerWeb names such as `SERVER_NAME`, `ADMIN_USERNAME`,
`ADMIN_PASSWORD`, and `api.example.com_REVERSE_PROXY_HOST`, rather than the former `BUNKER_*` variables.

For filesystem storage, set `FILESYSTEM_PUBLIC_URL` to the externally reachable media origin
(for example `https://media.example.com`), and `FILESYSTEM_EXPOSE_PORT` to the published HTTP port
(default `8822`). Clients download `/objects/<bucket>/<name>` directly from this service.
With BunkerWeb, the media domain routes this path to filesystem port 8822; port 8820 is the internal
management RPC endpoint and must remain private.
Set `FILESYSTEM_PUBLIC_URL=https://<media-domain>` in `deploy/{flavor}.env`, including BunkerWeb deployments.
The startup script passes the same resolved origin to filesystem and the application services.

`PRESET_PATH=/home/<user>/data` bind-mounts the host preset directory into Server and CLI
as `/app/deploy/preset_data`. CLI reads numbered JSON presets and referenced local files there.
Enable `CLI_INIT_ENABLE=true` only for deliberate data initialization; disable it afterwards.

## License

This is a private project. All rights reserved.

### Login sessions

Use **Sign out** in the App account menu or the Panel navigation drawer to end the current session.
Refreshing after sign-out keeps the session signed out; saved account history remains available for explicit login.

HTTP and WebSocket authentication use an HttpOnly session Cookie backed by Redis. A login expires after
30 days without user activity or 90 days after sign-in. Background refresh, heartbeats and incoming
notifications do not keep a login alive. Expired or revoked sessions require an explicit sign-in.
Browser clients support one primary account; child-account switching remains available in native clients.

The server Compose profile includes private Redis storage with AOF persistence and no exposed host port.
Set `SIGNIN_ALLOWED_ORIGINS` in the application flavor environment to the exact comma-separated browser
origins, for example `https://app.example.com,https://panel.example.com`. API and `/ws` must share a host.
An external Redis can be selected with `SIGNIN_REDIS_ENDPOINT`. Redis failure prevents authentication
until it recovers; credentials are retained locally during temporary outages.

Native GUI clients protect saved session Cookies with Android Keystore, Windows DPAPI, macOS Keychain,
or Linux Secret Service (`secret-tool`). Cookie persistence may retain an in-memory credential when
protected storage is unavailable; saving account keys requires the system secure store. There is no
plaintext fallback. Saved private keys and account history also use protected storage. Browser key history is encrypted
in IndexedDB with a non-extractable WebCrypto key; browser login Cookies remain HttpOnly. Old
Settings login history is ignored; import the account private key again after upgrading.
This migration invalidates older encrypted session Cookies; sign in once after upgrading.

Read-only accounts can sign in and browse, but cannot publish changes.
System notification rooms allow recipients to read messages; their message input is disabled.

Snapshot PDF signing accepts `SNAPSHOT_KEYSTORE_BASE64` (the single-line Base64 encoding of the existing PKCS#12 file) and `SNAPSHOT_KEYSTORE_PASS` in the selected environment file. The server validates and uses the key in memory; no keystore mount is required. Base64 is encoding, not encryption: protect the environment file and never commit or log its contents. `SNAPSHOT_KEYSTORE_BASE64` takes precedence over the legacy `SNAPSHOT_KEYSTORE_PATH`; malformed data or an incorrect/missing password fails startup without falling back or generating a replacement key. To migrate, encode the existing file with `base64 -w 0 < existing.p12`, save the output privately in the environment file, retain the password, and remove `SNAPSHOT_KEYSTORE_PATH`. With neither source configured, snapshot signing is disabled.

## Room calls

Android, desktop and browser clients share the room call page. Open a room's call action,
then choose **Join with video** or **Join with audio** and allow the requested permissions.
Participants appear in a scrollable adaptive grid with independent connection, camera and
mute status. Controls mute the microphone, hide video, switch supported cameras or leave.
Calls end when the call page is closed. Browser calls require HTTPS and camera/microphone
permission. CLI clients do not provide audio/video calls.

The iOS adapter and permissions are included; build and device acceptance require macOS/Xcode.
