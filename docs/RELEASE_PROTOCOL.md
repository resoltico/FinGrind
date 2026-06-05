---
afad: "4.0"
version: "0.52.0"
domain: RELEASE_PROTOCOL
updated: "2026-06-05"
route:
  keywords: [fingrind, release, gh, github release, ghcr, tag, branch protection, protocol]
  questions: ["how do I release fingrind", "what is the fingrind release process", "how are github release and container publication handled in fingrind"]
---

# Release Protocol

The entire release flow is driven by the GitHub CLI (`gh`). Every step that touches GitHub —
PRs, CI status, merges, releases, workflow monitoring — uses `gh`, not the GitHub web UI.

**BEFORE DOING ANYTHING ELSE**, run both checks:

```bash
gh --version
gh auth status
```

If either command fails — `gh` is not installed, or `gh auth status` reports "not logged in" —
**STOP IMMEDIATELY**. Do not attempt any further steps. Notify the user:

> GitHub CLI (`gh`) is not available or not authenticated. The release procedure cannot
> continue. Please install `gh` and run `gh auth login` (this requires browser interaction
> and possibly 2FA, so it must be done by you, not by me). Once `gh auth status` reports a
> logged-in account with repo access, tell me to resume.

Do not attempt to resolve missing `gh` or authentication failures autonomously.

This document is the operator procedure. For publication topology, attestation invariants,
cross-platform canary behavior, and safe post-tag repair theory, use
[DEVELOPER_RELEASE_PUBLICATION.md](./DEVELOPER_RELEASE_PUBLICATION.md).

---

### Step 1

Pre-flight: verify release readiness.

Before any build, version edit, or release-branch work, identify the checkout the user will keep
using after the release. Call it the primary checkout.

Run:

```bash
git rev-parse --show-toplevel
git branch --show-current
git status --short
git fetch origin --prune --tags
git rev-list --left-right --count HEAD...origin/main
./scripts/verify-repo-hygiene.sh
```

Requirements before continuing:

- the primary checkout path is known explicitly
- the primary checkout must not be left behind `origin/main` at release closeout
- if the primary checkout is already clean and current, release from it directly
- if the primary checkout is dirty only because it already contains the intended release payload,
  continue in place, but first inspect that diff deliberately and move immediately onto
  `release/X.Y.Z` before doing more release edits; do not keep release work floating on `main`
- if the primary checkout has unrelated local work, is intentionally dirty for some other reason,
  lives on a problematic or slow filesystem, or fails `./scripts/verify-repo-hygiene.sh` for a
  reason other than intentional root-local scratch state, create a clean release worktree from the same
  repository and do the release there:

```bash
PRIMARY_CHECKOUT=$(git rev-parse --show-toplevel)
git fetch origin --prune --tags
RELEASE_WORKTREE="$(mktemp -d -t fingrind-release-XXXXXX)"
git worktree add "$RELEASE_WORKTREE" origin/main
cd "$RELEASE_WORKTREE"
```

Use a Git worktree, not a disconnected clone, whenever possible. A worktree shares refs with the
primary checkout and makes post-release reconciliation mechanically obvious. A separate clone is a
last resort and, if used, must still be reconciled back into the primary checkout before the
release session ends.

If `./scripts/verify-repo-hygiene.sh` fails because the primary checkout's Git object store is
corrupt or unreadable, a worktree is not sufficient because it shares the same repository
metadata. In that case, bootstrap a clean release clone from the remote and move the intended
release payload into it explicitly before running `./check.sh`:

```bash
REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
RELEASE_CLONE="$(mktemp -d -t fingrind-release-clone-XXXXXX)"
git clone "https://github.com/${REPO}.git" "$RELEASE_CLONE"
cd "$RELEASE_CLONE"
git fetch origin --prune --tags
git checkout -b release/X.Y.Z origin/main
```

If the primary checkout has unpublished local work, decide before the release whether that work is
real or stale. Real work that is not part of this release must move onto a named branch or
exported patch before closeout. Stale work must be dropped. Never leave the primary checkout on
stale `main` plus unpublished overlays.

