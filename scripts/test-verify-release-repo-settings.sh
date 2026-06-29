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
    PATH="${stub_dir}:${PATH}" \
        FINGRIND_GH_REPO_VIEW_FIXTURE="${repo_view_fixture}" \
        FINGRIND_GH_PROTECTION_FIXTURE="${protection_fixture}" \
        "${verify_script}" main >/dev/null
}

run_verify_expect_failure() {
    local repo_view_fixture=$1
    local protection_fixture=$2
    local expected_message=$3
    local failure_output="${test_root}/failure-output.txt"

    if PATH="${stub_dir}:${PATH}" \
        FINGRIND_GH_REPO_VIEW_FIXTURE="${repo_view_fixture}" \
        FINGRIND_GH_PROTECTION_FIXTURE="${protection_fixture}" \
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

[[ -x "${verify_script}" ]] || die "missing executable verifier script at ${verify_script}"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
grep -Fq 'scripts/test-verify-release-repo-settings.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the release repository-settings regression"
grep -Fq './scripts/verify-release-repo-settings.sh' "${release_protocol}" || die \
    "release protocol no longer requires the repository-settings verifier"

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
    "enabled": false
  },
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": true,
    "required_approving_review_count": 1,
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
    "enabled": true
  },
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": true,
    "required_approving_review_count": 1,
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
    "enabled": false
  },
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": true,
    "required_approving_review_count": 1,
    "require_last_push_approval": false
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
        [[ "${1:-}" == "repos/resoltico/FinGrind/branches/main/protection" ]] || {
            printf 'unexpected gh api target: %s\n' "${1:-}" >&2
            exit 1
        }
        cat "${FINGRIND_GH_PROTECTION_FIXTURE}"
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
    "${test_root}/protection-success.json"
run_verify_expect_failure \
    "${test_root}/repo-view.json" \
    "${test_root}/protection-admins.json" \
    "administrator bypass is unavailable"
run_verify_expect_failure \
    "${test_root}/repo-view.json" \
    "${test_root}/protection-contexts.json" \
    "required status-check contexts must equal ['Gate']"

printf 'verify-release-repo-settings regression: success\n'
