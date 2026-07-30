#!/usr/bin/env bash
# Shared read-only GitHub tag-ruleset inventory support for release-control scripts.

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    printf 'error: %s\n' \
        "release-tag-ruleset-support.sh is a library and must be sourced by another script." >&2
    exit 1
fi

if ! declare -F fingrind_release_github_api_json >/dev/null || \
    ! declare -F fingrind_release_payload_error_message >/dev/null; then
    printf 'error: %s\n' \
        "release-tag-ruleset-support.sh requires release-check-gh-api-support.sh first." >&2
    return 1
fi

fingrind_release_tag_ruleset_details_json() {
    local ruleset_repo_full_name=$1
    [[ -n "${ruleset_repo_full_name}" ]] || {
        printf 'error: tag-ruleset inventory requires one repository name\n' >&2
        return 1
    }

    local inventory_json
    inventory_json="$(
        fingrind_release_github_api_json \
            "tag ruleset inventory for ${ruleset_repo_full_name}" \
            --paginate --slurp \
            "repos/${ruleset_repo_full_name}/rulesets?targets=tag&includes_parents=true&per_page=100"
    )" || return 1

    local inventory_error
    inventory_error="$(fingrind_release_payload_error_message "${inventory_json}")" || return 1
    if [[ "${inventory_error}" != "null" ]]; then
        printf 'error: %s\n' "${inventory_error}" >&2
        return 1
    fi

    local ruleset_ids
    ruleset_ids="$(
        FINGRIND_RELEASE_TAG_RULESET_INVENTORY_JSON="${inventory_json}" \
            python3 - <<'PY'
import json
import os

payload = json.loads(os.environ["FINGRIND_RELEASE_TAG_RULESET_INVENTORY_JSON"])
if not isinstance(payload, list) or not all(isinstance(page, list) for page in payload):
    raise SystemExit("tag-ruleset inventory must be one paginated JSON array of rule arrays")

seen_ids = set()
for page in payload:
    for ruleset in page:
        if not isinstance(ruleset, dict):
            raise SystemExit("tag-ruleset inventory entries must be objects")
        ruleset_id = ruleset.get("id")
        if isinstance(ruleset_id, bool) or not isinstance(ruleset_id, int) or ruleset_id <= 0:
            raise SystemExit("tag-ruleset inventory entries must expose positive numeric IDs")
        if ruleset_id in seen_ids:
            raise SystemExit("tag-ruleset inventory must not repeat one ruleset ID")
        seen_ids.add(ruleset_id)
        print(ruleset_id)
PY
    )" || {
        printf 'error: could not read the tag-ruleset inventory\n' >&2
        return 1
    }

    local -a detail_payloads=()
    if [[ -n "${ruleset_ids}" ]]; then
        local ruleset_id
        while IFS= read -r ruleset_id; do
            [[ "${ruleset_id}" =~ ^[1-9][0-9]*$ ]] || {
                printf 'error: tag-ruleset inventory produced an invalid ruleset ID\n' >&2
                return 1
            }
            local detail_json
            detail_json="$(
                fingrind_release_github_api_json \
                    "tag ruleset ${ruleset_id} for ${ruleset_repo_full_name}" \
                    "repos/${ruleset_repo_full_name}/rulesets/${ruleset_id}"
            )" || return 1
            local detail_error
            detail_error="$(fingrind_release_payload_error_message "${detail_json}")" || return 1
            if [[ "${detail_error}" != "null" ]]; then
                printf 'error: %s\n' "${detail_error}" >&2
                return 1
            fi
            detail_payloads+=("${detail_json}")
        done <<< "${ruleset_ids}"
    fi

    if (( ${#detail_payloads[@]} == 0 )); then
        printf '%s\n' '[]'
    else
        printf '%s\n' "${detail_payloads[@]}" | \
            python3 -c 'import json, sys; print(json.dumps([json.loads(line) for line in sys.stdin if line.strip()]))'
    fi
}
