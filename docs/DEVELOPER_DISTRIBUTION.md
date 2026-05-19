---
afad: "4.0"
version: "0.41.0"
domain: DEVELOPER_DISTRIBUTION
updated: "2026-05-19"
route:
  keywords: [fingrind, distribution, bundle, release asset, zulu, jlink, jpackage, runtime, checksum]
  questions: ["what does fingrind publish as its public cli artifact", "why does fingrind ship bundles instead of a jar", "why is zulu used in release automation", "does fingrind use jpackage"]
---

# Distribution Policy

**Purpose**: Codify the public FinGrind CLI artifact contract and the rules for building and
publishing it.
**Prerequisites**: Familiarity with [DEVELOPER.md](./DEVELOPER.md),
[DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md), [DEVELOPER_DEVCONTAINER.md](./DEVELOPER_DEVCONTAINER.md),
and [DEVELOPER_SQLITE.md](./DEVELOPER_SQLITE.md).

## Canonical Public Artifact

FinGrind's public CLI download is a self-contained per-platform archive, not a raw JAR.

Each published archive contains:
- launcher scripts under `bin/`: `fingrind`, `fingrind.ps1`, and `fingrind.cmd`
- a private Java 26 runtime image built with `jlink`
- the FinGrind application JAR
- the managed SQLite native library pinned by the canonical managed-SQLite contract for that target
- a top-level `README.md` for local human bootstrap
- a top-level generated `bundle-manifest.json` for machine bootstrap and target discovery
- top-level legal files: `LICENSE`, `LICENSE-APACHE-2.0`, `LICENSE-SIL-OFL-1.1`,
  `LICENSE-SQLITE3MULTIPLECIPHERS`, `NOTICE`, and `PATENTS.md`

The bundle launcher sets `fingrind.bundle.home` and starts the private runtime directly.
On Windows, the PowerShell launcher also hands staged bridge arguments to the JVM through the
dedicated `FINGRIND_LAUNCHER_ARGUMENTS_FILE` environment contract so Unicode-only acceptance paths
do not have to survive a second native argv rehydration seam inside PowerShell.
That keeps public execution independent from:
- a separately installed Java runtime
- a preconfigured `FINGRIND_SQLITE_LIBRARY`
- ambient host `libsqlite3` fallback

This is the supported public CLI contract.

The contributor devcontainer is intentionally not part of this public artifact contract. It is a
developer shell documented in [DEVELOPER_DEVCONTAINER.md](./DEVELOPER_DEVCONTAINER.md), not a
published runtime surface.

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

Declared but intentionally unsupported public bundle targets:
- `windows-aarch64`

Linux bundle policy:
- public Linux bundles are built on Ubuntu GitHub-hosted runners
- they therefore target ordinary glibc Linux hosts
- they are not claimed to be one universal binary for every Linux libc variant

Windows bundle policy:
- public Windows bundles are built on Windows GitHub-hosted runners
- they use the native MSVC toolchain through the Developer Command Prompt environment
- they are published as `.zip` archives and use `bin\fingrind.ps1` as the canonical launcher
- `bin\fingrind.cmd` remains in the archive as a compatibility wrapper

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
  `environment.distribution.runtimeDistribution = "container-image"`
- the bundle remains the canonical public CLI artifact; the container is an additional supported
  public runtime surface, not a weaker or differently pinned path

## Local Build Surface

Bundle entrypoints:

```bash
./gradlew :cli:bundleCliArchive
./scripts/bundle-smoke.sh
```

Local bundle restaging prunes older `fingrind-*` staging roots under the active CLI Gradle build
directory before writing the current versioned root, and `:cli:bundleCliArchive` also prunes
obsolete `fingrind-*` archives plus checksum files from both the active distribution directory and
any legacy in-checkout `cli/build/distributions/` leftovers before emitting the current bundle
artifact set. The task now prints the exact archive path and checksum path that it produced under
the active CLI Gradle build directory, so relocated build roots no longer require manual
filesystem searching before the bundle can be inspected or handed to `./scripts/bundle-smoke.sh`.

On Windows PowerShell, use:

```powershell
.\gradlew.bat :cli:bundleCliArchive
.\scripts\bundle-smoke.ps1
```

Source-checkout installed launcher entrypoint:

```bash
./gradlew :cli:installShadowDist prepareManagedSqlite
./scripts/source-checkout-cli.sh help
```

That wrapper resolves the active CLI build directory, then invokes the generated launcher with the
same Java native-access flag as the bundle, the source-checkout runtime-distribution contract, and
managed-SQLite checkout lookup already baked in. The launcher also carries the active root-project
build directory so relocated Gradle build roots resolve the prepared managed SQLite library tree
instead of guessing at `repo/build/...`.

Developer direct-Java entrypoints:

```bash
./gradlew :cli:shadowJar prepareManagedSqlite
./scripts/direct-java-cli.sh help
```

The direct-Java wrapper remains useful for:
- advanced contributor debugging
- validating the application JAR directly during development

That wrapper resolves the active CLI build directory and then runs the prepared application module.
It grants native access only to the `fingrind` module and keeps the same managed-SQLite
auto-discovery path as the generated source-checkout launcher. Manual
`FINGRIND_SQLITE_LIBRARY` export remains the escape hatch only for custom direct-Java launches
that have been moved away from the prepared checkout layout.

