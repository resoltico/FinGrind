---
afad: "5.0.1"
version: "0.62.2"
domain: RELEASE_PROTOCOL
updated: "2026-08-09"
route:
  keywords: [fingrind, release, gh, github release, ghcr, tag, branch protection, protocol]
  questions: ["how do I release fingrind", "what is the fingrind release process", "how are github release and container publication handled in fingrind"]
---

# Release Protocol

The entire release flow is terminal-driven. GitHub API interactions — PRs, CI status, merges,
releases, workflow monitoring, and repository settings — use the GitHub CLI (`gh`); Git ref and
checkout operations use `git`. Do not substitute the GitHub web UI for either path.

**BEFORE DOING ANYTHING ELSE**, run these checks:

```bash
gh --version
gh auth status
gh attestation verify --help >/dev/null
```

If any command fails — `gh` is not installed, `gh auth status` reports "not logged in", or the
installed CLI lacks `gh attestation verify` — **STOP IMMEDIATELY**. Do not attempt any further steps. Notify the user:

> GitHub CLI (`gh`) is not available, not authenticated, or lacks the required attestation command.
> The release procedure cannot continue. Please install or upgrade `gh` and run `gh auth login` (this
> requires browser interaction and possibly 2FA, so it must be done by you, not by me). Once `gh auth
> status` reports a logged-in account with repo access and `gh attestation verify --help` succeeds, tell me to resume.

Do not attempt to resolve missing, outdated, or unauthenticated `gh` autonomously.

This document is the operator procedure. For publication topology, attestation invariants,
cross-platform canary behavior, and safe post-tag repair theory, use
[DEVELOPER_RELEASE_PUBLICATION.md](./DEVELOPER_RELEASE_PUBLICATION.md).

---

### Step 1

Pre-flight: verify release readiness.

Before any build, version edit, or release-branch work, bind the checkout the user will keep using
after the release as the primary checkout. Preserve this binding through every worktree or clean
clone path because Step 10 reconciles this exact path.

Run:

```bash
PRIMARY_CHECKOUT="$(git rev-parse --show-toplevel)"
export PRIMARY_CHECKOUT
printf '%s\n' "$PRIMARY_CHECKOUT"
git branch --show-current
git status --short
git fetch origin --prune --tags
git rev-list --left-right --count HEAD...origin/main
./scripts/verify-repo-hygiene.sh
./scripts/verify-release-repo-settings.sh
```

Requirements before continuing:

- the primary checkout path is known explicitly
- the primary checkout must not be left behind `origin/main` at release closeout
- `./scripts/verify-release-repo-settings.sh` must confirm the complete release-tag policy: exactly
  one owner-authorized `v*` creation ruleset and one no-bypass `v*` update/deletion ruleset. If it
  cannot prove that policy, do not create, push, rerun, or repair a release tag; repair the repository control plane first through [GITHUB_RELEASE_TAG_GOVERNANCE.md](./GITHUB_RELEASE_TAG_GOVERNANCE.md).
- if the primary checkout is already clean and current, release from it directly
- if the primary checkout is dirty only because it already contains the intended release payload,
  continue in place only when it will own `release/X.Y.Z`; inspect that diff deliberately, create
  or switch to the release branch at Step 2, and do not keep release work floating on `main`
- if the primary checkout has unrelated local work, is intentionally dirty for some other reason,
  lives on a problematic or slow filesystem, or fails `./scripts/verify-repo-hygiene.sh` for a
  reason other than intentional root-local scratch state, create a clean release worktree from the same repository and do the release there:

```bash
git fetch origin --prune --tags
RELEASE_WORKTREE="$(mktemp -d -t fingrind-release-XXXXXX)"
git worktree add "$RELEASE_WORKTREE" origin/main
cd "$RELEASE_WORKTREE"
```

Use a Git worktree, not a disconnected clone, whenever possible. A worktree shares refs with the
primary checkout and makes post-release reconciliation mechanically obvious. A separate clone is a
last resort and, if used, must still be reconciled back into the primary checkout before the release session ends.

If `./scripts/verify-repo-hygiene.sh` fails because the primary checkout's Git object store is
corrupt or unreadable, a worktree is not sufficient because it shares the same repository metadata.
In that case, bootstrap a clean release clone from the remote and move the intended release payload into it explicitly before running `./check.sh`:

```bash
REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
RELEASE_CLONE="$(mktemp -d -t fingrind-release-clone-XXXXXX)"
git clone "https://github.com/${REPO}.git" "$RELEASE_CLONE"
cd "$RELEASE_CLONE"
git fetch origin --prune --tags
RELEASE_BRANCH="release/X.Y.Z"
if git show-ref --verify --quiet "refs/remotes/origin/${RELEASE_BRANCH}"; then
  printf 'remote release branch already exists and requires deliberate inspection: %s\n' "${RELEASE_BRANCH}" >&2
  exit 1
fi
git checkout -b "$RELEASE_BRANCH" origin/main
```

