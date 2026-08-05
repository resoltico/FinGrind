---
afad: "5.0.1"
version: "0.62.1"
domain: RELEASE_PUBLICATION_CONTAINER_VERIFICATION
updated: "2026-08-05"
route:
  keywords: [fingrind, release, public container, ghcr, docker, mounted book, pdf verification]
  questions: ["how do I verify the public FinGrind container after a release", "how do I verify FinGrind GHCR image availability"]
---

# Public Container Publication Verification

This is the second post-tag handoff from [RELEASE_PUBLICATION_VERIFICATION.md](./RELEASE_PUBLICATION_VERIFICATION.md). Complete the GitHub Release handoff first, then prove the public container surface before release hygiene or primary-checkout reconciliation.

Do not declare the release done until the GitHub Release exists and the operator-side public container surface verifier succeeds. The verifier uses a temporary Docker config directory so you are testing the public surface, not cached owner credentials, and so you do not mutate the operator's normal Docker login state. It also uses machine-readable `version --output json` checks plus exact text trial-balance row assertions so the operator is not left guessing about free-form CLI text:

```bash
gh release view vX.Y.Z
./scripts/verify-public-container-surface.sh ghcr.io/resoltico/fingrind X.Y.Z
```

The verifier always retries anonymous exact-tag pulls until the container reports the target release version through `version --output json`. When the release owns the canonical `latest` pointer, it also verifies `latest` through that same check. A successful `docker pull` alone is not sufficient verification. In particular: a multi-arch `docker pull` can succeed even when the platform manifests have been deleted — the index manifest is present but the image is not actually runnable. The `docker run ... version --output json` check is the definitive test.

If you are replaying a historical stable release that no longer owns `latest`, disable the `latest` check explicitly:

```bash
FINGRIND_VERIFY_PUBLIC_CONTAINER_LATEST=false \
  ./scripts/verify-public-container-surface.sh ghcr.io/resoltico/fingrind X.Y.Z
```

Record manifest identity directly as well as exercising the container. Docker Buildx's structured manifest output is the evidence surface; do not infer identity from a human-formatted pull result:

```bash
IMAGE=ghcr.io/resoltico/fingrind
EXACT_MANIFEST="$(docker buildx imagetools inspect --format '{{json .Manifest}}' "${IMAGE}:X.Y.Z")"
EXACT_DIGEST="$(printf '%s' "${EXACT_MANIFEST}" | jq -er '.digest')"
printf '%s\n' "${EXACT_DIGEST}"
printf '%s' "${EXACT_MANIFEST}" | jq -e '
  ([.manifests[] | select(.platform.os == "linux" and .platform.architecture == "amd64")] | length == 1)
  and
  ([.manifests[] | select(.platform.os == "linux" and .platform.architecture == "arm64")] | length == 1)
'
```

When this release owns `latest`, inspect it in the same way and require its digest to equal the accepted exact digest:

```bash
LATEST_DIGEST="$(docker buildx imagetools inspect --format '{{json .Manifest}}' "${IMAGE}:latest" | jq -er '.digest')"
test "${LATEST_DIGEST}" = "${EXACT_DIGEST}"
```

The public exact `X.Y.Z` tag is immutable by this descriptor digest. The workflow first creates a durable staging `X.Y.Z-candidate` index from the two staged platform indexes, preserving their complete descriptor multiset, including provenance and SBOM attachments. It then creates the public exact tag from that candidate digest. A rerun accepts an existing exact tag only when its retained candidate exists and has the same digest; it is then a no-op for the exact tag. A missing candidate or a different digest fails closed before either the exact tag or `latest` is changed. Inspect the public state and publish a new version for a materially different image.

The staging candidate is durable release provenance, not disposable cache. Package cleanup must retain `<staging-image>:X.Y.Z-candidate` while the corresponding immutable public exact tag is supported; losing it intentionally causes post-tag reruns to fail closed rather than guessing at equivalence from newly rebuilt mutable staging tags.

The temporary mounted-book workflow is also mandatory. It proves that the published public image can still perform one end-to-end bookkeeping/reporting loop, not just print discovery metadata. `trial-balance --output text` must render the posted Cash and Revenue rows for the seeded EUR 10.00 entry rather than failing in book initialization, key handling, or reporting. The same anonymous verification must also create an existing owner-only report parent and prove that `--pdf-out` publishes one valid PDF artifact there without replacing an existing target. Its text confirmation must name that artifact's canonical physical path (with only the standard redacted prefix), and `--output text --pdf-out ...` must replace the full report body with one artifact confirmation block on stdout. The mounted-workspace commands in that verifier must run the container as the caller's numeric `UID:GID`, matching the repo-owned `docker-smoke` contract, so bind-mounted key, book, and PDF artifacts remain owned and readable by the invoking operator on Linux hosts.

Because this verifier asserts text statement output and drives a real mounted-book initialization and posting path, it is part of the published bookkeeping contract, not just the container-publication machinery. When the text `trial-balance` layout changes — for example new bookkeeping columns appear — or when the mounted workflow grammar changes — for example `open-book` starts requiring additional identity flags or `post-entry` starts requiring new request fields — or when the mounted-workspace user contract changes — update `scripts/verify-public-container-surface.sh` and `scripts/test-verify-public-container-surface.sh` in the same change. Do not accept a release process where the operator-side verifier lags behind the published statement surface.

The release workflow's staged-container and promotion jobs run this same verifier after image publication, so a green publication workflow and this operator run now speak to the same public contract rather than two different verification depths.

If the public verifier fails, inspect the reported step, fix the published state or the verifier owner if the probe itself is wrong, and rerun the same anonymous verification command. Do not switch to the operator's normal Docker config as a fallback.

These checks are a second handoff checkpoint. Workflow success is not enough; public pull and run behavior is the authoritative state.

The public `ghcr.io/resoltico/fingrind` image receives the exact `X.Y.Z` tag and, only when that release is the newest stable release, the `latest` tag; there is no `X.Y` floating tag. `latest` is considered only after the exact tag has been accepted and directly verified, and it is copied from that accepted exact digest rather than from a mutable staging tag. The release workflow does not enforce a numeric GHCR retention window. Treat package retention and cleanup as separately administered operations, subject to the retained-candidate provenance requirement above, rather than inferring a retained-history guarantee from this publication flow.

Only after the full anonymous pull, run, mounted-book, and PDF verification sequence succeeds report to the user: the release is publicly available.

The release workflow is expected to perform the exact-tag pull-and-run verification internally after publication, and to perform the `latest` proof only when the release owns the canonical `latest` pointer. The operator-side verification remains mandatory because public availability, not workflow success, is the authoritative state.

Return to [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md) for post-publication PR hygiene and primary-checkout reconciliation.
