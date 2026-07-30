#!/usr/bin/env bash
# Prove deterministic Jazzer wrappers route each task to its owning Gradle build without fallback.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

note() {
    printf 'jazzer verification-wrapper routing check: %s\n' "$1"
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

assert_recorded_arguments() {
    local arguments_file=$1
    shift
    local actual_argument=''
    local expected_argument=''

    [[ -f "${arguments_file}" ]] || die "Gradle stub did not record an invocation"
    while IFS= read -r actual_argument || [[ -n "${actual_argument}" ]]; do
        [[ $# -gt 0 ]] || die "Gradle stub received unexpected argument: ${actual_argument}"
        expected_argument=$1
        shift
        [[ "${actual_argument}" == "${expected_argument}" ]] || die \
            "Gradle argument mismatch: expected '${expected_argument}', got '${actual_argument}'"
    done < "${arguments_file}"
    [[ $# -eq 0 ]] || die "Gradle stub omitted expected argument: $1"
}

assert_invocation_count() {
    local call_count_file=$1
    local expected_count=$2

    [[ -f "${call_count_file}" ]] || die "Gradle stub did not write its invocation count"
    [[ "$(tr -d '[:space:]' < "${call_count_file}")" == "${expected_count}" ]] || die \
        "verification wrapper invoked Gradle an unexpected number of times"
}

invoke_wrapper() {
    local wrapper_path=$1
    local expected_owner=$2
    local expected_exit_status=$3
    local arguments_file=$4
    local call_count_file=$5
    local output_file=$6

    set +e
    FG_REPO_ROOT="${stub_repo_root}" \
        FG_JAZZER_DIR="${stub_jazzer_dir}" \
        FG_GRADLEW="${stub_gradlew}" \
        FG_GRADLEW_ARGUMENTS_FILE="${arguments_file}" \
        FG_GRADLEW_CALL_COUNT_FILE="${call_count_file}" \
        FG_GRADLEW_EXPECTED_OWNER="${expected_owner}" \
        FG_GRADLEW_STUB_EXIT_STATUS="${expected_exit_status}" \
        "${wrapper_path}" --console=plain > "${output_file}" 2>&1
    wrapper_status=$?
    set -e
}

assert_help_route() {
    local wrapper_path=$1
    local expected_project_directory=$2
    local output_file=$3

    FG_REPO_ROOT="${stub_repo_root}" \
        FG_JAZZER_DIR="${stub_jazzer_dir}" \
        FG_GRADLEW="${stub_gradlew}" \
        "${wrapper_path}" --help > "${output_file}" 2>&1
    grep -Fq \
        "For raw Gradle task help, run ${stub_gradlew} -p ${expected_project_directory} help --task <task-name>." \
        "${output_file}" || die "wrapper help named the wrong Gradle project: ${wrapper_path}"
}

assert_wrapper_routes_and_fails_closed() {
    local wrapper_name=$1
    local expected_owner=$2
    local expected_project_directory=$3
    shift 3
    local expected_gradle_arguments=("$@")
    local wrapper_path="${repo_root}/jazzer/bin/${wrapper_name}"
    local success_arguments_file="${tmp_dir}/${wrapper_name}-success-arguments.txt"
    local success_call_count_file="${tmp_dir}/${wrapper_name}-success-call-count.txt"
    local success_output_file="${tmp_dir}/${wrapper_name}-success-output.txt"
    local failure_arguments_file="${tmp_dir}/${wrapper_name}-failure-arguments.txt"
    local failure_call_count_file="${tmp_dir}/${wrapper_name}-failure-call-count.txt"
    local failure_output_file="${tmp_dir}/${wrapper_name}-failure-output.txt"
    local help_output_file="${tmp_dir}/${wrapper_name}-help-output.txt"

    [[ -x "${wrapper_path}" ]] || die "missing executable Jazzer wrapper: ${wrapper_path}"

    note "${wrapper_name} successful route"
    invoke_wrapper \
        "${wrapper_path}" \
        "${expected_owner}" \
        0 \
        "${success_arguments_file}" \
        "${success_call_count_file}" \
        "${success_output_file}"
    [[ ${wrapper_status} -eq 0 ]] || die "${wrapper_name} rejected its owning Gradle route"
    assert_invocation_count "${success_call_count_file}" 1
    assert_recorded_arguments \
        "${success_arguments_file}.1" \
        -p \
        "${expected_project_directory}" \
        "${expected_gradle_arguments[@]}"

    note "${wrapper_name} failure propagation"
    invoke_wrapper \
        "${wrapper_path}" \
        "${expected_owner}" \
        73 \
        "${failure_arguments_file}" \
        "${failure_call_count_file}" \
        "${failure_output_file}"
    [[ ${wrapper_status} -eq 73 ]] || die \
        "${wrapper_name} did not propagate an owning-build failure without fallback"
    assert_invocation_count "${failure_call_count_file}" 1
    assert_recorded_arguments \
        "${failure_arguments_file}.1" \
        -p \
        "${expected_project_directory}" \
        "${expected_gradle_arguments[@]}"

    note "${wrapper_name} help route"
    assert_help_route "${wrapper_path}" "${expected_project_directory}" "${help_output_file}"
}

assert_check_routes_and_fails_closed() {
    local wrapper_path="${repo_root}/jazzer/bin/check"
    local success_arguments_file="${tmp_dir}/check-success-arguments.txt"
    local success_call_count_file="${tmp_dir}/check-success-call-count.txt"
    local success_output_file="${tmp_dir}/check-success-output.txt"
    local failure_arguments_file="${tmp_dir}/check-failure-arguments.txt"
    local failure_call_count_file="${tmp_dir}/check-failure-call-count.txt"
    local failure_output_file="${tmp_dir}/check-failure-output.txt"
    local help_output_file="${tmp_dir}/check-help-output.txt"

    [[ -x "${wrapper_path}" ]] || die "missing executable Jazzer wrapper: ${wrapper_path}"

    note 'check runs clean before verification'
    invoke_wrapper \
        "${wrapper_path}" nested 0 "${success_arguments_file}" "${success_call_count_file}" \
        "${success_output_file}"
    [[ ${wrapper_status} -eq 0 ]] || die 'check rejected its owning Gradle route'
    assert_invocation_count "${success_call_count_file}" 2
    assert_recorded_arguments \
        "${success_arguments_file}.1" \
        -p "${stub_jazzer_dir}" --no-configuration-cache clean --console=plain
    assert_recorded_arguments \
        "${success_arguments_file}.2" \
        -p "${stub_jazzer_dir}" --no-configuration-cache check --console=plain

    note 'check stops when clean fails'
    invoke_wrapper \
        "${wrapper_path}" nested 73 "${failure_arguments_file}" "${failure_call_count_file}" \
        "${failure_output_file}"
    [[ ${wrapper_status} -eq 73 ]] || die 'check did not propagate its clean-stage failure'
    assert_invocation_count "${failure_call_count_file}" 1
    assert_recorded_arguments \
        "${failure_arguments_file}.1" \
        -p "${stub_jazzer_dir}" --no-configuration-cache clean --console=plain

    note 'check help route'
    assert_help_route "${wrapper_path}" "${stub_jazzer_dir}" "${help_output_file}"
}

script_dir="$(resolve_script_dir)"
readonly script_dir
repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly repo_root
tmp_dir="$(mktemp -d)"
readonly tmp_dir
readonly stub_repo_root="${tmp_dir}/repository"
readonly stub_jazzer_dir="${stub_repo_root}/jazzer"
readonly stub_gradlew="${tmp_dir}/gradlew"
wrapper_status=0

cleanup() {
    rm -rf "${tmp_dir}"
}
trap cleanup EXIT

mkdir -p \
    "${stub_repo_root}/jazzer/bin" \
    "${stub_jazzer_dir}/src/main/resources/dev/erst/fingrind/jazzer/support" \
    "${stub_repo_root}/scripts"
printf '%s\n' \
    '#!/usr/bin/env bash' \
    'acquire_lock() { :; }' \
    'cleanup_lock() { :; }' > "${stub_repo_root}/jazzer/bin/_run-lock-support"
printf '%s\n' '#!/usr/bin/env bash' > "${stub_repo_root}/scripts/read-jazzer-topology.py"
printf '%s\n' '{}' > \
    "${stub_jazzer_dir}/src/main/resources/dev/erst/fingrind/jazzer/support/jazzer-run-targets.json"
cat > "${stub_gradlew}" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

: "${FG_GRADLEW_ARGUMENTS_FILE:?missing argument capture path}"
: "${FG_GRADLEW_CALL_COUNT_FILE:?missing call-count path}"
: "${FG_GRADLEW_EXPECTED_OWNER:?missing expected Gradle owner}"

call_count=0
if [[ -f "${FG_GRADLEW_CALL_COUNT_FILE}" ]]; then
    call_count="$(tr -d '[:space:]' < "${FG_GRADLEW_CALL_COUNT_FILE}")"
fi
call_count="$((call_count + 1))"
printf '%s\n' "${call_count}" > "${FG_GRADLEW_CALL_COUNT_FILE}"
printf '%s\n' "$@" > "${FG_GRADLEW_ARGUMENTS_FILE}.${call_count}"

case "${FG_GRADLEW_EXPECTED_OWNER}" in
    nested)
        [[ $# -ge 4 && "$1" == '-p' && "$2" == "${FG_JAZZER_DIR}" ]] || exit 86
        shift 2
        ;;
    root)
        [[ $# -ge 4 && "$1" == '-p' && "$2" == "${FG_REPO_ROOT}" ]] || exit 87
        shift 2
        ;;
    *) exit 88 ;;
esac
[[ "$1" == '--no-configuration-cache' && $# -ge 3 ]] || exit 89
exit "${FG_GRADLEW_STUB_EXIT_STATUS:-0}"
EOF
chmod +x "${stub_repo_root}/jazzer/bin/_run-lock-support" "${stub_gradlew}"

assert_wrapper_routes_and_fails_closed \
    test nested "${stub_jazzer_dir}" --no-configuration-cache test --console=plain
assert_wrapper_routes_and_fails_closed \
    regression nested "${stub_jazzer_dir}" --no-configuration-cache jazzerRegression --console=plain
assert_check_routes_and_fails_closed

printf 'jazzer verification-wrapper routing regression: success\n'
