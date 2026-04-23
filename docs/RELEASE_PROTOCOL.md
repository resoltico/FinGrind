---
afad: "3.5"
version: "0.24.0"
domain: RELEASE_PROTOCOL
updated: "2026-04-23"
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
```

Requirements before continuing:

- the primary checkout path is known explicitly
- the primary checkout must not be left behind `origin/main` at release closeout
- if the primary checkout is already clean and current, release from it directly
- if the primary checkout is dirty only because it already contains the intended release payload,
  continue in place, but first inspect that diff deliberately and move immediately onto
  `release/X.Y.Z` before doing more release edits; do not keep release work floating on `main`
- if the primary checkout has unrelated local work, is intentionally dirty for some other reason,
  or lives on a problematic or slow filesystem, create a clean release worktree from the same
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

If the primary checkout has unpublished local work, decide before the release whether that work is
real or stale. Real work that is not part of this release must move onto a named branch or
exported patch before closeout. Stale work must be dropped. Never leave the primary checkout on
stale `main` plus unpublished overlays.

Run `./check.sh`. It must exit 0. If it fails, fix all failures before proceeding.

Then verify every non-version item in this checklist. These repository and runtime conditions must
be true before any release commit or tag:

- `README.md` does not reference any prior version's container tags.
- All example JSON files use the current wire names and field shapes for this version.
- GitHub repository settings are still aligned with this procedure:
  - default branch is `main`
  - `delete_branch_on_merge` is enabled
  - `main` is protected with admin enforcement
  - required status checks are exactly `Check`, `Windows bundle smoke`, and `Docker smoke`

Before cutting the release branch, enumerate open PRs so dependency-automation work is never
surprise-discovered after publication:

```bash
gh pr list --state open \
  --json number,title,url,headRefName,mergeStateStatus,isDraft,author,statusCheckRollup
```

If any open PR is authored by `dependabot[bot]`, decide up front whether it changes release
machinery or release-critical dependencies. If it does, land or reject it before cutting the
release branch. If it does not, carry that decision forward and complete Step 10 before ending
the release session.

If you merge or close one release-critical PR, re-enumerate the remaining open PRs before acting
on the next one. A changed `main` branch can invalidate sibling merge state or required-check
evaluations.

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
git add <every modified file that belongs in the release — never .codex/>
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
gh pr view <N> --json number,state,mergeStateStatus,statusCheckRollup,url
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

Do not proceed until **every** required job in workflow `CI` has `"conclusion": "SUCCESS"`.
At the time of writing that means `Check`, `Windows bundle smoke`, and `Docker smoke`.
If any required job fails, fix the failure, push to the release branch, and wait again — do not
merge a red PR.

### Step 4

Merge PR and verify the merge handoff.

```bash
gh pr merge <N> --merge --admin --delete-branch --subject "release: bump version to X.Y.Z (#N)"
git checkout main
git pull
gh pr view <N> --json number,state,mergedAt,headRefName,baseRefName,url
```

The `--admin` flag uses administrator privileges to bypass branch-protection requirements,
specifically the review-approval rule that GitHub prevents the PR author from satisfying.
This is the GitHub-intended escape hatch for single-owner repositories where an agent drives
the release end-to-end. CI status checks remain the authoritative quality gate; the review
requirement adds no signal in a solo-owner workflow.

Requirements before continuing:

- PR state is `MERGED`.
- `mergedAt` is populated.
- Local `main` contains the merge commit you expect.
- The remote release branch is deleted by the merge step.

GitHub auto-delete on merge should also be enabled at the repository level. `--delete-branch`
remains mandatory here so the release handoff stays self-contained even if the repo setting is
misconfigured or temporarily changed.

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
```

Do not proceed until the remote tag ref exists. Never infer a successful tag push from the
absence of a local git error alone — verify the remote ref through GitHub.

The tag push is what triggers the Release and Container workflows. The PR merge alone does
not. These are two separate actions — both are required.

If either publication workflow later needs a targeted rerun against the existing tag, use:

```bash
gh workflow run release.yml -f release_tag=vX.Y.Z
gh workflow run container.yml -f release_tag=vX.Y.Z
```