If the primary checkout contains the real release payload but release verification must happen from
the clean release worktree, bootstrap that payload explicitly before you run any release build in
the worktree:

- preferred: move the unpublished release payload onto a local bootstrap branch, then create the
  release worktree from that branch
- acceptable: export one explicit patch from the primary checkout and apply it inside the clean
  release worktree before running checks

For example:

```bash
PRIMARY_CHECKOUT=$(git rev-parse --show-toplevel)
git diff --binary > /tmp/fingrind-release-bootstrap.patch
RELEASE_WORKTREE="$(mktemp -d -t fingrind-release-XXXXXX)"
git worktree add -b release/X.Y.Z "$RELEASE_WORKTREE" origin/main
cd "$RELEASE_WORKTREE"
git apply --index /tmp/fingrind-release-bootstrap.patch
```

If the unpublished payload includes new untracked release files, move them explicitly too — either
by committing them on the bootstrap branch or copying them into the release worktree before the
Step 2 staging checkpoint. Never fall back to running release verification from the dirty or
problematic primary checkout just because the unpublished release payload currently lives there.

If the primary checkout is blocked by another **live** FinGrind verification owner — for example
`./check.sh`, `./scripts/docker-smoke.sh`, or a long-running Jazzer wrapper already holds the
repo-wide verification lock — do not delete the lock by hand and do not start competing
verification in the same checkout. Either wait for the active owner to finish, or bootstrap the
release payload into a clean worktree and run the release gate there. A live lock owner is a
checkout-availability problem, not proof that the release payload is bad.

Run `./scripts/verify-repo-hygiene.sh`. It must exit 0. If it fails, repair the checkout or
move the release into a clean clone before proceeding. If it reports Git coordination lock files,
inspect ownership with `lsof` before removing anything: delete only orphaned lock files, and treat
any owned lock as proof that another Git owner has the checkout open. If it reports a persisted
`gc.log`, complete manual Git housekeeping first and remove that log only after a successful
`git gc` or equivalent cleanup.

Then run `./check.sh`. It must exit 0. If it fails, fix all failures before proceeding.
That gate now also proves the repo-owned JaCoCo snapshot contract: the pinned snapshot base
version, the published build label in `gradle/fingrind-build.properties`, and the resolved
artifact coordinates must align through `./scripts/verify-jacoco-snapshot.sh`, and the exact
pinned jars must stage successfully through `prepareJacocoSnapshotArtifacts`, before the Gradle
stages run.
Because this baseline gate runs before `./scripts/prepare-release-version.sh X.Y.Z YYYY-MM-DD`,
any bundle archive names, Docker smoke echoes, or distribution manifests produced in Step 1 will
reflect the checkout's pre-sweep version string. That is expected. Treat Step 1 as a payload
health check and Step 2's post-sweep `./check.sh` rerun as the authoritative release-version
verification pass.

Then verify every non-version item in this checklist. These repository and runtime conditions must
be true before any release commit or tag:

- `README.md` does not reference any prior version's container tags.
- All example JSON files use the current wire names and field shapes for this version.
- GitHub repository settings are still aligned with this procedure:
  - default branch is `main`
  - `delete_branch_on_merge` is enabled
  - `main` is protected with admin enforcement
  - required status checks are exactly `Gate` (the single aggregate check that subsumes all CI jobs)

Before cutting the release branch, enumerate open PRs so dependency-automation work is never
surprise-discovered after publication:

```bash
gh pr list --state open \
  --json number,title,url,headRefName,mergeStateStatus,isDraft,author,statusCheckRollup
```

If any open PR is authored by Dependabot, decide up front whether it changes release machinery or
release-critical dependencies. GitHub currently reports these PRs through `gh pr list` with
`author.login` set to `app/dependabot`; older surfaces may still render `dependabot[bot]`. If the
PR is release-critical, land or reject it before cutting the release branch. If it is not, carry
that decision forward and complete Step 10 before ending the release session.

