---
afad: "5.0.1"
version: "0.64.0"
domain: OPERATIONS
updated: "2026-09-01"
route:
  keywords: [fingrind, ci, github-actions, windows, powershell, mutation-testing, pitest, bundle smoke, devcontainer, gate]
  questions: ["when does the native windows bundle proof start", "what does the local windows contract preflight prove", "how does mutation testing run in ci", "why does the devcontainer gate skip"]
---

# CI Workflow Reference

**Purpose**: Explain the CI-owned contributor-environment gate and its relationship to the aggregate `Gate` check.

## Native Windows Feedback

The native `windows-2022` published-bundle proof is a release-blocking authority, but it is not an
output of the Linux `check` job. `published-bundle-smoke` owns a literal, reviewed workflow matrix
of the admitted target/runner pairs; it does not consume plan or target-checkout data to decide
`runs-on`. The target contracts and release plan carry no runner label, so publication data cannot
select a self-hosted or arbitrary runner. Every `published-bundle-smoke` row starts immediately.
The jobs use read-only Gradle caching and exchange no build artifact, so waiting for `check` would
only delay independent feedback.

`Gate` still requires `check`, every published bundle-smoke row, wrapper validation, and the
devcontainer pair. Parallel scheduling changes when native Windows feedback arrives; it never
makes that proof optional or lets a green Linux check stand in for it.

CI and release use the same repository-owned
`scripts/verify-windows-publication-surface.ps1` adapter for the native Windows proof. It proves
the actual runner identity, target build-logic tests, the attestation codec, both managed-SQLite
runtime surfaces, the self-contained bundle build, and the bundle smoke path as one execution
contract. The adapter derives the only acceptable archive and checksum locations from the target
repository's version and bundle-layout contract:
`cli/build/distributions/fingrind-<version>-windows-x86_64.zip` and its `.sha256` companion. It
rejects a manifest that names any other, external, relative, or reparse-point-traversing path
before that path can become an attestation or release-upload subject.

For bundle smoke it gives `TEMP` and `TMP` one fresh owner-only directory on the protected system
volume, rather than an inherited workspace directory, so the bundled managed SQLite runtime can
create its own verified private snapshot child.

For ordinary CI, the workflow and target roots are the same checkout. A release rerun deliberately
has two roots: its repaired `main` checkout owns release-control policy, while the explicit target
root remains the immutable tag checkout. The adapter uses the helper root only for that
release-control policy, runner-identity normalization, and checksum-verified pinned-runtime
bootstrap; its Gradle wrapper, Gradle support, runtime verifiers, bundle-smoke script, version,
bundle contract, manifest, archive, and checksum all come from the target root. A post-tag workflow
repair can therefore improve publication control without silently substituting repaired source for
the released payload. The Prepare release publication job resolves that helper checkout to one full commit ID and
passes the ID to every later rerun job; a release never mixes helpers from different advancing
`main` revisions.

Before invoking any repository-owned Windows PowerShell surface, the workflow provisions the exact
PowerShell `7.6.5` release pinned in `gradle/fingrind-build.properties`, verifies the
provisioned binary, places its directory on the following-step `PATH`, and exports its full path
explicitly. The workflow launches every native Windows proof owner through that explicit executable,
and the adapter passes it to its child runtime and smoke proofs rather than accepting an ambient
`pwsh` or a legacy `powershell.exe` fallback. The provisioner treats the installed runtime only as
a replaceable derivative: it re-hashes a retained exact release archive into private staging and
reconstructs the executable tree on every provisioning invocation, so a version-correct but altered cached runtime
is never trusted. The hosted runner's PowerShell is used only for bootstrap and the non-executing
failure-evidence writer; it never substitutes for the pinned runtime in a native proof owner.

On macOS or Linux, run the mandatory local preflight from a Git worktree. It resolves the exact
Python release pinned by the repository and uses the exact PowerShell `7.6.5` release pinned in
[`gradle/fingrind-build.properties`](../gradle/fingrind-build.properties). Provision it once into
a repository-local temporary directory, then pass the resulting immutable executable to the
preflight:

