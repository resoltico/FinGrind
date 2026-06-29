#!/usr/bin/env bash
# Shared root-boundary and local-state definitions for repository hygiene tooling.

readonly repo_hygiene_structural_root_entries=(
    .codex
    .devcontainer
    .dockerignore
    .git
    .gitattributes
    .github
    .gitignore
    AGENTS.md
    CHANGELOG.md
    Dockerfile
    LICENSE
    LICENSE-APACHE-2.0
    LICENSE-SIL-OFL-1.1
    LICENSE-SQLITE3MULTIPLECIPHERS
    NOTICE
    PATENTS.md
    README.md
    SECURITY.md
    build.gradle.kts
    check.sh
    cli
    contract
    core
    docs
    executor
    gradle
    gradle.properties
    gradlew
    gradlew.bat
    jazzer
    report-pdf
    requirements-python-tools.txt
    ruff.toml
    scripts
    settings.gradle.kts
    sqlite
    third_party
)

readonly repo_hygiene_local_state_root_entries=(
    .claude
    .gradle
    .local
    .ruff_cache
    .vscode
    build
    tmp
)

readonly repo_hygiene_generated_state_root_entries=(
    .gradle
    .ruff_cache
    build
)

readonly repo_hygiene_tool_state_root_entries=(
    .claude
    .local
    .vscode
)

readonly repo_hygiene_scratch_root_entries=(
    tmp
)

repo_hygiene_contains() {
    local candidate=$1
    shift
    local value
    for value in "$@"; do
        if [[ "${value}" == "${candidate}" ]]; then
            return 0
        fi
    done
    return 1
}

repo_hygiene_is_structural_root_entry() {
    repo_hygiene_contains "$1" "${repo_hygiene_structural_root_entries[@]}"
}

repo_hygiene_is_local_state_root_entry() {
    repo_hygiene_contains "$1" "${repo_hygiene_local_state_root_entries[@]}"
}

repo_hygiene_is_generated_state_root_entry() {
    repo_hygiene_contains "$1" "${repo_hygiene_generated_state_root_entries[@]}"
}

repo_hygiene_is_tool_state_root_entry() {
    repo_hygiene_contains "$1" "${repo_hygiene_tool_state_root_entries[@]}"
}

repo_hygiene_is_scratch_root_entry() {
    repo_hygiene_contains "$1" "${repo_hygiene_scratch_root_entries[@]}"
}

repo_hygiene_is_expected_root_entry() {
    local entry_name=$1
    repo_hygiene_is_structural_root_entry "${entry_name}" ||
        repo_hygiene_is_local_state_root_entry "${entry_name}"
}

repo_hygiene_local_state_category() {
    local entry_name=$1
    if repo_hygiene_is_generated_state_root_entry "${entry_name}"; then
        printf '%s\n' 'generated-state'
        return 0
    fi
    if repo_hygiene_is_tool_state_root_entry "${entry_name}"; then
        printf '%s\n' 'tool-state'
        return 0
    fi
    if repo_hygiene_is_scratch_root_entry "${entry_name}"; then
        printf '%s\n' 'scratch-state'
        return 0
    fi
    printf '%s\n' 'local-state'
}

repo_hygiene_local_state_cleanup_flag() {
    local entry_name=$1
    if repo_hygiene_is_generated_state_root_entry "${entry_name}"; then
        printf '%s\n' '--purge-generated-state'
        return 0
    fi
    if repo_hygiene_is_tool_state_root_entry "${entry_name}"; then
        printf '%s\n' '--purge-tool-state'
        return 0
    fi
    if repo_hygiene_is_scratch_root_entry "${entry_name}"; then
        printf '%s\n' '--purge-tmp'
        return 0
    fi
    printf '%s\n' '(none)'
}

repo_hygiene_list_root_entries() {
    local repo_root=$1
    local path
    local -a entries=()

    shopt -s nullglob
    entries=("${repo_root}"/* "${repo_root}"/.[!.]* "${repo_root}"/..?*)
    shopt -u nullglob

    for path in "${entries[@]}"; do
        printf '%s\n' "${path##*/}"
    done | LC_ALL=C sort -u
}

