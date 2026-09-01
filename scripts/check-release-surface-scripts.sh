#!/usr/bin/env bash
# Run the canonical Stage 5 release-surface shell-script verification workflow.

set -euo pipefail

print_usage() {
    printf '%s\n' \
        'Usage: ./scripts/check-release-surface-scripts.sh' \
        '' \
        'Runs the fixed Stage 5 release-surface shell-script verification workflow for this repository.' \
        '' \
        'This command does not accept additional arguments.'
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
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly repo_lock_support="${repo_root}/scripts/repo-verification-lock-support.sh"
readonly structural_governance_verifier="${repo_root}/scripts/verify-structural-governance.sh"

for argument in "$@"; do
    case "${argument}" in
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            printf 'error: unsupported argument for check-release-surface-scripts.sh: %s\n\n' \
                "${argument}" >&2
            print_usage >&2
            exit 1
            ;;
    esac
done

[[ -f "${stage_contract_script}" ]] || {
    printf 'error: missing check stage contract helper at %s\n' "${stage_contract_script}" >&2
    exit 1
}
[[ -f "${repo_lock_support}" ]] || {
    printf 'error: missing repo verification lock helper at %s\n' "${repo_lock_support}" >&2
    exit 1
}
[[ -x "${structural_governance_verifier}" ]] || {
    printf 'error: missing executable structural governance verifier at %s\n' "${structural_governance_verifier}" >&2
    exit 1
}

# shellcheck source=/dev/null
source "${repo_lock_support}"
# shellcheck source=/dev/null
source "${stage_contract_script}"
trap cleanup_lock EXIT
acquire_lock

release_surface_script_targets=("${repo_root}/check.sh" "${repo_root}/check_mutation.sh")
if [[ -d "${repo_root}/scripts" ]]; then
    while IFS= read -r shell_script_path; do
        release_surface_script_targets+=("${shell_script_path}")
    done < <(find "${repo_root}/scripts" -maxdepth 1 -type f -name '*.sh' | sort)
fi
if [[ -d "${repo_root}/jazzer/bin" ]]; then
    while IFS= read -r shell_script_path; do
        release_surface_script_targets+=("${shell_script_path}")
    done < <(find "${repo_root}/jazzer/bin" -maxdepth 1 -type f | sort)
fi

bash -n "${release_surface_script_targets[@]}"
"${structural_governance_verifier}" --surface shell-release
for script_path in "${check_stage5_executable_script_paths[@]}"; do
    printf 'release-surface subcheck: %s\n' "${script_path}"
    bash "${repo_root}/${script_path}"
done