If you merge or close one release-critical PR, re-enumerate the remaining open PRs before acting
on the next one. A changed `main` branch can invalidate sibling merge state or required-check
evaluations.

If the primary checkout is currently on an open PR branch and that branch's payload is being
absorbed into `release/X.Y.Z`, do not keep driving the release from the PR branch name. Branch to
`release/X.Y.Z` immediately and treat the original PR as provisional theory only. If the release
PR later ships the same payload, Step 10 must close the superseded PR and delete its branch unless
it still contains material that is not in `main`.

If Step 1 merges a release-critical PR and the primary checkout already contains the intended
release payload as uncommitted local changes, do **not** try to `git pull` that dirty `main`
checkout in place. Instead:

```bash
git checkout -b release/X.Y.Z
git add <intended release payload>
git commit -m "<descriptive payload commit>"
git fetch origin --prune --tags
```

Then integrate the newly changed `origin/main` onto `release/X.Y.Z` before running the release
version sweep. Prefer a normal rebase or merge when Git allows it. If local platform or Git
checkout behavior makes those operations fail despite a clean index, replay the newly landed
release-critical commit(s) onto `release/X.Y.Z` explicitly, resolve any conflicts deliberately,
and only then continue with Step 2. Never leave the intended release payload on dirty `main`, and
never assume a pre-merge Step 1 check remains authoritative after `origin/main` changed.

### Step 2

Commit on a release branch.

`main` is branch-protected. Never attempt `git push origin main` directly — it will be
rejected and wastes time. Always commit on a release branch.

```bash
git checkout -b release/X.Y.Z
./scripts/prepare-release-version.sh X.Y.Z YYYY-MM-DD
./check.sh
git add <every modified file that belongs in the release>
git status --short
git diff --cached --name-status
git diff --cached --stat
git commit -m "release: bump version to X.Y.Z"
git push origin release/X.Y.Z
```

Treat staging as a handoff checkpoint, not a formality. Before committing:

- if Step 1 continued in place from a dirty primary checkout, the branch creation above is the
  point where the release payload stops living on `main`; do not switch back to dirty `main`
- `./scripts/prepare-release-version.sh X.Y.Z YYYY-MM-DD` must be the canonical release-prep
  edit step; do not hand-edit scattered version-bearing files when the scripted sweep can do it
- after the version sweep, `gradle.properties` `version=` equals the target release version
  exactly (for example `X.Y.Z`)
- repo-owned agent metadata such as `AGENTS.md` and `.codex/**` stays versioned in Git when it
  belongs to the release, but it remains `export-ignore`d from GitHub source archives and is not
  part of the public bundle or container asset set
- all `docs/*.md` frontmatter `version:` fields equal the target release version
- all touched `docs/*.md` frontmatter `updated:` fields equal the release date used in the
  scripted sweep
- all user-facing archive names, release examples, and version-pinned tests now reference the
  target release version
- the rerun of `./check.sh` exits 0 after the version-bearing edits
- `git status --short` must show no intended release file left unstaged or untracked.
- `git diff --cached --name-status` must show the exact file set expected for the release.
- `git diff --cached --stat` must confirm that the staged payload includes both versioning or
  docs changes and every production, test, workflow, or script change that belongs in the
  release.

If the staged diff is incomplete or includes unintended files, fix the branch before committing.
Do not rely on memory alone to decide what is in the release.
If either staged diff command fails because Git cannot read a blob or tree object, treat that as
an unreadable object-store defect in the current checkout. Do not force the release forward from
that repository. Bootstrap a clean release clone from the remote, move the exact release payload
into it explicitly, rerun `./check.sh`, and continue the release from the clone after the staged
diff checkpoint succeeds there.

### Step 3

Open PR and wait for CI.

```bash
gh pr create \
  --title "release: bump version to X.Y.Z" \
  --base main \
  --head release/X.Y.Z \
  --body "..."
```

Note the PR number returned. Then poll CI until the required checks pass:

