#!/usr/bin/env bash
# Reproduce and guard all-target Jazzer wrapper aggregation and fail-fast behavior.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

die_with_run_log() {
    local message=$1

    printf 'error: %s\n' "${message}" >&2
    if [[ -f "${run_log:-}" ]]; then
        printf '%s\n' '--- fuzz-all run log ---' >&2
        cat "${run_log}" >&2
        printf '%s\n' '--- end fuzz-all run log ---' >&2
    fi
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

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly source_wrapper="${repo_root}/jazzer/bin/fuzz-all"
readonly source_common="${repo_root}/jazzer/bin/common.sh"

[[ -f "${source_wrapper}" ]] || die "missing fuzz-all wrapper"
[[ -f "${source_common}" ]] || die "missing common.sh wrapper library"

tmp_dir="$(mktemp -d)"
cleanup() {
    rm -rf "${tmp_dir}"
}
trap cleanup EXIT

readonly run_log="${tmp_dir}/run.log"
readonly list_findings_args="${tmp_dir}/list-findings-args.txt"

cp "${source_wrapper}" "${tmp_dir}/fuzz-all"
chmod +x "${tmp_dir}/fuzz-all"
cp "${source_common}" "${tmp_dir}/common.sh"
chmod +x "${tmp_dir}/common.sh"

declare -a active_target_keys=()
while IFS= read -r target_key; do
    active_target_keys+=("${target_key}")
done < <(
    "${repo_root}/gradlew" -p "${repo_root}/jazzer" --no-daemon --no-configuration-cache -q jazzerActiveTargets
)

[[ "${#active_target_keys[@]}" -ge 2 ]] || die "expected at least two active Jazzer targets"
readonly timeout_target="${active_target_keys[0]}"
readonly failure_target="${active_target_keys[1]}"

for index in "${!active_target_keys[@]}"; do
    target_key="${active_target_keys[index]}"
    wrapper_path="${tmp_dir}/fuzz-${target_key}"
    if [[ ${index} -eq 0 ]]; then
        cat > "${wrapper_path}" <<EOF
#!/usr/bin/env bash
printf '%s\n' "stub ${target_key} timeout"
exit 124
EOF
    elif [[ ${index} -eq 1 ]]; then
        cat > "${wrapper_path}" <<EOF
#!/usr/bin/env bash
printf '%s\n' "stub ${target_key} failure"
exit 1
EOF
    else
        cat > "${wrapper_path}" <<EOF
#!/usr/bin/env bash
printf '%s\n' "${target_key} harness should not run after actionable failure"
exit 0
EOF
    fi
    chmod +x "${wrapper_path}"
done

cat > "${tmp_dir}/list-findings" <<EOF
#!/usr/bin/env bash
printf '%s\n' "\$*" > "${list_findings_args}"
printf '%s\n' "Target: ${failure_target}"
printf '%s\n' "Summary: actionable=1 expected-invalid=0 replay-clean=0"
printf '%s\n' "crash-demo | unexpected-failure | synthetic regression finding"
exit 0
EOF
chmod +x "${tmp_dir}/list-findings"

set +e
FG_REPO_ROOT="${repo_root}" FG_JAZZER_DIR="${repo_root}/jazzer" \
    "${tmp_dir}/fuzz-all" --console=plain > "${run_log}" 2>&1
status=$?
set -e

[[ ${status} -eq 1 ]] || die "fuzz-all should stop with exit 1 after an actionable harness failure"
grep -Fq "[JAZZER-WRAPPER] Starting fuzz-${timeout_target}" "${run_log}" ||
    die_with_run_log "fuzz-all did not start the first harness"
grep -Fq "[JAZZER-WRAPPER] Finished fuzz-${timeout_target} with exit code 124" "${run_log}" ||
    die_with_run_log "fuzz-all did not keep the timeout result for the first harness"
grep -Fq "[JAZZER-WRAPPER] Starting fuzz-${failure_target}" "${run_log}" ||
    die_with_run_log "fuzz-all did not continue to the second harness after a timeout"
grep -Fq "[JAZZER-WRAPPER] Finished fuzz-${failure_target} with exit code 1" "${run_log}" ||
    die_with_run_log "fuzz-all did not record the actionable failure exit code"
grep -Fq "[JAZZER-WRAPPER] Classified findings for ${failure_target}:" "${run_log}" ||
    die_with_run_log "fuzz-all did not surface classified findings after the actionable failure"
grep -Fq 'Summary: actionable=1 expected-invalid=0 replay-clean=0' "${run_log}" ||
    die_with_run_log "fuzz-all did not print the classified finding summary"
grep -Fq "[JAZZER-WRAPPER] Stopping after actionable failure in fuzz-${failure_target}." "${run_log}" ||
    die_with_run_log "fuzz-all did not announce fail-fast shutdown after the actionable failure"
for target_key in "${active_target_keys[@]:2}"; do
    if grep -Fq "[JAZZER-WRAPPER] Starting fuzz-${target_key}" "${run_log}"; then
        die_with_run_log "fuzz-all should not continue to later harnesses after an actionable failure"
    fi
done
[[ -f "${list_findings_args}" ]] || die_with_run_log "fuzz-all did not invoke list-findings"
[[ "$(cat "${list_findings_args}")" == "${failure_target} --console=plain" ]] ||
    die_with_run_log "fuzz-all called list-findings with unexpected arguments"

printf 'jazzer fuzz-all wrapper regression: success\n'
