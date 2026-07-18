---
afad: "5.0.1"
version: "0.61.0"
domain: OPERATIONS
updated: "2026-07-16"
route:
  keywords: [fingrind, ci, github-actions, devcontainer, gate]
  questions: ["when does the devcontainer workflow run", "why does the devcontainer gate skip"]
---

# CI Workflow Reference

**Purpose**: Explain the CI-owned contributor-environment gate and its relationship to the aggregate `Gate` check.

## Path-Based Devcontainer Gate

The devcontainer gate validates the contributor environment, not application behavior. Application code changes are already proven by `check` and the published bundle-smoke matrix. Running a full Docker build-and-validate cycle for every pull request would repeat those application checks while consuming substantial CI time, so the environment gate fires only when its own inputs change:

- `.devcontainer/` for the Dockerfile and `devcontainer.json`
- `scripts/validate-devcontainer.sh`
- `scripts/devcontainer-prepare-user-home.sh`
- `scripts/repo-verification-lock-support.sh`
- `scripts/python-runtime-support.sh`

`devcontainer-changes` computes the pull-request diff before the gate is evaluated. When none of those paths change, `devcontainer` is skipped; that is an intended result, not a coverage gap. The aggregate `gate` job evaluates its dependencies with `if: always()` and explicit result handling, so this intended skip does not suppress the required `Gate` status.

All CI runners use pinned `ubuntu-24.04` and `windows-2022` images instead of floating labels. `workflow_dispatch` permits a manual aggregate `Gate` rerun when GitHub fails to attach the pull-request workflow on initial open.

## Related Protocols

- [GITHUB_BOOTSTRAP_PROTOCOL.md](./GITHUB_BOOTSTRAP_PROTOCOL.md)
- [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md)
