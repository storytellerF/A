# A

A Kotlin Multiplatform application for Android, Desktop, CLI, and Web, with cloud services and an
administration panel.

[Download the latest alpha build](https://nightly.link/storytellerF/A/workflows/alpha/alpha?preview)

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

Add `app` to a flavor's `COMPOSE_FILE_LIST` to deploy the user app and admin panel as Wasm sites. The local startup script builds both distributions before starting Compose.

```bash
./scripts/service_scripts/start-service-in-local.sh sample
```

Open the user app at `http://localhost:8080` and the panel at `http://localhost:8081` after the containers become healthy. Omit `app` to skip both Wasm builds and services.

Deployment settings are read from `deploy/<flavor>.env`. Use `deploy/sample.env` as the reference;
`BUILD_TYPE` is required, and `COMPOSE_FILE_LIST` selects which services are included in the stack.

## License

This is a private project. All rights reserved.
