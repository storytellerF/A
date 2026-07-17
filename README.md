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
- **Code quality**: Detekt static analysis, Kover coverage reports

## Project Structure

### Core Modules

- **App module** (`app/`) - Android/Desktop/CLI client applications
  - `composeApp` - Shared Compose Multiplatform UI
  - `android` - Android app shell
  - `desktopApp` - Desktop app entry point
  - `cliApp` - Command-line app
  - `core` - Core business logic

- **Cloud module** (`cloud/`) - Cloud server side
  - `server` - Ktor HTTP server
  - `service` - Cloud services and TOTP implementation
  - `worker` - Background task processing
  - `pdf/pdfbox/openpdf` - PDF processing services
  - `cli` - Cloud administration CLI

- **Backend module** (`backend/`) - Data storage layer
  - `exposed` - SQL database ORM
  - `redis` - Redis cache
  - `minio` - S3-compatible object storage
  - `lucene` / `elastic` - Search engines
  - `filesystem` - Filesystem storage

- **Client module** (`client/`) - Client libraries
  - `core` - Client core
  - `bot-lib` - Bot library
  - `room` - Room features
  - `model-storage` - Model storage
  - `ascii-parser` - ASCII parser

- **Panel module** (`panel/`) - Administration panel
  - `composeApp` - Administration UI
  - `android` / `desktopApp` / `cliApp` - Multiplatform entry points

- **API module** (`api/`) - API definitions and service routes

- **Bot module** (`bot/`) - Built-in bot features

- **Dev module** (`dev/`) - Development and test tools
  - `server` - Development server
  - `cli` - Development tools
  - `appium` - Optional Appium automation tests

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
- Gradle 8.x

### Build Commands

```bash
# Build all modules
./gradlew build

# Build the Cloud server
./gradlew :cloud:server:build

# Build the Android app
./gradlew :app:androidApp:assembleDebug

# Build the Desktop app
./gradlew :app:desktopApp:packageDistributionForCurrentOS

# Run tests
./gradlew test

# Run tests for specific modules
./gradlew :cloud:service:test :backend:minio:test
```

### Docker Deployment

```bash
# Build the image
docker build -t a-server .

# Start the service
sh ./bin/server
```

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

### Appium Tests

```bash
# Enable Appium test modules
./gradlew build -Pappium=true

# Build both APKs when required
./gradlew :app:androidApp:assembleDebug :panel:androidApp:assembleDebug
```

## CI/CD

Alpha Server CI runs the following tests before starting the remote service:

- `:backend:minio:test`
- `:cloud:cli:test`
- `:cloud:service:test`
- `:cloud:server:test`

Enable the Testcontainers path override:

```bash
ENABLE_TEST_CONTAINER=true ./gradlew test
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
