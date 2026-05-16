---
afad: "4.0"
version: "0.38.0"
domain: DEVELOPER_RELEASE_PUBLICATION
updated: "2026-05-16"
route:
  keywords: [fingrind, release publication, attestation, github release, workflow_dispatch, windows zip, gh attestation]
  questions: ["how does fingrind attest published release assets", "why did windows expose the release attestation bug first", "how should a release workflow defect be repaired after tagging", "what publication invariants does fingrind enforce"]
---

# Release Publication Reference

**Purpose**: Capture the publication topology, attestation invariants, cross-platform failure
notes, and safe repair path for FinGrind public releases. Use this document for the theory behind
release publication. Use [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md) for the step-by-step
operator procedure.
**Prerequisites**: Familiarity with [DEVELOPER_DISTRIBUTION.md](./DEVELOPER_DISTRIBUTION.md),
[DEVELOPER_SECURITY.md](./DEVELOPER_SECURITY.md), and
[RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md).

## Canonical Publication Topology

One public FinGrind release is the combination of:
- one immutable Git tag `vX.Y.Z`
- one GitHub Release object for that tag
- one complete archive-and-checksum asset set on that release object
- one GitHub artifact attestation per published archive and checksum file
- one public container publication that waits for the complete verified release asset set

The attested subject is the published release asset, not the runner-local build output.

That means the workflow order is:
1. build each archive on its target runner
2. upload the archive and checksum into the GitHub Release object
3. download those published bytes back on one neutral post-upload job
4. create artifact attestations from those downloaded bytes
5. verify the release object and published attestations
6. publish or verify the public container only after the release asset handoff is complete

Any flow that attests runner-local artifacts instead of the published bytes is proving the wrong
thing. It can pass while GitHub serves different bytes under the same asset name.

## Non-Negotiable Invariants

These publication invariants are release-critical:
- tag identity is immutable; fix workflow defects on `main` and rerun against the existing tag
- publication convergence is by asset name plus digest, not by asset name alone
- the tag-driven bundle publisher must consume the archive and checksum paths reported by
  `:cli:bundleCliArchive`; it must not guess checkout-local `cli/build/distributions/...` paths
  because ordinary Gradle project outputs are externalized outside the checkout by default
- when `release.yml` is rerun from `main` with `workflow_dispatch` against an existing immutable
  tag, the bundle-path parser must accept the bundle-output contract emitted by that tag's source
  line as well as the current `main` contract; a workflow repair on `main` is not allowed to
  strand a tagged release because the task output text evolved afterward
- each published archive and checksum file must verify through `gh attestation verify`
- the attested digest must match the exact asset bytes downloadable from GitHub Release
- the container workflow must wait for the verified release asset set before treating publication
  as complete
- verifier timeout budget must exceed the explicit retry budget for release-asset and attestation
  propagation

If any invariant fails, the defect is in the release system, not in operator patience.

## Why Published-Byte Attestation Matters

GitHub Releases and workflow runner workspaces are different surfaces. A runner can produce one
archive, while the release object can expose another archive with the same name if a rerun,
upload-order defect, or stale asset replacement bug intervenes.

FinGrind therefore treats the GitHub Release object as the public truth surface and requires the
workflow to attest the bytes downloaded back from that surface. The `.sha256` files are operator
convenience digests; publisher authenticity comes from GitHub artifact attestations tied to the
repository workflow identity.

## Cross-Platform Notes

The trust model is identical across all supported bundle targets:
- macOS and Linux publish `.tar.gz`
- Windows publishes `.zip`

Windows is not a separate provenance class. It is a canary surface.

In the `0.32.0` release repair, the Windows ZIP exposed the publication drift first because the
published ZIP digest diverged from the runner-local subject that had been attested. That symptom
looked Windows-specific at first glance, but the root cause was publication topology, not Windows
crypto or Windows signing.

Operational rule:
- if one target fails attestation because the published digest differs from the attested subject,
  assume the release publication path is wrong for every target until proven otherwise
- do not patch around the first failing platform with platform-specific exceptions

## Neutral Job Rules

The neutral post-upload job is the most failure-prone seam in this flow. It needs a few explicit
rules:
- pass explicit repository context to `gh release download` with `GH_REPO` or `--repo`
- retry downloads into the same directory with `--clobber`
- print the final GitHub CLI error on failure instead of hiding it behind a retry loop
- treat download and attestation propagation lag as normal and budget for it explicitly

Without those rules, the workflow can fail in ways that hide the real cause and waste hours on
guesswork.

## Safe Repair Path After Tagging

When the defect is in workflow publication logic rather than in the released code payload, the
safe repair path is:
1. fix the workflow or verifier on `main`
2. merge that repair normally through branch protection
3. rerun the release workflow with `workflow_dispatch` against the existing tag
4. rerun the container workflow only after the release asset handoff verifies cleanly
5. verify the public GitHub Release and public container surfaces directly

Do not move the release tag. Do not create a replacement tag for the same version. Repair the
publication machinery and replay it against the immutable released commit.

If the repair changes how `main` parses bundle-path task output, the rerun workflow must remain
compatible with the tagged source line it is about to rebuild. The rerun path executes the
workflow definition from `main` against source code from the immutable tag; those two surfaces can
legitimately speak different bundle-output dialects after a post-tag publication repair.

If the repair touches container publication, the rerun workflow on `main` must publish from the
staged Docker context mirrored at `cli/build/docker-context`, not from the repository root. Local
Docker acceptance already proves that staged context path; tag-rerun publication must reuse the
same assembly boundary instead of reopening checkout-local files through repository-root
`.dockerignore` semantics.

## Evidence Owners

The main executable evidence owners for this surface are:
- `.github/workflows/release.yml`
- `.github/workflows/container.yml`
- `./scripts/verify-github-release.sh`
- `./scripts/verify-public-container-surface.sh`
- `./scripts/test-verify-github-release.sh`
- `./scripts/test-verify-public-container-surface.sh`
- `./scripts/verify-release-primary-checkout.sh`

The minimum operator proof after a public release is:

```bash
gh release view vX.Y.Z
./scripts/verify-github-release.sh <owner/repo> X.Y.Z
./scripts/verify-public-container-surface.sh ghcr.io/resoltico/fingrind X.Y.Z
```

If those checks disagree with a green workflow badge, trust the direct verification.

The release procedure assumes the checkout driving publication has a readable Git object store. A
Git worktree is the preferred release vehicle because it keeps post-release reconciliation obvious,
but a worktree shares the same `.git` metadata as the primary checkout. If
`./scripts/verify-repo-hygiene.sh` reports object-store corruption in the primary checkout, switch
to a clean clone before continuing the publication path; a sibling worktree will inherit the same
object-store defect.

`verify-public-container-surface.sh` also owns part of the public bookkeeping contract. It proves
that the published image can initialize one mounted book with the current lifecycle grammar,
submit one current posting request, and render the current human `trial-balance` layout for that
workflow. When that mounted-book grammar changes or that human report layout changes, repair the
verifier and its mock-backed shell regression harness together before trusting the release
protocol again.
