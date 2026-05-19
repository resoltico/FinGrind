#!/usr/bin/env bash
# Verify that the repository root contains only approved structural entries plus explicit local-state roots.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

print_usage() {
    printf '%s\n' \
        'Usage: ./scripts/verify-repo-hygiene.sh [--report-local-state] [--repo-root <path>]' \
        '' \
        'Verifies that the repository root contains only approved first-class entries plus' \
        'explicit ignored local-state roots. Temporary investigation state must live under tmp/.' \
        '' \
        'Options:' \
        '  --report-local-state   print a size summary for allowed local-state roots before verifying' \
        '  --repo-root <path>     verify a specific repository root instead of the script owner' \
        '  -h, --help             show this help text'
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
readonly default_repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly support_script="${script_dir}/repo-hygiene-support.sh"

[[ -f "${support_script}" ]] || die "missing repo hygiene support helper at ${support_script}"
# shellcheck source=/dev/null
source "${support_script}"

repo_root="${default_repo_root}"
report_local_state=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --report-local-state)
            report_local_state=true
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
[[ -d "${repo_root}/.git" || -f "${repo_root}/.git" ]] || die \
    "repository root does not contain a .git entry: ${repo_root}"
git_dir="$(git -C "${repo_root}" rev-parse --path-format=absolute --git-dir 2>/dev/null)" || die \
    "repository root is not a readable Git checkout: ${repo_root}"
git_common_dir="$(git -C "${repo_root}" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)" || die \
    "repository root does not expose a readable Git common directory: ${repo_root}"
readonly git_dir
readonly git_common_dir

if [[ "${report_local_state}" == true ]]; then
    printf '%s\n' 'Allowed local-state root sizes:'
    printf '%s\n' '  SIZE   CATEGORY         CLEANUP FLAG             PATH'
    repo_hygiene_print_local_state_report "${repo_root}" || true
    printf '\n'
fi

unexpected_root_entries=()
unignored_local_state_entries=()

while IFS= read -r root_entry; do
    [[ -n "${root_entry}" ]] || continue
    if repo_hygiene_is_structural_root_entry "${root_entry}"; then
        continue
    fi
    if repo_hygiene_is_local_state_root_entry "${root_entry}"; then
        if ! git -C "${repo_root}" check-ignore -q -- "${root_entry}"; then
            unignored_local_state_entries+=("${root_entry}")
        fi
        continue
    fi
    unexpected_root_entries+=("${root_entry}")
done < <(repo_hygiene_list_root_entries "${repo_root}")

if (( ${#unexpected_root_entries[@]} > 0 )); then
    printf '%s\n' \
        'error: unexpected repository-root entries detected; only approved first-class entries plus' \
        'explicit local-state roots may live at checkout top level. Temporary investigation state' \
        'must live under tmp/.' >&2
    printf '  - %s\n' "${unexpected_root_entries[@]}" >&2
    exit 1
fi

if (( ${#unignored_local_state_entries[@]} > 0 )); then
    printf '%s\n' \
        'error: approved local-state roots exist at the repository top level but are not ignored by Git:' >&2
    printf '  - %s\n' "${unignored_local_state_entries[@]}" >&2
    exit 1
fi

git_lock_files=()
git_lock_search_roots=("${git_dir}")
if [[ "${git_common_dir}" != "${git_dir}" ]]; then
    git_lock_search_roots+=("${git_common_dir}")
fi

while IFS= read -r git_lock_file; do
    [[ -n "${git_lock_file}" ]] || continue
    git_lock_files+=("${git_lock_file}")
done < <(
    for git_lock_root in "${git_lock_search_roots[@]}"; do
        find "${git_lock_root}" -type f -name '*.lock' -print
    done | LC_ALL=C sort -u
)

if (( ${#git_lock_files[@]} > 0 )); then
    printf '%s\n' \
        'error: git coordination lock files are present; the checkout is not safe for release or hygiene-sensitive verification:' >&2
    printf '  - %s\n' "${git_lock_files[@]}" >&2
    printf '%s\n' \
        'Inspect lock ownership with lsof before removing any lock file. Remove only orphaned lock files; wait for or isolate live Git owners.' >&2
    exit 1
fi

set +e
object_store_output="$(git -C "${repo_root}" fsck --full --no-dangling --no-progress 2>&1)"
object_store_status=$?
set -e

if (( object_store_status != 0 )); then
    printf '%s\n' \
        'error: git object-store verification failed; the checkout is not safe for release or hygiene-sensitive verification:' >&2
    printf '%s\n' "${object_store_output}" >&2
    exit 1
fi

printf 'repo hygiene verification: success\n'
