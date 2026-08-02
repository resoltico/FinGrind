---
afad: "5.0.1"
version: "0.62.0"
domain: DEVELOPER_RELEASE_PUBLICATION
updated: "2026-08-02"
route:
  keywords: [fingrind, release publication, release worktree, bootstrap branch, attestation, github release, workflow_dispatch, windows publication lane, gh attestation]
  questions: ["how does fingrind attest published release assets", "how do I move an unpublished release payload into a clean worktree", "why did the windows publication lane expose the release attestation bug first", "how should a release workflow defect be repaired after tagging", "what publication invariants does fingrind enforce"]
---

# Release Publication Reference

**Purpose**: Capture the publication topology, attestation invariants, cross-platform failure
notes, and safe repair path for FinGrind public releases. Use this document for the theory behind
release publication. Use [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md) for release control and
closeout, then [RELEASE_PUBLICATION_VERIFICATION.md](./RELEASE_PUBLICATION_VERIFICATION.md) for
the post-tag public-verification journey.
**Prerequisites**: Familiarity with [DEVELOPER_DISTRIBUTION.md](./DEVELOPER_DISTRIBUTION.md),
[DEVELOPER_SECURITY.md](./DEVELOPER_SECURITY.md), and
[RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md) plus
[RELEASE_PUBLICATION_VERIFICATION.md](./RELEASE_PUBLICATION_VERIFICATION.md).

## Canonical Publication Topology

One public FinGrind release is the combination of:
- one immutable Git tag `vX.Y.Z`
- one GitHub Release object for that tag
- one complete archive-and-checksum asset set on that release object
- GitHub artifact attestations for runner-built archive/checksum outputs and the corresponding
  downloaded GitHub-hosted release-asset bytes
- one public container publication from the same release workflow after the complete verified
  release asset set exists

The public release proof is the GitHub-hosted release asset, not the runner-local build output
alone. On an initial publication, the workflow stages those assets in a draft GitHub Release. On a
repair rerun, it can instead find an already-public release, but only when its complete asset-name
and digest set already matches the immutable tagged payload. In either state, the GitHub-hosted
bytes — not a runner workspace — are the attestation and verification subjects.

That means the workflow order is:
1. build each archive on its target runner
2. attest the runner-built archive and checksum bytes for each target
3. reconcile the GitHub Release asset set: create or update a draft on first publication, or accept
   an already-public release only when every name and digest already matches
4. download the GitHub-hosted release-asset bytes back on one neutral post-upload job
5. create artifact attestations from those downloaded bytes
6. verify the GitHub Release object, archive/checksum pairs, source-archive boundary, and
   release-asset attestations; the first pass permits a draft so initial publication can continue
7. build, promote, and verify the public container only after that first GitHub Release handoff
8. immediately reverify the same GitHub-hosted release asset set after container promotion and
   immediately before finalization; this pass also permits a draft only because an initial release
   has not yet been made public
9. finalize an initial draft as public, or accept the already-public matching release as a no-op
10. verify the final GitHub Release as public (`isDraft=false`)

The immediate pre-finalization recheck closes the interval between the first draft-aware proof and
the public state mutation. The final public verifier remains separate: it proves the externally
visible non-draft release after that mutation rather than treating an earlier draft proof as public
availability.

Any flow that attests only runner-local artifacts without also attesting GitHub-hosted release-asset
bytes is proving the wrong public surface. It can pass while GitHub serves different bytes under the
same asset name.

## Release Checkout Topology

[RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md) Step 1 uses this recipe when a real unpublished
release payload must move from the primary checkout into a dedicated release worktree. It is an
operator-boundary discipline: one Git branch may be checked out in only one worktree, and an old
local or remote `release/X.Y.Z` branch is unknown release history until deliberately inspected.

First inspect the shared worktree topology. The primary checkout may own `release/X.Y.Z`, or the
dedicated release worktree may own it, but never both. If it is attached elsewhere, use that
worktree only after reviewing its payload or remove it only after proving it disposable.

