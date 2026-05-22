---
afad: "4.0"
version: "0.44.0"
domain: DEVELOPER_DEVCONTAINER
updated: "2026-05-22"
route:
  keywords: [fingrind, devcontainer, vscode, docker desktop, devcontainer cli, zulu26, contributor container, local repo mount, tooling agnostic]
  questions: ["what is the preferred contributor setup for fingrind", "how do i use the fingrind devcontainer", "does the repo stay on macos when i use the container", "why does fingrind prefer a devcontainer over host java tooling", "is vscode mandatory for fingrind", "how do i use the fingrind devcontainer without vscode"]
---

# Contributor Devcontainer Workflow

**Purpose**: Document FinGrind's preferred contributor workflow from first open through full local
verification.
**Prerequisites**: Docker Desktop running on macOS, a local checkout on the Mac's internal disk,
and either Visual Studio Code with the Dev Containers extension for the preferred integrated path,
the Dev Container CLI for the tooling-agnostic path, or another way to open a shell in the
contributor container.
**Companion references**: [DEVELOPER.md](./DEVELOPER.md),
[DEVELOPER_DOCKER.md](./DEVELOPER_DOCKER.md), [DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md),
[DEVELOPER_JAZZER_OPERATIONS.md](./DEVELOPER_JAZZER_OPERATIONS.md)

## Canonical Stance

FinGrind's preferred contributor path is the committed devcontainer:

- keep the Git checkout on the local macOS filesystem
- bind-mount that checkout into the container
- run Java, Gradle, Jazzer, and repo verification from the container terminal
- run Java and Gradle editor extensions inside the container instead of in the host extension
  process space

VS Code is not mandatory. The canonical owner is the Dev Container Specification surface in
`.devcontainer/`, while the VS Code settings under `customizations.vscode` are only one client
overlay on top of that environment contract. Any editor is acceptable if you are comfortable
managing the contributor container yourself and running the repo commands from a shell inside that
container.

Industry-standard references for this model:

