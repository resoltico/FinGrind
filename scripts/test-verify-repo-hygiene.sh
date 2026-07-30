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

script_dir="$(resolve_script_dir)"
readonly script_dir
repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly repo_root
readonly verifier="${repo_root}/scripts/verify-repo-hygiene.sh"
readonly cleaner="${repo_root}/scripts/clean-repo-hygiene.sh"

[[ -x "${verifier}" ]] || die "missing executable repo hygiene verifier"
[[ -x "${cleaner}" ]] || die "missing executable repo hygiene cleaner"

readonly fixture_parent="${TMPDIR:-/tmp}"
fixture_root="$(mktemp -d "${fixture_parent%/}/fingrind-repo-hygiene-fixture.XXXXXX")"
fixture_git_dir="$(mktemp -d "${fixture_parent%/}/fingrind-repo-hygiene-gitdir.XXXXXX")"
fixture_root="$(cd -P -- "${fixture_root}" && pwd)"
fixture_git_dir="$(cd -P -- "${fixture_git_dir}" && pwd)"
cleanup() {
    local cleanup_path
    local cleanup_failed=false

    for cleanup_path in "${fixture_root}" "${fixture_git_dir}"; do
        [[ -e "${cleanup_path}" ]] || continue
        if command -v chflags >/dev/null 2>&1; then
            chflags -R nohidden,nouchg,noschg "${cleanup_path}" 2>/dev/null || true
        fi
        chmod -RN "${cleanup_path}" 2>/dev/null || true
        chmod -R u+rwX "${cleanup_path}" 2>/dev/null || true
        if ! rm -rf "${cleanup_path}" 2>/dev/null || [[ -e "${cleanup_path}" ]]; then
            printf 'error: unable to remove repository hygiene fixture: %s\n' "${cleanup_path}" >&2
            cleanup_failed=true
        fi
    done

    [[ "${cleanup_failed}" == false ]]
}
trap 'cleanup || exit 1' EXIT
rmdir "${fixture_git_dir}" || die "unable to reserve separate fixture Git metadata path"

mkdir -p \
    "${fixture_root}/tmp/scratch" \
    "${fixture_root}/architecture" \
    "${fixture_root}/cli" \
    "${fixture_root}/scripts"
git init -q -b main --separate-git-dir="${fixture_git_dir}" "${fixture_root}"
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
.gradle-invocation-leases/
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
real_git="$(command -v git)"
readonly real_git
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

progress 'clean fixture root still uses --no-references when help text omits it'
shim_supports_no_references_dir="${fixture_root}/tmp/git-supports-no-references-shim"
mkdir -p "${shim_supports_no_references_dir}"
cat > "${shim_supports_no_references_dir}/git" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

readonly real_git="${REAL_GIT_PATH:?}"

run_fsck() {
    local -a forwarded_args=()
    local has_no_references=false
    local argument
    for argument in "$@"; do
        if [[ "${argument}" == '--no-references' ]]; then
            has_no_references=true
            continue
        fi
        forwarded_args+=("${argument}")
    done

    if [[ "${has_no_references}" != true ]]; then
        printf '%s\n' 'error: missing required --no-references flag' >&2
        exit 99
    fi

    exec "${real_git}" fsck "${forwarded_args[@]}"
}

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
        cd -- "${repo_path}"
        run_fsck "$@"
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
    run_fsck "$@"
fi

exec "${real_git}" "$@"
EOF
chmod +x "${shim_supports_no_references_dir}/git"
REAL_GIT_PATH="${real_git}" PATH="${shim_supports_no_references_dir}:${PATH}" "${verifier}" --repo-root "${fixture_root}" || die \
    "repo hygiene verifier should prefer the executable --no-references probe over help text"

progress 'local-state reporting stays available'
report_started_at=$SECONDS
report_output="$("${verifier}" --repo-root "${fixture_root}" --report-local-state)"
report_elapsed_seconds=$((SECONDS - report_started_at))
[[ "${report_output}" == *'SIZE   CATEGORY'* ]] || die \
    "repo hygiene verifier should support local-state reporting"
(( report_elapsed_seconds < 10 )) || die \
    "repo hygiene verifier local-state reporting should return promptly for a healthy fixture"

mkdir "${fixture_root}/2026-01-31"
printf 'finder drift\n' > "${fixture_root}/.DS_Store"
printf 'Git metadata must stay untouched\n' > "${fixture_git_dir}/.DS_Store"

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
[[ -f "${fixture_git_dir}/.DS_Store" ]] || die \
    "repo hygiene cleaner should never traverse or alter Git metadata"
rm -f "${fixture_git_dir}/.DS_Store"

printf '' > "${fixture_git_dir}/index.lock"