```bash
source scripts/python-runtime-support.sh
prepare_python_runtime_env
pwsh_executable="$(
  "$FINGRIND_PYTHON_EXECUTABLE" scripts/provision-powershell-runtime.py \
    --install-root "$PWD/tmp/fingrind-powershell"
)"
FINGRIND_PWSH_EXECUTABLE="$pwsh_executable" ./scripts/check-windows-contract.sh
```

That preflight runs the cross-platform PowerShell and contract regressions selected by
`check-stage-contract.sh`, including the Windows bundle launcher, Gradle wrapper helpers,
PowerShell owner, and batch adapter, MSVC environment plan, Defender helper, and failure-evidence
owner. It also proves the pure Kotlin Windows bundle-manifest, staging-layout, and MSVC
command-vector contracts through the normal build-logic tests. It then invokes
`./gradlew :cli:verifyTargetBundleLayout -PfingrindVerificationTargetClassifier=windows-x86_64`
to materialize a sentinel-only Windows layout inside Gradle task-temporary storage and run the
canonical bundle verifier in structural mode. That check validates the rendered launcher,
manifest, documentation, native checksum and provenance paths, and application module identity for
the declared Windows target without creating an archive, staging a public bundle, executing Java,
or loading a DLL.
Its synthetic target fixture also executes the shared Windows-publication policy: canonical
artifact-plan derivation, manifest equivalence, target containment, reparse-point refusal, and
safe workflow-output creation.
The provisioner accepts only the versioned GitHub release asset selected for the supported Linux,
macOS, or Windows host architecture, verifies its canonical SHA-256, rejects unsafe archive members, and
publishes it only after the executable reports the metadata-owned exact version. It retains a
checksum-revalidated archive cache for offline reuse but always reconstructs the executable tree
from a private verified snapshot; unsafe links, reparse points, hard links, or altered cached trees
fail closed or are replaced from that snapshot. The preflight does not download a runtime
implicitly: local provisioning is deliberate, visible, and reusable.

One transport timeout, connection failure, or non-HTTP `URL` failure with an operating-system
transport cause does not make a hosted proof non-deterministic: provisioning discards its
incomplete private archive and retries the immutable download at most twice after one- and
two-second waits. HTTP-status, non-transport URL, size, checksum, archive, and binary-version
failures remain single-attempt, fail-closed admission failures; a retry never makes an unverified
byte eligible for cache or runtime publication.
It rejects a missing or differently-versioned `pwsh` rather than silently skipping PowerShell
execution, then makes the supplied exact executable first on `PATH` for every subcheck. The
Windows Gradle adapter uses `pwsh` and has no `powershell.exe` fallback.

The preflight derives its AST source set from every tracked or in-flight, nonignored `*.ps1` file,
not from a second hand-maintained PowerShell list. It derives two further absolute-path inventories
from that same Git result: every non-`*.Tests.ps1` file is analyzed as production PowerShell, while
every `*.Tests.ps1` file is Pester input. A new owned surface therefore cannot bypass parsing,
analysis, or the relevant test inventory merely by being in flight rather than committed.

After the exact `pwsh` runtime is supplied, the preflight provisions only Pester `6.1.0`
(`0207a75ea09f81b27c1ded44898b2bb3c845bafa02045bd64a39e26a53ca41b4`) and PSScriptAnalyzer
`1.25.0` (`14e634c828eb98efb9f40b2918ba90f139ed5eccdf663a2a747736d996995d60`) from their fixed HTTPS
PowerShell Gallery package URLs. It verifies each archive before extraction, rejects unsafe or
ambiguous package trees and unsafe cache entries, and publishes the exact manifests under the
private deterministic `tmp/fingrind-powershell-quality-tools` root (or an explicit absolute
`FINGRIND_POWERSHELL_QUALITY_TOOLS_ROOT`). The runner imports those manifests and exact versions
directly rather than accepting ambient modules. Its explicit analyzer rules fail on any selected
warning or error in production scripts; Pester requires a nonempty owned inventory and rejects
failures, errors, skips, not-run, and inconclusive tests. Its host-independent behavior fixtures
use the production-owned Windows wrapper-path plan with explicit Windows paths and environment
values; they prove cache precedence, argument-path construction, and UNC build-root selection
without spoofing the host or attempting Windows filesystem or process behavior.