```bash
gh pr diff <N> --name-only
gh pr view <N> --json number,state,mergeStateStatus,url
./scripts/verify-release-pr-gate.sh <N>
```

If `gh pr diff <N> --name-only` fails with GitHub's oversized-diff response
(`PullRequest.diff too_large` / HTTP 406), fall back to the paginated pull-files API instead of
guessing:

```bash
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
gh api "repos/$REPO/pulls/<N>/files" --paginate --jq '.[].filename'
```

Treat the PR itself as a second scope-verification checkpoint:

- `gh pr diff <N> --name-only` must match the intended release file set.
- If `gh pr diff <N> --name-only` returns `PullRequest.diff too_large`, the paginated
  `gh api "repos/$REPO/pulls/<N>/files" --paginate --jq '.[].filename'` fallback becomes the
  authoritative file-set check for this step.
- If the PR diff is missing files or includes unintended files, fix the release branch before
  waiting on CI or merging.
- Every new commit pushed to the release branch reopens both the Step 2 staging checkpoint and
  this PR diff checkpoint. Re-verify both after each fix commit.

Do not proceed until `./scripts/verify-release-pr-gate.sh <N>` succeeds for the release PR. `Gate`
is the single authoritative required check for release promotion, and the verifier checks the PR
head commit directly instead of inferring readiness from `statusCheckRollup`.

The aggregate `Gate` check run appears only after `Check`, the published Linux bundle-smoke
matrix, and the devcontainer gate pair have finished or been skipped in workflow `CI`. A PR can
therefore show `Check` green while `Gate` is absent. Treat a missing `Gate` as pending, not as
success. The verifier is the canonical owner of that waiting logic.

If `./scripts/verify-release-pr-gate.sh <N>` reports a failing `Gate`, fix the failure, push to the
release branch, and run the verifier again — do not merge a red PR.

The verifier's default wait is sized for the normal PR-side CI fan-out where the aggregate `Gate`
arrives after the slower sibling jobs finish, especially the published Linux bundle-smoke matrix.
If GitHub Actions queueing is unusually slow, extend the wait explicitly instead of guessing:

The separate Windows non-public bundle smoke job remains an observational lane only. It does not
participate in the release-blocking `Gate` contract because public self-contained bundle
publication is Linux-only.

```bash
FINGRIND_RELEASE_CHECK_TIMEOUT_SECONDS=3000 ./scripts/verify-release-pr-gate.sh <N>
```

### Step 4

Merge PR and verify the merge handoff.

```bash
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
gh pr merge <N> --repo "$REPO" --merge --admin --delete-branch \
  --subject "release: bump version to X.Y.Z (#N)"
git fetch origin main
git switch --detach origin/main
./scripts/verify-release-merge-handoff.sh
gh pr view <N> --repo "$REPO" --json number,state,mergedAt,headRefName,baseRefName,url
```

The `--admin` flag uses administrator privileges to bypass branch-protection requirements,
specifically the review-approval rule that GitHub prevents the PR author from satisfying.
This is the GitHub-intended escape hatch for single-owner repositories where an agent drives
the release end-to-end. CI status checks remain the authoritative quality gate; the review
requirement adds no signal in a solo-owner workflow.

Requirements before continuing:

- PR state is `MERGED`.
- `mergedAt` is populated.
- The checked-out verifier commit contains the merge commit you expect.
- The checkout used for `./scripts/verify-release-merge-handoff.sh` exactly matches `origin/main`.
- The remote release branch is deleted by the merge step.
- `./scripts/verify-release-merge-handoff.sh` succeeds on the merged `main` commit, which means
  the canonical `Gate` check is green on the exact commit that will be tagged.

The verifier's default wait is intentionally long enough to cover the normal post-merge CI
fan-out where the published Linux bundle-smoke matrix follows `Check`. If GitHub Actions queueing
is unusually slow, extend the wait explicitly instead of guessing:

The separate Windows non-public bundle smoke lane remains observational. The canonical release
gate remains single-owned by `Check` plus the Linux publication-proof matrix.