```bash
git worktree list --porcelain
```

For a dedicated-worktree handoff, refresh refs and require both the final release branch and the
temporary bootstrap branch to be absent locally and on `origin`:

```bash
RELEASE_BRANCH="release/X.Y.Z"
BOOTSTRAP_BRANCH="release-bootstrap/X.Y.Z"
git -C "$PRIMARY_CHECKOUT" fetch origin --prune --tags

assert_unoccupied_branch() {
  local branch_name=$1
  if git -C "$PRIMARY_CHECKOUT" show-ref --verify --quiet "refs/heads/${branch_name}" || \
      git -C "$PRIMARY_CHECKOUT" show-ref --verify --quiet "refs/remotes/origin/${branch_name}"; then
    printf 'branch already exists locally or on origin and requires deliberate inspection: %s\n' \
      "${branch_name}" >&2
    return 1
  fi
  return 0
}

assert_unoccupied_branch "$RELEASE_BRANCH"
assert_unoccupied_branch "$BOOTSTRAP_BRANCH"
```

The preferred route applies only when the primary checkout's committed `HEAD` is exactly
`origin/main` and all of its local changes are intended release payload. It records that payload in
one local bootstrap commit, restores the primary checkout to `main`, and creates
`release/X.Y.Z` only in the dedicated worktree:

```bash
git -C "$PRIMARY_CHECKOUT" diff --quiet HEAD origin/main || {
  printf 'primary checkout committed baseline differs from origin/main; use the patch route or resolve it first\n' >&2
  exit 1
}
git -C "$PRIMARY_CHECKOUT" switch -c "$BOOTSTRAP_BRANCH"
git -C "$PRIMARY_CHECKOUT" add <every intended release file, including new files>
git -C "$PRIMARY_CHECKOUT" diff --cached --name-status
git -C "$PRIMARY_CHECKOUT" commit -m "release bootstrap: X.Y.Z"
git -C "$PRIMARY_CHECKOUT" switch main
RELEASE_WORKTREE="$(mktemp -d -t fingrind-release-XXXXXX)"
git -C "$PRIMARY_CHECKOUT" worktree add -b "$RELEASE_BRANCH" "$RELEASE_WORKTREE" "$BOOTSTRAP_BRANCH"
cd "$RELEASE_WORKTREE"
```

If the committed baseline or local change scope is not exact, use Step 1's patch route instead.
The bootstrap branch is local-only handoff evidence; after its payload is merged, Step 6 removes it
with `git branch -d release-bootstrap/X.Y.Z`.

## Non-Negotiable Invariants

These publication invariants are release-critical:
- public release admission accepts only stable `vX.Y.Z` tags. The `Prepare release publication`
  job's **Determine target release tag** boundary validates that shape before deriving the version,
  and `verify-release-candidate-tag.sh` repeats it before publication; prerelease, build, suffix,
  and arbitrary `v*` tags are not release targets
- tag identity is GitHub-enforced: one active repository-owned ruleset authorizes only the current
  repository-owner REST user ID to create `v*` tags, while a separate no-bypass ruleset blocks
  every update and deletion of `v*` tags. The release candidate verifier still owns the narrower
  stable `vX.Y.Z` admission grammar; fix workflow defects on `main` and rerun against the existing
  tag
- before its first public tag, unreleased repair commits may retain the target version; initial
  publication still requires the tag to name the current green default-branch head. The
  operator-side pre-tag candidate verifier proves that version, head, release-blocking CI, and
  reference-vacancy contract before it creates the immutable ref. Once `vX.Y.Z` exists, it is
  immutable: repairs use the rerun path and never reuse or move that tag
- publication convergence is by asset name plus digest, not by asset name alone
- the tag-driven bundle publisher must consume the archive and checksum paths reported by
  `:cli:bundleCliArchive`; it must not guess checkout-local `cli/build/distributions/...` paths
  because ordinary Gradle project outputs are externalized outside the checkout by default
