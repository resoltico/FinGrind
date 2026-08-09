#!/usr/bin/env bash
# Exercise the release repository-settings verifier against stubbed GitHub surfaces so the
# protected release-merge contract stays executable instead of prose-only.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

resolve_script_dir() {
    local source_path="${BASH_SOURCE[0]}"
    while [[ -h "${source_path}" ]]; do
        local source_dir
        source_dir="$(cd -P -- "$(dirname -- "${source_path}")" && pwd)"
        source_path="$(readlink "${source_path}")"
        if [[ "${source_path}" != /* ]]; then
            source_path="${source_dir}/${source_path}"
        fi
    done
    cd -P -- "$(dirname -- "${source_path}")" && pwd
}

run_verify_expect_success() {
    local repo_view_fixture=$1
    local protection_fixture=$2
    local runners_fixture=$3
    local workflow_permissions_fixture=$4
    local tag_rulesets_fixture=$5
    PATH="${stub_dir}:${PATH}" \
        FINGRIND_GH_REPO_VIEW_FIXTURE="${repo_view_fixture}" \
        FINGRIND_GH_REPOSITORY_METADATA_FIXTURE="${test_root}/repository-metadata.json" \
        FINGRIND_GH_PROTECTION_FIXTURE="${protection_fixture}" \
        FINGRIND_GH_RUNNERS_FIXTURE="${runners_fixture}" \
        FINGRIND_GH_WORKFLOW_PERMISSIONS_FIXTURE="${workflow_permissions_fixture}" \
        FINGRIND_GH_TAG_RULESETS_FIXTURE="${tag_rulesets_fixture}" \
        FINGRIND_GH_PRIVATE_REPORTING_ENABLED="${FINGRIND_GH_PRIVATE_REPORTING_ENABLED:-true}" \
        FINGRIND_GH_DEPENDABOT_SECURITY_UPDATES_STATUS="${FINGRIND_GH_DEPENDABOT_SECURITY_UPDATES_STATUS:-enabled}" \
        FINGRIND_GH_DEPENDABOT_ALERTS_STATUS="${FINGRIND_GH_DEPENDABOT_ALERTS_STATUS:-enabled}" \
        "${verify_script}" main >/dev/null
}

run_verify_expect_failure() {
    local repo_view_fixture=$1
    local protection_fixture=$2
    local runners_fixture=$3
    local workflow_permissions_fixture=$4
    local tag_rulesets_fixture=$5
    local expected_message=$6
    local failure_output="${test_root}/failure-output.txt"

    if PATH="${stub_dir}:${PATH}" \
        FINGRIND_GH_REPO_VIEW_FIXTURE="${repo_view_fixture}" \
        FINGRIND_GH_REPOSITORY_METADATA_FIXTURE="${test_root}/repository-metadata.json" \
        FINGRIND_GH_PROTECTION_FIXTURE="${protection_fixture}" \
        FINGRIND_GH_RUNNERS_FIXTURE="${runners_fixture}" \
        FINGRIND_GH_WORKFLOW_PERMISSIONS_FIXTURE="${workflow_permissions_fixture}" \
        FINGRIND_GH_TAG_RULESETS_FIXTURE="${tag_rulesets_fixture}" \
        FINGRIND_GH_PRIVATE_REPORTING_ENABLED="${FINGRIND_GH_PRIVATE_REPORTING_ENABLED:-true}" \
        FINGRIND_GH_DEPENDABOT_SECURITY_UPDATES_STATUS="${FINGRIND_GH_DEPENDABOT_SECURITY_UPDATES_STATUS:-enabled}" \
        FINGRIND_GH_DEPENDABOT_ALERTS_STATUS="${FINGRIND_GH_DEPENDABOT_ALERTS_STATUS:-enabled}" \
        "${verify_script}" main >"${failure_output}" 2>&1
    then
        die "verifier unexpectedly succeeded"
    fi

    grep -Fq "${expected_message}" "${failure_output}" || die \
        "failure output did not mention '${expected_message}'"
}

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly verify_script="${repo_root}/scripts/verify-release-repo-settings.sh"
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly release_protocol="${repo_root}/docs/RELEASE_PROTOCOL.md"
readonly release_tag_ruleset_contract="${repo_root}/scripts/release_tag_ruleset_contract.py"

[[ -x "${verify_script}" ]] || die "missing executable verifier script at ${verify_script}"
[[ -f "${release_tag_ruleset_contract}" ]] || die \
    "missing release-tag ruleset contract at ${release_tag_ruleset_contract}"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
grep -Fq 'scripts/test-verify-release-repo-settings.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the release repository-settings regression"
grep -Fq './scripts/verify-release-repo-settings.sh' "${release_protocol}" || die \
    "release protocol no longer requires the repository-settings verifier"
grep -Fq 'verify-security-policy-surface.sh' "${verify_script}" || die \
    "release repository-settings verifier no longer proves the live security-policy surface"

readonly temp_parent="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-verify-release-repo-settings.XXXXXX")"
test_root="${temp_parent}/run"
stub_dir="${temp_parent}/stub-bin"
mkdir -p "${test_root}" "${stub_dir}"
cleanup() {
    chmod -R u+rwx "${temp_parent}" 2>/dev/null || true
    rm -rf "${temp_parent}" 2>/dev/null || true
}
trap cleanup EXIT

cat > "${test_root}/repo-view.json" <<'EOF'
{
  "nameWithOwner": "resoltico/FinGrind",
  "defaultBranchRef": {
    "name": "main"
  },
  "deleteBranchOnMerge": true
}
EOF

cat > "${test_root}/repository-metadata.json" <<'EOF'
{
  "owner": {
    "id": 17160191,
    "login": "resoltico",
    "type": "User"
  }
}
EOF

cat > "${test_root}/protection-success.json" <<'EOF'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["Gate"],
    "checks": [
      {
        "context": "Gate",
        "app_id": 15368
      }
    ]
  },
  "enforce_admins": {
    "enabled": true
  },
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": false,
    "required_approving_review_count": 0,
    "require_last_push_approval": false
  }
}
EOF

cat > "${test_root}/protection-admins.json" <<'EOF'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["Gate"],
    "checks": [
      {
        "context": "Gate",
        "app_id": 15368
      }
    ]
  },
  "enforce_admins": {
    "enabled": false
  },
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": false,
    "required_approving_review_count": 0,
    "require_last_push_approval": false
  }
}
EOF

cat > "${test_root}/protection-contexts.json" <<'EOF'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["Check"],
    "checks": [
      {
        "context": "Check",
        "app_id": 15368
      }
    ]
  },
  "enforce_admins": {
    "enabled": true
  },
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": false,
    "required_approving_review_count": 0,
    "require_last_push_approval": false
  }
}
EOF

cat > "${test_root}/protection-code-owner-review.json" <<'EOF'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["Gate"],
    "checks": [
      {
        "context": "Gate",
        "app_id": 15368
      }
    ]
  },
  "enforce_admins": {
    "enabled": true
  },
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": true,
    "required_approving_review_count": 0,
    "require_last_push_approval": false
  }
}
EOF

cat > "${test_root}/protection-one-approval.json" <<'EOF'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["Gate"],
    "checks": [
      {
        "context": "Gate",
        "app_id": 15368
      }
    ]
  },
  "enforce_admins": {
    "enabled": true
  },
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": false,
    "required_approving_review_count": 1,
    "require_last_push_approval": false
  }
}
EOF

cat > "${test_root}/runners-none.json" <<'EOF'
{
  "total_count": 0,
  "runners": []
}
EOF

cat > "${test_root}/runners-one.json" <<'EOF'
{
  "total_count": 1,
  "runners": [
    {
      "id": 1,
      "name": "untrusted-runner"
    }
  ]
}
EOF

cat > "${test_root}/workflow-permissions-read.json" <<'EOF'
{
  "default_workflow_permissions": "read",
  "can_approve_pull_request_reviews": false
}
EOF

cat > "${test_root}/workflow-permissions-write.json" <<'EOF'
{
  "default_workflow_permissions": "write",
  "can_approve_pull_request_reviews": false
}
EOF

cat > "${test_root}/tag-rulesets-valid.json" <<'EOF'
{
  "inventory": [
    [
      {"id": 101},
      {"id": 102}
    ]
  ],
  "details": {
    "101": {
      "id": 101,
      "name": "Authorize FinGrind release tag creation",
      "source_type": "Repository",
      "target": "tag",
      "enforcement": "active",
      "conditions": {
        "ref_name": {
          "include": ["refs/tags/v*"],
          "exclude": []
        }
      },
      "rules": [
        {"type": "creation"}
      ],
      "bypass_actors": [
        {
          "actor_id": 17160191,
          "actor_type": "User",
          "bypass_mode": "always"
        }
      ]
    },
    "102": {
      "id": 102,
      "name": "Protect FinGrind release tag immutability",
      "source_type": "Repository",
      "target": "tag",
      "enforcement": "active",
      "conditions": {
        "ref_name": {
          "include": ["refs/tags/v*"],
          "exclude": []
        }
      },
      "rules": [
        {"type": "update"},
        {"type": "deletion"}
      ],
      "bypass_actors": []
    }
  }
}
EOF

cat > "${test_root}/tag-rulesets-empty.json" <<'EOF'
{
  "inventory": [],
  "details": {}
}
EOF

cat > "${test_root}/tag-rulesets-extra-bypass.json" <<'EOF'
{
  "inventory": [
    [
      {"id": 101},
      {"id": 102}
    ]
  ],
  "details": {
    "101": {
      "id": 101,
      "name": "Authorize FinGrind release tag creation",
      "source_type": "Repository",
      "target": "tag",
      "enforcement": "active",
      "conditions": {
        "ref_name": {
          "include": ["refs/tags/v*"],
          "exclude": []
        }
      },
      "rules": [
        {"type": "creation"}
      ],
      "bypass_actors": [
        {
          "actor_id": 17160191,
          "actor_type": "User",
          "bypass_mode": "always"
        },
        {
          "actor_id": 42,
          "actor_type": "User",
          "bypass_mode": "always"
        }
      ]
    },
    "102": {
      "id": 102,
      "name": "Protect FinGrind release tag immutability",
      "source_type": "Repository",
      "target": "tag",
      "enforcement": "active",
      "conditions": {
        "ref_name": {
          "include": ["refs/tags/v*"],
          "exclude": []
        }
      },
      "rules": [
        {"type": "update"},
        {"type": "deletion"}
      ],
      "bypass_actors": []
    }
  }
}
EOF

cat > "${test_root}/tag-rulesets-mutable.json" <<'EOF'
{
  "inventory": [
    [
      {"id": 101},
      {"id": 102}
    ]
  ],
  "details": {
    "101": {
      "id": 101,
      "name": "Authorize FinGrind release tag creation",
      "source_type": "Repository",
      "target": "tag",
      "enforcement": "active",
      "conditions": {
        "ref_name": {
          "include": ["refs/tags/v*"],
          "exclude": []
        }
      },
      "rules": [
        {"type": "creation"}
      ],
      "bypass_actors": [
        {
          "actor_id": 17160191,
          "actor_type": "User",
          "bypass_mode": "always"
        }
      ]
    },
    "102": {
      "id": 102,
      "name": "Protect FinGrind release tag immutability",
      "source_type": "Repository",
      "target": "tag",
      "enforcement": "active",
      "conditions": {
        "ref_name": {
          "include": ["refs/tags/v*"],
          "exclude": []
        }
      },
      "rules": [
        {
          "type": "update",
          "parameters": {
            "unexpected": true
          }
        },
        {"type": "deletion"}
      ],
      "bypass_actors": []
    }
  }
}
EOF

cat > "${stub_dir}/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

case "${1:-}" in
    repo)
        shift
        [[ "${1:-}" == "view" ]] || {
            printf 'unexpected gh repo command\n' >&2
            exit 1
        }
        cat "${FINGRIND_GH_REPO_VIEW_FIXTURE}"
        ;;
    api)
        shift
        while [[ "${1:-}" == "--paginate" || "${1:-}" == "--slurp" ]]; do
            shift
        done
        api_target="${1:-}"
        case "${api_target}" in
            /repos/resoltico/FinGrind/private-vulnerability-reporting)
                printf '%s\n' "${FINGRIND_GH_PRIVATE_REPORTING_ENABLED}"
                ;;
            /repos/resoltico/FinGrind)
                printf '%s\n' "${FINGRIND_GH_DEPENDABOT_SECURITY_UPDATES_STATUS}"
                ;;
            '/repos/resoltico/FinGrind/dependabot/alerts?state=open&per_page=1')
                printf '%s\n' "${FINGRIND_GH_DEPENDABOT_ALERTS_STATUS}"
                ;;
            repos/resoltico/FinGrind)
                cat "${FINGRIND_GH_REPOSITORY_METADATA_FIXTURE}"
                ;;
            repos/resoltico/FinGrind/branches/main/protection)
                cat "${FINGRIND_GH_PROTECTION_FIXTURE}"
                ;;
            repos/resoltico/FinGrind/actions/runners)
                cat "${FINGRIND_GH_RUNNERS_FIXTURE}"
                ;;
            repos/resoltico/FinGrind/actions/permissions/workflow)
                cat "${FINGRIND_GH_WORKFLOW_PERMISSIONS_FIXTURE}"
                ;;
            'repos/resoltico/FinGrind/rulesets?targets=tag&includes_parents=true&per_page=100')
                FINGRIND_GH_TAG_RULESETS_FIXTURE="${FINGRIND_GH_TAG_RULESETS_FIXTURE}" \
                    python3 - <<'PY'
import json
import os

with open(os.environ["FINGRIND_GH_TAG_RULESETS_FIXTURE"], encoding="utf-8") as fixture:
    print(json.dumps(json.load(fixture)["inventory"]))
PY
                ;;
            repos/resoltico/FinGrind/rulesets/*)
                ruleset_id="${api_target##*/}"
                FINGRIND_GH_TAG_RULESETS_FIXTURE="${FINGRIND_GH_TAG_RULESETS_FIXTURE}" \
                    FINGRIND_GH_TAG_RULESET_ID="${ruleset_id}" \
                    python3 - <<'PY'
import json
import os

with open(os.environ["FINGRIND_GH_TAG_RULESETS_FIXTURE"], encoding="utf-8") as fixture:
    details = json.load(fixture)["details"]
ruleset = details.get(os.environ["FINGRIND_GH_TAG_RULESET_ID"])
if ruleset is None:
    raise SystemExit(1)
print(json.dumps(ruleset))
PY
                ;;
            *)
                printf 'unexpected gh api target: %s\n' "${api_target}" >&2
                exit 1
                ;;
        esac
        ;;
    *)
        printf 'unexpected gh invocation: %s\n' "$*" >&2
        exit 1
        ;;
