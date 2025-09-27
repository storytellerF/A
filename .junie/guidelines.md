A — Project Guidelines for Advanced Contributors

Last updated: 2025-09-27

Scope
- This document captures project-specific knowledge to speed up setup, builds, testing, and day-to-day development for this repository.
- It assumes you are familiar with Gradle, Kotlin Multiplatform (KMP), Jetpack Compose (Android/Desktop), and common JVM/Android tooling.

1) Build and Configuration

1.1 Toolchains and prerequisites
- JDK: Java 21 is required across modules (Android and JVM targets). The build scripts set JvmTarget 21 for Android and JVM.
- Kotlin: KMP is used broadly; sources are split across commonMain, androidMain, jvm(Desktop)Main, wasmJsMain, iosMain.
- Compose: JetBrains Compose is used for Desktop; Android uses Jetpack Compose.
- Node (optional): Only needed if you enable wasm tests (browser/Karma) or Wasm target; see 2.4.

1.2 Gradle version catalogs and plugins
- Repositories are configured in settings.gradle.kts (Google, Maven Central, Ktor EAP, JitPack, JogAmp) and pluginManagement.
- Core repo-wide plugins: detekt, kover, versions, version-catalog-update (root build.gradle.kts).
- Compose Desktop packaging is configured in app/composeApp/build.gradle.kts under compose.desktop.application.

1.3 Key Gradle properties (gradle.properties)
- server.buildType: dev | prod, etc. Defaults to dev.
- app.flavor: flavor name (e.g., dev.win), used by app/composeApp to load ../{flavor}.env and populate BuildKonfig.
- target.ios: false by default. Set to true to include iOS targets.
- target.wasm: false by default. Set to true to include Wasm target (with browser/Karma test wiring in some modules).
- llama.enable: false by default. When true, includes :android-llama-cpp module and extra dependencies.
- org.gradle.configuration-cache=true is enabled; avoid configuration-time side effects.
- org.gradle.daemon=false is set; prefer explicit Gradle invocations.

You can override properties per-invocation, e.g.:
- gradlew :app:composeApp:tasks -Papp.flavor=dev.win -Pserver.buildType=dev
- gradlew build -Ptarget.ios=true -Ptarget.wasm=true

1.4 Environment and secrets
- The Android Compose app reads environment variables from app/composeApp/../../{flavor}.env at configuration time via BuildKonfig and injects into AppConfig constants:
  - SERVER_URL, WS_SERVER_URL
  - BUILD_TYPE, FLAVOR
- Android signing: The composeApp module defines a decodeBase64ToStoreFile task (group: signing). If the env var storyteller_f_sign_key is present (base64), it will generate build/signing/signing_key.jks and wire packageRelease to depend on it.
- Provided env files at repo root: dev.env, dev.win.env, alpha.env, prod.env, koyeb.env (and .filter). Ensure the selected -Papp.flavor matches an existing {flavor}.env.

1.5 Module map (partial)
- UI/Client: :app:composeApp (Android + Desktop), :app:cliApp, :client:core, :client:model-storage, :client:room, :client:ascii-parser
- Shared lib: :shared (common business logic, cryptography, markdown, etc.)
- Backend/Cloud: :backend:* and :cloud:* (server, worker, pdf tooling)
- Bots: :bot:builtin-bot
- Conditional: :android-llama-cpp when -Pllama.enable=true

1.6 Typical build targets
- Compose Desktop run (dev):
  - gradlew :app:composeApp:run -Papp.flavor=dev.win -Pserver.buildType=dev
- Compose Desktop package (Dev MSI/Deb/Dmg disabled proguard obfuscation turned on):
  - gradlew :app:composeApp:packageRelease -Papp.flavor=dev.win -Pserver.buildType=dev
- Android assemble debug:
  - gradlew :app:composeApp:assembleDebug -Papp.flavor=dev.win -Pserver.buildType=dev
- Backend service (example):
  - gradlew :backend:service:build

2) Testing

2.1 Source set layout and intent
- The project standardizes on a custom headlessTest source set in multiple modules (e.g., :shared, :app:composeApp, :client:ascii-parser, :client:kotbase). headlessTest depends on commonTest and is bridged into platform test tasks:
  - Android: androidUnitTest dependsOn(headlessTest)
  - JVM Desktop/JVM: desktopTest or jvmTest dependsOn(headlessTest)
  - Wasm (if enabled): wasmJs test is configured to run via Karma/Chrome in some modules (e.g., :shared)
- Benefit: Write KMP tests once in src/headlessTest/kotlin and run them on multiple targets without duplication.

2.2 Running tests (JVM/Desktop/Android unit)
- JVM tests in :shared (includes headlessTest via jvmTest):
  - gradlew :shared:jvmTest
- Desktop tests in :app:composeApp (includes headlessTest via desktopTest):
  - gradlew :app:composeApp:desktopTest