- every release bundle job has the same 130-minute observed-runtime ceiling as the equivalent CI
  publication proof, so the complete tagged bundle acceptance matrix can finish before asset
  staging and attestation begin
- when `release.yml` is rerun with `workflow_dispatch` against an existing immutable tag, the
  workflow definition and release-control verifier may come from repaired `main`, but they must
  receive the tagged source checkout explicitly and execute every payload-producing Gradle,
  runtime, bundle-smoke, version, contract, manifest, archive, and checksum input from that target
  root; the control verifier may not silently substitute helper-root source for the released payload;
  the Prepare release publication job resolves one full helper commit and every later rerun job checks out that
  immutable commit rather than independently reading a moving `main`
- a manually supplied rerun tag crosses the GitHub expression boundary as an environment value,
  never as text interpolated into a shell program; tag validation therefore treats it as data
- release-control initiation is owner-only and ref-bound: the workflow verifies both the initiating
  numeric actor ID and the triggering actor for every push, dispatch, and rerun before release-tag
  admission or public mutation, and rejects every manual dispatch whose ref is not exactly
  `refs/heads/main`. This closes the separate workflow-dispatch authority surface that tag rulesets
  cannot control
- each published archive and checksum file must verify through `gh attestation verify`
- the attested digest must match the exact asset bytes downloadable from GitHub Release
- public GitHub release assets are immutable once the release is finalized; any rerun that would
  require different public bytes must fail and cut a new version tag instead
- the release workflow's staged-container and promotion jobs must wait for the verified GitHub
  Release asset set before treating publication as complete; after container promotion, it must
  reverify that asset set immediately before finalizing an initial draft, and it must verify the
  resulting non-draft public release afterwards
- the complete `Release` workflow serializes repository-wide through the bounded
  release-publication concurrency queue: one run may publish at a time, up to 100 runs may wait,
  and an active run is never cancelled by a later release request
- the latest-publication policy is resolved only after immutable public exact-container acceptance;
  its one fresh result drives both the GitHub Release latest designation and GHCR `latest`
- the public `X.Y.Z` container tag is immutable by OCI descriptor digest. Its release-owned
  `<staging-image>:X.Y.Z-candidate` index is durable write-once provenance, not a mutable cache
- a candidate must preserve the complete descriptor multiset of the two staged platform indexes,
  including provenance and SBOM attachments; the candidate, exact public tag, and `latest` when
  present must each contain exactly one Linux `amd64` and one Linux `arm64` runtime descriptor
- a rerun may accept an existing public exact tag only when the retained candidate exists and has
  the same descriptor digest. A missing candidate or mismatch fails before exact or `latest` tag
  mutation; a materially different public image requires a new version
- package cleanup must retain the staging `X.Y.Z-candidate` while its corresponding immutable
  public exact tag remains supported, because deleting this evidence intentionally makes reruns
  fail closed
- the staged-container and promotion timeouts must leave room for buildx publication and post-push
  public verification
- verifier timeout budget must exceed the explicit retry budget for release-asset and attestation
  propagation

The two tag rulesets are deliberately separate: a creator bypass is necessary for initial tag
creation but would be unacceptable on update or deletion. `verify-release-repo-settings.sh`
fetches every effective tag ruleset and rejects a missing, extra, inactive, broadened, or
bypass-bearing policy before publication. This is GitHub control-plane enforcement, not
cryptographic permanence: a repository administrator can edit or remove policy, so the immutable
tag is always paired with direct tag-to-commit, release-asset, and attestation evidence.

If any invariant fails, the defect is in the release system, not in operator patience.

## Why Published-Byte Attestation Matters

GitHub Releases and workflow runner workspaces are different surfaces. A runner can produce one
archive, while the release object can expose another archive with the same name if a rerun,
upload-order defect, or stale asset replacement bug intervenes. On first publication that release
object is deliberately a draft during staging; on a safe repair rerun it can already be public.

