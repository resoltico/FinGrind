---
afad: "3.5"
version: "0.17.0"
domain: DEVELOPER_DISTRIBUTION
updated: "2026-04-17"
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
- `bin/fingrind.cmd`
- a private Java 26 runtime image built with `jlink`
- the FinGrind application JAR
- the managed SQLite 3.53.0 / SQLite3 Multiple Ciphers 2.3.3 native library for that target
- a top-level `README.md` for local human bootstrap
- a top-level `bundle-manifest.json` for machine bootstrap and target discovery
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
- `macos-x86_64`
- `linux-x86_64`
- `linux-aarch64`
- `windows-x86_64`

Linux bundle policy:
- public Linux bundles are built on Ubuntu GitHub-hosted runners
- they therefore target ordinary glibc Linux hosts
- they are not claimed to be one universal binary for every Linux libc variant

Windows bundle policy:
- public Windows bundles are built on Windows GitHub-hosted runners
- they use the native MSVC toolchain through the Developer Command Prompt environment
- they are published as `.zip` archives and use `bin\fingrind.cmd` as the platform launcher

## Release Build Policy

Release automation currently uses `actions/setup-java` with `distribution: zulu`.

Why that is acceptable:
- the release workflow already relies on GitHub-hosted provisioning, not a hand-maintained local
  release workstation
- Zulu 26 on GitHub-hosted runners provides the full JDK surface we actually need:
  `javac`, `jdeps`, and `jlink`
- the supported release matrix is covered by those runners today: Ubuntu x86_64, Ubuntu arm64,
  macOS arm64, macOS x86_64, and Windows x86_64

When to revisit this choice:
- if Zulu stops offering Java 26 for one of the supported public bundle builders
- if GitHub-hosted provisioning changes so the required full-JDK tools are no longer available
- if FinGrind changes its release environment strategy entirely

This does not replace the contributor workstation rule in [DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md):
local developer shells remain documented against the OpenJDK route published from `openjdk.org`.

## Runtime Image Policy

FinGrind's private Java runtime images are intentionally built as minimal execution images, not as
full developer JDKs or inherited full JRE installations.

Current rules:
- module discovery must fail loud on unresolved runtime dependencies; do not use
  `jdeps --ignore-missing-deps` on the public-runtime path
- runtime compression uses `jlink --compress=zip-6`, not deprecated numeric aliases
- the runtime image must not pull in tool modules such as `jdk.jdeps`, `jdk.jlink`, or
  `jdk.jpackage`
- do not use `jlink --bind-services` on the public-runtime path unless a demonstrated runtime
  need appears and is verified against the final module list, because it can drag tool modules
  into the image and erase the size benefit of the bundle

`./scripts/bundle-smoke.sh` and `./scripts/bundle-smoke.ps1` assert those runtime-image rules
directly against the extracted bundle on Unix and Windows, respectively.

## Container Parity Policy

The container image is a second public distribution surface and must stay on the same managed
runtime contract as the bundle archives.

Current rules:
- Docker image assembly verifies the vendored SQLite3MC source hash before compiling the native
  library
- the container image ships the same application JAR plus a private `jlink` runtime, not a full
  inherited distro JRE
- the container image advertises itself through
  `capabilities.environment.runtimeDistribution = "container-image"`
- the bundle remains the canonical public CLI artifact; the container is an additional supported
  public runtime surface, not a weaker or differently pinned path

## Local Build Surface

Bundle entrypoints:

```bash
./gradlew :cli:bundleCliArchive
./scripts/bundle-smoke.sh
```

On Windows PowerShell, use:

```powershell
.\gradlew.bat :cli:bundleCliArchive
.\scripts\bundle-smoke.ps1
```

Developer-only raw JAR entrypoints:

```bash
./gradlew :cli:shadowJar
./gradlew prepareManagedSqlite
export FINGRIND_SQLITE_LIBRARY="$(find "$PWD/build/managed-sqlite" -type f \( -name 'libsqlite3.dylib' -o -name 'libsqlite3.so.0' \) | head -n 1)"
java --enable-native-access=ALL-UNNAMED -jar cli/build/libs/fingrind.jar help
```

The raw JAR route remains useful for:
- Docker image assembly
- advanced contributor debugging
- validating the application JAR directly during development

`./gradlew :cli:shadowJar` also stages the compile-only JDeps support jars that the Docker build
uses to analyze the shaded application JAR under `cli/build/docker/jdeps/`.

It is not the public release artifact.

## Publication Rules

Every GitHub release must publish:
- one archive per supported target (`.tar.gz` for macOS and Linux, `.zip` for Windows)
- one `.sha256` checksum file per supported target archive

Every release must verify:
- the extracted bundle runs without ambient Java
- the extracted bundle runs without a preconfigured `FINGRIND_SQLITE_LIBRARY`
- the extracted bundle contains a top-level human `README.md` and machine-readable
  `bundle-manifest.json`
- `capabilities` reports the expected managed runtime contract
- the GitHub release object contains the complete bundle-and-checksum set
- the container workflow waits for the complete GitHub release asset set before it publishes the
  public image, so Docker publication cannot outrun an incomplete release handoff

Release helper scripts are part of that contract and must remain portable across the actual
GitHub-hosted release runners. In practice this means publication-critical shell code must work
with the runner-provided Bash on macOS, which is still Bash 3.2. Do not introduce Bash 4+
builtins such as `mapfile` into `scripts/bundle-smoke.sh` or other Bash-based release-path scripts
unless the release environment policy is changed explicitly and codified first. Windows bundle
verification is handled through `scripts/bundle-smoke.ps1`, so Windows-specific release-path logic
must remain portable across the runner-provided PowerShell as well.

These rules are enforced through:
- `./scripts/bundle-smoke.sh`
- `./scripts/bundle-smoke.ps1`
- `.github/workflows/release.yml`
- [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md)