```bash
FINGRIND_RELEASE_CHECK_TIMEOUT_SECONDS=3600 ./scripts/verify-release-merge-handoff.sh
```

GitHub auto-delete on merge should also be enabled at the repository level. `--delete-branch`
remains mandatory here so the release handoff stays self-contained even if the repo setting is
misconfigured or temporarily changed.

If the release is being driven from a dedicated worktree while the primary checkout already has
`main` checked out, do not rely on `gh pr merge` or `git checkout main` in the auxiliary
worktree without an explicit repository and detached-head plan. In that topology `gh pr merge`
can invoke local git operations that fail with:

```text
fatal: 'main' is already checked out at '/path/to/primary-checkout'
```

In worktree mode, prefer `gh pr merge --repo "$REPO"` so the GitHub-side merge is independent
of the local branch-checkout topology, then verify the merge handoff from any checkout whose
`HEAD` exactly matches `origin/main`. A detached `origin/main` checkout in the release worktree
is acceptable for Step 4. Step 11 remains the place where the primary checkout itself must be
returned to a truthful `main`.

Also, do not treat a non-zero `gh pr merge` exit as proof that the merge failed. The server-side
merge can succeed before `gh` trips over a local git follow-up step. After any merge-command
error, immediately inspect the PR directly:

```bash
gh pr view <N> --repo "$REPO" --json number,state,mergedAt,headRefName,baseRefName,url
```

If the PR already shows `state=MERGED` with `mergedAt` populated, treat GitHub's merged state as
authoritative, do not retry the merge, and continue with the post-merge verification and branch
hygiene steps.

The release branch must not be left behind. If the local `release/X.Y.Z` branch still exists
after the merge, delete it manually with:

```bash
git branch -d release/X.Y.Z
```

### Step 5

Create the tag, push it, and verify the tag handoff.

```bash
git tag vX.Y.Z
git push origin vX.Y.Z

REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
gh api "repos/$REPO/git/ref/tags/vX.Y.Z"
./scripts/verify-release-candidate-tag.sh vX.Y.Z
```

Do not proceed until the remote tag ref exists. Never infer a successful tag push from the
absence of a local git error alone — verify the remote ref through GitHub.

`./scripts/verify-release-candidate-tag.sh vX.Y.Z` is mandatory here. It proves the checked-out
commit matches the remote tag, the tag version matches `gradle.properties`, the tag commit is
still reachable from `origin/main`, and the release-blocking CI set is still green on that exact
commit before any publication workflow is trusted.

The tag push is what triggers the `Release` workflow. The PR merge alone does not. The same
workflow now owns bundle publication, GitHub Release verification, container publication, and
public-container verification.

If either publication workflow later needs a targeted rerun against the existing tag, use:

```bash
gh workflow run release.yml -f release_tag=vX.Y.Z
```

If the tag-triggered `Release` workflow fails because the workflow definition itself is wrong —
for example a missing permission, broken verifier wiring, or an incorrect publication timeout —
do **not** move the tag and do **not** cut a second release tag. Fix the workflow on `main`,
merge that fix, and then use the `workflow_dispatch` rerun command above against the existing
`vX.Y.Z` tag so the rebuilt assets and container are produced from the same verified release
commit.

The rerun workflow now reads the canonical bundle-archive manifest instead of scraping
`:cli:bundleCliArchive` console output, so post-tag publication repairs do not need compatibility
shims for historical log dialects.

The draft-first GitHub release publisher must also wait for a freshly created draft release to
become visible through the Releases API before it inspects or mutates draft assets. A successful
`gh release create` call is not, by itself, proof that the follow-up asset convergence queries can
already observe that draft.

