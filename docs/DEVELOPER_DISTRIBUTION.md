---
afad: "3.5"
version: "0.13.0"
domain: DEVELOPER_DISTRIBUTION
updated: "2026-04-14"
route:
  keywords: [fingrind, distribution, bundle, release asset, zulu, jlink, jpackage, runtime, checksum]
  questions: ["what does fingrind publish as its public cli artifact", "why does fingrind ship bundles instead of a jar", "why is zulu used in release automation", "does fingrind use jpackage"]
---

# Distribution Policy

**Purpose**: Codify the public FinGrind CLI artifact contract and the rules for building and
publishing it.
**Prerequisites**: Familiarity with [DEVELOPER.md](./DEVELOPER.md),
[DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md), and [DEVELOPER_SQLITE.md](./DEVELOPER_SQLITE.md).

## Canonical Public Artifact

FinGrind's public CLI download is a self-contained per-platform archive, not a raw JAR.

Each published archive contains:
- `bin/fingrind`
- a private Java 26 runtime image built with `jlink`
- the FinGrind application JAR
- the managed SQLite 3.53.0 / SQLite3 Multiple Ciphers 2.3.3 native library for that target
- license and notice files

The bundle launcher sets `fingrind.bundle.home` and starts the private runtime directly.
That keeps public execution independent from:
- a separately installed Java runtime
- a preconfigured `FINGRIND_SQLITE_LIBRARY`
- ambient host `libsqlite3` fallback

This is the supported public CLI contract.

## Why Bundles, Not A Bare JAR

FinGrind is not a pure-Java CLI. Its protected-book runtime depends on a pinned native SQLite3MC
library and the final Java 26 FFM API.

Because of that:
- a raw JAR is only an internal assembly input
- a raw JAR by itself is not a truthful public end-user artifact
- host `libsqlite3` fallback would weaken the managed runtime contract and is therefore rejected

The correct response is to package the required runtime, not to relax the dependency boundary.

## Why Bundles, Not `jpackage`

`jpackage` is intentionally out of scope for the current FinGrind phase.

Why:
- FinGrind is an agent-first CLI, not a desktop application
- unpack-and-run archives are a better fit for CLI automation and side-by-side versions
- the current problem is truthful runtime packaging, not native installer UX

If native installers are ever added later, they are a secondary convenience layer over the same
self-contained runtime contract, not the primary public artifact.

## Public Target Matrix

Current public bundle targets:
- `macos-aarch64`
- `linux-x86_64`
- `linux-aarch64`

Linux bundle policy:
- public Linux bundles are built on Ubuntu GitHub-hosted runners
- they therefore target ordinary glibc Linux hosts
- they are not claimed to be one universal binary for every Linux libc variant

Windows is not part of the current public bundle contract because the managed SQLite build and
verification policy are currently codified only for macOS and Linux.

## Release Build Policy

Release automation currently uses `actions/setup-java` with `distribution: zulu`.

Why that is acceptable:
- the release workflow already relies on GitHub-hosted provisioning, not a hand-maintained local
  release workstation
- Zulu 26 on GitHub-hosted runners provides the full JDK surface we actually need:
  `javac`, `jdeps`, and `jlink`
- the supported release matrix is covered by those runners today: Ubuntu x86_64, Ubuntu arm64,
  and macOS arm64

When to revisit this choice:
- if Zulu stops offering Java 26 for one of the supported public bundle builders
- if GitHub-hosted provisioning changes so the required full-JDK tools are no longer available
- if FinGrind changes its release environment strategy entirely

This does not replace the contributor workstation rule in [DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md):
local developer shells remain documented against the OpenJDK route published from `openjdk.org`.

## Local Build Surface

Bundle entrypoints:

```bash
./gradlew :cli:bundleCliArchive
./scripts/bundle-smoke.sh
```

Developer-only raw JAR entrypoints:

```bash
./gradlew :cli:shadowJar
./gradlew prepareManagedSqlite
export FINGRIND_SQLITE_LIBRARY="$(find "$PWD/build/managed-sqlite" -type f \( -name 'libsqlite3.dylib' -o -name 'libsqlite3.so.0' \) | head -n 1)"
java -jar cli/build/libs/fingrind.jar help
```

The raw JAR route remains useful for:
- Docker image assembly
- advanced contributor debugging
- validating the application JAR directly during development

It is not the public release artifact.

## Publication Rules

Every GitHub release must publish:
- one `.tar.gz` bundle per supported target
- one `.tar.gz.sha256` checksum file per supported target

Every release must verify:
- the extracted bundle runs without ambient Java
- the extracted bundle runs without a preconfigured `FINGRIND_SQLITE_LIBRARY`
- `capabilities` reports the expected managed runtime contract
- the GitHub release object contains the complete bundle-and-checksum set

These rules are enforced through:
- `./scripts/bundle-smoke.sh`
- `.github/workflows/release.yml`
- [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md)