If the primary checkout has unpublished local work, decide before the release whether that work is
real or stale. Real work that is not part of this release must move onto a named branch or
exported patch before closeout. For work judged stale, inspect the exact diff first and preserve it
in a named branch or exported patch whenever its disposition is not certain; only then discard it.
Never leave the primary checkout on stale `main` plus unpublished overlays.

If the primary checkout contains the real release payload but release verification must happen from
the clean release worktree, keep the primary checkout on its existing non-release branch and
bootstrap the payload explicitly before you run any release build in the worktree. Do not create
`release/X.Y.Z` in the primary checkout on this path; create it only in the release worktree:

- preferred: move the unpublished release payload onto a distinct local bootstrap branch, return
  the primary checkout to `main`, then create the release worktree's `release/X.Y.Z` branch from
  that bootstrap branch
- acceptable: export one explicit patch from the primary checkout and apply it inside the clean
  release worktree before running checks

The complete worktree topology, branch-admission, and preferred-bootstrap recipe are owned by
[Release Checkout Topology](./DEVELOPER_RELEASE_PUBLICATION.md#release-checkout-topology). Apply
that recipe before creating a worktree; it treats any occupied local or remote release branch as a
hard stop rather than silently reusing unknown history.

For the acceptable patch route, the following standalone guard deliberately creates
`release/X.Y.Z` only in the new worktree:

```bash
RELEASE_BRANCH="release/X.Y.Z"
git -C "$PRIMARY_CHECKOUT" fetch origin --prune --tags
for release_ref in "refs/heads/${RELEASE_BRANCH}" "refs/remotes/origin/${RELEASE_BRANCH}"; do
  if git -C "$PRIMARY_CHECKOUT" show-ref --verify --quiet "$release_ref"; then
    printf 'release branch already exists and requires deliberate inspection: %s\n' "$release_ref" >&2
    exit 1
  fi
done
RELEASE_WORKTREE="$(mktemp -d -t fingrind-release-XXXXXX)" || exit 1
(
  RELEASE_PATCH="$(mktemp -t fingrind-release-bootstrap-XXXXXX)" || exit 1
  trap 'rm -f -- "$RELEASE_PATCH"' EXIT
  git -C "$PRIMARY_CHECKOUT" diff --binary HEAD > "$RELEASE_PATCH" &&
    git -C "$PRIMARY_CHECKOUT" worktree add -b "$RELEASE_BRANCH" "$RELEASE_WORKTREE" origin/main &&
    git -C "$RELEASE_WORKTREE" apply --index "$RELEASE_PATCH"
) || exit 1
cd "$RELEASE_WORKTREE"
```

`git diff --binary HEAD` deliberately captures both staged and unstaged tracked changes, and
`git apply --index` carries that single tracked payload into the new worktree's index.

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
That gate now also proves the repo-owned JaCoCo GA pin: the shared `jacoco` version in
`gradle/libs.versions.toml` must resolve through `./scripts/verify-jacoco-artifacts.sh` before
the Gradle stages run, so release promotion never depends on an implicit or floating coverage
tool version.
Because this baseline gate runs before `./scripts/prepare-release-version.sh X.Y.Z YYYY-MM-DD`,
any bundle archive names, Docker smoke echoes, or distribution manifests produced in Step 1 will
reflect the checkout's pre-sweep version string. That is expected. Treat Step 1 as a payload
health check and Step 2's post-sweep `./check.sh` rerun as the authoritative release-version
verification pass.

Then verify every non-version item in this checklist. These repository and runtime conditions must
be true before any release commit or tag:

- `README.md` does not reference any prior version's container tags.
- All example JSON files use the current wire names and field shapes for this version.
- `./scripts/verify-release-repo-settings.sh` succeeds. That verifier owns the release-critical
  GitHub settings contract:
  - default branch is `main`
  - `delete_branch_on_merge` is enabled
  - `main` protection requires exactly the aggregate `Gate` check
  - protected-path ownership remains declared in `.github/CODEOWNERS` without an approval requirement
  - administrator enforcement remains enabled, so the solo-maintainer release and publication path
    cannot bypass the protected pull-request and `Gate` requirements
  - no self-hosted runner is available to this public repository
  - Actions defaults to read-only workflow permissions and cannot approve pull-request reviews
  - the complete effective tag-ruleset inventory is exactly the owner-authorized creation rule and
    no-bypass immutability rule defined in [GITHUB_RELEASE_TAG_GOVERNANCE.md](./GITHUB_RELEASE_TAG_GOVERNANCE.md)

Before cutting the release branch, enumerate open PRs so dependency-automation work is never
surprise-discovered after publication:

```bash
gh pr list --state open \
  --json number,title,url,headRefName,mergeStateStatus,isDraft,author
```

If any open PR is authored by Dependabot, decide up front whether it changes release machinery or
release-critical dependencies. GitHub currently reports these PRs through `gh pr list` with
`author.login` set to `app/dependabot`; older surfaces may still render `dependabot[bot]`. If the
PR is release-critical, land or reject it before cutting the release branch. If it is not, carry
that decision forward and complete Step 9 before ending the release session.

If you merge or close one release-critical PR, re-enumerate the remaining open PRs before acting
on the next one. A changed `main` branch can invalidate sibling merge state or required-check
evaluations.

If the primary checkout is currently on an open PR branch and that branch's payload is being
absorbed into `release/X.Y.Z`, do not keep driving the release from the PR branch name. Branch to
`release/X.Y.Z` immediately and treat the original PR as provisional theory only. If the release
PR later ships the same payload, Step 9 must close the superseded PR and delete its branch unless
it still contains material that is not in `main`.

If Step 1 merges a release-critical PR and the primary checkout already contains the intended
release payload as uncommitted local changes, do **not** try to `git pull` that dirty `main`
checkout in place. Instead:

```bash
git switch -c release/X.Y.Z
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
RELEASE_BRANCH="release/X.Y.Z"
CURRENT_WORKTREE="$(git rev-parse --show-toplevel)"
git fetch origin --prune --tags
if git show-ref --verify --quiet "refs/remotes/origin/${RELEASE_BRANCH}"; then
  printf 'remote release branch already exists and requires deliberate inspection: %s\n' "${RELEASE_BRANCH}" >&2
  exit 1
fi
RELEASE_BRANCH_WORKTREE="$(
  git worktree list --porcelain | awk -v branch="refs/heads/${RELEASE_BRANCH}" '
    $1 == "worktree" { worktree = substr($0, length("worktree ") + 1) }
    $1 == "branch" && $2 == branch { print worktree; exit }
  '
)"
if git show-ref --verify --quiet "refs/heads/${RELEASE_BRANCH}"; then
  if [ -n "${RELEASE_BRANCH_WORKTREE}" ] && [ "${RELEASE_BRANCH_WORKTREE}" != "${CURRENT_WORKTREE}" ]; then
    printf 'release branch is checked out at %s, not this worktree\n' "${RELEASE_BRANCH_WORKTREE}" >&2
    exit 1
  fi
  git merge-base --is-ancestor origin/main "${RELEASE_BRANCH}" || {
    printf 'existing release branch does not contain current origin/main: %s\n' "${RELEASE_BRANCH}" >&2
    exit 1
  }
  git log --oneline origin/main.."${RELEASE_BRANCH}"
  git diff --stat origin/main..."${RELEASE_BRANCH}"
  git switch "${RELEASE_BRANCH}"
else
  git switch -c "${RELEASE_BRANCH}"
fi
./scripts/prepare-release-version.sh X.Y.Z YYYY-MM-DD
./check.sh
git add <every modified file that belongs in the release>
git status --short
git diff --cached --name-status
git diff --cached --stat
git commit -m "release: bump version to X.Y.Z"
git push origin release/X.Y.Z
```

When an existing local release branch is reused, read the displayed ancestry and diff before
continuing. If either is not exactly the intended release payload, stop and repair or replace that
branch deliberately; do not resume an unknown historical release branch because its name happens
to match. A remote `release/X.Y.Z` branch is an explicit stop: inspect its PR and history, then
either complete that prior attempt or remove it only after proving it is disposable before starting
this procedure again.

Treat staging as a handoff checkpoint, not a formality. Before committing:

- if Step 1 continued in place from a dirty primary checkout, the branch creation above is the
  point where the release payload stops living on `main`; do not switch back to dirty `main`
- `./scripts/prepare-release-version.sh X.Y.Z YYYY-MM-DD` must be the canonical release-prep
  edit step; do not hand-edit scattered version-bearing files when the scripted sweep can do it
- after the version sweep, `gradle.properties` `version=` equals the target release version
  exactly (for example `X.Y.Z`)
- repository metadata classified as non-product by the source-archive boundary stays versioned in
  Git when it belongs to the release, while remaining excluded from GitHub source archives and
  outside the public bundle and container asset sets
- all touched `docs/*.md` frontmatter fields equal the target release version and scripted release date
- any ADR, capability catalog, scope document, or release-target guide describing the payload as unreleased states its release-truthful published status
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

If `gh pr diff <N> --name-only` fails with GitHub's oversized-diff response (`PullRequest.diff too_large` / HTTP 406), resolve the PR's exact GitHub object ids and establish its final path inventory before inspecting the API view:

```bash
PR_SCOPE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-release-pr-scope.XXXXXX)"
trap 'rm -rf -- "$PR_SCOPE_DIR"' EXIT
PR_METADATA="$(gh pr view <N> --json baseRefOid,headRefOid,changedFiles)"
PR_BASE_SHA="$(printf '%s' "$PR_METADATA" | jq -er '.baseRefOid')"
PR_HEAD_SHA="$(printf '%s' "$PR_METADATA" | jq -er '.headRefOid')"
PR_CHANGED_FILE_COUNT="$(printf '%s' "$PR_METADATA" | jq -er '.changedFiles')"
test "$PR_HEAD_SHA" = "$(git rev-parse HEAD)"
git fetch --no-tags --no-write-fetch-head origin "$PR_BASE_SHA" "$PR_HEAD_SHA"
git cat-file -e "$PR_BASE_SHA^{commit}"
git cat-file -e "$PR_HEAD_SHA^{commit}"
git diff --name-only --no-renames "$PR_BASE_SHA" "$PR_HEAD_SHA" | LC_ALL=C sort > "$PR_SCOPE_DIR/git-paths"

REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
if (( PR_CHANGED_FILE_COUNT < 3000 )); then
  gh api "repos/$REPO/pulls/<N>/files" --paginate --jq '.[] | if .status == "renamed" then .previous_filename, .filename else .filename end' \
    | LC_ALL=C sort > "$PR_SCOPE_DIR/github-paths"
  diff -u "$PR_SCOPE_DIR/github-paths" "$PR_SCOPE_DIR/git-paths"
else
  printf '%s\n' 'GitHub pull-files responses are capped at 3000 records; the exact GitHub-resolved Git range above is the complete scope evidence.' >&2
fi
```

Treat the PR itself as a second scope-verification checkpoint:

- `gh pr diff <N> --name-only` must match the intended release file set.
- If `gh pr diff <N> --name-only` returns `PullRequest.diff too_large`, the exact PR base/head ids resolved by GitHub must equal the checked-out release head and produce the intended `--no-renames` path inventory. The non-tracking fetch keeps that evidence bound to GitHub without updating a mutable local tracking ref.
- GitHub's paginated pull-files endpoint returns at most 3,000 records. Below that ceiling, its normalized source-and-destination path inventory must exactly match the exact Git range. GitHub may render a move as one `renamed` record or as separate added and removed records; emitting both `previous_filename` and `filename` makes either representation comparable. At the ceiling, inspect the complete exact Git range above; do not claim the API response is exhaustive.
- If the PR diff is missing files or includes unintended files, fix the release branch before
  waiting on CI or merging.
- Every new commit pushed to the release branch reopens both the Step 2 staging checkpoint and
  this PR diff checkpoint. Re-verify both after each fix commit.

Do not proceed until `./scripts/verify-release-pr-gate.sh <N>` succeeds for the release PR. `Gate`
is the single authoritative required check for release promotion, and the verifier checks the PR
head commit directly instead of inferring readiness from `statusCheckRollup`.

The aggregate `Gate` check run appears only after Gradle wrapper validation, `Check`, the published
bundle-smoke matrix, `devcontainer-changes`, and the `devcontainer` job have completed in workflow
`CI`. When the devcontainer trigger paths do not change, `devcontainer` reports successful
completion as a clean no-op; otherwise it performs the full validation. `Gate` passes only when
every dependency concludes successfully. `Gate` is the sole branch-protection context, while the
release verifier independently requires every contract-listed constituent job on the exact PR head.
A PR can therefore show `Check` green while `Gate` is absent. Treat a missing `Gate` as pending,
not as success. The verifier is the canonical owner of that waiting logic. The published
bundle-smoke matrix includes macOS, Linux, and Windows publication proofs, and the Linux rows each
add the minimum-glibc compatibility-floor rerun, so `Gate` naturally arrives after `Check`.

If `./scripts/verify-release-pr-gate.sh <N>` reports a failing `Gate`, fix the failure, push to the
release branch, and run the verifier again — do not merge a red PR.

The verifier's three-hour default covers the currently configured 130-minute ceiling for both
`Check` and the published bundle-smoke matrix, with room for `Gate` materialization. If GitHub
Actions queueing exceeds that observation window, extend it explicitly instead of guessing:

```bash
FINGRIND_RELEASE_CHECK_TIMEOUT_SECONDS=14400 ./scripts/verify-release-pr-gate.sh <N>
```

### Step 4

Merge PR and verify the merge handoff.

```bash
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
gh pr merge <N> --repo "$REPO" --merge --delete-branch \
  --subject "release: bump version to X.Y.Z (#N)"
git fetch --no-tags origin +refs/heads/main:refs/remotes/origin/main
git switch --detach origin/main
./scripts/verify-release-merge-handoff.sh
gh pr view <N> --repo "$REPO" --json number,state,mergedAt,headRefName,baseRefName,url
```

Merge through GitHub's normal protected pull-request path; do not use `--admin`. FinGrind's
supported repository settings require the aggregate `Gate` check and enforce that rule for
administrators as well as other contributors. The sole-maintainer policy keeps the pull-request
path while avoiding an impossible self-review requirement; `.github/CODEOWNERS` remains the
maintenance-routing map. `./scripts/verify-release-repo-settings.sh` is the executable owner of
that precondition.

Requirements before continuing:

- PR state is `MERGED`.
- `mergedAt` is populated.
- The checked-out verifier commit contains the merge commit you expect.
- The checkout used for `./scripts/verify-release-merge-handoff.sh` exactly matches `origin/main`.
- The remote release branch is deleted by the merge step.
- `./scripts/verify-release-merge-handoff.sh` succeeds on the merged `main` commit, which means
  the canonical `Gate` check is green on the exact commit that will be tagged.

The verifier's three-hour default covers the currently configured 130-minute ceiling for both
`Check` and the published bundle-smoke matrix, with room for `Gate` materialization. If GitHub
Actions queueing exceeds that observation window, extend it explicitly instead of guessing:

```bash
FINGRIND_RELEASE_CHECK_TIMEOUT_SECONDS=14400 ./scripts/verify-release-merge-handoff.sh
```

GitHub auto-delete on merge must remain enabled at the repository level. `--delete-branch` remains
mandatory here as defense in depth, so the release handoff stays self-contained if that setting is
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
is acceptable for Step 4. Step 10 remains the place where the primary checkout itself must be
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
./scripts/verify-release-repo-settings.sh
FINGRIND_RELEASE_TAG_VERIFIER_MODE=pre-tag \
  ./scripts/verify-release-candidate-tag.sh vX.Y.Z
git tag vX.Y.Z
git push origin vX.Y.Z

REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
gh api "repos/$REPO/git/ref/tags/vX.Y.Z"
./scripts/verify-release-candidate-tag.sh vX.Y.Z
```

Do not proceed until the remote tag ref exists. Never infer a successful tag push from the
absence of a local git error alone — verify the remote ref through GitHub.

The pre-tag verifier is mandatory immediately before `git tag`. It proves the stable tag grammar
and project-version match, that the checked-out commit is the current `origin/main` head with every
release-blocking CI owner green, and that neither a local nor a remote reference already occupies
the tag name. This is the last reversible admission boundary: if it fails, repair the release
payload or control plane and rerun it; never reserve an invalid protected tag to discover the
mistake later.

Public release admission is stable-only: the tag must be exactly `vX.Y.Z`, with decimal
non-negative `X`, `Y`, and `Z` components and no prerelease, build, prefix, suffix, or floating
form. The `Prepare release publication` job's **Determine target release tag** boundary enforces
that shape before it derives the version, and `./scripts/verify-release-candidate-tag.sh` repeats
the admission check before any payload-producing work. Do not use an arbitrary `v*` tag as a
release or rerun target.

The Step 1 repository-settings preflight is also mandatory tag-control evidence. GitHub permits
only the repository owner to create a `v*` tag and permits nobody to update or delete one; the two
separate rulesets are deliberately layered so the creation bypass cannot weaken immutability. The
`v*` ruleset scope is broader than the stable-tag grammar because it protects the entire release
namespace, while candidate admission decides which protected tag is a public release. Never work
around a failed tag-policy check by relaxing a ruleset, force-pushing, deleting, or recreating a
tag. A policy administrator can alter rulesets, so direct tag-to-commit and public artifact
verification remain required evidence rather than assuming cryptographic permanence from GitHub
policy alone.

After the push, `./scripts/verify-release-candidate-tag.sh vX.Y.Z` is mandatory in its default
initial-publication mode. It proves the new remote ref names the checked-out commit, repeats the
tag-version, default-branch-head, and exact-commit CI checks, and establishes the remote-ref proof
before any publication workflow is trusted. The paired pre-tag and post-push checks make the
irreversible handoff explicit rather than trusting either a local tag command or a later workflow
to diagnose an invalid tag.

If the `X.Y.Z` version bump landed on `main` and an unreleased pre-tag repair commit is needed before the first public tag, keep the version at `X.Y.Z`, merge the repair onto `main`, rerun the gates, and tag that repaired `origin/main` head. Do not cut `X.Y.(Z+1)` merely to express an unpublished release-control or payload repair. Later unreleased repair commits may still become the first public tag for `X.Y.Z`; post-tag repairs still use the immutable rerun path below and must not move the tag.

The tag push, not the PR merge, triggers the `Release` workflow. It owns bundle publication, the
GitHub Release handoff, container publication, and public-container verification. Monitor it under
Step 7, then complete the operator handoffs in Step 8. Each release bundle job has the same
130-minute observed-runtime ceiling as its equivalent CI publication proof; a `cancelled` bundle
job at that boundary is a release-control defect to repair on `main`, not evidence that the tagged
payload should be retagged. Staging containers provision the metadata-pinned Python and `uv` release-smoke environment before Docker acceptance; a missing launcher is a release-control defect to repair on `main`, never a reason to mutate the tag.

If publication fails after a valid tag exists, never move the tag or create a replacement tag for
the same version. [DEVELOPER_RELEASE_PUBLICATION.md](./DEVELOPER_RELEASE_PUBLICATION.md#safe-repair-path-after-tagging)
owns the safe post-tag repair theory: owner-only, main-ref-enforced dispatch of repaired
control-plane workflow logic, immutable tagged-payload selection, queue behavior, GitHub Release
asset propagation, first-run draft handling, and container-candidate identity.
[RELEASE_PUBLICATION_VERIFICATION.md](./RELEASE_PUBLICATION_VERIFICATION.md)
owns the direct GitHub Release evidence after that repair, and its linked container handoff owns
the direct public-image evidence. The developer reference also distinguishes the mandatory Step 5
pre-tag and post-push proofs from the durable tag-publication and rerun proofs used after a valid
tag exists.

Dispatch a repaired release control path only from the protected default branch:

```bash
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
gh workflow run release.yml --repo "$REPO" --ref main -f release_tag=vX.Y.Z
```

The workflow independently rejects every `workflow_dispatch` run whose GitHub ref is not exactly
`refs/heads/main`; `--ref main` is therefore an enforced control-plane boundary, not merely an
operator convention. The workflow-dispatch rerun automatically switches the verifier into `rerun` mode; a tag-triggered run uses `tag-publication` mode. Neither substitutes for the Step 5 pre-tag
admission and post-push initial proof, and neither may move or replace the immutable tag.

### Step 6

Branch hygiene.

After the merge and tag push, clean up stale remote-tracking refs and verify that no historical
release branches remain on GitHub.

```bash
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
git fetch origin --prune --tags
gh api "repos/$REPO/branches" --paginate --jq '.[].name'
```

Requirements:

- No `release/X.Y.Z` branch may remain on GitHub after the merge.
- No historical `release/` branches may remain on GitHub. Before deleting one, inspect its PR and
  the exact branch-only payload. Delete it only after proving it is fully merged or after an
  explicit abandonment decision that preserves any still-valuable work:

```bash
git log --oneline origin/main..origin/release/A.B.C
git diff --stat origin/main...origin/release/A.B.C
git push origin --delete release/A.B.C
```

- No fully merged local `release/` branches may remain. Delete them:

```bash
git branch -d release/A.B.C
```

- If the preferred bootstrap route was used, its local `release-bootstrap/X.Y.Z` handoff branch
  must also be fully merged and removed; it is never pushed:

```bash
git branch -d release-bootstrap/X.Y.Z
```

Do not leave release-branch leftovers behind locally or remotely. Branch hygiene is part of the
release procedure, not optional cleanup.

Open maintenance branches such as Dependabot are handled separately in Step 9. Do not treat a
non-`release/` branch as automatically acceptable just because Step 6 only hard-fails
`release/*` leftovers.

### Step 7

Monitor workflows by their deterministic release-target identity, with duplicate-run awareness.

```bash
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
RELEASE_RUN_NAME='Release vX.Y.Z'
gh api --paginate --slurp \
  "repos/${REPO}/actions/workflows/release.yml/runs?per_page=100" |
  jq --arg release_run_name "${RELEASE_RUN_NAME}" \
    '[.[].workflow_runs[]
      | select(.display_title == $release_run_name)
      | {
          databaseId: .id,
          displayTitle: .display_title,
          event,
          headSha: .head_sha,
          status,
          conclusion,
          url: .html_url
        }
    ]'
```

`release.yml` derives each display title only from `inputs.release_tag || github.ref_name`, so the
exact title `Release vX.Y.Z` is the target-identity check for both trigger paths. Do not identify a
release run by tag commit or one event filter: a tag-push run has the tag commit as `headSha`, while
a `workflow_dispatch` repair run's `headSha` identifies the ref resolved when GitHub accepted the
dispatch. It does not prove the helper revision actually executed: prepare-publication later pins a
specific `main` helper commit for every rerun job. For rerun-control provenance, inspect the
workflow log or step summary for the recorded `Release control helper commit: <40-hex SHA>` value.

`gh api --paginate --slurp` follows every workflow-runs result page before `jq` selects and
normalizes matching records. Do not replace it with a bounded `gh run list` history: a historical
repair run must remain discoverable even after later repository activity exceeds an arbitrary list
limit.

Do not assume there is exactly one run per workflow. A tag push, a deliberate repair dispatch, or
a duplicated delivery can all leave multiple runs for the same release target. Treat the workflow
boundary as a **handoff checkpoint**:

1. Enumerate every page of `release.yml` workflow-run records whose normalized `displayTitle`
   exactly equals `Release vX.Y.Z`; do not apply tag-commit or single-event filtering before that
   comparison. An initial `[]` is Actions propagation-pending, not evidence that the tag trigger
   failed: repeat that same all-page query at bounded intervals for no more than five minutes
   before classifying a matching run as absent.
2. For each candidate, inspect its facts and require the exact display title plus an event of either
   `push` or `workflow_dispatch`:

```bash
gh run view <run-id> --repo "$REPO" \
  --json databaseId,displayTitle,event,headSha,status,conclusion,url
```

3. Treat a matching `push` run and a matching `workflow_dispatch` run as the same target only when
   that exact title check holds. An unexpected event or a different title is not a candidate for
   this release, even if its commit happens to equal the tag commit.
4. A matching `queued` record is pending before the repository-wide publication queue admits it.
   Do not inspect failure logs, retry it, or classify it as failed while it remains queued.
5. A matching `in_progress` record is active publication work. Continue monitoring it; do not let a
   completed sibling's past failure override it.
6. A matching `completed` record whose conclusion is not `success` is a past failure. Inspect it
   only after checking whether a queued or active sibling can still converge the required public
   state:

```bash
gh run view <run-id> --repo "$REPO" --log-failed
```

7. Verify the external GitHub state directly before deciding the release is failed.

Rules:

- Never treat one failed run as authoritative if another sibling run for the same tag succeeded.
- A queued duplicate with the same exact release title is expected serialization. Treat it as
  pending until it completes or direct public-state verification makes further work unnecessary.
- Do not conflate the three temporal states: an initial empty discovery result is bounded
  propagation-pending, `queued` is waiting publication work, `in_progress` is active publication
  work, and a completed non-success conclusion is a past failure to investigate after its siblings.
- Never re-run blindly. First inspect whether the desired state already exists.
- A release-workflow failure with `Release.tag_name already exists` is **not** automatically a
  release failure. It may mean a sibling run already created the release successfully.
- Only classify the release workflow as failed if **no** run produced the required external
  state and direct GitHub inspection confirms that state is absent or incomplete.

Fix the root cause only after the direct-state inspection proves the release or container state
is actually missing or incorrect. Coordinate with the user if the failure is in CI infrastructure
outside this codebase.

When multiple runs are observed for the same workflow and release-target title, classify the
**source** of each dispatch separately from the **safety** of the publication system:

- The source may be the original tag push, an intentional workflow-dispatch repair, a user- or
  tool-driven duplicate tag push, a client retry, or a GitHub Actions delivery anomaly.
- Unless GitHub audit evidence proves which one occurred, treat the source as externally
  ambiguous. Do not present guesswork as certainty.
- Inside this repository, the required engineering response is still deterministic: the workflows
  must remain safe under duplicate dispatch. Concurrency, idempotent publication, and direct
  post-publication verification are mandatory.

### Step 8

Complete [RELEASE_PUBLICATION_VERIFICATION.md](./RELEASE_PUBLICATION_VERIFICATION.md) for the
GitHub Release asset-and-attestation handoff, then complete its linked
[RELEASE_PUBLICATION_CONTAINER_VERIFICATION.md](./RELEASE_PUBLICATION_CONTAINER_VERIFICATION.md)
handoff for anonymous public container, mounted-book, and PDF availability. Do not begin Step 9
until both handoffs succeed.

### Step 9

Triage leftover PRs and clear dependency-automation leftovers.

After the public release is verified, do not end the release session while open PRs that were
reviewed during the release are left in an ambiguous state. Release hygiene includes both
dependency-automation hygiene and cleanup of ordinary PRs that the release branch superseded.

Re-enumerate all open PRs and identify Dependabot-owned entries directly from GitHub metadata:

```bash
gh pr list --state open \
  --json number,title,url,headRefName,mergeStateStatus,isDraft,author
```

Treat any PR whose `author.login` identifies Dependabot as in scope for this step, even if it was
already reviewed during Step 1. Today that means `app/dependabot`; older GitHub surfaces may show
`dependabot[bot]`. Step 1 creates the release-time decision; Step 9 closes the loop before the
release session is allowed to end.

For each open Dependabot PR, inspect the exact payload and its current gate status:

```bash
gh pr diff <N> --name-only
gh pr view <N> --json number,title,state,mergeStateStatus,url
./scripts/verify-release-pr-gate.sh <N>
```

Rules:

- If the PR is wanted and mergeable, merge it only after
  `./scripts/verify-release-pr-gate.sh <N>` succeeds on its current head and every applicable
  special gate in [Dependabot Approval](./DEVELOPER_DEPENDABOT_APPROVAL.md#required-gates-before-any-dependabot-merge) succeeds. Do not
  infer that condition from `statusCheckRollup`. Then delete its branch:

```bash
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
gh pr merge <N> --repo "$REPO" --merge --delete-branch --subject "<title> (#<N>)"
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

### Step 10

Reconcile the primary checkout.

If the release used a dedicated release worktree or any checkout other than the primary checkout,
the session is not complete until the primary checkout is truthful again. This step is now a
scripted gate, not a reminder. Do not declare the release complete until the verifier passes.

If unpublished local work from the primary checkout is still needed, move it onto a named branch
based on current `main` first, then return the primary checkout itself to `main`.

Run:

```bash
git -C "$PRIMARY_CHECKOUT" checkout main
git -C "$PRIMARY_CHECKOUT" fetch origin --prune --tags
git -C "$PRIMARY_CHECKOUT" merge --ff-only origin/main
./scripts/verify-release-primary-checkout.sh "$PRIMARY_CHECKOUT" "X.Y.Z"
```

If Step 1 had to move the release into a clean clone because the original primary checkout's Git
metadata was corrupt or unreadable, do not try to repair that broken checkout in place during
closeout. Replace it with the verified release checkout instead:

```bash
./scripts/reconcile-release-primary-checkout.sh "$PRIMARY_CHECKOUT" "$RELEASE_CLONE" "X.Y.Z"
```

That helper switches the replacement checkout onto `main`, fast-forwards it to `origin/main`,
proves it satisfies `./scripts/verify-release-primary-checkout.sh`, installs it at the canonical
primary-checkout path, reruns the verifier there, and only then tries to remove the displaced
broken tree. It first normalizes owner permissions inside the displaced backup so ordinary
read-only residue does not strand release closeout. If the replacement is already proven truthful
and the filesystem still refuses backup deletion, the helper keeps the verified primary checkout
in place, preserves the hidden `.pre-release-backup*` path, and reports that preserved path
explicitly instead of pretending the release itself failed.

The verifier is authoritative. It fetches `origin`, requires the primary checkout to be on `main`,
requires `HEAD` to equal `origin/main`, checks that `gradle.properties` and `CHANGELOG.md` reflect the released version, rejects tracked overlays, and rejects unexpected untracked debris outside the repo's explicit scratch prefixes.

Requirements before declaring the release session complete:

- `./scripts/verify-release-primary-checkout.sh "$PRIMARY_CHECKOUT" "X.Y.Z"` exits 0
- no stale release-only checkout may be left behind with the appearance of being authoritative
- if unpublished local work from the primary checkout is still needed, replay it deliberately onto
  a named branch based on current `main`; do not leave it only in a stash or mixed into `main`
- if that unpublished local work is stale, superseded, or regresses the shipped release state,
  inspect its exact diff and preserve a named branch or exported patch whenever there is any doubt
  before discarding it instead of preserving misleading debris
- if a clean-clone replacement path was required, `scripts/reconcile-release-primary-checkout.sh`
  is the canonical closeout path; do not leave both the broken original tree and the replacement
  clone behind
- if that helper reports a preserved displaced backup path because automatic cleanup failed, treat it as cleanup-only debris rather than an authoritative checkout, remove it before leaving the host tidy when the filesystem permits, and report the exact preserved path plus failure reason if host-level deletion remains blocked

If a disposable release worktree was created and is no longer needed:

```bash
git worktree remove "$RELEASE_WORKTREE"
```

---

## Dependabot approval

Step 9 uses the independent [Dependabot Approval](./DEVELOPER_DEPENDABOT_APPROVAL.md) policy for triage tiers, required gates, and prohibited outcomes. Keep the policy separate from the release sequence so dependency approval remains usable outside a release and the protocol stays focused on its terminal operator path.