FinGrind therefore treats the GitHub Release object as the public truth surface and requires the
workflow to attest the bytes downloaded back from that surface. The `.sha256` files are operator
convenience digests; publisher authenticity comes from GitHub artifact attestations tied to the
repository workflow identity.

## Cross-Platform Notes

The trust model is identical across every release-control surface:
- published bundle assets are every classifier whose publication status is `published` in
  `bundle-publication-contract.json`
- the current published asset set is `macos-aarch64`, `macos-x86_64`, `linux-x86_64`,
  `linux-aarch64`, and `windows-x86_64`
- `windows-aarch64` remains declared but not published, so it must not appear as a release asset
- unsigned macOS and Windows bundles use the same checksum-plus-attestation trust model as Linux;
  there is no platform-specific provenance exception

In the `0.32.0` release repair, the Windows publication lane exposed the publication drift first
because the published asset digest diverged from the runner-local subject that had been attested.
That symptom looked Windows-specific at first glance, but the root cause was publication
topology, not Windows crypto or Windows signing.

Operational rule:
- if one target fails attestation because the published digest differs from the attested subject,
  assume the release publication path is wrong for every target until proven otherwise
- do not patch around the first failing platform with platform-specific exceptions

## Native Windows Feedback Before Publication

The CI Windows publication proof is a full native `windows-2022` build, runtime, and bundle-smoke
execution. It starts alongside `Check`, because it consumes no Linux-produced artifact and uses a
read-only cache. The aggregate `Gate` still requires both results; concurrent scheduling shortens
diagnosis without weakening the release boundary.

The target and publication contracts carry no runner labels. CI and release instead own literal,
reviewed matrices of canonical target identifiers and pinned GitHub-hosted images, so `runs-on`
resolves without consulting candidate-checkout plan output. Container promotion uses its fixed
`ubuntu-24.04` control runner directly. A contract edit therefore cannot route a proof or
publication job onto a self-hosted or arbitrary runner.
[`verify-release-repo-settings.sh`](../scripts/verify-release-repo-settings.sh) separately
requires the public repository to expose zero self-hosted runners.

The CI and release workflows invoke the same native
`scripts/verify-windows-publication-surface.ps1` adapter. It makes runner identity,
build-logic verification, attestation-codec verification, direct and source-checkout
managed-SQLite runtime proof, Windows bundle construction, canonical manifest validation, and
bundle smoke one indivisible publication contract. The policy owner derives the archive and
checksum from the tagged target's version and bundle-layout contract and accepts only the
repository-contained, non-reparse
`cli/build/distributions/fingrind-<version>-windows-x86_64.zip` and its `.sha256` file. A
manifest cannot redirect attestation or upload toward an arbitrary runner path.

On a post-tag rerun, the adapter itself is deliberately taken from the repaired helper checkout as
release control, but the explicit target checkout supplies every input that can change public
bytes. This is not an implicit compatibility layer: a missing target script or a helper/target
root mismatch fails the run. The separate roots make a workflow repair auditable without changing
the immutable release payload.