The neutral attestation and verification jobs must also download draft assets through the
repo-owned `./scripts/download-github-release-assets.sh` seam rather than `gh release download
<tag>`. GitHub can expose a draft release object that tag-based `gh release view` resolves while
tag-based `gh release download` still reports `release not found` until finalization.
Those draft-downloading jobs must also keep write-scoped `contents` permission. In the live
workflow surface, a read-scoped Actions token can list the draft release while failing to fetch
the staged asset bytes themselves.
Those neutral jobs must also resolve repo-owned downloader, verifier, and finalizer helpers from
the workflow-owner helper checkout during `workflow_dispatch` reruns; the repair theory is not
real if the rerun keeps calling stale tag-local helper scripts after `main` fixes them.

If the repair changes container publication, verify that the `Release` workflow's staging-container
build job builds from the staged Docker context under the active CLI build root instead of the
repository root before dispatching the rerun. The local Docker acceptance gate
already proves that staged context boundary; the public container publication path must reuse that
same checked assembly input rather than reopening the checkout root under repository-root
`.dockerignore` rules.
If helper-rooted rerun scripts participate in that staged-container path, they must receive the
tagged checkout root explicitly. The helper checkout on `main` owns repaired release-control
scripts; the tagged checkout owns the staged Docker context and the source inputs those scripts
must verify.

Never create a second tag or move an existing release tag just to retry CI.

The `Release` workflow also runs `./scripts/verify-release-candidate-tag.sh` internally before it
builds or publishes anything. That guard is intentional: tag-driven release publication must not
depend on an operator remembering the verification step only from the prose protocol.

### Step 6

Branch hygiene.

After the merge and tag push, clean up stale remote-tracking refs and verify that no historical
release branches remain on GitHub.

```bash
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
git remote prune origin
gh api "repos/$REPO/branches" --paginate --jq '.[].name'
```

Requirements:

- No `release/X.Y.Z` branch may remain on GitHub after the merge.
- No historical `release/` branches may remain on GitHub. If any are present, delete them:

```bash
git push origin --delete release/A.B.C
```

- No fully merged local `release/` branches may remain. Delete them:

```bash
git branch -d release/A.B.C
```

Do not leave release-branch leftovers behind locally or remotely. Branch hygiene is part of the
release procedure, not optional cleanup.

Open maintenance branches such as Dependabot are handled separately in Step 10. Do not treat a
non-`release/` branch as automatically acceptable just because Step 6 only hard-fails
`release/*` leftovers.

### Step 7

Monitor workflows with duplicate-run awareness.

```bash
TAG_SHA=$(git rev-list -n 1 vX.Y.Z)
gh run list --workflow=release.yml --commit "$TAG_SHA" --event=push --limit=10
```

Do not assume there is exactly one run per workflow. A single tag push may produce multiple runs
for the same workflow. Treat the workflow boundary as a **handoff checkpoint**:

1. Resolve the tag commit with `git rev-list -n 1 vX.Y.Z`.
2. Enumerate **all** `release.yml` runs for that commit.
3. Inspect each run that is not `completed/success` with:

```bash
gh run view <run-id> --log-failed
```

4. Verify the external GitHub state directly before deciding the release is failed.

Rules:

- Never treat one failed run as authoritative if another sibling run for the same tag succeeded.
- Never re-run blindly. First inspect whether the desired state already exists.
- A release-workflow failure with `Release.tag_name already exists` is **not** automatically a
  release failure. It may mean a sibling run already created the release successfully.
- Only classify the release workflow as failed if **no** run produced the required external
  state and direct GitHub inspection confirms that state is absent or incomplete.

Fix the root cause only after the direct-state inspection proves the release or container state
is actually missing or incorrect. Coordinate with the user if the failure is in CI infrastructure
outside this codebase.

When duplicate runs are observed for the same workflow, tag, and commit, classify the **source**
of the duplicate dispatch separately from the **safety** of the publication system:

- The source may be a user- or tool-driven duplicate tag push, a client retry, or a GitHub
  Actions delivery anomaly.
- Unless GitHub audit evidence proves which one occurred, treat the source as externally
  ambiguous. Do not present guesswork as certainty.
- Inside this repository, the required engineering response is still deterministic: the workflows
  must remain safe under duplicate dispatch. Concurrency, idempotent publication, and direct
  post-publication verification are mandatory.

