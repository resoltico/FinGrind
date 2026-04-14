---
afad: "3.5"
version: "0.9.0"
domain: DEVELOPER_DOCKER
updated: "2026-04-14"
route:
  keywords: [fingrind, docker, docker desktop, docker smoke, check.sh, anonymous docker config, docker context, container]
  questions: ["how should i set up docker for fingrind", "why does fingrind use an anonymous docker config for docker smoke", "what docker runtime is supported for fingrind", "how do i verify docker before running check.sh"]
---

# Docker Workstation Setup

**Purpose**: Codify the supported Docker setup for FinGrind contributors on macOS.
**Prerequisites**: Java 26 and wrapper-based Gradle setup already in place through
[DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md).

Supported workstation shape:
- Docker Desktop installed from Docker's own macOS distribution path on `docker.com`
- the Docker daemon is running and reachable from the current shell
- the active Docker context targets the local Docker Desktop engine
- the repository checkout lives on the Mac's local filesystem

## Canonical Stance

For FinGrind's local container work, the documented standard is:
- Docker comes from Docker Desktop, not from a separate Homebrew-only container-runtime story
- `docker` and the Docker daemon must already work in the current shell before `./check.sh`
- local smoke and release verification must not depend on personal Docker login state
- public-image verification should run through a temporary anonymous `DOCKER_CONFIG` while still
  targeting the active local Docker engine

The repository now enforces that last rule in `scripts/docker-smoke.sh`.

## Why Anonymous Docker Config Matters

FinGrind's Docker smoke and release verification pull only public images. Those operations should
not depend on:
- Docker Desktop credential-helper availability
- a contributor's personal Docker Hub login state
- Docker Desktop plugin and hook behavior in `~/.docker/config.json`

On fresh macOS machines, Docker Desktop's credential helper can stall public metadata fetches even
though the daemon itself is healthy. FinGrind's smoke script therefore uses a temporary empty
`DOCKER_CONFIG` and derives the active engine endpoint from the current Docker context. That keeps
the container-runtime target correct while making public pulls and runs independent from personal
Docker auth state.

## Verification

Before running FinGrind's whole-repo gate, confirm the shell sees a live Docker runtime:

```bash
docker --version
docker context show
docker info --format '{{.ServerVersion}}'
```

Expected local shape on Docker Desktop:
- `docker --version` returns a real Docker CLI version
- `docker info` returns a server version instead of a connection error
- `docker context show` usually prints `desktop-linux`

Then the supported local gates are:

```bash
./scripts/docker-smoke.sh
./check.sh
```

`./check.sh` Stage 5 invokes `scripts/docker-smoke.sh`, which:
- builds the local image from the repository root
- verifies `version`
- verifies the managed SQLite 3.53.0 / SQLite3 Multiple Ciphers 2.3.3 runtime contract through
  `capabilities`
- verifies `open-book` against a mounted path with spaces and punctuation
- verifies `declare-account` and `list-accounts`
- verifies `preflight-entry` and `post-entry` after the explicit Phase 2 lifecycle setup
- verifies the containerized protected-book metadata surface:
  `bookProtectionMode`, `defaultBookCipher`, `requiredSqlite3mcVersion`, and
  `loadedSqlite3mcVersion`
- verifies that reopening the same mounted book with the wrong key fails as `runtime-failure`
  rather than silently reading the file

## Troubleshooting

If Docker verification fails on a fresh machine:
- confirm Docker Desktop is actually running, not only installed
- rerun `docker info` and `docker context show` from the same shell that will run `./check.sh`
- prefer fixing the local Docker runtime over weakening `./check.sh`
- if a public pull still hangs, inspect whether personal Docker config customizations were
  reintroduced into the verification path
