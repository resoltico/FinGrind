---
afad: "5.0.1"
version: "0.64.0"
domain: RELEASE_WORKFLOW_OPERATIONS
updated: "2026-09-01"
route:
  keywords: [fingrind, release workflow, duplicate dispatch, release target, actions queue, release repair]
  questions: ["how do I monitor a FinGrind release workflow", "how do I distinguish duplicate release runs", "how do I repair a failed tagged release workflow"]
---

# Release Workflow Operations

**Purpose**: Monitor one tagged release through GitHub Actions, distinguish propagation and duplicate dispatch from failure, and choose the safe post-tag repair path.
**Prerequisites**: A stable `vX.Y.Z` tag that passed the pre-tag and post-push checks in [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md).

## Identify The Release Target

```bash
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
RELEASE_RUN_NAME='Release vX.Y.Z'
gh api --paginate --slurp \
  "repos/${REPO}/actions/workflows/release.yml/runs?per_page=100" |
  jq --arg release_run_name "${RELEASE_RUN_NAME}" \
    '[.[].workflow_runs[]
      | select(.display_title == $release_run_name)
      | {databaseId: .id, displayTitle: .display_title, event, headSha: .head_sha, status, conclusion, url}]'
```

The exact display title is the release-target identity for tag pushes and workflow-dispatch repairs. Do not select by commit or event alone: a tag push uses the tag commit as `headSha`, while a repair dispatch uses the main ref resolved at dispatch time. For a repair, inspect the run summary for `Release control helper commit: <40-hex SHA>`; that is the control-plane revision used by later jobs.

The all-page API query is deliberate. A bounded `gh run list` history can omit a historical repair. An initial empty result is Actions propagation-pending, not a failed trigger: repeat this exact query at bounded intervals for no more than five minutes before classifying the run as absent.

## Classify Every Matching Run

For each matching record, require the exact display title and an event of `push` or `workflow_dispatch`:

```bash
gh run view <run-id> --repo "$REPO" \
  --json databaseId,displayTitle,event,headSha,status,conclusion,url
```

- `queued` means publication serialization is pending. Do not inspect failure logs or dispatch a retry.
- `in_progress` means active publication work. Continue monitoring it even if a sibling failed.
- A non-success `completed` run is a past failure only after no queued or active matching run can converge the required public state. Inspect it with `gh run view <run-id> --repo "$REPO" --log-failed`.

Never treat one failed duplicate as release failure when another matching run succeeded. Likewise, `Release.tag_name already exists` can mean a sibling already created the release. Directly inspect the GitHub Release and GHCR state before classifying the release as failed.

## Repair A Tagged Release

Fix only a proven workflow or publication defect on protected `main`; never move, replace, or retag the immutable `vX.Y.Z` tag. Dispatch the repair from `main`:

```bash
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
gh workflow run release.yml --repo "$REPO" --ref main -f release_tag=vX.Y.Z
```

The workflow rejects dispatches not made from `refs/heads/main`, switches to durable rerun admission, and can accept only public assets and container digests that already match the tagged payload. If a repair would require replacing public bytes, publish a new version instead. Complete [RELEASE_PUBLICATION_VERIFICATION.md](./RELEASE_PUBLICATION_VERIFICATION.md) and [RELEASE_PUBLICATION_CONTAINER_VERIFICATION.md](./RELEASE_PUBLICATION_CONTAINER_VERIFICATION.md) after convergence, then return to [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md) for release hygiene.