The dedicated Docker assembly entrypoint is `./gradlew :cli:stageDockerBuildContext`. It stages
one canonical Docker build-context directory under the active CLI build root, plus a mirrored
checkout-local copy at `cli/build/docker-context/` for plain inspection and manual use. That
context now contains the Dockerfile, the internal application JAR, the runtime-module list, the
rendered Docker entrypoint, the managed-SQLite contract, a generated
`docker-build-context-manifest.json`, and a `source-root/` snapshot of every checked source/build
input the container assembly depends on, so the public bundle and container image consume one
trimmed Java runtime closure and one compile-option owner instead of deriving competing module
sets or private Docker-only contract copies. That same build path resolves runtime-distribution,
public-distribution, storage, and managed-SQLite facts from the protocol-owned contract resources
through
`DistributionContractReader`, which now stays a small facade over dedicated path, JSON, schema,
bundle-layout, host-platform, and text-rendering collaborators, while the shell verifiers read
that same contract through
`scripts/read-contract-values.py`. Bundle metadata, launchers, Docker staging, and operator
verification therefore all consume one canonical runtime-surface owner instead of parallel copied
literal values.
The staged manifest now also records a SHA3-256 fingerprint of the current CLI, contract, core,
executor, report, SQLite, and Gradle build inputs that feed that staged runtime. A plain
`docker build` from that staged context therefore rejects stale assembly inputs instead of
silently packaging an older `fingrind.jar`, entrypoint script, or Dockerfile after local source
edits. Repository-root `docker build .` is intentionally unsupported; the hygiene boundary is the
staged context, not the whole checkout. When Gradle stages into a relocated build root, the same
task mirrors that fresh context back into `cli/build/docker-context/` automatically so manual
checkout-local Docker work sees the same payload Gradle assembled instead of an older leftover
tree under the repository.
The resulting image mirrors the bundle-owned runtime layout as well: `/opt/fingrind/` now
contains `runtime/`, `lib/app/fingrind.jar`, and `lib/native/libsqlite3.so.0` plus its
`.sha256` and `.trusted.sha256` sidecars, and the rendered entrypoint sets
`fingrind.bundle.home` before launch. The published container therefore resolves SQLite through
the same publisher-managed bundle contract as the extracted archive instead of routing through the
operator override path.
The Unix bundle and Docker acceptance entrypoints now also delegate their shared office-worker
workflow through `scripts/release-smoke-support.sh`, whose Bash wrapper now delegates the shared
command/fixture/assertion lifecycle into the single Python owner
`scripts/release-smoke-workflow.py`, while the Windows PowerShell entrypoint stays thin through
`scripts/bundle-smoke-support.ps1` plus a matching office-worker wrapper that delegates to that
same Python owner. Release-surface assertions therefore keep one executable workflow owner instead
of diverging across multiple near-copied entry scripts or collapsing back into a new god-file. The
shared workflow now derives its full fixture layout from the compact environment tuple
`FINGRIND_RELEASE_SMOKE_WORK_ROOT`,
`FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE`, and
`FINGRIND_RELEASE_SMOKE_SCENARIO_ID`, so the Bash bundle verifier, Docker verifier, and Windows
PowerShell verifier no longer re-author dozens of per-path environment variables at the wrapper
seam. The Windows entrypoint also keeps its Unicode workspace-path coverage alive through
`workspace odd/Rīga büro/...`, while the shared Python scenario builder preserves the matching
Unicode nested book/key paths across bundle and container acceptance.
The Bash `release-smoke-*.sh` support files are source-only libraries, not runnable entrypoints:
direct execution now fails fast with an explicit sourced-only error instead of returning a false
green no-op.
`cli/build.gradle.kts` also renders `bundle-manifest.json` through `BundleManifestRenderer` into
`build/generated/bundle/root/` during staging instead of checking a pseudo-JSON source template
into `cli/src/bundle/root/`, so the shipped manifest stays valid JSON derived from the same
canonical contract facts.
The target archive format, launcher path, and native library filename now come from the shared
`bundle-layout-contract.json` resource, and the managed SQLite version, source-id, and required
compile-option pins come from `managed-sqlite-contract.json`, so build logic, bundle metadata, and
shell acceptance do not maintain separate per-platform lookup tables.

It is not the public release artifact.

## Publication Rules

For the GitHub Release publication topology, published-byte attestation rationale, Windows ZIP
canary behavior, and post-tag workflow-repair path, use
[DEVELOPER_RELEASE_PUBLICATION.md](./DEVELOPER_RELEASE_PUBLICATION.md). This document keeps the
distribution contract concise; the publication reference carries the failure theory.

Every GitHub release must publish:
- one archive per supported target (`.tar.gz` for macOS and Linux, `.zip` for Windows)
- one `.sha256` checksum file per supported target archive
- one GitHub artifact attestation per published archive and checksum file, created from the exact bytes downloaded from the published GitHub Release rather than attesting runner-local bundle outputs

Every release must verify:
- the extracted bundle runs without ambient Java
- the extracted bundle runs without a preconfigured `FINGRIND_SQLITE_LIBRARY`
- the extracted bundle contains a top-level human `README.md` and machine-readable
  `bundle-manifest.json`
- the shipped `bundle-manifest.json` points machine clients at the canonical `help`,
  `capabilities`, `print-request-template`, and `print-plan-template` operations instead of
  reauthoring static command-group arrays
- `capabilities` reports the expected managed runtime contract
- the GitHub release object contains the complete bundle-and-checksum set
- the published archive and checksum assets verify through `gh attestation verify` against
  `.github/workflows/release.yml`
- the container workflow waits for the complete GitHub release asset set before it publishes the
  public image, so Docker publication cannot outrun an incomplete release handoff
- the container workflow gives that release-asset wait budget enough runway to outlast the
  slowest supported release bundle build on GitHub-hosted runners, particularly the Intel macOS
  archive path, rather than assuming a few minutes of propagation is always enough
- the container workflow keeps enough timeout budget for post-publish verification of both the
  versioned and `latest` public tags on real GitHub-hosted runners, so a successful registry push
  cannot still end as a red release-surface workflow

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
