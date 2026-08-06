#!/usr/bin/env bash
# Exercise safe empty, partial, complete, and drifted release-tag ruleset configuration states.

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

write_state() {
    local kind=$1
    FINGRIND_TEST_RULESET_STATE="${state_file}" \
        FINGRIND_TEST_RULESET_STATE_KIND="${kind}" \
        python3 - <<'PY'
import json
import os

creation = {
    "id": 101,
    "name": "Authorize FinGrind release tag creation",
    "source_type": "Repository",
    "target": "tag",
    "enforcement": "active",
    "conditions": {"ref_name": {"include": ["refs/tags/v*"], "exclude": []}},
    "rules": [{"type": "creation"}],
    "bypass_actors": [
        {"actor_id": 17160191, "actor_type": "User", "bypass_mode": "always"}
    ],
}
immutability = {
    "id": 102,
    "name": "Protect FinGrind release tag immutability",
    "source_type": "Repository",
    "target": "tag",
    "enforcement": "active",
    "conditions": {"ref_name": {"include": ["refs/tags/v*"], "exclude": []}},
    "rules": [
        {"type": "update"},
        {"type": "deletion"},
    ],
    "bypass_actors": [],
}

kind = os.environ["FINGRIND_TEST_RULESET_STATE_KIND"]
if kind == "empty":
    rulesets = []
elif kind == "creation-only":
    rulesets = [creation]
elif kind == "immutability-only":
    rulesets = [immutability]
elif kind == "complete":
    rulesets = [creation, immutability]
elif kind == "drifted":
    creation["bypass_actors"].append(
        {"actor_id": 42, "actor_type": "User", "bypass_mode": "always"}
    )
    rulesets = [creation]
elif kind == "extra":
    rulesets = [
        creation,
        {
            "id": 103,
            "name": "Unexpected tag policy",
            "source_type": "Repository",
            "target": "tag",
            "enforcement": "active",
            "conditions": {"ref_name": {"include": ["refs/tags/v*"], "exclude": []}},
            "rules": [{"type": "creation"}],
            "bypass_actors": [],
        },
    ]
else:
    raise SystemExit(f"unknown state kind: {kind}")

with open(os.environ["FINGRIND_TEST_RULESET_STATE"], "w", encoding="utf-8") as target:
    json.dump({"rulesets": rulesets}, target)
PY
}

state_count() {
    FINGRIND_TEST_RULESET_STATE="${state_file}" python3 - <<'PY'
import json
import os

with open(os.environ["FINGRIND_TEST_RULESET_STATE"], encoding="utf-8") as source:
    print(len(json.load(source)["rulesets"]))
PY
}

post_count() {
    if [[ ! -e "${post_log}" ]]; then
        printf '0\n'
        return
    fi
    wc -l < "${post_log}" | tr -d '[:space:]'
}

run_configurator() {
    PATH="${stub_dir}:${PATH}" \
        FINGRIND_TEST_RULESET_STATE="${state_file}" \
        FINGRIND_TEST_RULESET_POST_LOG="${post_log}" \
        FINGRIND_TEST_REPO_VIEW="${test_root}/repo-view.json" \
        FINGRIND_TEST_REPOSITORY_METADATA="${test_root}/repository-metadata.json" \
        FINGRIND_TEST_PROTECTION="${test_root}/protection.json" \
        FINGRIND_TEST_RUNNERS="${test_root}/runners.json" \
        FINGRIND_TEST_WORKFLOW_PERMISSIONS="${test_root}/workflow-permissions.json" \
        "${configurator}"
}

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly configurator="${repo_root}/scripts/configure-release-tag-rulesets.sh"
readonly ruleset_contract="${repo_root}/scripts/release_tag_ruleset_contract.py"

[[ -x "${configurator}" ]] || die "missing executable ruleset configurator at ${configurator}"
[[ -f "${ruleset_contract}" ]] || die "missing ruleset contract at ${ruleset_contract}"

readonly temp_parent="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-configure-release-tag-rulesets.XXXXXX")"
readonly test_root="${temp_parent}/run"
readonly stub_dir="${temp_parent}/stub-bin"
readonly state_file="${test_root}/tag-rulesets.json"
readonly post_log="${test_root}/post-log.txt"
mkdir -p "${test_root}" "${stub_dir}"
cleanup() {
    chmod -R u+rwx "${temp_parent}" 2>/dev/null || true
    rm -rf "${temp_parent}" 2>/dev/null || true
}
trap cleanup EXIT

