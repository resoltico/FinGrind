---
afad: "5.0.1"
version: "0.61.0"
domain: RELEASE_PUBLICATION_VERIFICATION
updated: "2026-07-26"
route:
  keywords: [fingrind, release, github release, gh attestation, release assets, ghcr, anonymous container, mounted book, pdf verification]
  questions: ["how do I verify published FinGrind GitHub Release assets", "how do I verify the public FinGrind container after a release", "how do I verify FinGrind release attestations"]
---

# Release Publication Verification

This is the post-tag publication-verification journey from
[RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md). After its workflow-monitoring checkpoint, complete
both handoffs here before release hygiene or primary-checkout reconciliation.

## GitHub Release Handoff

Do not infer release publication from workflow success alone. Verify the release object directly:

```bash
gh release view vX.Y.Z --json tagName,isDraft,isPrerelease,publishedAt,url,assets
./scripts/verify-github-release.sh vX.Y.Z
```

Requirements:

- The release exists for tag `vX.Y.Z`.
- `isDraft` is `false`.
- `isPrerelease` is `false` unless the target release is intentionally a prerelease.
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
- Targets disclosed through
  `environment.publication.unsupportedPublicCliBundleTargets` such as the current
  `windows-aarch64` entry must not appear as release assets unless the bundle-layout publication
  status changes first.
- Every published archive and published checksum file verifies through `gh attestation verify`
  against the repository's `.github/workflows/release.yml` signer workflow. The helper script
  downloads the draft-or-published assets through the repo-owned draft-aware downloader and
  performs that verification for you.
- The release workflow must create those attestations from the published release assets
  themselves. Upload the bundle and checksum files first, download the published bytes back from
  GitHub on a neutral attestation job, and attest those downloaded files. Do not attest
  runner-local build outputs directly.
- The release workflow must also attest the runner-built archive and checksum bytes on each
  target runner before publication so build-output provenance and public-byte provenance both
  exist and can be compared by digest.
- Publication convergence is by asset name and digest while the release is a draft. Once the
  release is public, asset bytes are immutable: if a repaired rerun would need different bytes
  under the same public asset name, fail the rerun and cut a new version instead of replacing
  public assets in place.
- Every published checksum file must target the matching archive name and its declared digest must
  match the downloadable archive bytes.
- The generated GitHub source archives do not include repo-owned agent metadata such as
  `AGENTS.md` or `.codex/**`.

If these conditions are satisfied, the GitHub Release handoff is complete even if an additional
duplicate release workflow run failed after the release was already created.

The release workflow is expected to perform this same verification internally after publication.
The operator-side `gh release view` plus `./scripts/verify-github-release.sh` checks remain
mandatory because workflow success is still not the authoritative state. They now prove both the
release object and the published attestation-backed provenance of the shipped bundle assets.
`./scripts/verify-github-release.sh` also runs `./scripts/verify-security-policy-surface.sh`, so
public release verification fails if the repository's private vulnerability reporting surface no
longer matches the checked-in security policy.
The release workflow carries an explicit retry budget for release-asset and attestation
propagation because GitHub can publish those surfaces asynchronously after the bundle jobs
complete. The release workflow's verifier job timeout must exceed that retry budget with headroom;
treat a shorter timeout as a release-system defect.

The release workflow's staged Linux container builds are also expected to wait for this complete
GitHub release draft asset set before they promote the public image tags. If public container
promotion succeeds while the release asset set is incomplete, treat that as a release-system
defect and fix the repository before the next release.

## Public Container Availability

Do not declare the release done until the GitHub Release exists and the operator-side public
container surface verifier succeeds. The verifier uses a temporary Docker config directory so you
are testing the public surface, not cached owner credentials, and so you do not mutate the
operator's normal Docker login state. It also uses machine-readable `version --output json`
checks plus exact text trial-balance row assertions so the operator is not left guessing about
free-form CLI text:

```bash
gh release view vX.Y.Z
./scripts/verify-public-container-surface.sh ghcr.io/resoltico/fingrind X.Y.Z
```

The verifier always retries anonymous exact-tag pulls until the container reports the target
release version through `version --output json`. When the release owns the canonical `latest`
pointer, it also verifies `latest` through that same check. A successful `docker pull` alone is
not sufficient verification. In particular: a multi-arch `docker pull` can succeed even when the
platform manifests have been deleted — the index manifest is present but the image is not
actually runnable. The `docker run ... version --output json` check is the definitive test.

If you are replaying a historical stable release that no longer owns `latest`, disable the
`latest` check explicitly:

```bash
FINGRIND_VERIFY_PUBLIC_CONTAINER_LATEST=false \
  ./scripts/verify-public-container-surface.sh ghcr.io/resoltico/fingrind X.Y.Z
```

The temporary mounted-book workflow is also mandatory. It proves that the published public image
can still perform one end-to-end bookkeeping/reporting loop, not just print discovery metadata.
`trial-balance --output text` must render the posted Cash and Revenue rows for the seeded EUR
10.00 entry rather than failing in book initialization, key handling, or reporting. The same
anonymous verification must also create an existing owner-only report parent and prove that
`--pdf-out` publishes one valid PDF artifact there without replacing an existing target. Its text
confirmation must name that artifact's canonical physical path (with only the standard redacted
prefix), and `--output text --pdf-out ...` must replace the full report body with one artifact
confirmation block on stdout. The mounted-workspace commands in that verifier must run the
container as the caller's numeric `UID:GID`, matching the repo-owned `docker-smoke` contract, so
bind-mounted key, book, and PDF artifacts remain owned and readable by the invoking operator on
Linux hosts.

Because this verifier asserts text statement output and drives a real mounted-book initialization
and posting path, it is part of the published bookkeeping contract, not just the
container-publication machinery. When the text `trial-balance` layout changes — for example new
bookkeeping columns appear — or when the mounted workflow grammar changes — for example
`open-book` starts requiring additional identity flags or `post-entry` starts requiring new
request fields — or when the mounted-workspace user contract changes — update
`scripts/verify-public-container-surface.sh` and
`scripts/test-verify-public-container-surface.sh` in the same change. Do not accept a release
process where the operator-side verifier lags behind the published statement surface.

The release workflow's staged-container and promotion jobs run this same verifier after image
publication, so a green publication workflow and this operator run now speak to the same public
contract rather than two different verification depths.

If the public verifier fails, inspect the reported step, fix the published state or the verifier
owner if the probe itself is wrong, and rerun the same anonymous verification command. Do not
switch to the operator's normal Docker config as a fallback.

These checks are a second handoff checkpoint. Workflow success is not enough; public pull and run
behavior is the authoritative state.

The container registry retains the last 5 releases. Only `X.Y.Z` and `latest` tags are
published per release; there is no `X.Y` floating tag.

Only after the full anonymous pull, run, mounted-book, and PDF verification sequence succeeds
report to the user: the release is publicly available.

The release workflow is expected to perform the exact-tag pull-and-run verification internally
after publication, and to perform the `latest` proof only when the release owns the canonical
`latest` pointer. The operator-side verification remains mandatory because public availability,
not workflow success, is the authoritative state.

Return to [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md) for post-publication PR hygiene and
primary-checkout reconciliation.
