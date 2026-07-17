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

repo_hygiene_list_git_coordination_locks() {
    local git_metadata_root=$1
    local metadata_lock_path
    local direct_ref_lock_path
    local coordination_directory
    local -a repo_owned_ref_namespaces=(heads tags remotes notes replace bisect)
    local -a metadata_lock_paths=(
        HEAD.lock
        index.lock
        packed-refs.lock
        config.lock
        config.worktree.lock
        shallow.lock
        FETCH_HEAD.lock
        ORIG_HEAD.lock
    )
    local -a direct_ref_lock_paths=(stash.lock)

    # Git writes coordination locks alongside metadata and repository-owned refs.
    # The caller supplies the active worktree's git_dir, so its own metadata locks
    # are covered directly. Object storage and Git-private tool ref namespaces have
    # separate owners, so never recurse through either from this release-safety check.
    for metadata_lock_path in "${metadata_lock_paths[@]}"; do
        [[ -f "${git_metadata_root}/${metadata_lock_path}" ]] &&
            printf '%s\n' "${git_metadata_root}/${metadata_lock_path}"
    done

    if [[ -d "${git_metadata_root}/refs" ]]; then
        for direct_ref_lock_path in "${direct_ref_lock_paths[@]}"; do
            [[ -f "${git_metadata_root}/refs/${direct_ref_lock_path}" ]] &&
                printf '%s\n' "${git_metadata_root}/refs/${direct_ref_lock_path}"
        done
        for coordination_directory in "${repo_owned_ref_namespaces[@]}"; do
            [[ -d "${git_metadata_root}/refs/${coordination_directory}" ]] || continue
            find "${git_metadata_root}/refs/${coordination_directory}" \
                -type f \
                -name '*.lock' \
                -print
        done
    fi

    if [[ -d "${git_metadata_root}/logs" ]]; then
        [[ -f "${git_metadata_root}/logs/HEAD.lock" ]] &&
            printf '%s\n' "${git_metadata_root}/logs/HEAD.lock"
        for direct_ref_lock_path in "${direct_ref_lock_paths[@]}"; do
            [[ -f "${git_metadata_root}/logs/refs/${direct_ref_lock_path}" ]] &&
                printf '%s\n' "${git_metadata_root}/logs/refs/${direct_ref_lock_path}"
        done
        for coordination_directory in "${repo_owned_ref_namespaces[@]}"; do
            [[ -d "${git_metadata_root}/logs/refs/${coordination_directory}" ]] || continue
            find "${git_metadata_root}/logs/refs/${coordination_directory}" \
                -type f \
                -name '*.lock' \
                -print
        done
    fi

    if [[ -d "${git_metadata_root}/reftable" ]]; then
        find "${git_metadata_root}/reftable" -type f -name '*.lock' -print
    fi
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
    local probe_output
    probe_output="$(
        git -C "${repo_root}" fsck \
            --no-references \
            --no-dangling \
            --no-progress \
            --connectivity-only \
            'HEAD^{commit}' 2>&1 >/dev/null
    )" && return 0

    case "${probe_output}" in
        *"unknown option"*no-references*|*"usage: git fsck"*)
            return 1
            ;;
    esac

    # Any other failure came from the repository state rather than flag parsing,
    # so keep the optimized flag enabled and let the full verifier surface the
    # real defect.
    return 0
}

repo_hygiene_run_git_fsck_with_heartbeat() {
    local repo_root=$1
    local heartbeat_seconds=30
    local elapsed_seconds=0
    local output_file
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
    while repo_hygiene_process_is_live "${fsck_pid}"; do
        sleep 1
        elapsed_seconds=$((elapsed_seconds + 1))
        if (( elapsed_seconds % heartbeat_seconds == 0 )) &&
            repo_hygiene_process_is_live "${fsck_pid}"; then
            printf 'repo hygiene verification: git fsck still running (%ss elapsed)\n' "${elapsed_seconds}"
        fi
    done
    wait "${fsck_pid}"
    local fsck_status=$?
    # shellcheck disable=SC2034 # Sourced by verify-repo-hygiene.sh for failure output.
    object_store_output="$(cat "${output_file}")"
    rm -f "${output_file}"
    return "${fsck_status}"
}
