---
afad: "5.0.1"
version: "0.62.1"
domain: DEVELOPER_DEPENDABOT_APPROVAL
updated: "2026-08-06"
route:
  keywords: [fingrind, dependabot, dependency update, dependency approval, release hygiene]
  questions: ["how are fingrind dependabot pull requests approved", "what checks does a dependency update require", "when should a dependabot pull request be closed"]
---

# Dependabot Approval

**Purpose**: Define the maintainer decision and verification requirements for FinGrind Dependabot pull requests.
**Prerequisites**: A current branch, GitHub CLI access, and the release PR gate described in [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md).

FinGrind is a financial application. **No Dependabot PR may be auto-merged.** Every update — regardless of ecosystem, scope, or whether it is flagged as a security fix — requires an operator decision before landing on `main`.

## Security-control prerequisite

GitHub Dependabot alerts and automated security updates must remain enabled. The repository's
`.github/dependabot.yml` controls scheduled version-update coverage; the GitHub controls identify
vulnerable dependencies and create the security-update pull requests covered by this policy.
`./scripts/verify-security-policy-surface.sh` proves those live controls together with private
vulnerability reporting during release verification.

## Triage tiers

| Tier | Trigger | Deadline | Action |
|:-----|:--------|:---------|:-------|
| **Security** | Dependabot security advisory on any direct or transitive dependency | Within 7 calendar days of PR open | Review, verify CI passes, merge or reject with documented reason |
| **Regular** | Non-security weekly update | Before the next release | Review during release hygiene; merge or close |
| **Major version bump** | `semver-major` update on any ecosystem | Before the next release | Treat as a considered upgrade, not a routine bump; verify API compatibility explicitly |

## Required gates before any Dependabot merge

1. Run `./scripts/verify-release-pr-gate.sh <N>` on the Dependabot PR head. It requires `Gate`, the sole required branch-protection context, and confirms every contract-listed release-blocking CI owner concluded with `success` on that same head. Do not substitute `statusCheckRollup` or unrelated workflow jobs for this verifier.
2. For Docker base image updates: `docker-smoke` specifically passes, confirming the new base image does not break the containerized runtime.
3. For Gradle dependency updates that touch `sqlite` or `sqlite3mc`: the `Verify managed SQLite CLI runtime` step in `check` passes and the managed SQLite hash in `gradle.properties` is still consistent.
4. For GitHub Actions updates: the pinned commit SHA in the workflow file matches the SHA of the tagged release being adopted — verify with `gh api repos/<owner>/<repo>/git/ref/tags/<tag>`.

## Prohibited outcomes

- Never merge a Dependabot PR that has a failing or missing `Gate` check.
- Never merge a Dependabot PR that changes the SQLite native library without verifying the managed runtime still initializes correctly.
- Never retag or amend a published release to absorb a post-release Dependabot merge.
- Never leave a Dependabot PR open indefinitely without an explicit keep-open reason documented in a PR comment.