repo_hygiene_print_local_state_report() {
    local repo_root=$1
    local local_state_root
    local size
    local category
    local cleanup_flag

    for local_state_root in "${repo_hygiene_local_state_root_entries[@]}"; do
        [[ -e "${repo_root}/${local_state_root}" ]] || continue
        size="$(du -sh "${repo_root}/${local_state_root}" | awk '{print $1}')"
        category="$(repo_hygiene_local_state_category "${local_state_root}")"
        cleanup_flag="$(repo_hygiene_local_state_cleanup_flag "${local_state_root}")"
        printf '%s\t%s\t%s\t%s\n' \
            "${size}" \
            "${category}" \
            "${cleanup_flag}" \
            "${repo_root}/${local_state_root}"
    done |
        sort -h -k1,1 |
        while IFS=$'\t' read -r size category cleanup_flag path; do
            printf '  %-6s %-16s %-23s %s\n' "${size}" "${category}" "${cleanup_flag}" "${path}"
        done
}

repo_hygiene_verify_repo_owned_refs() {
    local repo_root=$1
    local error_file
    local head_ref=''
    error_file="$(mktemp "${TMPDIR:-/tmp}/fingrind-git-refs.XXXXXX")"
    head_ref="$(git -C "${repo_root}" symbolic-ref -q HEAD 2>/dev/null || true)"

    if ! git -C "${repo_root}" rev-parse --verify -q HEAD >/dev/null 2>"${error_file}"; then
        if [[ -n "${head_ref}" ]]; then
            if ! git -C "${repo_root}" show-ref --verify --quiet "${head_ref}" 2>>"${error_file}"; then
                repo_owned_ref_output="$(cat "${error_file}")"
                rm -f "${error_file}"
                if [[ -z "${repo_owned_ref_output}" ]]; then
                    repo_owned_ref_output="HEAD points at missing repo-owned ref ${head_ref}."
                fi
                return 1
            fi
        else
            repo_owned_ref_output="$(cat "${error_file}")"
            rm -f "${error_file}"
            return 1
        fi
    fi

    : >"${error_file}"
    git -C "${repo_root}" for-each-ref --format='%(refname) %(objectname)' \
        refs/heads \
        refs/tags \
        refs/remotes \
        refs/notes >/dev/null 2>"${error_file}"
    local ref_scan_status=$?
    repo_owned_ref_output="$(cat "${error_file}")"
    rm -f "${error_file}"
    if (( ref_scan_status != 0 )) || [[ -n "${repo_owned_ref_output}" ]]; then
        return 1
    fi
}

repo_hygiene_process_is_live() {
    local pid=$1
    local process_state
    process_state="$(ps -o stat= -p "${pid}" 2>/dev/null | awk 'NR == 1 {print $1}')"
    [[ -n "${process_state}" && "${process_state}" != Z* ]]
}

repo_hygiene_git_fsck_supports_no_references() {
    local repo_root=$1
    git -C "${repo_root}" fsck -h 2>&1 | grep -Fq -- '--no-references'
}

repo_hygiene_run_git_fsck_with_heartbeat() {
    local repo_root=$1
    local heartbeat_seconds=30
    local elapsed_seconds=0
    local output_file
    local heartbeat_pid
    local -a fsck_command
    output_file="$(mktemp "${TMPDIR:-/tmp}/fingrind-git-fsck.XXXXXX")"
    printf '%s\n' 'repo hygiene verification: checking git object store'
    fsck_command=(git -C "${repo_root}" fsck --full --no-dangling --no-progress)
    if repo_hygiene_git_fsck_supports_no_references "${repo_root}"; then
        fsck_command+=(--no-references)
    fi
    (
        # Git-private tool refs can live under .git/refs without belonging to the repository
        # contract, so verify repo-owned refs explicitly first and use git's ref-elision
        # switch when the local Git build supports it.
        "${fsck_command[@]}"
    ) >"${output_file}" 2>&1 &
    local fsck_pid=$!
    (
        while repo_hygiene_process_is_live "${fsck_pid}"; do
            sleep "${heartbeat_seconds}"
            elapsed_seconds=$((elapsed_seconds + heartbeat_seconds))
            if repo_hygiene_process_is_live "${fsck_pid}"; then
                printf 'repo hygiene verification: git fsck still running (%ss elapsed)\n' "${elapsed_seconds}"
            fi
        done
    ) &
    heartbeat_pid=$!
    wait "${fsck_pid}"
    local fsck_status=$?
    kill "${heartbeat_pid}" 2>/dev/null || true
    wait "${heartbeat_pid}" 2>/dev/null || true
    object_store_output="$(cat "${output_file}")"
    rm -f "${output_file}"
    return "${fsck_status}"
}