cat > "${test_root}/repo-view.json" <<'EOF'
{
  "nameWithOwner": "resoltico/FinGrind",
  "defaultBranchRef": {"name": "main"},
  "deleteBranchOnMerge": true
}
EOF

cat > "${test_root}/repository-metadata.json" <<'EOF'
{
  "owner": {"id": 17160191, "login": "resoltico", "type": "User"}
}
EOF

cat > "${test_root}/protection.json" <<'EOF'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["Gate"],
    "checks": [{"context": "Gate", "app_id": 15368}]
  },
  "enforce_admins": {"enabled": true},
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": false,
    "required_approving_review_count": 0,
    "require_last_push_approval": false
  }
}
EOF

cat > "${test_root}/runners.json" <<'EOF'
{"total_count": 0, "runners": []}
EOF

cat > "${test_root}/workflow-permissions.json" <<'EOF'
{"default_workflow_permissions": "read", "can_approve_pull_request_reviews": false}
EOF

cat > "${stub_dir}/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

emit_state_response() {
    local action=$1
    local ruleset_id=${2:-}
    FINGRIND_TEST_RULESET_STATE="${FINGRIND_TEST_RULESET_STATE}" \
        FINGRIND_TEST_RULESET_ACTION="${action}" \
        FINGRIND_TEST_RULESET_ID="${ruleset_id}" \
        python3 - <<'PY'
import json
import os

with open(os.environ["FINGRIND_TEST_RULESET_STATE"], encoding="utf-8") as source:
    state = json.load(source)
rulesets = state["rulesets"]
action = os.environ["FINGRIND_TEST_RULESET_ACTION"]
if action == "inventory":
    print(json.dumps([[{"id": ruleset["id"]} for ruleset in rulesets]]))
elif action == "detail":
    identifier = int(os.environ["FINGRIND_TEST_RULESET_ID"])
    for ruleset in rulesets:
        if ruleset["id"] == identifier:
            print(json.dumps(ruleset))
            break
    else:
        raise SystemExit(1)
else:
    raise SystemExit(1)
PY
}

case "${1:-}" in
    repo)
        shift
        [[ "${1:-}" == "view" ]] || exit 1
        cat "${FINGRIND_TEST_REPO_VIEW}"
        ;;
    api)
        shift
        method=GET
        input_source=
        while [[ $# -gt 0 && "${1}" == -* ]]; do
            case "${1}" in
                --paginate|--slurp)
                    shift
                    ;;
                --method)
                    method="${2:-}"
                    shift 2
                    ;;
                --input)
                    input_source="${2:-}"
                    shift 2
                    ;;
                *)
                    printf 'unexpected gh api option: %s\n' "${1}" >&2
                    exit 1
                    ;;
            esac
        done
        endpoint="${1:-}"
        case "${method}:${endpoint}" in
            GET:repos/resoltico/FinGrind)
                cat "${FINGRIND_TEST_REPOSITORY_METADATA}"
                ;;
            GET:repos/resoltico/FinGrind/branches/main/protection)
                cat "${FINGRIND_TEST_PROTECTION}"
                ;;
            GET:repos/resoltico/FinGrind/actions/runners)
                cat "${FINGRIND_TEST_RUNNERS}"
                ;;
            GET:repos/resoltico/FinGrind/actions/permissions/workflow)
                cat "${FINGRIND_TEST_WORKFLOW_PERMISSIONS}"
                ;;
            'GET:repos/resoltico/FinGrind/rulesets?targets=tag&includes_parents=true&per_page=100')
                emit_state_response inventory
                ;;
            GET:repos/resoltico/FinGrind/rulesets/*)
                emit_state_response detail "${endpoint##*/}"
                ;;
            POST:repos/resoltico/FinGrind/rulesets)
                [[ "${input_source}" == "-" ]] || exit 1
                request_json="$(cat)"
                FINGRIND_TEST_RULESET_STATE="${FINGRIND_TEST_RULESET_STATE}" \
                    FINGRIND_TEST_RULESET_POST_LOG="${FINGRIND_TEST_RULESET_POST_LOG}" \
                    FINGRIND_TEST_RULESET_REQUEST_JSON="${request_json}" \
                    python3 - <<'PY'
import json
import os

with open(os.environ["FINGRIND_TEST_RULESET_STATE"], encoding="utf-8") as source:
    state = json.load(source)