The result is intentionally narrower than a native Windows pass: it does not claim to prove
`cmd.exe`, `vswhere.exe`, `VsDevCmd.bat`, MSVC linking, NTFS/ACL behavior, PE/DLL loading, or Java
FFM on Windows. Those facts remain owned by the GitHub `windows-2022` matrix row.

When that native row fails, every CI or release publication row publishes one seven-day
`windows-failure-evidence-*` artifact. The helper-owned writer controls both the collected and
fallback shapes; in a release rerun it remains in the trusted release-control checkout while the
target checkout supplies only the bounded artifact facts. The artifact is a single redacted JSON
record containing only allowlisted normalized toolchain,
managed-SQLite build-contract, canonical public bundle checksum, bundle-manifest, JUnit aggregate,
and Gradle problem-report metadata. It never includes protected books, keys, request material,
arbitrary workspace files, raw environment dumps, general logs, or hashes of arbitrary workspace
or report content. After a failed build it runs only in the runner-provided step shell, never
executes the mutable pinned build runtime, and creates one fresh reparse-free directory directly
below the trusted runner temporary root. If collection itself cannot complete, CI writes the same
versioned privacy envelope with `collectionStatus: "fallback"` and no gathered facts, so an upload
never obscures the primary native failure.

## Path-Based Devcontainer Gate

The devcontainer gate validates the contributor environment, not application behavior. Application code changes are already proven by `check` and the published bundle-smoke matrix. Running a full Docker build-and-validate cycle for every pull request would repeat those application checks while consuming substantial CI time, so the environment gate fires only when its own inputs change:

- `.devcontainer/` for the Dockerfile and `devcontainer.json`
- `.dockerignore` for the root-context allowlist
- `scripts/validate-devcontainer.sh`
- `scripts/devcontainer-prepare-user-home.sh`
- `scripts/repo-verification-lock-support.sh`
- `scripts/python-runtime-support.sh`
- `scripts/provision-powershell-runtime.py`
- `scripts/powershell_runtime.py`
- `scripts/powershell_runtime_archives.py`
- `scripts/powershell_runtime_cache.py`
- `scripts/powershell_runtime_download.py`
- `scripts/powershell_runtime_installation.py`
- `scripts/powershell_runtime_metadata.py`
- `scripts/powershell_runtime_models.py`
- `gradle/fingrind-build.properties`

`devcontainer-changes` computes the pull-request diff before the gate is evaluated. When none of those paths change, `devcontainer` completes successfully as a clean no-op; only its Docker build-and-validation steps are omitted. The aggregate `gate` job evaluates its dependencies with `if: always()` and explicit result handling, and requires every dependency to conclude successfully.

All CI runners use only pinned hosted images: `ubuntu-24.04`, `ubuntu-24.04-arm`, `macos-15`,
`macos-15-intel`, and `windows-2022`; no workflow lane uses a floating or self-hosted label.
[`verify-release-repo-settings.sh`](../scripts/verify-release-repo-settings.sh) also requires the
public repository to expose zero self-hosted runners, so an accidental or hostile label cannot
resolve onto private execution infrastructure.
`workflow_dispatch` permits a manual aggregate `Gate` rerun when GitHub fails to attach the
pull-request workflow on initial open.

## Mutation Workflow

The always-running `mutation` job in `.github/workflows/ci.yml` owns release-critical PIT
execution. It runs on every pull request and `main` push, and the aggregate `Gate` waits for it.
That gives the exact merge and release-candidate commit one mandatory mutation result while keeping
branch protection on the repository's single `Gate` context. `.github/workflows/mutation.yml` is
the separate weekly/manual surveillance run for the same scope.

Both jobs resolve the exact metadata-owned Zulu runtime through the same Java distribution/version
selector and runtime checks as the main CI graph. Pull requests may only read the Gradle cache.
Each job places externalized module build output under one runner-temporary root, runs
`./check_mutation.sh`, requires both HTML/XML report trees to exist, and retains them for 30 days
even when mutation verification fails.

The normal CI graph still owns JPMS, complete JaCoCo coverage, native SQLite, published bundles,
containers, and cross-platform behavior; PIT strengthens the deterministic accounting behavior that
those other proofs do not mutate.

## Related Protocols

- [GITHUB_BOOTSTRAP_PROTOCOL.md](./GITHUB_BOOTSTRAP_PROTOCOL.md)
- [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md)
