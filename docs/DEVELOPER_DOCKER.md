---
afad: "5.0.1"
version: "0.62.2"
domain: DEVELOPER_DOCKER
updated: "2026-08-09"
route:
  keywords: [fingrind, docker, docker desktop, docker smoke, check.sh, anonymous docker config, docker context, container, devcontainer]
  questions: ["how should i set up docker for fingrind", "why does fingrind use an anonymous docker config for docker smoke", "what docker runtime is supported for fingrind", "how do i verify docker before running check.sh", "how is the contributor devcontainer different from the runtime container"]
---

# Docker Runtime And Workstation Setup

**Purpose**: Codify the supported Docker setup for FinGrind contributors on macOS and distinguish
the contributor devcontainer from the published runtime container.
**Prerequisites**: Java 26 and wrapper-based Gradle setup already in place through
[DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md), or the preferred contributor devcontainer path through
[DEVELOPER_DEVCONTAINER.md](./DEVELOPER_DEVCONTAINER.md).

Supported workstation shape:
- Docker Desktop installed from Docker's own macOS distribution path on `docker.com`
- the Docker daemon is running and reachable from the current shell
- the `docker buildx` plugin is available in the current shell
- the active Docker context targets the local Docker Desktop engine
- the repository checkout lives on the Mac's local filesystem
- the committed contributor environment is preferably entered through the Dev Container Spec path
  documented in [DEVELOPER_DEVCONTAINER.md](./DEVELOPER_DEVCONTAINER.md), with raw `docker run`
  reserved for lower-level targeted tasks such as the Docker-only Jazzer walkthrough

## Canonical Stance

For FinGrind's local container work, the documented standard is:
- the preferred contributor workflow is the committed devcontainer documented in
  [DEVELOPER_DEVCONTAINER.md](./DEVELOPER_DEVCONTAINER.md)
- Docker comes from Docker Desktop, not from a separate Homebrew-only container-runtime story
- `docker` and the Docker daemon must already work in the current shell before `./check.sh`
- `docker buildx` is required; the smoke gate uses `docker buildx build --load`, not Docker's
  deprecated legacy builder path
- local smoke and release verification must not depend on personal Docker login state
- public-image verification should run through a temporary anonymous `DOCKER_CONFIG` while still
  targeting the active local Docker engine
- any temporary secret-bearing fixture files created by smoke scripts must obey the same
  filesystem-security contract as production, not a weakened test-only variant

The repository now enforces these Docker-runtime rules in `scripts/docker-smoke.sh`.

This Docker runtime guidance is separate from the contributor devcontainer:
- the devcontainer is a glibc-based contributor shell with the exact Zulu 26.0.2 JDK and editor tooling
- the published runtime container is the public execution artifact verified by Docker smoke and
  release publication checks
- VS Code is only one client for that contributor environment; the repo also documents an official
  tooling-agnostic `devcontainer` CLI path in [DEVELOPER_DEVCONTAINER.md](./DEVELOPER_DEVCONTAINER.md)

The container image itself also stays on the same managed-runtime policy as the bundle archives:
- it verifies the pinned vendored SQLite3MC source hash during image build before compiling the
  native library
- it pins both Docker base images by digest and installs exact Alpine package revisions for the
  builder and runtime layers, so container assembly does not float forward on mutable tags or
  repository package updates
- it consumes a Linux-target managed SQLite artifact from the same Gradle-owned managed-SQLite
  pipeline that owns bundle artifacts, together with its checksum, toolchain fingerprint, and
  build contract, so Docker does not carry a second native build pipeline
- on Linux hosts that Docker-target artifact reuses the native host build directly; on non-Linux
  hosts `:cli:stageDockerBuildContext` materializes the Linux target through one pinned anonymous
  compiler container before Docker image assembly
- it assembles and ships a private `jlink` runtime instead of inheriting a full general-purpose
  JRE layer
- it consumes one repository-built staged Docker context produced by
  `:cli:stageDockerBuildContext`; that staged directory lives under the active CLI build root
- that staged context carries the Dockerfile, the internal application JAR, the runtime-module
  list, the rendered entrypoint, `libsqlite3.so.0`, `libsqlite3.so.0.sha256`,
  `toolchain-fingerprint.json`, `build-contract.json`, `docker-build-context-manifest.json`, and
  a `source-root/` snapshot of every checked source/build input the container assembly depends on
- that manifest includes a SHA3-256 fingerprint of the current source/build inputs behind the
  staged runtime, and both Docker image build and `scripts/docker-smoke.sh` verify that
  fingerprint before trusting the staged context, so Docker and bundle publication cannot drift
  onto competing private-runtime closures, private compile-option inputs, or stale checkout-local
  leftovers
- if an older checkout-local `cli/build/docker-context` tree lingers after the active Gradle build
  root moved elsewhere, `:cli:stageDockerBuildContext` quarantines that stale tree before staging
  the current context so the old path cannot be mistaken for the supported container assembly input