- Android unit tests (Robolectric enabled, with Android resources included in :shared and :app:composeApp):
  - gradlew :shared:testDebugUnitTest
  - gradlew :app:composeApp:testDebugUnitTest
Notes:
- Robolectric logging is enabled in :shared testOptions. Ensure JDK 21.
- Some tests may rely on flavors/env; pass -Papp.flavor and -Pserver.buildType as needed for the app module.

2.3 Running tests (Wasm, optional)
- Enable wasm at build time: -Ptarget.wasm=true
- Example for :shared that configures browser tests with Karma + Chrome:
  - gradlew :shared:wasmJsBrowserTest -Ptarget.wasm=true
Prereqs:
- Node + a recent Chrome available to Karma.

2.4 Adding new tests
- Preferred: add to src/headlessTest/kotlin in the target module to get cross-platform coverage. Example paths:
  - shared/src/headlessTest/kotlin/…
  - app/composeApp/src/headlessTest/kotlin/…
- Use kotlin.test APIs (Test, assertEquals, etc.) for maximum KMP compatibility.
- If you need Android-only test APIs, add to androidUnitTest and guard with expect/actual or platform checks.

2.5 Example minimal test flow (verified)
- We validated the pipeline with a minimal headless test in :shared (kotlin.test). Running with the IDE runner executed the test on JVM (and mapped headless). For CI/CLI, use:
  - gradlew :shared:jvmTest
- After validation, the temporary example test was removed from the repository. You can reproduce by adding a one-liner kotlin.test in shared/src/headlessTest/kotlin.

2.6 Coverage
- The root project applies Kover to a set of modules (compose and JVM libs). Generate reports with:
  - gradlew koverXmlReport koverHtmlReport

3) Additional Development Information

3.1 Static analysis (Detekt)
- Detekt is applied to all subprojects from the root; configuration is at config/detekt/detekt.yml.
- Auto-correct is enabled. Parallel mode on. Release Android build types are excluded from task creation for Detekt.
- Reports (xml, html, txt, sarif, md) are enabled. A SARIF merge task aggregates into build/reports/detekt/merge.sarif at root.
- Run at repo root:
  - gradlew detekt

3.2 Compose Desktop packaging
- compose.desktop.application is configured in app/composeApp with mainClass com.storyteller_f.a.app.compose_app.MainKt.
- NativeDistributions target formats: Dmg, Msi, Deb.
- Proguard for desktop release uses version 7.5.0 with obfuscate/optimize enabled and a module-specific rules file (proguard-rules-desktop.pro). Packaging task is wired to signing helper if storyteller_f_sign_key is present.

3.3 BuildKonfig and flavors
- app/composeApp uses BuildKonfig to inject SERVER_URL / WS_SERVER_URL and metadata (BUILD_TYPE, FLAVOR). Values are read from ../../{flavor}.env relative to the module (i.e., at repo root). If missing, blank strings are used for URLs.
- Always ensure -Papp.flavor matches an env file present in the repo root to avoid empty endpoints at runtime.

3.4 iOS/Wasm target gating
- iOS targets (iosX64/Arm64/SimulatorArm64) and Wasm are opt-in. Pass -Ptarget.ios=true and/or -Ptarget.wasm=true. iOS frameworks are static and named ComposeApp when enabled.

3.5 Logging and debugging
- Napier is used for logging in shared/app modules; consider initializing appropriate logger trees in tests when diagnosing failures.
- For Android instrumented tests, LeakCanary is available in the app module’s androidInstrumentedTest configuration.

3.6 Repositories and dependency constraints
- Some dependencies exclude protobuf-java (e.g., libs.connector) within composeApp to avoid conflicts with Android toolchains.
- JitPack and Ktor EAP are allowed repositories. Be mindful of reproducibility; prefer pinned versions in gradle/libs.versions.toml.

3.7 Performance and configuration cache
- org.gradle.configuration-cache=true is enabled globally. Avoid tasks or plugins that use non-cacheable configuration-time IO or environment probing.

Appendix — Handy commands
- List tasks for a module: gradlew :app:composeApp:tasks --all
- Clean: gradlew clean
- Full build (common modules): gradlew build
- Run Desktop app: gradlew :app:composeApp:run -Papp.flavor=dev.win -Pserver.buildType=dev
- JVM tests for shared: gradlew :shared:jvmTest
- Desktop tests for app: gradlew :app:composeApp:desktopTest
- Android unit tests: gradlew :app:composeApp:testDebugUnitTest
- Wasm tests (if enabled): gradlew :shared:wasmJsBrowserTest -Ptarget.wasm=true

Notes
- This document intentionally focuses on project-specific wiring (flavors via BuildKonfig, headlessTest mapping, Compose Desktop packaging, and Detekt/Kover setup). Standard Android/iOS/Compose setup steps are intentionally omitted.
