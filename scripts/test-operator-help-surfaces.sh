#!/usr/bin/env bash
# Guard user-facing help output for repo-owned operator scripts and Jazzer wrappers.

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

poison_python3_dir() {
    local directory=$1

    mkdir -p "${directory}"
    cat > "${directory}/python3" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' 'python3 should not run while printing --help' >&2
exit 99
EOF
    chmod +x "${directory}/python3"
}

run_help() {
    local output=''
    local status=0

    set +e
    output="$("$@" 2>&1)"
    status=$?
    set -e
    [[ ${status} -eq 0 ]] || die "help surface failed for: $*"
    printf '%s' "${output}"
}

assert_wrapper_help_output() {
    local expected_usage=$1
    shift
    local output=''

    output=$1
    shift
    [[ "${output}" == *"${expected_usage}"* ]] || die "missing usage line '${expected_usage}' in: $*"
    [[ "${output}" != *'USAGE: gradlew'* ]] || die "raw Gradle help leaked through: $*"
    [[ "${output}" != *'To see help contextual to the project, use gradlew help'* ]] || die \
        "wrapper help fell through to Gradle project help: $*"
}

assert_help_leaves_tmpdir_empty() {
    local temp_dir=$1
    shift
    if find "${temp_dir}" -mindepth 1 -print -quit | grep -q .; then
        die "help path created temporary files or directories: $*"
    fi
}

assert_wrapper_help() {
    local expected_usage=$1
    shift
    local help_temp_dir=''
    local output=''

    help_temp_dir="$(mktemp -d "${temp_root}/help-surface.XXXXXX")"
    output="$(run_help env "TMPDIR=${help_temp_dir}" "$@")"
    assert_wrapper_help_output "${expected_usage}" "${output}" "$@"
    assert_help_leaves_tmpdir_empty "${help_temp_dir}" "$@"
}

assert_wrapper_help_without_python3() {
    local expected_usage=$1
    local python3_override_dir=$2
    shift 2
    local help_temp_dir=''
    local output=''

    help_temp_dir="$(mktemp -d "${temp_root}/help-surface.XXXXXX")"
    output="$(run_help env "PATH=${python3_override_dir}:${PATH}" "TMPDIR=${help_temp_dir}" "$@")"
    assert_wrapper_help_output "${expected_usage}" "${output}" "$@"
    [[ "${output}" != *'python3 should not run while printing --help'* ]] || die \
        "help path unexpectedly invoked python3: $*"
    assert_help_leaves_tmpdir_empty "${help_temp_dir}" "$@"
}

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly temp_root="$(mktemp -d)"
trap 'rm -rf "${temp_root}"' EXIT
readonly poisoned_python3_dir="${temp_root}/poisoned-python3"

poison_python3_dir "${poisoned_python3_dir}"

assert_wrapper_help_without_python3 'Usage: ./check.sh [supported gradle options]' \
    "${poisoned_python3_dir}" \
    "${repo_root}/check.sh" --help
assert_wrapper_help 'Usage: ./scripts/run-quality-gates.sh [supported Gradle options]' \
    "${repo_root}/scripts/run-quality-gates.sh" --help
assert_wrapper_help 'Usage: ./scripts/check-release-surface-scripts.sh' \
    "${repo_root}/scripts/check-release-surface-scripts.sh" --help
assert_wrapper_help_without_python3 'Usage: ./scripts/bundle-smoke.sh [bundle-archive-path]' \
    "${poisoned_python3_dir}" \
    "${repo_root}/scripts/bundle-smoke.sh" --help
assert_wrapper_help 'Usage: ./scripts/docker-smoke.sh' \
    "${repo_root}/scripts/docker-smoke.sh" --help

assert_wrapper_help 'Usage: jazzer/bin/check [supported Gradle options]' \
    "${repo_root}/jazzer/bin/check" --help
assert_wrapper_help 'Usage: jazzer/bin/test [supported Gradle options]' \
    "${repo_root}/jazzer/bin/test" --help
assert_wrapper_help 'Usage: jazzer/bin/regression [supported Gradle options]' \
    "${repo_root}/jazzer/bin/regression" --help
assert_wrapper_help 'Usage: jazzer/bin/replay <target-key> <input-path> [--json] [supported Gradle options]' \
    "${repo_root}/jazzer/bin/replay" --help
assert_wrapper_help 'Usage: jazzer/bin/list-findings [<target-key>] [--json] [supported Gradle options]' \
    "${repo_root}/jazzer/bin/list-findings" --help
assert_wrapper_help 'Usage: jazzer/bin/clean-local-findings [supported Gradle options]' \
    "${repo_root}/jazzer/bin/clean-local-findings" --help
assert_wrapper_help 'Usage: jazzer/bin/clean-local-corpus [supported Gradle options]' \
    "${repo_root}/jazzer/bin/clean-local-corpus" --help
assert_wrapper_help 'Usage: jazzer/bin/fuzz-all [supported Gradle options]' \
    "${repo_root}/jazzer/bin/fuzz-all" --help

while IFS= read -r target_key; do
    [[ -n "${target_key}" ]] || continue
    assert_wrapper_help "Usage: jazzer/bin/fuzz-${target_key} [supported Gradle options]" \
        "${repo_root}/jazzer/bin/fuzz-${target_key}" --help
done < <(
    "${repo_root}/gradlew" \
        -p "${repo_root}/jazzer" \
        --no-daemon \
        --no-configuration-cache \
        -q \
        jazzerActiveTargets
)

printf 'operator help surface regression: success\n'