set +e
lock_output="$("${verifier}" --repo-root "${fixture_root}" 2>&1)"
lock_status=$?
set -e
[[ ${lock_status} -ne 0 ]] || die \
    "repo hygiene verifier should fail when Git coordination lock files are present"
[[ "${lock_output}" == *'git coordination lock files are present'* ]] || die \
    "repo hygiene verifier did not report Git coordination lock files"
[[ "${lock_output}" == *"${fixture_git_dir}/index.lock"* ]] || die \
    "repo hygiene verifier did not report the Git index lock path"
rm -f "${fixture_git_dir}/index.lock"

progress 'lock-file cleanup restores verifier success'
"${verifier}" --repo-root "${fixture_root}" || die \
    "repo hygiene verifier should pass after removing the Git coordination lock file"

mkdir -p \
    "${fixture_git_dir}/refs/heads" \
    "${fixture_git_dir}/logs/refs/heads" \
    "${fixture_git_dir}/reftable"
touch \
    "${fixture_git_dir}/refs/heads/main.lock" \
    "${fixture_git_dir}/refs/stash.lock" \
    "${fixture_git_dir}/logs/refs/heads/main.lock" \
    "${fixture_git_dir}/logs/refs/stash.lock" \
    "${fixture_git_dir}/reftable/tables.list.lock"

set +e
bounded_lock_output="$("${verifier}" --repo-root "${fixture_root}" 2>&1)"
bounded_lock_status=$?
set -e
[[ ${bounded_lock_status} -ne 0 ]] || die \
    "repo hygiene verifier should fail when nested Git coordination lock files are present"
for expected_lock_path in \
    "${fixture_git_dir}/refs/heads/main.lock" \
    "${fixture_git_dir}/refs/stash.lock" \
    "${fixture_git_dir}/logs/refs/heads/main.lock" \
    "${fixture_git_dir}/logs/refs/stash.lock" \
    "${fixture_git_dir}/reftable/tables.list.lock"; do
    [[ "${bounded_lock_output}" == *"${expected_lock_path}"* ]] || die \
        "repo hygiene verifier did not report the nested Git coordination lock path ${expected_lock_path}"
done
rm -f \
    "${fixture_git_dir}/refs/heads/main.lock" \
    "${fixture_git_dir}/refs/stash.lock" \
    "${fixture_git_dir}/logs/refs/heads/main.lock" \
    "${fixture_git_dir}/logs/refs/stash.lock" \
    "${fixture_git_dir}/reftable/tables.list.lock"

progress 'nested lock cleanup restores verifier success'
"${verifier}" --repo-root "${fixture_root}" || die \
    "repo hygiene verifier should pass after removing nested Git coordination lock files"

mkdir -p "${fixture_git_dir}/refs/codex/turn-diffs/captures"
touch "${fixture_git_dir}/refs/codex/turn-diffs/captures/private.lock"

progress 'Git-private ref namespaces do not participate in repository lock verification'
"${verifier}" --repo-root "${fixture_root}" || die \
    "repo hygiene verifier should ignore Git-private tool ref locks"
rm -rf "${fixture_git_dir}/refs/codex"

printf 'warning: unreachable loose objects remain\n' > "${fixture_git_dir}/gc.log"

set +e
gc_log_output="$("${verifier}" --repo-root "${fixture_root}" 2>&1)"
gc_log_status=$?
set -e
[[ ${gc_log_status} -ne 0 ]] || die \
    "repo hygiene verifier should fail when Git housekeeping is suspended by gc.log"
[[ "${gc_log_output}" == *'git housekeeping is suspended by a persisted gc.log'* ]] || die \
    "repo hygiene verifier did not report the persisted gc.log failure"
[[ "${gc_log_output}" == *"${fixture_git_dir}/gc.log"* ]] || die \
    "repo hygiene verifier did not report the gc.log path"
rm -f "${fixture_git_dir}/gc.log"

progress 'gc.log cleanup restores verifier success'
"${verifier}" --repo-root "${fixture_root}" || die \
    "repo hygiene verifier should pass after removing the persisted gc.log"

printf 'not-an-oid\n' > "${fixture_git_dir}/refs/heads/broken"
set +e
broken_ref_output="$("${verifier}" --repo-root "${fixture_root}" 2>&1)"
broken_ref_status=$?
set -e
[[ ${broken_ref_status} -ne 0 ]] || die \
    "repo hygiene verifier should fail when a repo-owned ref is broken"
[[ "${broken_ref_output}" == *'repo-owned Git reference verification failed'* ]] || die \
    "repo hygiene verifier did not report repo-owned ref verification failures"
rm -f "${fixture_git_dir}/refs/heads/broken"

