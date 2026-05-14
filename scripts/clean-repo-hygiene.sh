#!/usr/bin/env bash
# Prune repo-owned local state and root clutter without touching tracked project structure.

set -euo pipefail

warn() {
    printf 'warning: %s\n' "$1" >&2
}

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

print_usage() {
    printf '%s\n' \
        'Usage: ./scripts/clean-repo-hygiene.sh [--purge-generated-state] [--purge-tool-state] [--purge-tmp] [--repo-root <path>]' \
        '' \
        'Always removes:' \
        '  - root and nested .DS_Store files' \
        '  - empty unexpected repository-root entries' \
        '' \
        'Optional removals:' \
        '  --purge-generated-state  remove repo-owned generated state such as build/, .gradle/,' \
        '                           .ruff_cache/, module build/bin directories, and .local/tooling/' \
        '  --purge-tool-state       remove ignored external-tool state roots such as .claude/,' \
        '                           .local/, and .vscode/' \
        '  --purge-tmp              remove the repo tmp/ scratch root as well' \
        '  --repo-root <path>       clean a specific repository root instead of the script owner' \
        '  -h, --help               show this help text'
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

remove_path() {
    local target_path=$1
    [[ -e "${target_path}" || -L "${target_path}" ]] || return 0
    rm -rf -- "${target_path}" 2>/dev/null || {
        warn "unable to remove ${target_path}"
        return 1
    }
}

readonly script_dir="$(resolve_script_dir)"
readonly default_repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly support_script="${script_dir}/repo-hygiene-support.sh"

[[ -f "${support_script}" ]] || die "missing repo hygiene support helper at ${support_script}"
# shellcheck source=/dev/null
source "${support_script}"

repo_root="${default_repo_root}"
purge_generated_state=false
purge_tool_state=false
purge_tmp=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --purge-generated-state)
            purge_generated_state=true
            shift
            ;;
        --purge-tool-state)
            purge_tool_state=true
            shift
            ;;
        --purge-tmp)
            purge_tmp=true
            shift
            ;;
        --repo-root)
            [[ $# -ge 2 ]] || die "--repo-root requires a path"
            repo_root=$2
            shift 2
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            die "unsupported argument: $1"
            ;;
    esac
done

repo_root="$(cd -P -- "${repo_root}" && pwd)"
[[ -d "${repo_root}" ]] || die "repository root is not a directory: ${repo_root}"

find "${repo_root}" -name '.DS_Store' -type f -delete 2>/dev/null || true

while IFS= read -r root_entry; do
    [[ -n "${root_entry}" ]] || continue
    if repo_hygiene_is_expected_root_entry "${root_entry}"; then
        continue
    fi
    if ! rmdir "${repo_root}/${root_entry}" 2>/dev/null; then
        warn "unexpected repository-root entry is not empty and was left in place: ${root_entry}"
    fi
done < <(repo_hygiene_list_root_entries "${repo_root}")

if [[ "${purge_generated_state}" == true ]]; then
    generated_state_paths=(
        "${repo_root}/.gradle"
        "${repo_root}/.ruff_cache"
        "${repo_root}/build"
        "${repo_root}/cli/bin"
        "${repo_root}/cli/build"
        "${repo_root}/contract/build"
        "${repo_root}/core/bin"
        "${repo_root}/core/build"
        "${repo_root}/executor/bin"
        "${repo_root}/executor/build"
        "${repo_root}/gradle/build-logic/.gradle"
        "${repo_root}/gradle/build-logic/.kotlin"
        "${repo_root}/gradle/build-logic/bin"
        "${repo_root}/gradle/build-logic/build"
        "${repo_root}/jazzer/.gradle"
        "${repo_root}/jazzer/build"
        "${repo_root}/report-pdf/build"
        "${repo_root}/sqlite/bin"
        "${repo_root}/sqlite/build"
        "${repo_root}/.local/tooling"
    )
    for generated_state_path in "${generated_state_paths[@]}"; do
        remove_path "${generated_state_path}" || true
    done
fi

if [[ "${purge_tool_state}" == true ]]; then
    tool_state_paths=(
        "${repo_root}/.claude"
        "${repo_root}/.local"
        "${repo_root}/.vscode"
    )
    for tool_state_path in "${tool_state_paths[@]}"; do
        remove_path "${tool_state_path}" || true
    done
fi

if [[ "${purge_tmp}" == true ]]; then
    remove_path "${repo_root}/tmp" || true
fi

printf 'repo hygiene cleanup: success\n'