request = json.loads(os.environ["FINGRIND_TEST_RULESET_REQUEST_JSON"])
if any(item["name"] == request.get("name") for item in state["rulesets"]):
    raise SystemExit(1)
request["id"] = max((item["id"] for item in state["rulesets"]), default=100) + 1
request["source_type"] = "Repository"
state["rulesets"].append(request)
with open(os.environ["FINGRIND_TEST_RULESET_STATE"], "w", encoding="utf-8") as target:
    json.dump(state, target)
with open(os.environ["FINGRIND_TEST_RULESET_POST_LOG"], "a", encoding="utf-8") as log:
    log.write(f"{request['name']}\n")
print(json.dumps(request))
PY
                ;;
            *)
                printf 'unexpected gh api target: %s:%s\n' "${method}" "${endpoint}" >&2
                exit 1
                ;;
        esac
        ;;
    *)
        printf 'unexpected gh invocation: %s\n' "$*" >&2
        exit 1
        ;;
esac
EOF
chmod +x "${stub_dir}/gh"

write_state empty
rm -f "${post_log}"
empty_output="$(run_configurator)"
grep -Fq 'Created creation release tag ruleset.' <<<"${empty_output}" || die \
    "empty configuration did not create the canonical creation ruleset"
grep -Fq 'Created immutability release tag ruleset.' <<<"${empty_output}" || die \
    "empty configuration did not create the canonical immutability ruleset"
[[ "$(state_count)" == 2 ]] || die "empty configuration did not create exactly two rulesets"
[[ "$(post_count)" == 2 ]] || die "empty configuration did not make exactly two mutation requests"
FINGRIND_TEST_RULESET_STATE="${state_file}" python3 - <<'PY' | \
    python3 "${ruleset_contract}" --release-owner-id 17160191 >/dev/null
import json
import os

with open(os.environ["FINGRIND_TEST_RULESET_STATE"], encoding="utf-8") as source:
    print(json.dumps(json.load(source)["rulesets"]))
PY

complete_output="$(run_configurator)"
if grep -Fq 'Created ' <<<"${complete_output}"; then
    die "complete canonical configuration unexpectedly mutated rulesets"
fi
[[ "$(post_count)" == 2 ]] || die "complete canonical configuration made a duplicate mutation request"

write_state creation-only
rm -f "${post_log}"
partial_output="$(run_configurator)"
if grep -Fq 'Created creation release tag ruleset.' <<<"${partial_output}"; then
    die "partial canonical configuration recreated the existing creation ruleset"
fi
grep -Fq 'Created immutability release tag ruleset.' <<<"${partial_output}" || die \
    "partial canonical configuration did not create its missing immutability ruleset"
[[ "$(state_count)" == 2 ]] || die "partial canonical configuration did not converge to two rulesets"
[[ "$(post_count)" == 1 ]] || die "partial canonical configuration made more than one mutation request"

write_state immutability-only
rm -f "${post_log}"
reverse_partial_output="$(run_configurator)"
if grep -Fq 'Created immutability release tag ruleset.' <<<"${reverse_partial_output}"; then
    die "partial canonical configuration recreated the existing immutability ruleset"
fi
grep -Fq 'Created creation release tag ruleset.' <<<"${reverse_partial_output}" || die \
    "partial canonical configuration did not create its missing creation ruleset"
[[ "$(state_count)" == 2 ]] || die "reverse partial canonical configuration did not converge to two rulesets"
[[ "$(post_count)" == 1 ]] || die "reverse partial canonical configuration made more than one mutation request"

write_state drifted
rm -f "${post_log}"
if drifted_output="$(run_configurator 2>&1)"; then
    die "drifted configuration unexpectedly mutated or verified rulesets"
fi
grep -Fq 'release tag-ruleset configuration is not safely reconcilable' <<<"${drifted_output}" || die \
    "drifted configuration did not fail through the safe reconciliation boundary"
[[ "$(post_count)" == 0 ]] || die "drifted configuration made a ruleset mutation request"

write_state extra
rm -f "${post_log}"
if extra_output="$(run_configurator 2>&1)"; then
    die "extra tag-ruleset configuration unexpectedly mutated or verified rulesets"
fi
grep -Fq 'tag-ruleset inventory contains an unexpected ruleset' <<<"${extra_output}" || die \
    "extra tag-ruleset configuration did not fail through the exact inventory boundary"
[[ "$(post_count)" == 0 ]] || die "extra tag-ruleset configuration made a ruleset mutation request"

printf 'configure-release-tag-rulesets regression: success\n'