### Step 8

Verify the GitHub Release handoff.

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
  - `fingrind-X.Y.Z-linux-x86_64.tar.gz`
  - `fingrind-X.Y.Z-linux-x86_64.tar.gz.sha256`
  - `fingrind-X.Y.Z-linux-aarch64.tar.gz`
  - `fingrind-X.Y.Z-linux-aarch64.tar.gz.sha256`
- Targets disclosed through
  `environment.publication.unsupportedPublicCliBundleTargets` such as the current `macos-*` and
  `windows-*` entries must not appear as release assets unless the public-distribution contract
  changes first.
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

### Step 9

Verify public availability.

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
anonymous verification must also prove that `--pdf-out` writes one valid PDF artifact to the
mounted workspace. The mounted-workspace commands in that verifier must run the container as the
caller's numeric `UID:GID`, matching the repo-owned `docker-smoke` contract, so bind-mounted key,
book, and PDF artifacts remain owned and readable by the invoking operator on Linux hosts.

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
publication, so a green publication workflow and a green Step 9 operator run now speak to the
same public contract rather than two different verification depths.

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

### Step 10

Triage leftover PRs and clear dependency-automation leftovers.

After the public release is verified, do not end the release session while open PRs that were
reviewed during the release are left in an ambiguous state. Release hygiene includes both
dependency-automation hygiene and cleanup of ordinary PRs that the release branch superseded.

Re-enumerate all open PRs and identify Dependabot-owned entries directly from GitHub metadata:

```bash
gh pr list --state open \
  --json number,title,url,headRefName,mergeStateStatus,isDraft,author,statusCheckRollup
```

Treat any PR whose `author.login` identifies Dependabot as in scope for this step, even if it was
already reviewed during Step 1. Today that means `app/dependabot`; older GitHub surfaces may show
`dependabot[bot]`. Step 1 creates the release-time decision; Step 10 closes the loop before the
release session is allowed to end.

For each open Dependabot PR, inspect the exact payload and its current gate status:

```bash
gh pr diff <N> --name-only
gh pr view <N> --json number,title,state,mergeStateStatus,statusCheckRollup,url
```

Rules:

- If the PR is wanted, mergeable, and already green on the required `CI` checks, merge it
  immediately and delete its branch:

```bash
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
gh pr merge <N> --repo "$REPO" --merge --admin --delete-branch --subject "<title> (#<N>)"
```

- If the PR is stale, superseded by `main`, intentionally rejected, or replaced by a different
  change path, close it explicitly and delete its branch:

```bash
gh pr close <N> --comment "Superseded or intentionally rejected during release hygiene." --delete-branch
```

- If the PR needs follow-up work before it is acceptable, do that work as a normal post-release
  change on `main` and then land or replace the Dependabot PR. Do not leave a green but
  unattended Dependabot PR parked indefinitely just because the release itself already shipped.

- Never retag, amend, or move the just-published release tag to absorb a Dependabot change. The
  published release remains immutable. Dependabot resolution is post-release `main` hygiene.

- There is no "ignore it and leave the branch there" option. Every open Dependabot PR must end
  this step in exactly one of these states:
  - merged and branch deleted
  - closed and branch deleted
  - consciously kept open with an explicit still-valid reason

After the Dependabot pass, inspect any remaining open non-Dependabot PR that overlaps the shipped
release. This includes the common case where an earlier release-critical PR branch was used as the
starting theory for the final `release/X.Y.Z` branch. For each such PR:

- If `main` now contains the PR payload and the open PR no longer carries material beyond the
  shipped release, close it explicitly and delete its branch:

```bash
gh pr close <N> --comment "Superseded by the published release branch and now present in main." --delete-branch
```

- If the PR remains open, verify that it still differs materially from `main` and record the
  keep-open reason in a PR comment. A branch that is only a stale precursor to the shipped release
  must not remain open.

After each merge or close, resync and re-check GitHub branch state:

