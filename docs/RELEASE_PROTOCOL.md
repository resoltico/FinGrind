---
afad: "3.5"
version: "0.2.0"
domain: RELEASE_PROTOCOL
updated: "2026-04-08"
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

Run `./check.sh`. It must exit 0. If it fails, fix all failures before proceeding.

Then verify every item in this checklist. All must be true before any commit or tag:

- `gradle.properties` `version=` equals the target release version exactly (for example `0.3.0`).
- All `docs/*.md` frontmatter `version:` fields equal the target version.
- `README.md` does not reference any prior version's container tags.
- All example JSON files use the current wire names and field shapes for this version.
- GitHub repository settings are still aligned with this procedure:
  - default branch is `main`
  - `delete_branch_on_merge` is enabled
  - `main` is protected with admin enforcement
  - required status checks are exactly `Check` and `Docker smoke`

### Step 2

Commit on a release branch.

`main` is branch-protected. Never attempt `git push origin main` directly — it will be
rejected and wastes time. Always commit on a release branch.

```bash
git checkout -b release/X.Y.Z
git add <every modified file that belongs in the release — never .claude/>
git status --short
git diff --cached --name-status
git diff --cached --stat
git commit -m "release: bump version to X.Y.Z"
git push origin release/X.Y.Z
```

Treat staging as a handoff checkpoint, not a formality. Before committing:

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

Treat the PR itself as a second scope-verification checkpoint:

- `gh pr diff <N> --name-only` must match the intended release file set.
- If the PR diff is missing files or includes unintended files, fix the release branch before
  waiting on CI or merging.
- Every new commit pushed to the release branch reopens both the Step 2 staging checkpoint and
  this PR diff checkpoint. Re-verify both after each fix commit.

Do not proceed until **every** required job in workflow `CI` has `"conclusion": "SUCCESS"`.
At the time of writing that means `Check` and `Docker smoke`.
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

### Step 7

Monitor workflows with duplicate-run awareness.

```bash
gh run list --workflow=release.yml --branch=vX.Y.Z --event=push --limit=10
gh run list --workflow=container.yml --branch=vX.Y.Z --event=push --limit=10
```

Do not assume there is exactly one run per workflow. A single tag push may produce multiple runs
for the same workflow. Treat the workflow boundary as a **handoff checkpoint**:

1. Enumerate **all** runs for `release.yml` on `vX.Y.Z`.
2. Enumerate **all** runs for `container.yml` on `vX.Y.Z`.
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
```

Requirements:

- The release exists for tag `vX.Y.Z`.
- `isDraft` is `false`.
- `isPrerelease` is `false` unless the target release is intentionally a prerelease.
- The fat JAR asset is present (currently `fingrind.jar`).

If these conditions are satisfied, the GitHub Release handoff is complete even if an additional
duplicate release workflow run failed after the release was already created.

The release workflow is expected to perform this same verification internally after publication.
The operator-side `gh release view` check remains mandatory because workflow success is still not
the authoritative state.

### Step 9

Verify public availability.

Do not declare the release done until the GitHub Release exists and the following anonymous
Docker verification succeeds. Use a temporary Docker config directory so you are testing the
public surface, not cached owner credentials, and so you do not mutate the operator's normal
Docker login state:

```bash
ANON_DOCKER_CONFIG="$(mktemp -d)"
gh release view vX.Y.Z
DOCKER_CONFIG="$ANON_DOCKER_CONFIG" docker pull ghcr.io/resoltico/fingrind:X.Y.Z
DOCKER_CONFIG="$ANON_DOCKER_CONFIG" docker pull ghcr.io/resoltico/fingrind:latest
DOCKER_CONFIG="$ANON_DOCKER_CONFIG" docker run --rm ghcr.io/resoltico/fingrind:X.Y.Z version
DOCKER_CONFIG="$ANON_DOCKER_CONFIG" docker run --rm ghcr.io/resoltico/fingrind:latest version
rm -rf "$ANON_DOCKER_CONFIG"
```

Both `docker run ... version` commands must report the target release version exactly. A
successful `docker pull` alone is not sufficient verification. In particular: a multi-arch
`docker pull` can succeed even when the platform manifests have been deleted — the index
manifest is still present but the image is not actually runnable. The `docker run ... version`
check is the definitive test.

If any anonymous Docker command fails, remove the temporary config directory, inspect the
published state, and rerun the full anonymous verification sequence after the fix. Do not switch
to the operator's normal Docker config as a fallback.

These checks are a second handoff checkpoint. Workflow success is not enough; public pull and run
behavior is the authoritative state.

The container registry retains the last 5 releases. Only `X.Y.Z` and `latest` tags are
published per release; there is no `X.Y` floating tag.

Only after all five succeed report to the user: the release is publicly available.

The container workflow is expected to perform the exact-tag and `latest` pull-and-run verification
internally after publication. The operator-side verification remains mandatory because public
availability, not workflow success, is the authoritative state.
