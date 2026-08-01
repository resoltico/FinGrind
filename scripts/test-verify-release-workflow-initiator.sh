#!/usr/bin/env bash
# Exercise owner-only release workflow initiation and main-only manual dispatch admission without GitHub Actions.

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

run_expect_success() {
    FINGRIND_RELEASE_EVENT_NAME="$1" \
        FINGRIND_RELEASE_REF="$2" \
        FINGRIND_RELEASE_ACTOR_ID=17160191 \
        FINGRIND_RELEASE_REPOSITORY_OWNER_ID=17160191 \
        FINGRIND_RELEASE_TRIGGERING_ACTOR=resoltico \
        FINGRIND_RELEASE_REPOSITORY_OWNER=resoltico \
        "${verifier}" >/dev/null
}

run_expect_failure() {
    local expected_message=$1
    shift
    local output
    if output="$("$@" 2>&1)"; then
        die "workflow initiator verifier unexpectedly succeeded"
    fi
    grep -Fq "${expected_message}" <<<"${output}" || die \
        "workflow initiator failure did not mention '${expected_message}'"
}

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly verifier="${repo_root}/scripts/verify-release-workflow-initiator.sh"

[[ -x "${verifier}" ]] || die "missing executable workflow initiator verifier at ${verifier}"

run_expect_success push refs/tags/v0.62.0
run_expect_success workflow_dispatch refs/heads/main
run_expect_failure \
    "tag-push release workflow must use one GitHub tag ref" \
    env \
    FINGRIND_RELEASE_EVENT_NAME=push \
    FINGRIND_RELEASE_REF=refs/heads/main \
    FINGRIND_RELEASE_ACTOR_ID=17160191 \
    FINGRIND_RELEASE_REPOSITORY_OWNER_ID=17160191 \
    FINGRIND_RELEASE_TRIGGERING_ACTOR=resoltico \
    FINGRIND_RELEASE_REPOSITORY_OWNER=resoltico \
    "${verifier}"
run_expect_failure \
    "manual release workflow dispatch must use GitHub ref refs/heads/main" \
    env -u FINGRIND_RELEASE_REF \
    FINGRIND_RELEASE_EVENT_NAME=workflow_dispatch \
    FINGRIND_RELEASE_ACTOR_ID=17160191 \
    FINGRIND_RELEASE_REPOSITORY_OWNER_ID=17160191 \
    FINGRIND_RELEASE_TRIGGERING_ACTOR=resoltico \
    FINGRIND_RELEASE_REPOSITORY_OWNER=resoltico \
    "${verifier}"
run_expect_failure \
    "manual release workflow dispatch must use GitHub ref refs/heads/main" \
    env \
    FINGRIND_RELEASE_EVENT_NAME=workflow_dispatch \
    FINGRIND_RELEASE_REF=refs/heads/release/0.62.0 \
    FINGRIND_RELEASE_ACTOR_ID=17160191 \
    FINGRIND_RELEASE_REPOSITORY_OWNER_ID=17160191 \
    FINGRIND_RELEASE_TRIGGERING_ACTOR=resoltico \
    FINGRIND_RELEASE_REPOSITORY_OWNER=resoltico \
    "${verifier}"
run_expect_failure \
    "only the repository owner may initiate a release publication" \
    env \
    FINGRIND_RELEASE_EVENT_NAME=workflow_dispatch \
    FINGRIND_RELEASE_REF=refs/heads/main \
    FINGRIND_RELEASE_ACTOR_ID=42 \
    FINGRIND_RELEASE_REPOSITORY_OWNER_ID=17160191 \
    FINGRIND_RELEASE_TRIGGERING_ACTOR=resoltico \
    FINGRIND_RELEASE_REPOSITORY_OWNER=resoltico \
    "${verifier}"
run_expect_failure \
    "only the repository owner may trigger or rerun a release publication" \
    env \
    FINGRIND_RELEASE_EVENT_NAME=push \
    FINGRIND_RELEASE_REF=refs/tags/v0.62.0 \
    FINGRIND_RELEASE_ACTOR_ID=17160191 \
    FINGRIND_RELEASE_REPOSITORY_OWNER_ID=17160191 \
    FINGRIND_RELEASE_TRIGGERING_ACTOR=collaborator \
    FINGRIND_RELEASE_REPOSITORY_OWNER=resoltico \
    "${verifier}"
run_expect_failure \
    "release workflow event must be push or workflow_dispatch" \
    env \
    FINGRIND_RELEASE_EVENT_NAME=pull_request \
    FINGRIND_RELEASE_ACTOR_ID=17160191 \
    FINGRIND_RELEASE_REPOSITORY_OWNER_ID=17160191 \
    FINGRIND_RELEASE_TRIGGERING_ACTOR=resoltico \
    FINGRIND_RELEASE_REPOSITORY_OWNER=resoltico \
    "${verifier}"

printf 'verify-release-workflow-initiator regression: success\n'
