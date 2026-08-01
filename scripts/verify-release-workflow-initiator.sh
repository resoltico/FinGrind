#!/usr/bin/env bash
# Fail closed unless the repository owner initiates the release and manual dispatches use main.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

readonly release_event_name="${FINGRIND_RELEASE_EVENT_NAME:-}"
readonly release_ref="${FINGRIND_RELEASE_REF:-}"
readonly release_actor_id="${FINGRIND_RELEASE_ACTOR_ID:-}"
readonly release_repository_owner_id="${FINGRIND_RELEASE_REPOSITORY_OWNER_ID:-}"
readonly release_triggering_actor="${FINGRIND_RELEASE_TRIGGERING_ACTOR:-}"
readonly release_repository_owner="${FINGRIND_RELEASE_REPOSITORY_OWNER:-}"

case "${release_event_name}" in
    push)
        [[ "${release_ref}" == refs/tags/* ]] || die \
            "tag-push release workflow must use one GitHub tag ref"
        ;;
    workflow_dispatch)
        [[ "${release_ref}" == "refs/heads/main" ]] || die \
            "manual release workflow dispatch must use GitHub ref refs/heads/main"
        ;;
    *)
        die "release workflow event must be push or workflow_dispatch"
        ;;
esac

[[ "${release_actor_id}" =~ ^[1-9][0-9]*$ ]] || die \
    "release workflow actor ID must be one positive GitHub user ID"
[[ "${release_repository_owner_id}" =~ ^[1-9][0-9]*$ ]] || die \
    "release workflow repository-owner ID must be one positive GitHub user ID"
[[ -n "${release_triggering_actor}" ]] || die \
    "release workflow triggering actor must be present"
[[ -n "${release_repository_owner}" ]] || die \
    "release workflow repository owner must be present"

[[ "${release_actor_id}" == "${release_repository_owner_id}" ]] || die \
    "only the repository owner may initiate a release publication"
[[ "${release_triggering_actor}" == "${release_repository_owner}" ]] || die \
    "only the repository owner may trigger or rerun a release publication"

printf '%s\n' "Verified release workflow initiator: repository owner"
