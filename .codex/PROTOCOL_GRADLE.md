# PROTOCOL_GRADLE.md — Gradle Build Protocol

**Version:** 1.0.0
**Updated:** 2026-06-13
**Inherits:** [.codex/UNIVERSAL_ENGINEERING_CONTRACT.md](./UNIVERSAL_ENGINEERING_CONTRACT.md) v3.0.0+
**Scope:** Gradle build logic in any repository, regardless of implementation language. Loads alongside the language protocol for the touched surface.

This file owns the language-independent Gradle rules. Language-specific build wiring — Java preview-feature flags and `--release` targeting, Kotlin compiler options and plugin alignment — stays in the language protocols, which take precedence where they are stricter.

## 1. Wrapper and toolchains

Use the Gradle wrapper. Do not invoke a globally installed `gradle`.

Use Java toolchains for compilation and, where appropriate, test and runtime tasks. The build must not depend on whichever JDK happens to be installed on the machine.

When upgrading the wrapper, prefer the current stable Gradle version supported by the repository's plugins rather than a minimal version alone.

## 2. Build authoring language

For new build logic, prefer Gradle Kotlin DSL (`settings.gradle.kts`, `build.gradle.kts`). If the repository uses Groovy DSL, preserve that choice unless migration is part of the task.

Do not turn an ordinary task into an accidental DSL migration.

## 3. Dependencies

Prefer version catalogs (`gradle/libs.versions.toml`) for shared plugin and dependency coordinates. Do not scatter repeated version strings across build files or hardcode versions in module build files.

Pin versions. Avoid floating versions such as `latest.release`, `latest.integration`, or `1.+`.

Before adding or updating a dependency:

- verify the exact group, artifact, and version in the declared repository;
- verify compatibility with the repository's language and platform baseline, and that it is not EOL;
- verify it is not already provided by the JDK, the standard library, or an existing dependency;
- verify the API and the correct scope/configuration from current documentation, not memory.

Do not invent coordinates. Do not add a library to avoid writing a small amount of straightforward code.

## 4. Repositories

Keep repositories minimal and explicit. Do not add broad or duplicate repositories casually.

## 5. Shared build logic

For substantial shared build logic, prefer convention plugins in an included build such as `build-logic`.

`buildSrc` is acceptable when the repository already uses it, the logic is small and local, or migration cost exceeds benefit.

Convention plugin IDs must be qualified (`com.example.project.java-library`), not generic (`java-library`, `jvm-conventions`).

## 6. Multi-module structure

Keep module responsibilities sharp. Avoid circular dependencies. Put shared policy in convention plugins rather than duplicated snippets. Use type-safe project accessors when appropriate.

Do not create modules that exist only to look clean without reducing coupling.

## 7. Build performance features

Configuration cache, build cache, parallelism, and test distribution are good when correct for the repository. Correctness first. Do not cargo-cult performance flags.

## 8. Build isolation and daemon management

Never run multiple Gradle invocations concurrently against the same project directory.

For concurrent builds across different projects, isolate Gradle user homes:

```bash
GRADLE_USER_HOME="$PROJECT_ROOT/.gradle-home" ./gradlew check
```

Add `.gradle-home/` to `.gitignore` if this convention is adopted.

Do not use `./gradlew --stop` routinely. Stop daemons only to recover from confirmed daemon corruption. Treat daemon-connection failures as infrastructure first: verify daemon and process state before editing source or build logic.

Keep `org.gradle.jvmargs` at the minimum heap the project actually requires.