Never create a second tag or move an existing release tag just to retry CI.

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
gh run list --workflow=container.yml --commit "$TAG_SHA" --event=push --limit=10
```

Do not assume there is exactly one run per workflow. A single tag push may produce multiple runs
for the same workflow. Treat the workflow boundary as a **handoff checkpoint**:

1. Resolve the tag commit with `git rev-list -n 1 vX.Y.Z`.
2. Enumerate **all** `release.yml` runs for that commit.
3. Enumerate **all** `container.yml` runs for that commit.
4. Inspect each run that is not `completed/success` with:

```bash
gh run view <run-id> --log-failed
```

5. Verify the external GitHub state directly before deciding the release is failed.

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

If these conditions are satisfied, the GitHub Release handoff is complete even if an additional
duplicate release workflow run failed after the release was already created.

The release workflow is expected to perform this same verification internally after publication.
The operator-side `gh release view` check remains mandatory because workflow success is still not
the authoritative state.

The container workflow is also expected to wait for this complete GitHub release asset set before
it publishes the public image. If container publication succeeds while the release asset set is
incomplete, treat that as a release-system defect and fix the repository before the next release.

### Step 9

Verify public availability.

Do not declare the release done until the GitHub Release exists and the operator-side public
container surface verifier succeeds. The verifier uses a temporary Docker config directory so you
are testing the public surface, not cached owner credentials, and so you do not mutate the
operator's normal Docker login state. It also uses machine-readable `version --output json`
checks plus exact human trial-balance row assertions so the operator is not left guessing about
free-form CLI text:

```bash
gh release view vX.Y.Z
./scripts/verify-public-container-surface.sh ghcr.io/resoltico/fingrind X.Y.Z
```

The verifier retries anonymous exact-tag and `latest` pulls until both containers report the
target release version through `version --output json`. A successful `docker pull` alone is not
sufficient verification. In particular: a multi-arch `docker pull` can succeed even when the
platform manifests have been deleted — the index manifest is still present but the image is not
actually runnable. The `docker run ... version --output json` check is the definitive test.

The temporary mounted-book workflow is also mandatory. It proves that the published public image
can still perform one end-to-end bookkeeping/reporting loop, not just print discovery metadata.
`trial-balance --output human` must render the posted Cash and Revenue rows for the seeded EUR
10.00 entry rather than failing in book initialization, key handling, or reporting. The same
anonymous verification must also prove that `--pdf-out` writes one valid PDF artifact to the
mounted workspace.

If the public verifier fails, inspect the reported step, fix the published state, and rerun the
same anonymous verification command. Do not switch to the operator's normal Docker config as a
fallback.

These checks are a second handoff checkpoint. Workflow success is not enough; public pull and run
behavior is the authoritative state.

The container registry retains the last 5 releases. Only `X.Y.Z` and `latest` tags are
published per release; there is no `X.Y` floating tag.

Only after the full anonymous pull, run, mounted-book, and PDF verification sequence succeeds
report to the user: the release is publicly available.

The container workflow is expected to perform the exact-tag and `latest` pull-and-run verification
internally after publication. The operator-side verification remains mandatory because public
availability, not workflow success, is the authoritative state.

### Step 10

Triage Dependabot PRs and clear dependency-automation leftovers.

After the public release is verified, do not end the release session while open Dependabot PRs are
still sitting untriaged. Release hygiene includes dependency-automation hygiene.

Re-enumerate all open PRs and identify Dependabot-owned entries directly from GitHub metadata:

```bash
gh pr list --state open \
  --json number,title,url,headRefName,mergeStateStatus,isDraft,author,statusCheckRollup
```

Treat any PR whose `author.login` is `dependabot[bot]` as in scope for this step, even if it was
already reviewed during Step 1. Step 1 creates the release-time decision; Step 10 closes the loop
before the release session is allowed to end.

For each open Dependabot PR, inspect the exact payload and its current gate status:

```bash
gh pr diff <N> --name-only
gh pr view <N> --json number,title,state,mergeStateStatus,statusCheckRollup,url
```

Rules:

- If the PR is wanted, mergeable, and already green on the required `CI` checks, merge it
  immediately and delete its branch:

```bash
gh pr merge <N> --merge --admin --delete-branch --subject "<title> (#<N>)"
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

After each merge or close, resync and re-check GitHub branch state:

```bash
git checkout main
git pull
git remote prune origin
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
gh api "repos/$REPO/branches" --paginate --jq '.[].name'
```

Requirements before declaring the release session complete:

- No stale Dependabot PR may remain open without an explicit keep-open decision.
- No merged or closed Dependabot branch may remain on GitHub.
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
