#!/usr/bin/env bash
# Shared GitHub API and payload-validation helpers for release-blocking workflow verification.

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    printf 'error: %s\n' \
        "release-check-gh-api-support.sh is a library and must be sourced by another script." >&2
    exit 1
fi

fingrind_require_non_negative_integer() {
    local value=$1
    local name=$2
    [[ "${value}" =~ ^[0-9]+$ ]] || die "${name} must be a non-negative integer, got '${value}'"
}

fingrind_require_positive_integer() {
    local value=$1
    local name=$2
    [[ "${value}" =~ ^[1-9][0-9]*$ ]] || die "${name} must be a positive integer, got '${value}'"
}

fingrind_release_github_api_error_payload() {
    local description=$1
    local status=$2
    local message=$3

    FINGRIND_RELEASE_GH_API_ERROR_DESCRIPTION="${description}" \
        FINGRIND_RELEASE_GH_API_ERROR_STATUS="${status}" \
        FINGRIND_RELEASE_GH_API_ERROR_MESSAGE="${message}" \
        python3 - <<'PY'
import json
import os

message = " ".join(os.environ["FINGRIND_RELEASE_GH_API_ERROR_MESSAGE"].split())
if not message:
    message = "GitHub API request failed without diagnostic output"
print(
    json.dumps(
        {
            "_fingrindGhApiError": {
                "description": os.environ["FINGRIND_RELEASE_GH_API_ERROR_DESCRIPTION"],
                "status": int(os.environ["FINGRIND_RELEASE_GH_API_ERROR_STATUS"]),
                "message": message,
            }
        }
    )
)
PY
}

fingrind_release_payload_error_message() {
    local payload_json=$1

    FINGRIND_RELEASE_PAYLOAD_JSON="${payload_json}" \
        python3 - <<'PY'
import json
import os

payload = json.loads(os.environ["FINGRIND_RELEASE_PAYLOAD_JSON"])
error = payload.get("_fingrindGhApiError")
if isinstance(error, dict) and isinstance(error.get("message"), str):
    description = error.get("description")
    if isinstance(description, str) and description:
        print(f"{description}: {error['message']}")
    else:
        print(error["message"])
else:
    print("null")
PY
}

fingrind_release_github_api_json() {
    local description=$1
    shift
    local retry_attempts="${FINGRIND_RELEASE_GH_API_RETRY_ATTEMPTS:-3}"
    local retry_delay_seconds="${FINGRIND_RELEASE_GH_API_RETRY_DELAY_SECONDS:-0}"

    fingrind_require_positive_integer \
        "${retry_attempts}" \
        "FINGRIND_RELEASE_GH_API_RETRY_ATTEMPTS"
    fingrind_require_non_negative_integer \
        "${retry_delay_seconds}" \
        "FINGRIND_RELEASE_GH_API_RETRY_DELAY_SECONDS"

    local attempt=1
    local output=""
    local status=1
    local error_message=""

    while (( attempt <= retry_attempts )); do
        if output="$(gh api "$@" 2>&1)"; then
            if [[ -z "${output}" ]]; then
                status=1
                error_message="GitHub API returned an empty response for ${description}"
            elif FINGRIND_RELEASE_GH_API_JSON="${output}" python3 - <<'PY' >/dev/null 2>&1
import json
import os

json.loads(os.environ["FINGRIND_RELEASE_GH_API_JSON"])
PY
            then
                printf '%s' "${output}"
                return 0
            else
                status=1
                error_message="GitHub API returned invalid JSON for ${description}: ${output}"
            fi
        else
            status=$?
            error_message="GitHub API request failed for ${description}: ${output}"
        fi

        if (( attempt < retry_attempts )) && (( retry_delay_seconds > 0 )); then
            sleep "${retry_delay_seconds}"
        fi
        attempt="$((attempt + 1))"
    done

    fingrind_release_github_api_error_payload \
        "${description}" \
        "${status}" \
        "${error_message}"
}
