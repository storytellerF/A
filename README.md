# A

[Download app](https://nightly.link/storytellerF/A/workflows/alpha/alpha?preview)

## Project Overview

A is a Kotlin Multiplatform application framework that targets Android, Desktop, CLI, and Web. It uses a modular architecture and includes cloud services, backend storage, bot features, and other components needed for a full application stack.

## Tech Stack

- **Languages**: Java 21 (Eclipse Temurin), Kotlin
- **Multiplatform architecture**: Android + Desktop + CLI + Web
- **UI framework**: Compose Multiplatform
- **Networking framework**: Ktor (JetBrains)
- **Database / storage**: Exposed ORM, Redis, MinIO, Lucene, Elasticsearch, Filesystem
- **PDF processing**: Apache PDFBox, OpenPDF

## Project Structure

### Core Modules

- **App module** (`app/`) - Android/Desktop/CLI client applications
  - `composeApp` - Shared Compose Multiplatform UI
  - `androidApp` - Android app shell
  - `desktopApp` - Desktop app entry point
  - `cliApp` - Command-line app

- **Cloud module** (`cloud/`) - Cloud server side
  - `server` - Ktor HTTP server
  - `runtime` - Runtime bootstrap and shared runtime utilities
  - `ws` / `ws-api` - WebSocket service and API support
  - `service` - Cloud services and TOTP implementation
  - `worker` - Background task processing
  - `pdf` / `pdfbox` / `openpdf` - PDF processing services
  - `cli` - Cloud administration CLI

- **Backend module** (`backend/`) - Data storage layer
  - `core` - Backend storage interfaces and shared backend models
  - `exposed` - SQL database ORM
  - `redis` - Redis cache
  - `minio` - S3-compatible object storage
  - `lucene` / `elastic` - Search engines
  - `filesystem` / `simple` - Filesystem and simple storage implementations

- **Client module** (`client/`) - Client libraries
  - `core` - Client core
  - `composeCore` - Shared Compose UI components, media helpers, and cross-platform UI utilities
  - `bot-lib` - Bot library
  - `room` - Room features
  - `model-storage` - Model storage
  - `asciidoc-parser` - AsciiDoc parser

- **Panel module** (`panel/`) - Administration panel
  - `composeApp` - Administration UI
  - `androidApp` / `desktopApp` / `cliApp` - Multiplatform entry points
  - `benchmark` - Android benchmark module

- **API module** (`api/`) - API definitions and service routes

- **Bot module** (`bot/`) - Built-in bot features

## Key Features

### File Uploads

- Standard uploads (`POST /files/upload`)
- Chunked uploads (`POST /files/chunk/*`)
- SHA256 integrity checks
- Configurable storage backend: MinIO or local filesystem

### User Authentication

- Private-key signature sign-in (`/accounts/sign-in`)
- TOTP two-factor authentication (2FA)
- User sign-up control through the `ENABLE_SIGN_UP` environment variable

### Task Management

- Worker execution record storage and querying
- Paginated task records in the Panel administration UI
- Filtering by task type
- Persisted worker task switches, fetch sizes, and wait durations managed from the Panel
- Local Gemma 3n topic safety review for communities, user spaces, and public community rooms
- Automatic `READ_ONLY` status for authors of harmful non-private topics

Worker task settings are stored per task type. A task without a persisted configuration does not run.
The CLI `add` command accepts a `taskConfig` preset whose `taskConfigData` entries contain `type`,
`isEnabled`, `fetchSize`, and `waitDurationMillis`; see `deploy/dev-data/7_preset_task_config.json`.
Administrators can edit the same values from the Panel's **Worker task configurations** page. Workers
reload the setting before every iteration, so enabling, disabling, or tuning a task does not require a restart.

### Worker Topic Moderation

When the persisted `TOPIC_MODERATION` task configuration is enabled, the worker uses Google LiteRT-LM
with `litert-community/gemma-4-E2B-it-litert-lm` and downloads then verifies
`gemma-4-E2B-it.litertlm` in its home directory before the first moderation iteration. The worker verifies
the cached model SHA-256 before loading it; a corrupt cache is downloaded again.

The model is approximately 2.59 GB. A complete existing file is reused without another download.
For offline deployment, place the model at `${HOME}/gemma-4-E2B-it.litertlm` before starting the
worker. The Docker Compose worker persists `/home/app` in the `worker-home` volume.

LiteRT-LM's prebuilt Linux x86_64 library requires AVX. On a NAS or other host without AVX support,
set `TOPIC_MODERATION_ENABLED=false` in the worker's `deploy/<flavor>.env` file. This hard override skips
the model download and LiteRT initialization while continuing its other background tasks. Topic content
is not reviewed and harmful-topic authors are not automatically marked `READ_ONLY` while moderation is disabled.

Moderation covers topics and comments in communities, user spaces, and public rooms that belong to a
community. Encrypted private and notification-room topics are excluded. Harmful content such as abusive
profanity, threats or graphic violence, pornography, hate, self-harm, exploitation, or promoted illegal
activity causes the author account to be marked `READ_ONLY`.

### Media Playback

- Media playlist management
- Cross-platform media player service

### Cloud CLI

- Preset account key generation
- Dilithium signing key and Kyber encryption key management
- Dev-data initialization tools

## Build and Run

### Requirements

- Java 21 (Eclipse Temurin)
- The included Gradle wrapper (`./gradlew`)

### Build Commands

```bash
# Assemble included modules
./gradlew assemble

# Build the Cloud server
./gradlew :cloud:server:assemble

# Build the Android app
./gradlew :app:androidApp:assembleDebug

# Build the Desktop app
./gradlew :app:desktopApp:packageDistributionForCurrentOS
```

### Docker Deployment

```bash
# Build the image
docker build -t a-server .

# Start the service
sh ./bin/server
```

### Compose Stack With Wasm Apps

Add `app` to a flavor's `COMPOSE_FILE_LIST` to deploy the user app and admin panel as Wasm sites. The local startup script builds both distributions before starting Compose.

```bash
./scripts/service_scripts/start-service-in-local.sh sample
```

Open the user app at `http://localhost:8080` and the panel at `http://localhost:8081` after the containers become healthy. Omit `app` to skip both Wasm builds and services.

The Wasm distributions bundle Noto Sans SC so CJK text works in browsers such as Firefox without relying on installed fonts. On Chromium browsers that support Local Font Access, the app requests local-font permission after the first pointer or keyboard interaction and prefers an installed CJK font when permission is granted; otherwise it keeps using the bundled font.

The user Wasm app uses native browser audio and video controls and supports AVIF images through its browser-backed Coil decoder. MinIO downloads override signed response types from file records without modifying object metadata.

### Environment Variables

- `HOST_TYPE`: Host type, such as `docker`
- `BUILD_TYPE`: Build type
- `FLAVOR`: Build flavor, such as `dev` or `alpha`
- `BUILD_ON`: Build platform
- `APP_UID/GID`: Container user permissions, default `1000`
- `ENABLE_SIGN_UP`: Whether user sign-up is allowed, default `true`
- `TOPIC_MODERATION_ENABLED`: Whether the worker runs local topic moderation, default `true`
- `HUGGING_FACE_HUB_TOKEN`: Optional read token forwarded when the worker downloads the Gemma model
- `MEDIA_SERVICE`: Media storage backend, either `minio` or `filesystem`

### Gradle Properties

Configure these values in `~/.gradle/gradle.properties`:

```properties
gpr.user=<GitHub username>
gpr.key=<GitHub personal access token>
```

## Development Tools

### Gradle Dependency Pruning

```bash
# Automatically prune unused implementation dependencies
./scripts/build_scripts/gradle-prune-implementations.sh

# Specify the build flavor
./scripts/build_scripts/gradle-prune-implementations.sh -Pserver.flavor=alpha
```

## Architecture Notes

### RefCell Pattern

- `TopicRefCell`, `RoomRefCell`, `CommunityRefCell`, and `UserRefCell` obtain `LoadingHandler` through `LocalRefCellHandlerProvider`.
- They do not create ViewModels directly; dependencies are provided through injection.

### Android Module Boundaries

- `app/androidApp` - App shell module that contains Activities and the manifest
- `app/composeApp` - Shared Compose UI and actual implementations
- `composeApp` must not depend on the Android module at compile time; use explicit Intents to launch Activities.

### Panel / Worker Communication

- Worker execution records are stored in the `TaskRecords` table.
- Panel queries and manages them through the `/admin/task-records` API.

## More Documentation

For detailed project design and implementation notes, see [project.md](project.md).

## License

This is a private project. All rights reserved.