```bash
git fetch origin --prune
git switch --detach origin/main
git remote prune origin
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
gh api "repos/$REPO/branches" --paginate --jq '.[].name'
```

Requirements before declaring the release session complete:

- No stale Dependabot PR may remain open without an explicit keep-open decision.
- No superseded ordinary PR may remain open after release hygiene.
- No merged or closed branch handled in this step may remain on GitHub.
- Any remaining non-`main` branch on GitHub must correspond to an intentional still-open PR that
  was reviewed during this step and deliberately kept alive.

### Step 11

Reconcile the primary checkout.

If the release used a dedicated release worktree or any checkout other than the primary checkout,
the session is not complete until the primary checkout is truthful again. This step is now a
scripted gate, not a reminder. Do not declare the release complete until the verifier passes.

If unpublished local work from the primary checkout is still needed, move it onto a named branch
based on current `main` first, then return the primary checkout itself to `main`.

Run:

```bash
git -C "$PRIMARY_CHECKOUT" checkout main
git -C "$PRIMARY_CHECKOUT" merge --ff-only origin/main
./scripts/verify-release-primary-checkout.sh "$PRIMARY_CHECKOUT" "X.Y.Z"
```

The verifier is authoritative. It fetches `origin`, requires the primary checkout to be on `main`,
requires `HEAD` to equal `origin/main`, checks that `gradle.properties` and `CHANGELOG.md` reflect
the released version, rejects tracked overlays, and rejects unexpected untracked debris outside the
repo's explicit scratch prefixes.

Requirements before declaring the release session complete:

- `./scripts/verify-release-primary-checkout.sh "$PRIMARY_CHECKOUT" "X.Y.Z"` exits 0
- no stale release-only checkout may be left behind with the appearance of being authoritative
- if unpublished local work from the primary checkout is still needed, replay it deliberately onto
  a named branch based on current `main`; do not leave it only in a stash or mixed into `main`
- if that unpublished local work is stale, superseded, or regresses the shipped release state,
  delete it instead of preserving misleading debris

If a disposable release worktree was created and is no longer needed:

```bash
git worktree remove "$RELEASE_WORKTREE"
```

---

## Dependabot Approval Strategy

FinGrind is a financial application. **No Dependabot PR may be auto-merged.** Every update —
regardless of ecosystem, scope, or whether it is flagged as a security fix — requires an operator
decision before landing on `main`.

### Triage tiers

| Tier | Trigger | Deadline | Action |
|:-----|:--------|:---------|:-------|
| **Security** | Dependabot security advisory on any direct or transitive dependency | Within 7 calendar days of PR open | Review, verify CI passes, merge or reject with documented reason |
| **Regular** | Non-security weekly update | Before the next release | Review during Step 10 Dependabot hygiene; merge or close |
| **Major version bump** | `semver-major` update on any ecosystem | Before the next release | Treat as a considered upgrade, not a routine bump; verify API compatibility explicitly |

### Required gates before any Dependabot merge

1. The full CI `Gate` check passes on the Dependabot PR head commit, and every release-blocking
   job in `ci.yml` concluded with `success`.
2. For Docker base image updates: `docker-smoke` specifically passes, confirming the new base image does not break the containerized runtime.
3. For Gradle dependency updates that touch `sqlite` or `sqlite3mc`: the `Verify managed SQLite CLI runtime` step in `check` passes and the managed SQLite hash in `gradle.properties` is still consistent.
4. For GitHub Actions updates: the pinned commit SHA in the workflow file matches the SHA of the tagged release being adopted — verify with `gh api repos/<owner>/<repo>/git/ref/tags/<tag>`.

### What to never do

- Never merge a Dependabot PR that has a failing or missing `Gate` check.
- Never merge a Dependabot PR that changes the SQLite native library without verifying the managed runtime still initializes correctly.
- Never retag or amend a published release to absorb a post-release Dependabot merge.
- Never leave a Dependabot PR open indefinitely without an explicit keep-open reason documented in a PR comment.
