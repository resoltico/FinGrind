---
afad: "4.0"
version: "0.57.0"
domain: DEVELOPER_DISTRIBUTION
updated: "2026-06-19"
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
- the canonical target launcher declared by `bundle-layout-contract.json`:
  `bin/fingrind` on Unix targets and `bin/fingrind.ps1` on `windows-x86_64`
- a private Java 26 runtime image built with `jlink`
- the FinGrind application JAR
- the managed SQLite native library pinned by the canonical managed-SQLite contract for that target
- a top-level `README.md` for local operator bootstrap
- a top-level `quick-start-request.json` for the first posting flow
- a top-level generated `bundle-manifest.json` for machine bootstrap and target discovery
- top-level legal files: `LICENSE`, `LICENSE-APACHE-2.0`, `LICENSE-SIL-OFL-1.1`,
  `LICENSE-SQLITE3MULTIPLECIPHERS`, `NOTICE`, and `PATENTS.md`

The bundle launcher sets `fingrind.bundle.home` and starts the private runtime directly.
That keeps public execution independent from:
- a separately installed Java runtime
- any inherited `FINGRIND_SQLITE_LIBRARY` override
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

Current published public bundle targets:
- `macos-aarch64`
- `macos-x86_64`
- `linux-x86_64`
- `linux-aarch64`
- `windows-x86_64`

Declared but intentionally not-published public bundle targets:
- `windows-aarch64`

Linux bundle policy:
- public Linux bundles are built on Ubuntu GitHub-hosted runners
- the public compatibility contract is not inferred from the builder image alone; it is declared
  in `bundle-layout-contract.json`, emitted into `bundle-manifest.json`, and rendered into the
  public install docs
- the current published Linux targets require glibc `2.34` or newer
- every published Linux bundle is re-smoked twice before release promotion: once on the native
  GitHub-hosted runner and once inside the contract-declared Rocky Linux 9 compatibility-floor
  container that represents the minimum supported glibc surface
- they are not claimed to be one universal binary for every Linux libc variant

macOS and Windows publication policy:
- the bundle-layout contract keeps macOS and Windows target identities explicit, while
  `bundle-publication-contract.json` owns the per-target publication status plus the proving
  runner metadata
- macOS bundles are published for both Apple Silicon and Intel hosts
- `windows-x86_64` is a published PowerShell-first bundle surface; there is no parallel `.cmd`
  shim
- `windows-aarch64` remains the single declared but intentionally not-published classifier
- the repository publishes unsigned macOS and Windows bundles and relies on checksum plus GitHub
  attestation for provenance rather than certificate trust

## Release Build Policy

Release automation currently uses `actions/setup-java` with `distribution: zulu`.

Why that is acceptable:
- the release workflow already relies on GitHub-hosted provisioning, not a hand-maintained local
  release workstation
- Zulu 26 on GitHub-hosted runners provides the full JDK surface we actually need:
  `javac`, `jdeps`, and `jlink`
- the published release matrix is covered by those runners today: macOS arm64, macOS x86_64,
  Ubuntu x86_64, Ubuntu arm64, and Windows x86_64

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
- module discovery must fail loud on unresolved runtime dependencies; if `jdeps` can only finish
  with `--ignore-missing-deps`, those missing classes must first be proven against the
  repo-owned `runtime-module-discovery-contract.json` allowlist instead of being ignored blindly
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
  `environment.runtime.runtimeDistribution = "container-image"`
- the bundle remains the canonical public CLI artifact; the container is an additional supported
  public runtime surface, not a weaker or differently pinned path

## Local Build Surface

Bundle entrypoints:

```bash
./gradlew :cli:bundleCliArchive
./scripts/bundle-smoke.sh
./scripts/bundle-smoke.sh --execution-surface compatibility-floor
```

Local bundle restaging prunes older `fingrind-*` staging roots under the active CLI Gradle build
directory before writing the current versioned root, and `:cli:bundleCliArchive` also prunes
obsolete `fingrind-*` archives plus checksum files from both the active distribution directory and
any legacy in-checkout `cli/build/distributions/` leftovers before emitting the current bundle
artifact set. The task now prints the exact archive path and checksum path that it produced under
the active CLI Gradle build directory, so relocated build roots no longer require manual
filesystem searching before the bundle can be inspected or handed to `./scripts/bundle-smoke.sh`.
On Linux hosts with Docker available, `--execution-surface compatibility-floor` reruns the same
office-worker acceptance workflow inside the contract-declared minimum-glibc container instead of
the live shell, which is the same proof surface used by CI, the weekly freshness canary, and the
tagged release workflow.

On Windows PowerShell, use:

```powershell
.\gradlew.bat :cli:bundleCliArchive
.\scripts\bundle-smoke.ps1
```

Source-checkout wrapper entrypoint:

```bash
./scripts/source-checkout-cli.sh help
```