esac
EOF
chmod +x "${stub_dir}/gh"

run_verify_expect_success \
    "${test_root}/repo-view.json" \
    "${test_root}/protection-success.json" \
    "${test_root}/runners-none.json" \
    "${test_root}/workflow-permissions-read.json" \
    "${test_root}/tag-rulesets-valid.json"
FINGRIND_GH_DEPENDABOT_SECURITY_UPDATES_STATUS='disabled' \
    run_verify_expect_failure \
        "${test_root}/repo-view.json" \
        "${test_root}/protection-success.json" \
        "${test_root}/runners-none.json" \
        "${test_root}/workflow-permissions-read.json" \
        "${test_root}/tag-rulesets-valid.json" \
        "release security-policy verification failed: error: GitHub Dependabot security updates are disabled"
run_verify_expect_failure \
    "${test_root}/repo-view.json" \
    "${test_root}/protection-admins.json" \
    "${test_root}/runners-none.json" \
    "${test_root}/workflow-permissions-read.json" \
    "${test_root}/tag-rulesets-valid.json" \
    "administrator enforcement must remain enabled"
run_verify_expect_failure \
    "${test_root}/repo-view.json" \
    "${test_root}/protection-code-owner-review.json" \
    "${test_root}/runners-none.json" \
    "${test_root}/workflow-permissions-read.json" \
    "${test_root}/tag-rulesets-valid.json" \
    "code-owner review must not be required"
