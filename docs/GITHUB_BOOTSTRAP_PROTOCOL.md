---
afad: "4.0"
version: "0.37.0"
domain: GITHUB_BOOTSTRAP_PROTOCOL
updated: "2026-05-14"
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

Grant Actions workflow write permissions so the release and container workflows can publish:

```bash
gh api \
  --method PUT \
  repos/resoltico/FinGrind/actions/permissions/workflow \
  -f default_permissions=write \
  -F can_approve_pull_request_reviews=false
```

## Step 5

Enable branch protection on `main` only after the required CI status-check name exists.
For FinGrind, the single required check is `Gate`. That aggregate check is the canonical owner of
the release-blocking CI contract and already covers the current public-product jobs plus the
path-gated contributor-devcontainer surface.

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
  "required_pull_request_reviews": null,
  "restrictions": null
}
EOF
```

From this point forward, changes to `main` should go through pull requests.

## Step 6

Recommended repository settings alignment:
- default branch is `main`
- branch auto-delete on merge is enabled
- Actions workflow permissions default to write
- `main` protection enforces admins
- required checks remain exactly `Gate`
- the separate `Contributor devcontainer` CI job remains visible under that aggregate Gate contract

## Step 7

Container posture should follow the same hardened publication stance already proven out in the sibling project:
- keep GHCR publication enabled
- publish only `X.Y.Z` and `latest`
- verify both exact-tag and `latest` pulls after publication
- prune old GHCR package versions conservatively by anchored tagged releases

FinGrind already carries the matching workflow surfaces locally:
- [../.github/workflows/ci.yml](../.github/workflows/ci.yml)
- [../.github/workflows/release.yml](../.github/workflows/release.yml)
- [../.github/workflows/container.yml](../.github/workflows/container.yml)

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
