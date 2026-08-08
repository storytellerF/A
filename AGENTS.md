# Project Development Rules

## Global Principles
- Use JDK 21. All modules must target Kotlin/JVM 21.
- Prefer KMP-shareable code. Handle platform differences with `expect`/`actual` or platform source directories.
- Keep the Gradle configuration cache enabled (`org.gradle.configuration-cache=true`) and avoid side effects or IO during configuration.
- Logging: use Napier in shared/app code. Initialize a logging tree only when needed for tests or debugging.
- Static analysis: run Detekt with `config/detekt/detekt.yml`. Existing `detekt-baseline*.xml` files are immutable after creation: never edit, regenerate, add, remove, or migrate their entries. Fix every new or newly exposed issue in source code, and do not add or broaden suppressions to bypass it.
- Detekt permits public library entities and public data classes only in the configured `api` and `shared.model` protocol/model scopes and the `client/core` request boundary; keep these exceptions narrowly scoped in `config/detekt/detekt.yml`.
- New Kotlin source files must start with the private-project license header configured by Detekt.
- Coverage: generate Kover reports when needed.
- Environment/secrets: `app/composeApp` and `panel/composeApp` inject values from `deploy/{flavor}.env` through BuildKonfig. `app/androidApp` reads the same env file for deep links. `app/androidApp` and `panel/androidApp` use flavor/buildType values to configure application IDs and build types. Always pass `-Pserver.flavor` and `-Pserver.buildType`.
- Commit messages: start with a verb in English or Chinese and include the module/scope, for example `client/core: add getAllUsers request`.
- Do not use Chinese method names.
- Do not use bare `InputStream`/`OutputStream`; use `BufferedInputStream`/`BufferedOutputStream`.
- Do not use `println` or `System.out.println` in code.
- When editing files, preserve the existing line endings.
- When adding dependencies, verify the correct group and artifact from GitHub or the upstream repository. If the dependency cannot be found, do not downgrade versions.

## Module Responsibilities
- **api**: Defines REST API endpoints, query/path models, and the admin `AdminApi` (`/admin/*`).
- **client/core**: Session management and request wrappers using unified `serviceCatching`.
- **client/composeCore**: Shared Compose UI components, media helpers, cross-platform UI utilities, and related actual implementations.
- **client/asciidoc-parser**: AsciiDoc preview HTML generation used by markdown rendering.
- **client/model-storage**: Abstract storage interfaces and collection identifiers.
- **client/room**: Room-based storage implementation and aggregation entry points.
- **shared**: Cross-platform business models and utilities.
- **app/composeApp**: Shared user-facing Compose UI, actual implementations, and services.
- **app/androidApp** / **app/desktopApp** / **app/cliApp**: User-facing platform entry points.
- **panel/composeApp**: Shared Compose UI for the admin panel.
- **panel/androidApp** / **panel/desktopApp** / **panel/cliApp**: Admin panel platform entry points.
- **backend/***: Backend core functionality, storage, search, and related services.
- **cloud/***: Core service modules that integrate backend functionality.
- **bot/***: Bot modules.

## Code Style And Practices
- **Kotlin**: Do not expose mutable collections in public APIs. Prefer `data class`, `sealed interface`/`sealed class`, and `suspend` + `Result<T>`. Use verb-object naming for functions.
- **Compose**: Name `@Composable` functions in PascalCase. The general lowercase function-naming checks exclude `composeApp` source trees because Compose uses a different naming convention.
- **Architecture**: UI -> `SessionManager` request extensions -> API endpoint. Add local storage and paging when appropriate.
- **Paging**: Store cursors consistently in `RemoteKeyStorage` (`PRE_COLLECTION`/`NEXT_COLLECTION`).
- **Logging**: Instrument external network requests through `serviceCatching`. Avoid noisy logging in hot paths.
- **Tests**: Write tests whenever possible. Client-side non-UI tests go under `src/headlessTest/kotlin`. Compose tests should follow https://kotlinlang.org/docs/multiplatform/compose-test.html. End-to-end tests use `scripts/test_scripts/build-and-test.sh --e2e`, which runs the Appium runner modules' explicit `appiumTest` tasks and CLI E2E tasks; no Gradle property is required.

## Static Checks
- After code changes, run `./gradlew assemble --console=plain` to check for compilation errors.
- After compilation checks, run `./scripts/tool_scripts/exec-until-success.sh ./gradlew detekt --console=plain` for static code style checks.
  - On Windows, run this through Git Bash.
- Detekt allows function names up to 50 characters so tests can use behavior-descriptive names. Keep production function names concise even when the configured limit permits longer names.

## Tests
- Actively test affected modules.
    * For small changes, use `./gradlew :module:test --console=plain`.
    * For the full regular unit test suite, use `./scripts/test_scripts/build-and-test.sh --unit --plain` (excluding device-dependent Compose and Appium tests). Filter multiple tests with `--tests 'package.FirstTest' 'package.SecondTest'`; repeating `--tests` is also supported.
    * For end-to-end tests, use `./scripts/test_scripts/build-and-test.sh --e2e --plain`; this runs Appium and CLI E2E suites.
    * For Compose common tests under `device_based`, use a real device with `./gradlew :module:connectedAndroidTest` or run `./gradlew :module:jvmTest`.
    * For Compose UI changes, run `./gradlew validateDebugScreenshotTest` for snapshot tests.
- Keep Compose screenshot tests context-free: do not inject app runtime `CompositionLocal` dependencies such as session managers, dialog controllers, or navigation contexts. Cover components that require those dependencies in another test layer.
- Tests are meant to reveal problems. If a test finds a problem, fix the problem instead of working around it in the test.
- Do not add extra test steps into a test case unless there is an actual dependency between the steps.
- Extract repeated test steps into helper methods.
- Appium tests require a real device or emulator and cannot run in parallel.
- Appium tests compose a target helper (`AppAppiumHelper` or `PanelAppiumHelper`) with a platform helper (`AndroidAppiumHelper` or `DesktopAppiumHelper`). Keep concrete test methods as calls to shared `test*ByHelper` functions; target helpers create sessions and platform helpers own launch, cleanup, and log collection.

## Additional AI Collaboration Rules
- Make the smallest necessary changes. Prefer extending `client/core` and `api`; do not casually change public models.
- Do not create files outside the repository. Sensitive files (`deploy/*.env`) are excluded by `.aiexclude`.
- For changes involving the configuration cache, avoid reading external environment state during Gradle configuration.
- Keep existing tests compatible. If tests must be updated, explain the migration reason and steps.
- Save knowledge learned while coding or scanning the project to `project.md`. Edit outdated or incorrect knowledge there when found.
- Save rule-related knowledge learned while coding or scanning the project to this file.

## Troubleshooting Quick Reference
- Network errors: check `client/core/*Request.kt` `serviceCatching` logs and `AdminApi`/`CustomApi` routes.
- Paging errors: check how `RemoteKeyStorage`/`RemoteKeyRoomStorage` saves and reads cursors.
- Data not persisted: check `*RoomStorage.save`/`observeData` implementations and `commonJson` serialization.
