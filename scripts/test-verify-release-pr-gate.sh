#!/usr/bin/env bash
# Reproduce and guard the release-PR Gate verifier so a green Check job cannot be mistaken for a
# complete aggregate Gate on release PRs.

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

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly verifier="${repo_root}/scripts/verify-release-pr-gate.sh"
readonly verification_support="${repo_root}/scripts/release-check-verification-support.sh"
readonly release_check_support="${repo_root}/scripts/release-check-support.sh"
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly release_protocol="${repo_root}/docs/RELEASE_PROTOCOL.md"

[[ -x "${verifier}" ]] || die "missing executable PR Gate verifier at ${verifier}"
[[ -f "${verification_support}" ]] || die "missing verification helper at ${verification_support}"
[[ -f "${release_check_support}" ]] || die "missing release-check support helper at ${release_check_support}"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${release_protocol}" ]] || die "missing release protocol at ${release_protocol}"

grep -Fq 'scripts/test-verify-release-pr-gate.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the PR Gate verifier regression"
grep -Fq './scripts/verify-release-pr-gate.sh <N>' "${release_protocol}" || die \
    "release protocol no longer requires the PR Gate verifier"
grep -Fq 'FINGRIND_RELEASE_CHECK_TIMEOUT_SECONDS=3000 ./scripts/verify-release-pr-gate.sh <N>' "${release_protocol}" || die \
    "release protocol no longer documents the PR Gate verifier timeout override"
grep -Fq 'The aggregate `Gate` check run appears only after `Check`, `Windows bundle smoke`, and `Docker' "${release_protocol}" || die \
    "release protocol no longer documents the delayed Gate materialization contract"
grep -Fq '`Gate` is still absent. Treat a missing `Gate` as pending, not as success.' "${release_protocol}" || die \
    "release protocol no longer documents missing-Gate-as-pending semantics"
grep -Fq 'release-check-support.sh' "${verifier}" || die \
    "PR Gate verifier no longer sources the canonical release-check owner"
grep -Fq 'release-check-verification-support.sh' "${verifier}" || die \
    "PR Gate verifier no longer shares the canonical check-run polling helper"

readonly timeout_default="$(
    sed -n 's/^readonly timeout_seconds="${FINGRIND_RELEASE_CHECK_TIMEOUT_SECONDS:-\([0-9][0-9]*\)}"$/\1/p' \
        "${verifier}"
)"
[[ -n "${timeout_default}" ]] || die "failed to read PR Gate verifier default timeout"
(( timeout_default >= 2400 )) || die \
    "PR Gate verifier default timeout regressed below 2400 seconds (${timeout_default})"

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-verify-release-pr-gate.XXXXXX")"
cleanup() {
    rm -rf "${fixture_root}"
}
trap cleanup EXIT

mkdir -p "${fixture_root}/bin" "${fixture_root}/state"

cat > "${fixture_root}/bin/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

repo="${FAKE_GH_REPOSITORY:-resoltico/FinGrind}"
pr_number="${FAKE_GH_PR_NUMBER:-52}"
pr_state="${FAKE_GH_PR_STATE:-OPEN}"
pr_head_ref_name="${FAKE_GH_HEAD_REF_NAME:-release/0.32.0}"
pr_head_ref_oid="${FAKE_GH_HEAD_REF_OID:-99e1257ea944d7b7c10acb3b14cbede94cc93c11}"
pr_url="${FAKE_GH_PR_URL:-https://github.com/resoltico/FinGrind/pull/52}"
check_mode="${FAKE_GH_CHECK_MODE:-pending-then-success}"
state_dir="${FAKE_GH_STATE_DIR:?}"

if [[ "${1:-}" == "repo" && "${2:-}" == "view" ]]; then
    [[ "${3:-}" == "--json" && "${4:-}" == "nameWithOwner" && "${5:-}" == "--jq" && "${6:-}" == ".nameWithOwner" ]] || exit 1
    printf '%s\n' "${repo}"
    exit 0
fi

if [[ "${1:-}" == "pr" && "${2:-}" == "view" ]]; then
    [[ "${3:-}" == "${pr_number}" ]] || exit 1
    [[ "${4:-}" == "--repo" && "${5:-}" == "${repo}" && "${6:-}" == "--json" ]] || exit 1
    printf '{"number":%s,"state":"%s","headRefName":"%s","headRefOid":"%s","url":"%s"}\n' \
        "${pr_number}" \
        "${pr_state}" \
        "${pr_head_ref_name}" \
        "${pr_head_ref_oid}" \
        "${pr_url}"
    exit 0
fi

if [[ "${1:-}" == "api" && "${2:-}" == "/repos/${repo}/commits/${pr_head_ref_oid}/check-runs?per_page=100" ]]; then
    [[ "${3:-}" == "--jq" ]] || exit 1
    counter_file="${state_dir}/check-runs-count"
    count=0
    if [[ -f "${counter_file}" ]]; then
        count="$(cat "${counter_file}")"
    fi
    count="$((count + 1))"
    printf '%s' "${count}" > "${counter_file}"

    case "${check_mode}" in
        pending-then-success)
            if (( count == 1 )); then
                printf '%s\n' \
                    $'Check\tcompleted\tsuccess' \
                    $'Windows bundle smoke\tin_progress\t' \
                    $'Docker smoke\tqueued\t'
            else
                printf '%s\n' \
                    $'Check\tcompleted\tsuccess' \
                    $'Windows bundle smoke\tcompleted\tsuccess' \
                    $'Docker smoke\tcompleted\tsuccess' \
                    $'Gate\tcompleted\tsuccess'
            fi
            ;;
        gate-failure)
            printf '%s\n' \
                $'Check\tcompleted\tsuccess' \
                $'Windows bundle smoke\tcompleted\tsuccess' \
                $'Docker smoke\tcompleted\tsuccess' \
                $'Gate\tcompleted\tfailure'
            ;;
        *)
            exit 1
            ;;
    esac
    exit 0
fi

exit 1
EOF
chmod +x "${fixture_root}/bin/gh"

PATH="${fixture_root}/bin:${PATH}" \
    FAKE_GH_STATE_DIR="${fixture_root}/state" \
    bash "${verifier}" 52 >/dev/null

check_runs_count="$(cat "${fixture_root}/state/check-runs-count")"
(( check_runs_count >= 2 )) || die \
    "PR Gate verifier accepted a missing Gate instead of waiting for the aggregate check run"

rm -f "${fixture_root}/state/check-runs-count"

set +e
failure_output="$(
    PATH="${fixture_root}/bin:${PATH}" \
        FAKE_GH_STATE_DIR="${fixture_root}/state" \
        FAKE_GH_CHECK_MODE='gate-failure' \
        bash "${verifier}" 52 2>&1
)"
failure_exit=$?
set -e

if [[ ${failure_exit} -eq 0 ]]; then
    die "PR Gate verifier accepted a failed Gate check"
fi
printf '%s\n' "${failure_output}" | grep -Fq 'release-blocking checks failed (Gate)' || die \
    "PR Gate verifier did not report the failed Gate check"

printf 'verify-release-pr-gate regression: success\n'
