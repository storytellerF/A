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

### Sample Compose Stack With Wasm App

`sample` starts PostgreSQL, Elasticsearch, MinIO, initialization CLI, server, WebSocket server, and the browser app. The script builds the Wasm distribution first and passes the sample HTTP/WebSocket URLs into its build configuration.

```bash
./scripts/service_scripts/start-sample-service.sh
```

Open `http://localhost:8080` after the containers become healthy. Stop the stack with the same flavor and `down` command.

### Environment Variables

- `HOST_TYPE`: Host type, such as `docker`
- `BUILD_TYPE`: Build type
- `FLAVOR`: Build flavor, such as `dev` or `alpha`
- `BUILD_ON`: Build platform
- `APP_UID/GID`: Container user permissions, default `1000`
- `ENABLE_SIGN_UP`: Whether user sign-up is allowed, default `true`
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