That wrapper resolves the active CLI build directory, then launches the raw application module
through the same Gradle-owned Java 26 toolchain executable that bundle packaging uses. It carries
the same Java native-access flag as the bundle, the source-checkout runtime-distribution contract,
and managed-SQLite checkout lookup already baked in. The wrapper also carries the active
root-project build directory so relocated Gradle build roots resolve the prepared managed SQLite
library tree instead of guessing at `repo/build/...`. Before each launch it delegates freshness
back to Gradle by refreshing `:cli:writeSourceCheckoutRuntimeManifest` plus `prepareManagedSqlite`,
then executes through the generated runtime manifest instead of replaying source hashing in shell.

Developer direct-Java entrypoints:

```bash
./scripts/direct-java-cli.sh help
```

The direct-Java wrapper remains useful for:
- advanced contributor debugging
- validating the application JAR directly during development

That wrapper resolves the active CLI build directory and then runs the prepared application module
through the same Gradle-owned Java 26 toolchain executable as the source-checkout wrapper. It
grants native access only to the `fingrind` module and keeps the same managed-SQLite
auto-discovery path as the source-checkout wrapper. Launches moved away from the prepared checkout
layout are unsupported rather than repaired through ambient native-library overrides. The wrapper
uses the same source-hash and runtime-manifest guards as the source-checkout wrapper, so stale
raw-JAR bytes or stale toolchain metadata are refreshed from the current checkout before direct
execution.

The dedicated Docker assembly entrypoint is `./gradlew :cli:stageDockerBuildContext`. It stages
one canonical Docker build-context directory under the active CLI build root. That context now
contains the Dockerfile, the internal application JAR, the runtime-module list, the rendered
Docker entrypoint, the managed-SQLite shared library plus its checksum/toolchain/build-contract
provenance files, a generated `docker-build-context-manifest.json`, and a `source-root/`
snapshot of every checked source/build input the container assembly depends on, so the public
bundle and container image consume one trimmed Java runtime closure and one native-SQLite owner
instead of deriving competing module sets or Docker-only native build pipelines. That same build
path resolves runtime-distribution, public-distribution, storage, and managed-SQLite facts from
the protocol-owned contract resources through
`DistributionContractReader`, which now stays a small facade over dedicated path, JSON, schema,
bundle-layout, host-platform, and text-rendering collaborators, while the shell verifiers read
that same contract through
`scripts/read-contract-values.py`. Bundle metadata, launchers, Docker staging, and operator
verification therefore all consume one canonical runtime-surface owner instead of parallel copied
literal values.
When the workstation is not Linux, that staging task now materializes a Linux-target managed
SQLite artifact through a pinned Buildx builder image before the Docker build starts, so the
staged context never mislabels a host-native macOS or Windows library as the container runtime.
The staged manifest now also records a SHA3-256 fingerprint of the current CLI, contract, core,
executor, report, SQLite, and Gradle build inputs that feed that staged runtime. A plain
`docker build` from that staged context therefore rejects stale assembly inputs instead of
silently packaging an older `fingrind.jar`, entrypoint script, or Dockerfile after local source
edits. Repository-root `docker build .` is intentionally unsupported; the hygiene boundary is the
staged context, not the whole checkout.
When the active CLI build root lives outside the checkout, `:cli:stageDockerBuildContext` also
quarantines any lingering legacy `cli/build/docker-context` tree before it stages the current
context, so the old checkout-local path cannot masquerade as the live container assembly input.
The resulting image mirrors the bundle-owned runtime layout as well: `/opt/fingrind/` now
contains `runtime/`, `lib/app/fingrind.jar`, and `lib/native/libsqlite3.so.0` plus its
`.sha256` sidecar, and the rendered entrypoint sets
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

For the GitHub Release publication topology, published-byte attestation rationale, Windows
publication-lane canary behavior, and post-tag workflow-repair path, use
[DEVELOPER_RELEASE_PUBLICATION.md](./DEVELOPER_RELEASE_PUBLICATION.md). This document keeps the
distribution contract concise; the publication reference carries the failure theory.

Every GitHub release must publish:
- one archive per published Linux target (`.tar.gz`)
- one `.sha256` checksum file per supported target archive
- one GitHub artifact attestation per published archive and checksum file, created from the exact bytes downloaded from the published GitHub Release rather than attesting runner-local bundle outputs

Every release must verify:
- the extracted bundle runs without ambient Java
- the extracted bundle ignores any inherited `FINGRIND_SQLITE_LIBRARY` override
- the extracted bundle contains a top-level operator `README.md` and machine-readable
  `bundle-manifest.json` plus `quick-start-request.json`
- the shipped `bundle-manifest.json` points machine clients at the canonical `help`,
  `capabilities`, `print-request-template`, and `print-plan-template` operations instead of
  reauthoring static command-group arrays
- `capabilities` reports the expected managed runtime contract
- the GitHub release object contains the complete bundle-and-checksum set
- the published archive and checksum assets verify through `gh attestation verify` against
  `.github/workflows/release.yml`
- the release workflow's staged-container and promotion jobs wait for the complete verified draft
  GitHub release asset set before they publish the public image, so Docker publication cannot
  outrun an incomplete release handoff
- the staged-container and promotion jobs keep enough timeout budget for post-publish verification
  of the versioned public tag, and of `latest` when the release owns that pointer, on real
  GitHub-hosted runners, so a successful registry push cannot still end as a red release-surface
  workflow

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
