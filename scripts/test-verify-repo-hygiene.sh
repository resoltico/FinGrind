#!/usr/bin/env bash
# Regress the repository-root hygiene verifier and cleaner.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

progress() {
    printf 'repo hygiene verifier check: %s\n' "$1"
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
readonly verifier="${repo_root}/scripts/verify-repo-hygiene.sh"
readonly cleaner="${repo_root}/scripts/clean-repo-hygiene.sh"

[[ -x "${verifier}" ]] || die "missing executable repo hygiene verifier"
[[ -x "${cleaner}" ]] || die "missing executable repo hygiene cleaner"

mkdir -p "${repo_root}/tmp"
fixture_root="$(mktemp -d "${repo_root}/tmp/repo-hygiene-fixture.XXXXXX")"
cleanup() {
    rm -rf "${fixture_root}" 2>/dev/null || true
}
trap cleanup EXIT

mkdir -p \
    "${fixture_root}/tmp/scratch" \
    "${fixture_root}/cli" \
    "${fixture_root}/scripts"
git -C "${fixture_root}" init -q -b main
git -C "${fixture_root}" config user.name 'Repo Hygiene Fixture'
git -C "${fixture_root}" config user.email 'repo-hygiene-fixture@example.invalid'
touch \
    "${fixture_root}/README.md" \
    "${fixture_root}/AGENTS.md" \
    "${fixture_root}/build.gradle.kts" \
    "${fixture_root}/settings.gradle.kts"
cat > "${fixture_root}/.gitignore" <<'EOF'
tmp/
build/
.gradle/
.ruff_cache/
.local/
.vscode/
.claude/
EOF
git -C "${fixture_root}" add .
git -C "${fixture_root}" commit -q -m 'fixture: initial state'

progress 'clean fixture root passes verification'
"${verifier}" --repo-root "${fixture_root}" || die \
    "repo hygiene verifier should accept a clean fixture root"

progress 'clean fixture root stays portable when git fsck lacks --no-references'
readonly real_git="$(command -v git)"
readonly shim_dir="${fixture_root}/tmp/git-shim"
mkdir -p "${shim_dir}"
cat > "${shim_dir}/git" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

readonly real_git="${REAL_GIT_PATH:?}"

if [[ "${1:-}" == '-C' && $# -ge 3 ]]; then
    repo_path=$2
    shift 2
    if [[ "${1:-}" == 'fsck' ]]; then
        shift
        if [[ "${1:-}" == '-h' || "${1:-}" == '--help' ]]; then
            cat >&2 <<'USAGE'
usage: git fsck [--tags] [--root] [--unreachable] [--cache] [--no-reflogs]
                [--[no-]full] [--strict] [--verbose] [--lost-found]
                [--[no-]dangling] [--[no-]progress] [--connectivity-only]
                [--[no-]name-objects] [<object>...]
USAGE
            exit 129
        fi
        for argument in "$@"; do
            if [[ "${argument}" == '--no-references' ]]; then
                printf '%s\n' "error: unknown option 'no-references'" >&2
                exit 129
            fi
        done
        exec "${real_git}" -C "${repo_path}" fsck "$@"
    fi
    exec "${real_git}" -C "${repo_path}" "$@"
fi

if [[ "${1:-}" == 'fsck' ]]; then
    shift
    if [[ "${1:-}" == '-h' || "${1:-}" == '--help' ]]; then
        cat >&2 <<'USAGE'
usage: git fsck [--tags] [--root] [--unreachable] [--cache] [--no-reflogs]
                [--[no-]full] [--strict] [--verbose] [--lost-found]
                [--[no-]dangling] [--[no-]progress] [--connectivity-only]
                [--[no-]name-objects] [<object>...]
USAGE
        exit 129
    fi
    for argument in "$@"; do
        if [[ "${argument}" == '--no-references' ]]; then
            printf '%s\n' "error: unknown option 'no-references'" >&2
            exit 129
        fi
    done
    exec "${real_git}" fsck "$@"
fi

exec "${real_git}" "$@"
EOF
chmod +x "${shim_dir}/git"
REAL_GIT_PATH="${real_git}" PATH="${shim_dir}:${PATH}" "${verifier}" --repo-root "${fixture_root}" || die \
    "repo hygiene verifier should fall back when the local Git lacks git fsck --no-references"
progress 'local-state reporting stays available'
report_output="$("${verifier}" --repo-root "${fixture_root}" --report-local-state)"
[[ "${report_output}" == *'SIZE   CATEGORY'* ]] || die \
    "repo hygiene verifier should support local-state reporting"

mkdir "${fixture_root}/2026-01-31"
printf 'finder drift\n' > "${fixture_root}/.DS_Store"

set +e
failure_output="$("${verifier}" --repo-root "${fixture_root}" 2>&1)"
failure_status=$?
set -e
[[ ${failure_status} -ne 0 ]] || die "repo hygiene verifier should fail for unexpected root entries"
[[ "${failure_output}" == *'2026-01-31'* ]] || die \
    "repo hygiene verifier did not report the unexpected root directory"
[[ "${failure_output}" == *'.DS_Store'* ]] || die \
    "repo hygiene verifier did not report the root .DS_Store file"

progress 'unexpected root entries are cleaned and reverified'
"${cleaner}" --repo-root "${fixture_root}" >/dev/null || die \
    "repo hygiene cleaner should remove empty unexpected entries and .DS_Store files"
"${verifier}" --repo-root "${fixture_root}" || die \
    "repo hygiene verifier should pass after cleanup"

printf '' > "${fixture_root}/.git/index.lock"

set +e
lock_output="$("${verifier}" --repo-root "${fixture_root}" 2>&1)"
lock_status=$?
set -e
[[ ${lock_status} -ne 0 ]] || die \
    "repo hygiene verifier should fail when Git coordination lock files are present"
[[ "${lock_output}" == *'git coordination lock files are present'* ]] || die \
    "repo hygiene verifier did not report Git coordination lock files"
[[ "${lock_output}" == *'.git/index.lock'* ]] || die \
    "repo hygiene verifier did not report the Git index lock path"
rm -f "${fixture_root}/.git/index.lock"

progress 'lock-file cleanup restores verifier success'
"${verifier}" --repo-root "${fixture_root}" || die \
    "repo hygiene verifier should pass after removing the Git coordination lock file"

printf 'warning: unreachable loose objects remain\n' > "${fixture_root}/.git/gc.log"

set +e
gc_log_output="$("${verifier}" --repo-root "${fixture_root}" 2>&1)"
gc_log_status=$?
set -e
[[ ${gc_log_status} -ne 0 ]] || die \
    "repo hygiene verifier should fail when Git housekeeping is suspended by gc.log"
[[ "${gc_log_output}" == *'git housekeeping is suspended by a persisted gc.log'* ]] || die \
    "repo hygiene verifier did not report the persisted gc.log failure"
[[ "${gc_log_output}" == *'.git/gc.log'* ]] || die \
    "repo hygiene verifier did not report the gc.log path"
rm -f "${fixture_root}/.git/gc.log"

progress 'gc.log cleanup restores verifier success'
"${verifier}" --repo-root "${fixture_root}" || die \
    "repo hygiene verifier should pass after removing the persisted gc.log"

mkdir -p "${fixture_root}/.git/refs/codex/turn-diffs/captures"
chmod 000 "${fixture_root}/.git/refs/codex/turn-diffs/captures"
set +e
private_ref_output="$("${verifier}" --repo-root "${fixture_root}" 2>&1)"
private_ref_status=$?
set -e
[[ ${private_ref_status} -eq 0 ]] || die \
    "repo hygiene verifier should ignore protected Git-private ref namespaces"
chmod 700 "${fixture_root}/.git/refs/codex/turn-diffs/captures"
rm -rf "${fixture_root}/.git/refs/codex"

printf 'not-an-oid\n' > "${fixture_root}/.git/refs/heads/broken"
set +e
broken_ref_output="$("${verifier}" --repo-root "${fixture_root}" 2>&1)"
broken_ref_status=$?
set -e
[[ ${broken_ref_status} -ne 0 ]] || die \
    "repo hygiene verifier should fail when a repo-owned ref is broken"
[[ "${broken_ref_output}" == *'repo-owned Git reference verification failed'* ]] || die \
    "repo hygiene verifier did not report repo-owned ref verification failures"
rm -f "${fixture_root}/.git/refs/heads/broken"

mkdir -p \
    "${fixture_root}/build/report" \
    "${fixture_root}/cli/build/cache" \
    "${fixture_root}/.claude/worktrees/session-a" \
    "${fixture_root}/.gradle/caches" \
    "${fixture_root}/.ruff_cache/0" \
    "${fixture_root}/.local/tooling/sqlite" \
    "${fixture_root}/.vscode/tasks" \
    "${fixture_root}/tmp/transient"
printf 'generated\n' > "${fixture_root}/build/report/summary.txt"
printf 'generated\n' > "${fixture_root}/cli/build/cache/item.txt"
printf 'generated\n' > "${fixture_root}/.claude/worktrees/session-a/state.txt"
printf 'generated\n' > "${fixture_root}/.gradle/caches/state.txt"
printf 'generated\n' > "${fixture_root}/.ruff_cache/0/index"
printf 'generated\n' > "${fixture_root}/.local/tooling/sqlite/archive.txt"
printf 'generated\n' > "${fixture_root}/.vscode/tasks/tasks.json"
printf 'generated\n' > "${fixture_root}/tmp/transient/data.txt"

progress 'generated-state purge removes repo-owned build artifacts'
"${cleaner}" --repo-root "${fixture_root}" --purge-generated-state >/dev/null || die \
    "repo hygiene cleaner should purge repo-owned generated state"
[[ ! -e "${fixture_root}/build" ]] || die "generated root build state was not removed"
[[ ! -e "${fixture_root}/cli/build" ]] || die "generated module build state was not removed"
[[ ! -e "${fixture_root}/.gradle" ]] || die "generated Gradle state was not removed"
[[ ! -e "${fixture_root}/.ruff_cache" ]] || die "generated Ruff cache was not removed"
[[ ! -e "${fixture_root}/.local/tooling" ]] || die "generated local tooling state was not removed"
[[ -d "${fixture_root}/.claude" ]] || die "tool state should remain without --purge-tool-state"
[[ -d "${fixture_root}/.local" ]] || die "local tool root should remain without --purge-tool-state"
[[ -d "${fixture_root}/.vscode" ]] || die "editor state should remain without --purge-tool-state"
[[ -d "${fixture_root}/tmp" ]] || die "tmp root should remain without --purge-tmp"

progress 'tool-state purge remains opt-in and explicit'
"${cleaner}" --repo-root "${fixture_root}" --purge-tool-state >/dev/null || die \
    "repo hygiene cleaner should support explicit tool-state cleanup"
[[ ! -e "${fixture_root}/.claude" ]] || die ".claude tool state was not removed"
[[ ! -e "${fixture_root}/.local" ]] || die ".local tool state was not removed"
[[ ! -e "${fixture_root}/.vscode" ]] || die ".vscode tool state was not removed"

progress 'tmp purge remains opt-in and explicit'
"${cleaner}" --repo-root "${fixture_root}" --purge-tmp >/dev/null || die \
    "repo hygiene cleaner should support explicit tmp cleanup"
[[ ! -e "${fixture_root}/tmp" ]] || die "tmp root was not removed by --purge-tmp"

mkdir -p "${fixture_root}/.git/objects/aa"
printf 'corrupt loose object\n' > \
    "${fixture_root}/.git/objects/aa/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

set +e
corrupt_output="$("${verifier}" --repo-root "${fixture_root}" 2>&1)"
corrupt_status=$?
set -e
[[ ${corrupt_status} -ne 0 ]] || die \
    "repo hygiene verifier should fail when the git object store is corrupt"
[[ "${corrupt_output}" == *'git object-store verification failed'* ]] || die \
    "repo hygiene verifier did not report git object-store corruption"
rm -f "${fixture_root}/.git/objects/aa/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

printf 'repo hygiene verifier regression: success\n'