Before sending a change to GitHub, a macOS or Linux contributor should provision the exact
metadata-pinned PowerShell `7.6.4` runtime and run the local command shown in
[DEVELOPER_CI.md](./DEVELOPER_CI.md#native-windows-feedback). That command validates the actual
canonical contracts that are portable to the host: PowerShell parser checks, checksum-pinned
Pester behavior tests, explicit PSScriptAnalyzer rules, the Windows launcher, bundle-manifest and
staging-layout facts, the MSVC environment-command plan, and the managed-SQLite MSVC argument
vector. It is deliberately not a synthetic Windows bundle and does not claim native execution.

The irreducibly native facts remain MSVC and linker behavior, Visual Studio discovery, Windows
filesystem and ACL semantics, DLL loading, Java FFM, and the Windows process boundary. GitHub's
native matrix is the only release authority for those facts. On a failure, its short-lived
`windows-failure-evidence-*` artifact contains only redacted allowlisted normalized provenance,
contract, canonical public bundle-checksum, and aggregate-result metadata; it excludes protected
books, keys, request data, arbitrary logs, environment dumps, and hashes of arbitrary workspace or
report content. The writer runs only in the runner-provided PowerShell step shell after a failure,
not the build runtime that failed earlier in the job. It owns one fresh, reparse-free directory
directly below the runner temporary root before it writes the single allowlisted document.

## Neutral Job Rules

The neutral post-upload job is the most failure-prone seam in this flow. It needs a few explicit
rules:
- use the repo-owned `scripts/download-github-release-assets.sh` seam with an explicit `--repo`,
  `--tag`, output directory, and complete expected asset-name set; it reads the draft-or-published
  asset API rather than relying on the tag-based GitHub CLI release downloader
- retry each named asset through a temporary path, then publish it atomically into the output
  directory so no partial file can become an attestation subject
- preserve an actionable named-asset and tag diagnostic when retries are exhausted
- treat download and attestation propagation lag as normal and budget for it explicitly

Without those rules, the workflow can fail in ways that hide the real cause and waste hours on
guesswork.

## Safe Repair Path After Tagging

When the defect is in workflow publication logic rather than in the released code payload, the
safe repair path is:
1. fix the workflow or verifier on `main`
2. merge that repair through the protected PR path, using GitHub's administrator bypass when the
   single-owner review requirement would otherwise deadlock the repair
3. rerun the repaired workflow definition from `main` with `workflow_dispatch` against the existing
   tag. The workflow independently rejects any dispatch ref other than `refs/heads/main`:

```bash
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
gh workflow run release.yml --repo "$REPO" --ref main -f release_tag=vX.Y.Z
```

4. verify the public GitHub Release and public container surfaces directly

Do not move the release tag. Do not create a replacement tag for the same version. Repair the
publication machinery and replay it against the immutable released commit.

If the rerun finds an already-public GitHub Release, it is an asset-convergence no-op only when the
complete expected name-and-digest set already matches the tagged payload. It must not turn that
release back into a draft or replace an asset in place. A mismatch is a new-version defect, not a
repair opportunity for an immutable public release.

The release-candidate verifier has three intentionally distinct admission modes. The mandatory
operator-side initial check made immediately after creating a new tag requires the tag commit to
equal the current default-branch head. That is the only contemporaneous-head equality proof. It
does not require that commit to be the very first commit that introduced the target version:
unreleased same-version repair commits may still become the first public tag as long as they are
the current green `origin/main` head.

The tag-triggered workflow enters a repository-wide publication queue and can therefore begin
after `main` has legitimately advanced. Its tag-publication mode refreshes the default branch,
requires the tag to remain reachable from it, and requires the original release-blocking CI set on
the exact tag commit to be green. It must not claim contemporaneous-head equality. A
`workflow_dispatch` repair run uses `rerun` mode, which applies that same durable
ancestry-and-exact-tag-CI admission rule while keeping the payload checkout pinned to the immutable
tag commit.

The workflow display title is the release-target identity: `release.yml` derives `Release vX.Y.Z`
only from the dispatch `release_tag` input or the pushed tag ref. Monitoring therefore selects
every run with that exact title and then admits only `push` or `workflow_dispatch` events. Its
`headSha` is not the target selector: it names the tag commit for the initial push and the ref
resolution recorded when GitHub accepts a manual dispatch, while both runs build the immutable tag
payload. A rerun's actual release-control revision is the separately pinned helper commit, recorded
in its log and step summary as `Release control helper commit: <40-hex SHA>`.

The whole release workflow serializes publication under one bounded repository-wide queue, rather
than a tag-local lock, because both the GitHub Release latest designation and GHCR `latest` are
shared mutable pointers. A queued run is pending work, not a failed publication. After immutable
public exact-container acceptance, one fresh latest-policy decision drives both pointers. Container
promotion writes a durable staging `X.Y.Z-candidate` descriptor before accepting the public exact
descriptor; once the exact tag exists, a rerun requires that retained candidate to exist and match
the exact digest. It never recomposes the candidate from mutable staging tags. Docker's tag API
does not provide a documented create-only compare-and-swap operation, so the repository queue
serializes this workflow's publication attempts; an external writer with package-write access is
an operational integrity incident, not a state this workflow can safely reconcile.

The rerun workflow now reads the canonical bundle-archive manifest instead of scraping Gradle
console output, so post-tag publication repairs do not need compatibility shims for historical
log dialects.

If the repair touches container publication, the rerun workflow on `main` must publish from the
staged Docker context under the active CLI build root, not from the repository root. Local Docker
acceptance already proves that staged context boundary; tag-rerun publication must reuse that same
checked assembly input instead of reopening checkout-local files through repository-root
`.dockerignore` semantics.

## Evidence Owners

The main executable evidence owners for this surface are:
- `.github/workflows/release.yml`
- `./scripts/promote-container-image.sh`
- `./scripts/container-promotion-support.sh`
- `./scripts/resolve-release-latest-policy.py`
- `./scripts/verify-github-release.sh`
- `./scripts/verify-public-container-surface.sh`
- `./scripts/test-promote-container-image.sh`
- `./scripts/test-resolve-release-latest-policy.sh`
- `./scripts/test-verify-github-release.sh`
- `./scripts/test-verify-public-container-surface.sh`
- `./scripts/reconcile-release-primary-checkout.sh`
- `./scripts/verify-release-primary-checkout.sh`

The minimum operator proof after a public release is:

```bash
gh release view vX.Y.Z
./scripts/verify-github-release.sh vX.Y.Z
./scripts/verify-public-container-surface.sh ghcr.io/resoltico/fingrind X.Y.Z
```

If those checks disagree with a green workflow badge, trust the direct verification.

The release procedure assumes the checkout driving publication has a readable Git object store. A
Git worktree is the preferred release vehicle because it keeps post-release reconciliation obvious,
but a worktree shares the same `.git` metadata as the primary checkout. If
`./scripts/verify-repo-hygiene.sh` reports object-store corruption in the primary checkout, switch
to a clean clone before continuing the publication path; a sibling worktree will inherit the same
object-store defect. The same rule applies when repo hygiene reports Git coordination lock files:
inspect ownership with `lsof`, remove only orphaned lock files, and treat any live owner as proof
that the checkout is unavailable for publication. If repo hygiene reports a persisted `gc.log`,
run manual Git housekeeping first and remove that log only after a successful cleanup pass.
After publication, collapse that clean clone back onto the canonical primary-checkout path with
`./scripts/reconcile-release-primary-checkout.sh`; a corrupt original checkout is not an
acceptable second source of truth to leave behind.

`verify-public-container-surface.sh` also owns part of the public bookkeeping contract. It proves
that the published image can initialize one mounted book with the current lifecycle grammar,
submit one current posting request, render the current text `trial-balance` layout for that
workflow, and expose the published native-provenance files through a shell probe that does not
route filesystem checks back through the FinGrind entrypoint. The mounted-workspace portion of
that verifier must run the container as the caller's numeric `UID:GID`, matching the repo-owned
`docker-smoke` contract, so Linux bind-mounted book, key, and PDF artifacts are owned by the
invoking operator instead of by container-root. The release workflow's staged-container and
promotion jobs now run this same verifier after push, so workflow automation and the operator's
run defined by [RELEASE_PUBLICATION_VERIFICATION.md](./RELEASE_PUBLICATION_VERIFICATION.md) are
held to one public surface contract. When that mounted-book grammar changes,
that text report layout changes, the mounted-workspace user contract changes, or the
container-native provenance surface
moves, repair the verifier and its mock-backed shell regression harness together before trusting
the release protocol again.
