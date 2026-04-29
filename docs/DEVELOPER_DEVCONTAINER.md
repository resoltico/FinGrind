---
afad: "3.5"
version: "0.29.0"
domain: DEVELOPER_DEVCONTAINER
updated: "2026-04-29"
route:
  keywords: [fingrind, devcontainer, vscode, docker desktop, zulu26, contributor container, local repo mount]
  questions: ["what is the preferred contributor setup for fingrind", "how do i use the fingrind devcontainer", "does the repo stay on macos when i use the container", "why does fingrind prefer a devcontainer over host java tooling"]
---

# Contributor Devcontainer Workflow

**Purpose**: Document FinGrind's preferred contributor workflow from first open through full local
verification.
**Prerequisites**: Docker Desktop running on macOS, a local checkout on the Mac's internal disk,
and Visual Studio Code with the Dev Containers extension available.
**Companion references**: [DEVELOPER.md](./DEVELOPER.md),
[DEVELOPER_DOCKER.md](./DEVELOPER_DOCKER.md), [DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md)

## Canonical Stance

FinGrind's preferred contributor path is the committed devcontainer:

- keep the Git checkout on the local macOS filesystem
- bind-mount that checkout into the container
- run Java, Gradle, Jazzer, bundle verification, and Docker verification from the container terminal
- run Java and Gradle editor extensions inside the container instead of in the host extension
  process space

This is a contributor environment, not the published runtime image. The contributor container is
glibc-based and ships a full Azul Zulu 26 JDK plus verification tooling. The published runtime
image stays a separate minimal execution artifact for released bundles and public container
distribution.

The committed owner files are:

- [../.devcontainer/devcontainer.json](../.devcontainer/devcontainer.json)
- [../.devcontainer/Dockerfile](../.devcontainer/Dockerfile)
- [../scripts/validate-devcontainer.sh](../scripts/validate-devcontainer.sh)

## What Runs Where

Host macOS responsibilities:

- stores the Git checkout
- runs Docker Desktop and exposes the local Docker engine
- runs the VS Code UI

Container responsibilities:

- provides the contributor shell
- owns Java 26, `javac`, Gradle invocation, shell tooling, and release helpers
- hosts the Java language server and Gradle extension when the workspace is opened in-container

This split is the reason the devcontainer is the preferred path for this repository. The host no
longer needs to share the same Java extension daemons and attach tooling with the repository build.

## First Open

1. Clone or move the repository onto the Mac's local filesystem.
2. Start Docker Desktop and confirm `docker info` succeeds on the host.
3. Open the repository in VS Code.
4. Run `Dev Containers: Reopen in Container`.
5. Wait for the initial build to finish.
6. Open a container terminal and verify:

```bash
java --version
javac --version
docker version --format '{{.Server.Version}}'
./gradlew --version --console=plain
./scripts/validate-devcontainer.sh
```

Expected contributor shape:

- `java` and `javac` report Java 26
- Java vendor is Azul Zulu inside the container
- `docker version` reaches the host Docker Desktop engine
- `./scripts/validate-devcontainer.sh` succeeds

## Day-To-Day Workflow

Run normal contributor commands inside the container terminal:

```bash
./gradlew test --console=plain
./gradlew check --console=plain
./check.sh
./scripts/docker-smoke.sh
./scripts/validate-devcontainer.sh
```

The repository files still live on the host, so edits made in the container are the same tracked
files you see from macOS. The devcontainer keeps Gradle and general Java caches in named Docker
volumes instead of bind-mounted host dot-directories, which reduces churn in the checkout and
keeps tool caches out of the host editor's process space.

## Under The Hood

The committed setup intentionally does three things:

- uses a pinned glibc-based devcontainer base image because VS Code remote extensions are more
  reliable there than in Alpine-based environments
- installs a pinned Zulu 26 JDK so contributor Java matches the CI vendor and major version
- routes Docker access through the official docker-outside-of-docker devcontainer feature, so
  the contributor shell talks to the host engine instead of starting a nested daemon

The VS Code settings in `devcontainer.json` also force the Java, Gradle, and Java-test extensions
into the workspace/container extension host. That is intentional: Java tooling should live next to
the repo toolchain, not in the host plugin process tree.

## Host-Native Fallback

Host-native Java work is still supported, and the repository docs keep that path accurate. Use it
when you explicitly want local-shell Java. The devcontainer remains the preferred path because it
reduces toolchain drift and isolates host-side interference.

If you stay host-native, follow [DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md) and
[DEVELOPER_DOCKER.md](./DEVELOPER_DOCKER.md) exactly.

## Troubleshooting

If the container opens but Java tooling still appears to run on the host:

- confirm you reopened the folder in the container rather than merely attaching a terminal
- confirm the Java-related extensions listed in `devcontainer.json` are installed in the container
  workspace
- rebuild the container after significant `.devcontainer/` edits

If Docker commands fail inside the container:

- confirm Docker Desktop is running on the host
- confirm the host shell can run `docker info`
- rebuild the container if the Docker feature changed

If the repository still behaves better host-native for one narrow task, that is a valid local
escape hatch. The documented standard remains the committed devcontainer for normal contributor
work and full verification.