- it generates the Docker entrypoint and verifies the image's runtime-surface disclosure from the
  same protocol-owned contract resources that drive bundle metadata, so the container does not
  carry a parallel handwritten runtime-distribution or storage contract
- it sets `fingrind.runtime.distribution=container-image` so `capabilities` discloses the active
  distribution surface explicitly

## Why Anonymous Docker Config Matters

FinGrind's Docker-target Linux artifact preparation plus Docker smoke and release verification pull
only public images. Those operations should not depend on:
- Docker Desktop credential-helper availability
- a contributor's personal Docker Hub login state
- Docker Desktop plugin and hook behavior in `~/.docker/config.json`

On fresh macOS machines, Docker Desktop's credential helper can stall public metadata fetches even
though the daemon itself is healthy. FinGrind therefore uses a temporary empty `DOCKER_CONFIG`
for both the non-Linux managed-SQLite compiler container and the smoke script's public image build
and run path. The smoke script additionally derives the active engine endpoint from the current
Docker context and, only if that empty config would hide Buildx, stages an already-installed host
`docker-buildx` plugin into the anonymous config. On macOS that plugin often comes from Docker
Desktop; on CI or other hosts it may come from a system CLI-plugin directory. That keeps the
container-runtime target correct while making public pulls and runs independent from personal
Docker auth state without falling back to Docker's deprecated legacy builder path.

## Verification

Before running FinGrind's whole-repo gate, confirm the shell sees a live Docker runtime:

```bash
docker --version
docker buildx version
docker context show
docker info --format '{{.ServerVersion}}'
```

Expected local shape on Docker Desktop:
- `docker --version` returns a real Docker CLI version
- `docker buildx version` returns a real Buildx version
- `docker info` returns a server version instead of a connection error
- `docker context show` usually prints `desktop-linux`

Then the supported local gates are:

```bash
./scripts/docker-smoke.sh
./check.sh
```

`./check.sh` Stage 6 invokes `scripts/docker-smoke.sh`, which:
- refreshes `:cli:stageDockerBuildContext` first, verifies the staged context against the live
  repository copy, and then builds the local image from that staged context through
  `docker buildx build --load`
- treats the staged Docker context as the only supported container-assembly boundary; a
  repository-root `docker build .` is intentionally unsupported because it reopens unrelated local
  state and sibling build outputs
- stages its mounted-workspace scratch tree under system temp instead of the repository root so
  SMB/WebDAV tombstones left behind by container cleanup cannot break later source-checkout gates
- verifies that the image's private runtime stays trimmed and does not drag in `jdk.jdeps`,
  `jdk.jlink`, or `jdk.jpackage`
- runs mounted-path container commands under the caller's UID:GID so generated key files and book
  files stay owned by the invoking operator on both macOS Docker Desktop and Linux CI runners
- binds Java's `user.home` to the physical mounted working directory, because a caller UID need
  not exist in the image's passwd database; protected-book operations therefore create their
  owner-only `.fingrind-coordination-v4` controls beside the mounted work rather than attempting
  an unsafe fallback beneath `/`
- makes the public-container verifier's mounted report root its container working directory as
  well as its `/work` volume, so the entrypoint's `user.home` always names an owner-writable
  coordination location during the real mounted-book and PDF proof
- verifies `version`
- verifies the managed SQLite 3.53.4 / SQLite3 Multiple Ciphers 2.4.0 runtime contract through
  `capabilities`
- verifies `open-book` against a mounted path with spaces and punctuation
- creates the mounted book-key fixtures with owner-only permissions (`0600`) so containerized
  verification matches the real protected-book contract
- verifies `declare-account` and `list-accounts`
- verifies `preflight-entry` and `post-entry` after the explicit second lifecycle setup
- verifies the containerized protected-book metadata surface:
  `bookProtectionMode`, `defaultProtectedBookFormat.cipher`,
  `defaultProtectedBookFormat.pageSize`, `requiredSqlite3mcVersion`, and
  `loadedSqlite3mcVersion`
- verifies that reopening the same mounted book with the wrong key fails as the deterministic
  `protected-book-verification-failed` error rather than silently reading the file or leaking raw
  SQLite storage symptoms

The tag-driven `Release` workflow's staged-container and promotion jobs also wait for the complete
verified draft GitHub release asset set before image publication, so the public image cannot race
ahead of an incomplete bundle release.

## Troubleshooting

If Docker verification fails on a fresh machine:
- confirm Docker Desktop is actually running, not only installed
- rerun `docker info` and `docker context show` from the same shell that will run `./check.sh`
- prefer fixing the local Docker runtime over weakening `./check.sh`
- if a public pull still hangs, inspect whether personal Docker config customizations were
  reintroduced into the verification path
- if a mounted protected-book operation fails unexpectedly, inspect the mounted key-file
  permissions before changing the runtime policy
