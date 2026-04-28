#!/usr/bin/env bash
# Run the canonical Stage 5 shell-syntax and release-surface script verification workflow.

set -euo pipefail

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

[[ -f "${stage_contract_script}" ]] || {
    printf 'error: missing check stage contract helper at %s\n' "${stage_contract_script}" >&2
    exit 1
}

# shellcheck source=/dev/null
source "${stage_contract_script}"

shell_syntax_targets=("${repo_root}/check.sh")
if [[ -d "${repo_root}/scripts" ]]; then
    while IFS= read -r shell_script_path; do
        shell_syntax_targets+=("${shell_script_path}")
    done < <(find "${repo_root}/scripts" -maxdepth 1 -type f -name '*.sh' | sort)
fi
if [[ -d "${repo_root}/jazzer/bin" ]]; then
    while IFS= read -r shell_script_path; do
        shell_syntax_targets+=("${shell_script_path}")
    done < <(find "${repo_root}/jazzer/bin" -maxdepth 1 -type f | sort)
fi

bash -n "${shell_syntax_targets[@]}"
for script_path in "${check_stage5_script_paths[@]}"; do
    bash "${repo_root}/${script_path}"
done
