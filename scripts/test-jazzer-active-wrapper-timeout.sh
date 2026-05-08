#!/usr/bin/env bash
# Reproduce and guard active-wrapper timeout behavior around delayed fuzz startup.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

die_with_log() {
    local message=$1
    local log_path=$2

    printf 'error: %s\n' "${message}" >&2
    if [[ -f "${log_path}" ]]; then
        printf '%s\n' '--- wrapper run log ---' >&2
        cat "${log_path}" >&2
        printf '%s\n' '--- end wrapper run log ---' >&2
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
readonly source_wrapper="${repo_root}/jazzer/bin/fuzz-cli-request"
readonly source_common="${repo_root}/jazzer/bin/common.sh"
readonly source_run_lock_support="${repo_root}/jazzer/bin/_run-lock-support"
readonly source_repo_lock_support="${repo_root}/scripts/repo-verification-lock-support.sh"
readonly source_topology_reader="${repo_root}/scripts/read-jazzer-topology.py"
readonly source_topology_file="${repo_root}/jazzer/src/main/resources/dev/erst/fingrind/jazzer/support/jazzer-topology.json"

[[ -f "${source_wrapper}" ]] || die "missing fuzz-cli-request wrapper"
[[ -f "${source_common}" ]] || die "missing common.sh wrapper library"
[[ -f "${source_run_lock_support}" ]] || die "missing Jazzer run-lock helper"
[[ -f "${source_repo_lock_support}" ]] || die "missing repo verification lock helper"
[[ -f "${source_topology_reader}" ]] || die "missing Jazzer topology reader"
[[ -f "${source_topology_file}" ]] || die "missing Jazzer topology file"

tmp_dir="$(mktemp -d)"
cleanup() {
    rm -rf "${tmp_dir}"
}
trap cleanup EXIT

readonly stub_repo_root="${tmp_dir}/repo"
readonly stub_jazzer_dir="${stub_repo_root}/jazzer"
readonly stub_bin_dir="${stub_jazzer_dir}/bin"
readonly stub_scripts_dir="${stub_repo_root}/scripts"
readonly stub_topology_dir="${stub_jazzer_dir}/src/main/resources/dev/erst/fingrind/jazzer/support"
readonly stub_run_directory="${stub_jazzer_dir}/.local/runs/cli-request"
mkdir -p "${stub_bin_dir}" "${stub_scripts_dir}" "${stub_topology_dir}" "${stub_jazzer_dir}/.local/runs"

cp "${source_wrapper}" "${tmp_dir}/fuzz-cli-request"
chmod +x "${tmp_dir}/fuzz-cli-request"
cp "${source_common}" "${tmp_dir}/common.sh"
chmod +x "${tmp_dir}/common.sh"
cp "${source_run_lock_support}" "${stub_bin_dir}/_run-lock-support"
cp "${source_repo_lock_support}" "${stub_scripts_dir}/repo-verification-lock-support.sh"
cp "${source_topology_reader}" "${stub_scripts_dir}/read-jazzer-topology.py"
cp "${source_topology_file}" "${stub_topology_dir}/jazzer-topology.json"
chmod +x "${stub_bin_dir}/_run-lock-support" "${stub_scripts_dir}/repo-verification-lock-support.sh"

cat > "${tmp_dir}/gradlew" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

mode="${FG_GRADLEW_STUB_MODE:?missing FG_GRADLEW_STUB_MODE}"

printf '%s\n' '> Task :fuzzCliRequest'
case "${mode}" in
    delayed-success)
        sleep 4
        printf '%s\n' 'INFO: Seed: 123'
        sleep 1
        printf '%s\n' '#1 DONE   cov: 1 ft: 1 corp: 1/1b lim: 1 exec/s: 1 rss: 1Mb'
        exit 0
        ;;
    overrun-after-start)
        sleep 1
        printf '%s\n' 'INFO: Seed: 123'
        sleep 4
        exit 0
        ;;
    startup-timeout)
        sleep 4
        exit 0
        ;;
    *)
        printf 'unknown stub mode: %s\n' "${mode}" >&2
        exit 99
        ;;
esac
EOF
chmod +x "${tmp_dir}/gradlew"

run_wrapper() {
    local mode=$1
    local startup_timeout_seconds=$2
    local log_path=$3
    local exit_code

    set +e
    FG_REPO_ROOT="${stub_repo_root}" \
        FG_JAZZER_DIR="${stub_jazzer_dir}" \
        FG_GRADLEW="${tmp_dir}/gradlew" \
        FG_TIMEOUT_GRACE_SECONDS=1 \
        FG_TIMEOUT_STARTUP_SECONDS="${startup_timeout_seconds}" \
        FG_GRADLEW_STUB_MODE="${mode}" \
        "${tmp_dir}/fuzz-cli-request" -PjazzerMaxDuration=1s --console=plain > "${log_path}" 2>&1
    exit_code=$?
    set -e
    printf '%s' "${exit_code}"
}

readonly delayed_success_log="${tmp_dir}/delayed-success.log"
readonly overrun_log="${tmp_dir}/overrun.log"
readonly startup_timeout_log="${tmp_dir}/startup-timeout.log"

mkdir -p "${stub_run_directory}"
printf '%s\n' 'INFO: Seed: stale previous run marker' > "${stub_run_directory}/latest.log"
delayed_success_exit_code="$(run_wrapper delayed-success 10 "${delayed_success_log}")"
[[ "${delayed_success_exit_code}" == "0" ]] ||
    die_with_log \
        "active wrapper should ignore stale latest.log content and wait for the current fuzz start marker before arming the runtime deadline" \
        "${delayed_success_log}"
if grep -Fq "Timed out" "${delayed_success_log}"; then
    die_with_log \
        "active wrapper reported a timeout for a run that completed after delayed startup" \
        "${delayed_success_log}"
fi

overrun_exit_code="$(run_wrapper overrun-after-start 10 "${overrun_log}")"
[[ "${overrun_exit_code}" == "124" ]] ||
    die_with_log \
        "active wrapper should keep wrapper-enforced runtime overruns on exit 124" \
        "${overrun_log}"
grep -Fq \
    "[JAZZER-WRAPPER] Timed out after the libFuzzer start marker plus the requested duration and 1s grace." \
    "${overrun_log}" ||
    die_with_log "active wrapper did not report the post-start runtime timeout message" "${overrun_log}"

startup_timeout_exit_code="$(run_wrapper startup-timeout 2 "${startup_timeout_log}")"
[[ "${startup_timeout_exit_code}" == "124" ]] ||
    die_with_log \
        "active wrapper should fail with exit 124 when fuzz startup never reaches the libFuzzer marker" \
        "${startup_timeout_log}"
grep -Fq \
    "[JAZZER-WRAPPER] Timed out before fuzz execution reached the libFuzzer start marker within 2s." \
    "${startup_timeout_log}" ||
    die_with_log "active wrapper did not report the startup timeout message" "${startup_timeout_log}"

printf 'jazzer active-wrapper timeout regression: success\n'
