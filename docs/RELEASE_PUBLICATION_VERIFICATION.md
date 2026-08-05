---
afad: "5.0.1"
version: "0.62.1"
domain: RELEASE_PUBLICATION_VERIFICATION
updated: "2026-08-05"
route:
  keywords: [fingrind, github release, gh attestation, release assets, checksum, source archive, release handoff]
  questions: ["how do I verify published FinGrind GitHub Release assets", "how do I verify FinGrind release attestations"]
---

# Release Publication Verification

This is the GitHub Release handoff in the post-tag publication-verification journey from
[RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md). Complete the linked public-container handoff after
this one, before release hygiene or primary-checkout reconciliation.

This handoff proves the public GitHub Release object and its downloadable bytes. It does not
replace the pre-publication repository-settings check that proves the `v*` tag namespace permits
only repository-owner creation and rejects every update and deletion. Tag-policy enforcement is a
GitHub control-plane property; this guide independently verifies the resulting public release and
attestation evidence. [GITHUB_RELEASE_TAG_GOVERNANCE.md](./GITHUB_RELEASE_TAG_GOVERNANCE.md) owns
that tag-policy configuration and drift-recovery procedure.

## GitHub Release Handoff

Do not infer release publication from workflow success alone. Verify the release object directly:

```bash
gh release view vX.Y.Z --json tagName,isDraft,isPrerelease,publishedAt,url,assets
./scripts/verify-github-release.sh vX.Y.Z
```

Requirements:

- The release exists for tag `vX.Y.Z`.
- `isDraft` is `false`.
- `isPrerelease` is `false`. The current release contract admits stable `vX.Y.Z` tags only; the
  release workflow's target-tag boundary and release-candidate verifier enforce that admission
  before publication. Adding prerelease publication requires an intentional contract and verifier
  change first.
- The complete bundle asset set is present:
  - `fingrind-X.Y.Z-macos-aarch64.tar.gz`
  - `fingrind-X.Y.Z-macos-aarch64.tar.gz.sha256`
  - `fingrind-X.Y.Z-macos-x86_64.tar.gz`
  - `fingrind-X.Y.Z-macos-x86_64.tar.gz.sha256`
  - `fingrind-X.Y.Z-linux-x86_64.tar.gz`
  - `fingrind-X.Y.Z-linux-x86_64.tar.gz.sha256`
  - `fingrind-X.Y.Z-linux-aarch64.tar.gz`
  - `fingrind-X.Y.Z-linux-aarch64.tar.gz.sha256`
  - `fingrind-X.Y.Z-windows-x86_64.zip`
  - `fingrind-X.Y.Z-windows-x86_64.zip.sha256`
- The release asset-name inventory is exact: every expected asset name appears once, and no
  duplicate or additional asset name is present. Unsupported targets are one important example of
  forbidden extra assets, not the whole rule.
- Targets disclosed through
  `environment.publication.unsupportedPublicCliBundleTargets` such as the current
  `windows-aarch64` entry must not appear as release assets unless the bundle-layout publication
  status changes first.
- Every archive and checksum file served by the GitHub Release object verifies through
  `gh attestation verify` against the repository's `.github/workflows/release.yml` signer
  workflow. On an initial publication, the workflow verifies the staged draft asset set before
  finalization; on a repair rerun, it can verify an already-public matching asset set. The helper
  script uses the repo-owned draft-aware downloader for either state.
- The release workflow must create GitHub-hosted release-asset attestations from the GitHub Release
  assets themselves. On first publication, upload the bundle and checksum files into the draft,
  download those GitHub-hosted bytes back on a neutral attestation job, and attest the downloaded
  files. On a matching-public repair rerun, download and attest the already-public matching bytes
  without replacing them. Do not substitute runner-local build outputs for this GitHub-hosted
  release-asset attestation.
- The release workflow must also attest the runner-built archive and checksum bytes on each target
  runner before draft upload so build-output provenance and GitHub-hosted release-asset provenance
  both exist and can be compared by digest.
- Publication convergence is by asset name and digest while an initial release is a draft. Once a
  release is public, asset bytes are immutable: a repaired rerun may only accept the complete
  matching public set. If it would need different bytes under the same public asset name, fail the
  rerun and cut a new version instead of replacing public assets in place.
- Every published checksum file must target the matching archive name and its declared digest must
  match the downloadable archive bytes.
- The generated GitHub source archives exclude the non-product metadata identified by the
  repository's source-archive boundary.

If these conditions are satisfied, the GitHub Release handoff is complete even if an additional
duplicate release workflow run failed after the release was already created.

The release workflow verifies the GitHub-hosted asset set three times: after attestation and before
container publication, immediately after container promotion and before its finalization step, and
after finalization with a non-draft requirement. For an initial release, the middle check proves
the current asset inventory, downloadable bytes, checksum pairs, source-archive boundary, and
attestations at the exact public-transition seam. For a matching-public repair rerun it verifies
the existing public object and the finalizer makes no asset mutation. The operator-side
`gh release view` plus `./scripts/verify-github-release.sh` checks remain mandatory because
workflow success is still not the authoritative state. They prove both the public release object
and the attestation-backed provenance of the shipped bundle assets.
`./scripts/verify-github-release.sh` also runs `./scripts/verify-security-policy-surface.sh`, so
public release verification fails if the repository's private vulnerability reporting surface no
longer matches the checked-in security policy.
The release workflow carries an explicit retry budget for release-asset and attestation
propagation because GitHub can publish those surfaces asynchronously after the bundle jobs
complete. The release workflow's verifier job timeout must exceed that retry budget with headroom;
treat a shorter timeout as a release-system defect.

The release workflow's staged Linux container builds wait for this complete GitHub Release asset
set, and public image-tag promotion waits for those staged builds to complete. The release remains
a draft during initial container publication, then the finalization job repeats GitHub Release
verification immediately before it changes `isDraft` to `false`. If public container promotion
succeeds while the release asset set is incomplete, or an initial draft becomes public without that
immediate recheck, treat it as a release-system defect and fix the repository before the next
release.

If this handoff fails after a valid tag exists, distinguish propagation delay from missing or
incorrect public state before changing anything. Never move or replace the tag. When a publication
repair is required, follow the
[Safe Repair Path After Tagging](./DEVELOPER_RELEASE_PUBLICATION.md#safe-repair-path-after-tagging),
then rerun this handoff before continuing to the container handoff.

## Continue To Public Container Availability

Complete [RELEASE_PUBLICATION_CONTAINER_VERIFICATION.md](./RELEASE_PUBLICATION_CONTAINER_VERIFICATION.md).
It owns anonymous image availability, manifest identity, mounted-book, and PDF proof. Only after
that second handoff succeeds is the release fully verified as publicly available.
