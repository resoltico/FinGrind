---
afad: "5.0.1"
version: "0.64.0"
domain: GITHUB_RELEASE_TAG_GOVERNANCE
updated: "2026-09-01"
route:
  keywords: [fingrind, github, release tag, ruleset, tag immutability, tag creation]
  questions: ["how does fingrind protect release tags", "how do I configure fingrind tag rulesets", "how do I recover drifted fingrind release-tag rulesets", "who may create fingrind release tags"]
---

# GitHub Release Tag Governance

**Purpose**: Configure the GitHub rulesets that authorize FinGrind release-tag creation and make published release tags immutable.
**Prerequisites**: GitHub CLI authentication as the repository owner or an administrator with repository-ruleset read and write authority, an existing `resoltico/FinGrind` repository, and the bootstrap readiness checks in [GITHUB_BOOTSTRAP_PROTOCOL.md](./GITHUB_BOOTSTRAP_PROTOCOL.md).

Do not create tags or publish releases while configuring this control plane. Release publication is covered by [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md).

## Required rulesets

The policy has two separate, active, repository-owned tag rulesets:

- `Authorize FinGrind release tag creation` blocks creation of every `v*` tag except by the current repository owner.
- `Protect FinGrind release tag immutability` blocks every update and deletion of every `v*` tag, with no bypass actor.

They must remain separate. A bypass on a combined creation, update, and deletion ruleset would let the release creator move or delete a tag after creating it. The `v*` ref pattern is only the GitHub lifecycle scope; the release workflow and candidate verifier separately require the narrower stable `vX.Y.Z` grammar.

## Configuration

Use the canonical configurator from the repository root:

```bash
./scripts/configure-release-tag-rulesets.sh
```

The configurator derives the repository-owner numeric REST user ID, fetches every effective tag
ruleset, and accepts only an empty inventory, one exact canonical ruleset, or the exact complete
pair. It creates only the missing canonical rule, never deletes or replaces existing policy, then
runs `verify-release-repo-settings.sh`. This makes a retry after an interrupted first request safe:
the known-good existing rule is retained and only its missing counterpart can be created. An extra,
inactive, inherited, broadened, or bypass-bearing rule fails closed and requires deliberate
control-plane repair before a release can continue.

The verifier fails unless the final state is exactly the two canonical policies, with the live
repository-owner numeric REST user ID as the sole creation bypass and no immutability bypass. It
also detects policy drift before each release. Do not temporarily relax, combine, or replace these
rulesets as a workaround for a release failure; the explicit fail-closed drift procedure below is
the only supported control-plane correction.

GitHub rulesets protect the tag reference at the GitHub control plane; a repository administrator can still edit or remove a ruleset. They do not replace the tag-to-commit, release-asset, and attestation proofs in the release protocol.

## Drift recovery

The normal configurator intentionally refuses a drifted, extra, inactive, broadened, inherited, or
bypass-bearing policy. That is not a failed configuration retry: it is a release-control incident.
Stop every tag and publication action, preserve the evidence, and repair the control plane before
starting another release preflight.

First capture the effective inventory and each ruleset detail by ID. Keep this snapshot until the
recovery is independently verified:

```bash
REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
SNAPSHOT_DIR="$(mktemp -d -t fingrind-release-tag-rulesets-XXXXXX)"
gh api --paginate --slurp \
  "repos/${REPO}/rulesets?targets=tag&includes_parents=true&per_page=100" \
  > "${SNAPSHOT_DIR}/inventory.json"
jq -r '.[] | .[]? | [.id, .name, .source_type, .target, .enforcement] | @tsv' \
  "${SNAPSHOT_DIR}/inventory.json"
jq -r '.[] | .[]? | .id' "${SNAPSHOT_DIR}/inventory.json" |
  while IFS= read -r RULESET_ID; do
    gh api "repos/${REPO}/rulesets/${RULESET_ID}" \
      > "${SNAPSHOT_DIR}/ruleset-${RULESET_ID}.json"
  done
./scripts/verify-release-repo-settings.sh
```

The safe recovery depends on the observed state:

- If one canonical rule is wholly absent and every remaining rule is exact, rerun
  `./scripts/configure-release-tag-rulesets.sh`. That is the only state the normal configurator
  repairs automatically.
- If a repository-owned rule with one of the two canonical names is malformed or inactive, identify
  its exact ID from the snapshot. During a deliberate control-plane maintenance window with no tag
  creation, workflow dispatch, or release publication in progress, an authorized repository owner
  may remove only that identified malformed repository-owned rule, immediately recreate the missing
  canonical rule, and verify the complete pair:

  ```bash
  gh api --method DELETE "repos/${REPO}/rulesets/<identified-repository-ruleset-id>"
  ./scripts/configure-release-tag-rulesets.sh
  ./scripts/verify-release-repo-settings.sh
  ```

  Removing a rule is not an ordinary release repair: it creates a temporary policy gap, so do not
  create or push a release tag until the final verifier succeeds.
- If the snapshot contains an inherited or organization-owned rule, an unfamiliar extra rule, or a
  rule whose ownership cannot be established, do not delete it to make the verifier green. Its
  policy owner must deliberately remove, retarget, or replace it. If that policy must remain, the
  repository cannot claim the exact FinGrind release-tag contract and no release may continue under
  an exception.

After any correction, rerun the complete preflight from [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md).
The snapshot proves what changed; the final verifier, remote tag admission, and public artifact
checks prove that the repaired policy was not merely assumed.