mkdir -p \
    "${fixture_root}/build/report" \
    "${fixture_root}/cli/build/cache" \
    "${fixture_root}/.claude/worktrees/session-a" \
    "${fixture_root}/.gradle/caches" \
    "${fixture_root}/.gradle-invocation-leases" \
    "${fixture_root}/.ruff_cache/0" \
    "${fixture_root}/.local/tooling/sqlite" \
    "${fixture_root}/.vscode/tasks" \
    "${fixture_root}/tmp/transient"
printf 'generated\n' > "${fixture_root}/build/report/summary.txt"
printf 'generated\n' > "${fixture_root}/cli/build/cache/item.txt"
printf 'generated\n' > "${fixture_root}/.claude/worktrees/session-a/state.txt"
printf 'generated\n' > "${fixture_root}/.gradle/caches/state.txt"
printf 'generated\n' > "${fixture_root}/.gradle-invocation-leases/build.lease"
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
[[ ! -e "${fixture_root}/.gradle-invocation-leases" ]] || die "generated Gradle invocation leases were not removed"
[[ ! -e "${fixture_root}/.ruff_cache" ]] || die "generated Ruff cache was not removed"
[[ ! -e "${fixture_root}/.local/tooling" ]] || die "generated local tooling state was not removed"
[[ -d "${fixture_root}/.claude" ]] || die "tool state should remain without --purge-tool-state"
[[ -d "${fixture_root}/.local" ]] || die "local tool root should remain without --purge-tool-state"
[[ -d "${fixture_root}/.vscode" ]] || die "editor state should remain without --purge-tool-state"
[[ -d "${fixture_root}/tmp" ]] || die "tmp root should remain without --purge-tmp"

progress 'requested purge failure is reported as a failed cleanup'
mkdir -p "${fixture_root}/build/blocked"
real_rm="$(command -v rm)"
readonly real_rm
readonly failing_rm_dir="${fixture_root}/tmp/failing-rm"
mkdir -p "${failing_rm_dir}"
cat > "${failing_rm_dir}/rm" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

readonly real_rm="${REAL_RM_PATH:?}"
readonly failing_target="${FAILING_RM_TARGET:?}"

for argument in "$@"; do
    if [[ "${argument}" == "${failing_target}" ]]; then
        printf 'injected remove failure for %s\n' "${failing_target}" >&2
        exit 1
    fi
done

exec "${real_rm}" "$@"
EOF
chmod +x "${failing_rm_dir}/rm"

set +e
cleanup_failure_output="$(
        REAL_RM_PATH="${real_rm}" \
        FAILING_RM_TARGET="${fixture_root}/build" \
        PATH="${failing_rm_dir}:${PATH}" \
        "${cleaner}" --repo-root "${fixture_root}" --purge-generated-state 2>&1
)"
cleanup_failure_status=$?
set -e
[[ ${cleanup_failure_status} -ne 0 ]] || die \
    "repo hygiene cleaner should fail when requested generated-state cleanup is incomplete"
[[ "${cleanup_failure_output}" == *'requested repository-local-state cleanup was incomplete'* ]] || die \
    "repo hygiene cleaner did not report incomplete requested cleanup"
rm -rf "${fixture_root}/build"

progress 'Finder-artifact cleanup failure is reported as a failed cleanup'
printf 'finder drift\n' > "${fixture_root}/.DS_Store"
set +e
finder_cleanup_failure_output="$(
    REAL_RM_PATH="${real_rm}" \
        FAILING_RM_TARGET="${fixture_root}/.DS_Store" \
        PATH="${failing_rm_dir}:${PATH}" \
        "${cleaner}" --repo-root "${fixture_root}" 2>&1
)"
finder_cleanup_failure_status=$?
set -e
[[ ${finder_cleanup_failure_status} -ne 0 ]] || die \
    "repo hygiene cleaner should fail when Finder-artifact cleanup is incomplete"
[[ "${finder_cleanup_failure_output}" == *'requested repository-local-state cleanup was incomplete'* ]] || die \
    "repo hygiene cleaner did not report incomplete Finder-artifact cleanup"
rm -f "${fixture_root}/.DS_Store"

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

mkdir -p "${fixture_git_dir}/objects/aa"
printf 'corrupt loose object\n' > \
    "${fixture_git_dir}/objects/aa/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

set +e
corrupt_output="$("${verifier}" --repo-root "${fixture_root}" 2>&1)"
corrupt_status=$?
set -e
[[ ${corrupt_status} -ne 0 ]] || die \
    "repo hygiene verifier should fail when the git object store is corrupt"
[[ "${corrupt_output}" == *'git object-store verification failed'* ]] || die \
    "repo hygiene verifier did not report git object-store corruption"
rm -f "${fixture_git_dir}/objects/aa/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

printf 'repo hygiene verifier regression: success\n'