run_verify_expect_failure \
    "${test_root}/repo-view.json" \
    "${test_root}/protection-one-approval.json" \
    "${test_root}/runners-none.json" \
    "${test_root}/workflow-permissions-read.json" \
    "${test_root}/tag-rulesets-valid.json" \
    "required approving review count must equal 0"
run_verify_expect_failure \
    "${test_root}/repo-view.json" \
    "${test_root}/protection-contexts.json" \
    "${test_root}/runners-none.json" \
    "${test_root}/workflow-permissions-read.json" \
    "${test_root}/tag-rulesets-valid.json" \
    "required status-check contexts must equal ['Gate']"
run_verify_expect_failure \
    "${test_root}/repo-view.json" \
    "${test_root}/protection-success.json" \
    "${test_root}/runners-one.json" \
    "${test_root}/workflow-permissions-read.json" \
    "${test_root}/tag-rulesets-valid.json" \
    "public release repository must not expose self-hosted runners to workflow jobs"
run_verify_expect_failure \
    "${test_root}/repo-view.json" \
    "${test_root}/protection-success.json" \
    "${test_root}/runners-none.json" \
    "${test_root}/workflow-permissions-write.json" \
    "${test_root}/tag-rulesets-valid.json" \
    "Actions default workflow permissions must be read"
run_verify_expect_failure \
    "${test_root}/repo-view.json" \
    "${test_root}/protection-success.json" \
    "${test_root}/runners-none.json" \
    "${test_root}/workflow-permissions-read.json" \
    "${test_root}/tag-rulesets-empty.json" \
    "must expose exactly the two canonical release-tag rulesets"
run_verify_expect_failure \
    "${test_root}/repo-view.json" \
    "${test_root}/protection-success.json" \
    "${test_root}/runners-none.json" \
    "${test_root}/workflow-permissions-read.json" \
    "${test_root}/tag-rulesets-extra-bypass.json" \
    "must authorize exactly the repository-owner GitHub user"
run_verify_expect_failure \
    "${test_root}/repo-view.json" \
    "${test_root}/protection-success.json" \
    "${test_root}/runners-none.json" \
    "${test_root}/workflow-permissions-read.json" \
    "${test_root}/tag-rulesets-mutable.json" \
    "must contain exactly the no-bypass update and deletion tag rules"

printf 'verify-release-repo-settings regression: success\n'
