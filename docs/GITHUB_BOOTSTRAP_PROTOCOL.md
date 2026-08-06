---
afad: "5.0.1"
version: "0.62.1"
domain: GITHUB_BOOTSTRAP_PROTOCOL
updated: "2026-08-05"
route:
  keywords: [fingrind, github, bootstrap, gh, repo-create, branch-protection, actions, ghcr]
  questions: ["how do I bootstrap the fingrind github repo", "how do I create the fingrind github repository", "how should github actions and branch protection be configured for fingrind"]
---

# GitHub Bootstrap Protocol

**Purpose**: Prepare the public GitHub repository and repository settings for FinGrind.
**Prerequisites**: `gh` installed, `gh auth status` succeeds, and local `./check.sh` passes.

This protocol is for the first-time repository bootstrap of:
- repository: `resoltico/FinGrind`
- container image: `ghcr.io/resoltico/fingrind`

Do not create tags or publish releases during bootstrap.
Release publication is covered separately in [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md).

## Step 0

Verify the GitHub CLI surface before doing anything else:

```bash
gh --version
gh auth status
```

If either command fails, stop and fix `gh` installation or authentication first.

## Step 1

Verify the local repository is ready:

```bash
./check.sh
```

Do not create or push the GitHub repository until the local release surface is green.

## Step 2

Create the public repository:

```bash
gh repo create FinGrind \
  --public \
  --description "Finance-grade bookkeeping kernel with an agent-first CLI and SQLite-first persistence" \
  --clone=false
```

This creates the target repository without mutating the local working tree.

## Step 3

Connect the local repository and perform the first push when ready:

```bash
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/resoltico/FinGrind.git
git push -u origin main
```

Then watch the first CI run:

```bash
gh run watch --repo resoltico/FinGrind
```

Do not continue until the first `CI` workflow has completed successfully.

## Step 4

Set the repository-wide Actions default to read-only. Publication jobs declare the narrow write
scopes they need in their own workflow definitions, so ordinary CI and future jobs do not inherit
release authority:

```bash
gh api \
  --method PUT \
  repos/resoltico/FinGrind/actions/permissions/workflow \
  -f default_permissions=read \
  -F can_approve_pull_request_reviews=false
```

## Step 5

Enable branch protection on `main` only after the required CI status-check name exists and the
protected-path owner file is committed. For FinGrind, the single required check is `Gate`, and
`.github/CODEOWNERS` declares maintenance ownership for protected paths. `Gate` remains the
canonical owner of the release-blocking CI contract and already covers the current public-product
jobs plus the path-gated contributor-devcontainer surface.

Apply protection:

```bash
gh api \
  --method PUT \
  repos/resoltico/FinGrind/branches/main/protection \
  --input - <<'EOF'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["Gate"]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": false,
    "required_approving_review_count": 0,
    "require_last_push_approval": false
  },
  "restrictions": null
}
EOF
```

From this point forward, changes to `main` go through pull requests, including changes by the
repository owner. FinGrind requires the aggregate `Gate` check and administrator enforcement; it
does not require an author to obtain an impossible self-review. `.github/CODEOWNERS` remains the
maintenance-routing map, not a merge-blocking approval rule. `./scripts/verify-release-repo-settings.sh`
is the executable owner of that release-preflight contract.

## Step 6

Establish stable-release tag governance before any release tag can exist. Follow
[GITHUB_RELEASE_TAG_GOVERNANCE.md](./GITHUB_RELEASE_TAG_GOVERNANCE.md) exactly, then run:

```bash
./scripts/verify-release-repo-settings.sh
```

Do not create any release tag until the verifier succeeds.

## Step 7

Recommended repository settings alignment:
- default branch is `main`
- branch auto-delete on merge is enabled
- Actions workflow permissions default to read; individual publication jobs request only the write
  scopes they need
- no self-hosted runner is available to this public repository
- `main` protection enforces its rules for administrators as well as other contributors
- required checks remain exactly `Gate`
- protected-path ownership remains declared in `.github/CODEOWNERS` without an approval requirement
- the separate `Contributor devcontainer` CI job remains visible under that aggregate Gate contract
- the two rulesets in [GITHUB_RELEASE_TAG_GOVERNANCE.md](./GITHUB_RELEASE_TAG_GOVERNANCE.md) remain the complete active tag-ruleset inventory

## Step 8

Container posture should follow the same hardened publication stance already proven out in the
sibling project:
- keep GHCR publication enabled
- publish exact-tag images for every stable release
- publish `latest` only when the release tag is the newest stable release
- verify the exact tag on every release and verify `latest` only when that latest-policy applies
- prune old GHCR package versions conservatively by anchored tagged releases

FinGrind already carries the matching workflow surfaces locally:
- [../.github/workflows/ci.yml](../.github/workflows/ci.yml)
- [../.github/workflows/release.yml](../.github/workflows/release.yml)

## Notes

- `gh auth status` currently needs `repo` and `workflow` scope for bootstrap itself.
- Local package inspection via `gh api /user/packages` may additionally require `write:packages`.
- For a public repository, GHCR packages are expected to be public automatically.
- Do not publish a release tag during bootstrap. Use [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md) later.
- Jazzer active fuzzing remains local-only.
  GitHub must never invoke `jazzer/bin/*`, and active harness execution hard-fails when
  `GITHUB_ACTIONS=true` as a defense-in-depth backstop.
- GitHub CI currently keeps Jazzer on the deterministic side only by running the root build and
  CLI runtime checks, not standalone Jazzer deterministic tests, regression replay, or active
  fuzzing.