- [Development Containers overview](https://containers.dev/overview)
- [Supporting tools and services](https://containers.dev/supporting)
- [Dev Container CLI](https://code.visualstudio.com/docs/devcontainers/devcontainer-cli)

This is a contributor environment, not the published runtime image. The contributor container is
glibc-based and ships a full Azul Zulu 26 JDK plus verification tooling. The published runtime
image stays a separate minimal execution artifact for released bundles and public container
distribution.

The contributor image also ships `python3` plus `python3 -m pip` so the pinned repo-owned `uv`
launcher can be installed before Gradle resolves the helper-tool manifest in
[`requirements-python-tools.txt`](../requirements-python-tools.txt).

The committed owner files are:

- [../.devcontainer/devcontainer.json](../.devcontainer/devcontainer.json)
- [../.devcontainer/Dockerfile](../.devcontainer/Dockerfile)
- [../scripts/devcontainer-prepare-user-home.sh](../scripts/devcontainer-prepare-user-home.sh)
- [../scripts/validate-devcontainer.sh](../scripts/validate-devcontainer.sh)

## Canonical Owner Versus Client Overlay

The committed devcontainer has two layers:

- portable environment contract:
  `.devcontainer/Dockerfile` plus the non-editor-specific keys in
  `.devcontainer/devcontainer.json`, such as `build`, `features`, `workspaceMount`,
  `workspaceFolder`, `mounts`, `containerEnv`, `postStartCommand`, `remoteUser`, and
  `updateRemoteUserUID`
- VS Code-only overlay:
  `customizations.vscode` inside `.devcontainer/devcontainer.json`

That distinction is why "VS Code is not mandatory" and "`devcontainer.json` exists" are both true
at the same time. The environment contract is tooling-agnostic. The `customizations.vscode`
section only tells one specific editor client how to behave when it connects to that environment.

Humans may use any editor they want. AI agents do not need VS Code either. What both actually need
is access to the contributor container plus the bind-mounted repository files.

## What Runs Where

Host macOS responsibilities:

- stores the Git checkout
- runs Docker Desktop and exposes the local Docker engine
- runs the VS Code UI when you use the preferred integrated workflow

Container responsibilities:

- provides the contributor shell
- owns Java 26, `javac`, Gradle invocation, shell tooling, fonts, and release helpers
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
python3 -m pip --version
docker version --format '{{.Server.Version}}'
./gradlew --version --console=plain
./scripts/validate-devcontainer.sh
```

7. Install the pinned repo-owned `uv` launcher once for the container user before the first root
   `check` run:

```bash
python3 -m pip install --user uv==0.11.15
```

Expected contributor shape:

- `java` and `javac` report Java 26
- Java vendor is Azul Zulu inside the container
- `docker version` reaches the host Docker Desktop engine
- `python3 -m pip` is available for the pinned repo-owned `uv` bootstrap
- `./scripts/validate-devcontainer.sh` succeeds

This remains the most integrated path because the Java, Gradle, and Java-test extensions are all
forced into the container workspace host instead of the macOS host extension process tree.

## Tooling-Agnostic Dev Container CLI Workflow

This is the official non-VS-Code path when you still want the committed devcontainer contract
instead of a hand-maintained `docker run` variant.

Host prerequisites for this path:

- Docker Desktop
- a local checkout on the Mac filesystem
- a shell-visible `devcontainer` CLI

The Dev Container CLI is the reference implementation of the Dev Container Specification. If your
host does not already have it, install it first:

```bash
npm install -g @devcontainers/cli
```

Important truth boundary:

- this tooling-agnostic path still uses the committed `.devcontainer/devcontainer.json`
- the CLI is the client that materializes that spec outside VS Code
- this means the host needs Docker plus the CLI itself
- if you want a lower-level Docker-only path for one narrow task, use the raw Docker workflows
  documented elsewhere, such as the Docker-only Jazzer session in
  [DEVELOPER_JAZZER_OPERATIONS.md](./DEVELOPER_JAZZER_OPERATIONS.md#run-one-docker-only-fuzz-session-from-a-fresh-terminal)

Run these commands in order from the host shell:

1. Change into the repository:

   ```bash
   cd /absolute/path/to/FinGrind
   ```

2. Confirm Docker is running:

   ```bash
   docker info >/dev/null && echo "Docker is running"
   ```

3. Confirm the Dev Container CLI is available:

   ```bash
   devcontainer --version
   ```

4. Ask the CLI to create and start the committed contributor container:

   ```bash
   devcontainer up --workspace-folder .
   ```

   What this does:

   - reads `.devcontainer/devcontainer.json`
   - builds the contributor image from `.devcontainer/Dockerfile`
   - applies the committed docker-outside-of-docker feature
   - bind-mounts the local checkout into `/workspaces/fingrind`
   - creates the named Gradle and general-cache volumes
   - starts the container as the committed `vscode` remote user

5. Run one shell command inside the running contributor container:

   ```bash
   devcontainer exec --workspace-folder . bash -lc 'java --version && javac --version'
   ```

6. Verify Docker access and the repo build from the same containerized shell:

   ```bash
   devcontainer exec --workspace-folder . bash -lc 'docker version --format "{{.Server.Version}}" && ./gradlew --version --console=plain'
   ```

7. Run the repo's own devcontainer validator from that same path:

   ```bash
   devcontainer exec --workspace-folder . ./scripts/validate-devcontainer.sh
   ```

8. Run normal contributor commands through the same CLI surface when you want them:

   ```bash
   devcontainer exec --workspace-folder . ./gradlew check --console=plain
   devcontainer exec --workspace-folder . ./check.sh --console=plain
   devcontainer exec --workspace-folder . ./jazzer/bin/regression --console=plain
   ```

This path is editor-agnostic. You can keep editing the same bind-mounted checkout from any host
editor while the `devcontainer exec` commands run inside the committed contributor environment.

## Editor-Agnostic Container Use

If you do not want VS Code, the underlying contributor model is still valid:

- keep the repository on the host filesystem
- materialize the committed devcontainer from `.devcontainer/`
- get a shell inside that container
- run `./gradlew`, `./check.sh`, `./scripts/docker-smoke.sh`, and `jazzer/bin/*` there
- edit the same bind-mounted files from any host editor you like

What you lose outside VS Code is the committed editor-in-container routing for the Java, Gradle,
and Java-test extensions. The build and fuzz tooling do not depend on those extensions; the
preferred VS Code path exists because it keeps that tooling out of the host process tree.

Two boundary rules matter here:

- `jazzer/bin/*`, `./gradlew`, and `./check.sh` do not auto-enter Docker on your behalf
- the full contributor environment is defined by both `.devcontainer/Dockerfile` and
  `.devcontainer/devcontainer.json`

That second rule matters because the contributor setup depends on the devcontainer feature wiring
in `devcontainer.json`, especially Docker access through the
ghcr.io/devcontainers/features/docker-outside-of-docker:1 feature entry. A plain `docker build` of
`.devcontainer/Dockerfile` is not, by itself, the entire documented contributor environment.

So the truthful editor-agnostic rule is:

- use a devcontainer-spec-aware launcher if you want the same environment without VS Code
- or reproduce the `devcontainer.json` mounts, features, and runtime contract manually and accept
  that you are now maintaining that local variant yourself

The repository now standardizes one exact non-VS-Code launcher family too:

- `devcontainer up --workspace-folder .`
- `devcontainer exec --workspace-folder . <command ...>`

One practical consequence of that boundary: if you start the contributor image with a plain
`docker run` and mount named volumes directly onto `/home/vscode/.gradle` or `/home/vscode/.cache`,
those mounts can come up owned by `root` rather than the `vscode` user. In that shape the Gradle
wrapper fails before normal repo commands start. For ad hoc terminal-only work, prefer a writable
temporary `HOME` and `GRADLE_USER_HOME` inside the container unless you are using a
devcontainer-spec-aware launcher that applies the full runtime contract for you.

The committed devcontainer now also runs `./scripts/devcontainer-prepare-user-home.sh` on start so
the named cache volumes stay writable for the remote user even if an earlier ad hoc container run
poisoned them with root-owned entries.

If your immediate goal is "run one real fuzzing session from a plain terminal with Docker and no
host Java tooling", use the numbered noob-safe walkthrough in
[DEVELOPER_JAZZER_OPERATIONS.md](./DEVELOPER_JAZZER_OPERATIONS.md#run-one-docker-only-fuzz-session-from-a-fresh-terminal).

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

- uses a pinned glibc-based devcontainer base image because remote extensions are more reliable
  there than in Alpine-based environments
- installs a pinned Zulu 26 JDK so contributor Java matches the CI vendor and major version
- routes Docker access through the official docker-outside-of-docker devcontainer feature, so the
  contributor shell talks to the host engine instead of starting a nested daemon

The VS Code settings in `devcontainer.json` also force the Java, Gradle, and Java-test extensions
into the workspace/container extension host. That is intentional: Java tooling should live next to
the repo toolchain, not in the host plugin process tree.

## Host-Native Fallback

Host-native Java work is still supported, and the repository docs keep that path accurate. Use it
when you explicitly want local-shell Java. The devcontainer remains the preferred path because it
reduces toolchain drift and isolates host-side interference.

If you stay host-native, follow [DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md) and
[DEVELOPER_DOCKER.md](./DEVELOPER_DOCKER.md) exactly.

## CI Gate Behavior

The `devcontainer` CI job fires only when devcontainer-relevant files change. The path set that
triggers it:

- `.devcontainer/` — the Dockerfile and `devcontainer.json`
- `scripts/validate-devcontainer.sh`
- `scripts/devcontainer-prepare-user-home.sh`
- `scripts/repo-verification-lock-support.sh`
- `scripts/python-runtime-support.sh`

PRs that touch only application code, documentation, or tests do not trigger the devcontainer
gate. The `check`, `windows-bundle-smoke`, and `docker-smoke` jobs already prove the code builds
and tests pass; the devcontainer gate proves the contributor environment. Running the full Docker
build-and-validate cycle for a change that cannot affect the environment wastes 15-20 minutes per
run.

The `devcontainer` job no longer depends on `check`. The devcontainer environment is orthogonal to
code correctness: it should be proven whenever its files change regardless of whether the
application gate passes.

When the gate is skipped, the aggregate `Gate` required-status job still succeeds — a skipped
devcontainer gate is a correct, intended outcome, not a coverage gap. Only a *failed* or
*cancelled* gate prevents merge. `Gate` uses `if: always()` with explicit
`${{ toJSON(needs.*.result) }}` inspection so a skipped job does not block it from being reported.

When the gate fires, CI runs `./scripts/validate-devcontainer.sh` for the full image and contract
proof.

If you change any of the files in the trigger path above, run the validator from the host before
pushing:

```bash
./scripts/validate-devcontainer.sh
```

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
