#!/usr/bin/env bash
# Guard the repo-owned Python runtime helper so shell gates resolve a usable interpreter and uv
# launcher deterministically.

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

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly python_runtime_support="${repo_root}/scripts/python-runtime-support.sh"

[[ -f "${python_runtime_support}" ]] || die "missing Python runtime support helper"

scenario_dir="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-python-runtime-support.XXXXXX")"
trap 'rm -rf "${scenario_dir}"' EXIT

run_with_stub_path() {
    local stub_path=$1
    local log_path=$2
    local inherited_shims_dir=${3:-}
    STUB_PATH="${stub_path}" ORIGINAL_PATH="${PATH}" LOG_PATH="${log_path}" \
        INHERITED_SHIMS_DIR="${inherited_shims_dir}" \
        PYTHON_RUNTIME_SUPPORT="${python_runtime_support}" bash <<'EOF'
set -euo pipefail
if [[ -n "${INHERITED_SHIMS_DIR:-}" ]]; then
    export FINGRIND_PYTHON_SHIMS_DIR="${INHERITED_SHIMS_DIR}"
    export PATH="${STUB_PATH}:${INHERITED_SHIMS_DIR}:${ORIGINAL_PATH}"
else
    export PATH="${STUB_PATH}:${ORIGINAL_PATH}"
fi
unset ORG_GRADLE_PROJECT_fingrindPythonExecutable
unset ORG_GRADLE_PROJECT_fingrindUvExecutable
unset FINGRIND_PYTHON_EXECUTABLE
# shellcheck source=/dev/null
source "${PYTHON_RUNTIME_SUPPORT}"
prepare_python_runtime_env
printf 'shim=%s\n' "${FINGRIND_PYTHON_SHIMS_DIR}" >> "${LOG_PATH}"
printf 'python3-command=%s\n' "$(command -v python3)" >> "${LOG_PATH}"
python3 >/dev/null
printf 'python=%s\n' "${ORG_GRADLE_PROJECT_fingrindPythonExecutable}" >> "${LOG_PATH}"
printf 'uv=%s\n' "${ORG_GRADLE_PROJECT_fingrindUvExecutable:-missing}" >> "${LOG_PATH}"
EOF
}

preferred_stub_dir="${scenario_dir}/preferred"
mkdir -p "${preferred_stub_dir}"
cat > "${preferred_stub_dir}/python3.12" <<'EOF'
#!/bin/bash
if [[ "${1:-}" == "-" ]]; then
    shift
    read -r _script
    required_version="${1:-3.12}"
    if [[ "${required_version}" == "3.12" ]]; then
        exit 0
    fi
    exit 1
fi
printf 'Python 3.12 stub\n'
EOF
chmod +x "${preferred_stub_dir}/python3.12"
cat > "${preferred_stub_dir}/python3" <<'EOF'
#!/bin/bash
printf 'unexpected ambient python3 stub\n' >&2
exit 1
EOF
chmod +x "${preferred_stub_dir}/python3"
cat > "${preferred_stub_dir}/uv" <<'EOF'
#!/bin/bash
if [[ "${1:-}" == "--version" ]]; then
    printf 'uv 0.12.0\n'
    exit 0
fi
printf 'uv invoked unexpectedly\n' >&2
exit 1
EOF
chmod +x "${preferred_stub_dir}/uv"
preferred_log="${scenario_dir}/preferred.log"
run_with_stub_path "${preferred_stub_dir}" "${preferred_log}"
grep -Fx "shim=${scenario_dir}/preferred-shims" "${preferred_log}" >/dev/null && die \
    "clean-shell preferred-path scenario must allocate its own shim directory"
grep -F "python3-command=" "${preferred_log}" >/dev/null || die \
    "prepare_python_runtime_env must publish the shell-visible python3 command"
grep -Fx "python=${preferred_stub_dir}/python3.12" "${preferred_log}" >/dev/null || die \
    "prepare_python_runtime_env must prefer an on-path exact Python 3.12 interpreter"
grep -Fx "uv=${preferred_stub_dir}/uv" "${preferred_log}" >/dev/null || die \
    "prepare_python_runtime_env must publish the on-path uv launcher when available"

release_smoke_probe="${scenario_dir}/release-smoke-probe.py"
printf 'raise SystemExit(0)\n' > "${release_smoke_probe}"
tools_stub_dir="${scenario_dir}/release-smoke-tools"
mkdir -p "${tools_stub_dir}"
ln -s "${preferred_stub_dir}/python3.12" "${tools_stub_dir}/python3.12"
ln -s "${preferred_stub_dir}/python3" "${tools_stub_dir}/python3"
cat > "${tools_stub_dir}/uv" <<'EOF'
#!/bin/bash
if [[ "${1:-}" == "--version" ]]; then
    printf 'uv 0.12.0\n'
    exit 0
fi
if [[ "${1:-}" == "run" ]]; then
    printf '%s\n' "$*" >> "${UV_RUN_LOG}"
    exit 0
fi
printf 'unexpected uv invocation\n' >&2
exit 1
EOF
chmod +x "${tools_stub_dir}/uv"
release_smoke_uv_log="${scenario_dir}/release-smoke-uv.log"
REPO_ROOT="${repo_root}" STUB_PATH="${tools_stub_dir}" ORIGINAL_PATH="${PATH}" \
    UV_RUN_LOG="${release_smoke_uv_log}" RELEASE_SMOKE_PROBE="${release_smoke_probe}" \
    PYTHON_RUNTIME_SUPPORT="${python_runtime_support}" bash <<'EOF'
set -euo pipefail
cd "${REPO_ROOT}"
export PATH="${STUB_PATH}:${ORIGINAL_PATH}"
unset ORG_GRADLE_PROJECT_fingrindPythonExecutable
unset ORG_GRADLE_PROJECT_fingrindUvExecutable
unset FINGRIND_PYTHON_EXECUTABLE
# shellcheck source=/dev/null
source "${PYTHON_RUNTIME_SUPPORT}"
printf 'support-dir=%s\n' "${python_runtime_support_dir}" >> "${UV_RUN_LOG}"
printf 'repo-root=%s\n' "${python_runtime_support_repo_root}" >> "${UV_RUN_LOG}"
prepare_python_runtime_env
fingrind_run_python_with_tools "${RELEASE_SMOKE_PROBE}"
EOF
expected_release_smoke_uv_run="run --no-config --python ${tools_stub_dir}/python3.12 --with-requirements ${repo_root}/requirements-release-smoke-workflow.txt python ${release_smoke_probe}"
grep -Fx "support-dir=${repo_root}/scripts" "${release_smoke_uv_log}" >/dev/null || die \
    "sourcing Python runtime support from the repository root did not resolve its own scripts directory"
grep -Fx "repo-root=${repo_root}" "${release_smoke_uv_log}" >/dev/null || die \
    "sourcing Python runtime support from the repository root did not resolve its own repository root"
grep -Fx "${expected_release_smoke_uv_run}" "${release_smoke_uv_log}" >/dev/null || die \
    "repo-owned release-smoke tools must run through pinned uv with their isolated requirements"
STUB_PATH="${preferred_stub_dir}" ORIGINAL_PATH="${PATH}" PYTHON_RUNTIME_SUPPORT="${python_runtime_support}" bash <<'EOF'
set -euo pipefail
export PATH="${STUB_PATH}:${ORIGINAL_PATH}"
unset ORG_GRADLE_PROJECT_fingrindPythonExecutable
unset ORG_GRADLE_PROJECT_fingrindUvExecutable
unset FINGRIND_PYTHON_EXECUTABLE
# shellcheck source=/dev/null
source "${PYTHON_RUNTIME_SUPPORT}"
# shellcheck source=/dev/null
source "${PYTHON_RUNTIME_SUPPORT}"
prepare_python_runtime_env
EOF

preferred_inherited_shims_dir="${scenario_dir}/preferred-shims"
mkdir -p "${preferred_inherited_shims_dir}"
preferred_inherited_log="${scenario_dir}/preferred-inherited.log"
run_with_stub_path "${preferred_stub_dir}" "${preferred_inherited_log}" "${preferred_inherited_shims_dir}"
grep -Fx "shim=${preferred_inherited_shims_dir}" "${preferred_inherited_log}" >/dev/null || die \
    "prepare_python_runtime_env must reuse an inherited repo shim directory when one is published"
grep -Fx "python3-command=${preferred_inherited_shims_dir}/python3" "${preferred_inherited_log}" >/dev/null || die \
    "prepare_python_runtime_env must restore repo shim precedence ahead of ambient PATH entries"
grep -Fx "python=${preferred_stub_dir}/python3.12" "${preferred_inherited_log}" >/dev/null || die \
    "prepare_python_runtime_env must keep preferring the on-path exact Python 3.12 interpreter when reusing inherited shims"
grep -Fx "uv=${preferred_stub_dir}/uv" "${preferred_inherited_log}" >/dev/null || die \
    "prepare_python_runtime_env must preserve uv publication when inherited repo shims are present"

mismatched_uv_stub_dir="${scenario_dir}/mismatched-uv"
mkdir -p "${mismatched_uv_stub_dir}"
ln -s "${preferred_stub_dir}/python3.12" "${mismatched_uv_stub_dir}/python3.12"
cat > "${mismatched_uv_stub_dir}/python3" <<EOF
#!/bin/bash
if [[ "\${1:-}" == "-" ]]; then
    shift
    read -r _script
    exit 1
fi
if [[ "\${1:-}" == "-c" ]]; then
    printf '%s\n' "${scenario_dir}/missing-user-scripts"
    exit 0
fi
exit 1
EOF
chmod +x "${mismatched_uv_stub_dir}/python3"
cat > "${mismatched_uv_stub_dir}/uv" <<'EOF'
#!/bin/bash
if [[ "${1:-}" == "--version" ]]; then
    printf 'uv 0.11.28\n'
    exit 0
fi
printf 'uv invoked unexpectedly\n' >&2
exit 1
EOF
chmod +x "${mismatched_uv_stub_dir}/uv"
mismatched_uv_log="${scenario_dir}/mismatched-uv.log"
run_with_stub_path "${mismatched_uv_stub_dir}" "${mismatched_uv_log}"
grep -Fx 'uv=missing' "${mismatched_uv_log}" >/dev/null || die \
    "prepare_python_runtime_env must not abort or publish a mismatched uv launcher"
missing_uv_log="${scenario_dir}/missing-release-smoke-uv.log"
if STUB_PATH="${mismatched_uv_stub_dir}" ORIGINAL_PATH="${PATH}" \
    RELEASE_SMOKE_PROBE="${release_smoke_probe}" PYTHON_RUNTIME_SUPPORT="${python_runtime_support}" \
    bash <<'EOF' >"${missing_uv_log}" 2>&1
set -euo pipefail
export PATH="${STUB_PATH}:${ORIGINAL_PATH}"
unset ORG_GRADLE_PROJECT_fingrindPythonExecutable
unset ORG_GRADLE_PROJECT_fingrindUvExecutable
unset FINGRIND_PYTHON_EXECUTABLE
# shellcheck source=/dev/null
source "${PYTHON_RUNTIME_SUPPORT}"
prepare_python_runtime_env
fingrind_run_python_with_tools "${RELEASE_SMOKE_PROBE}"
EOF
then
    die "repo-owned release-smoke tools must reject a missing or mismatched pinned uv launcher"
fi
grep -F 'Install the pinned uv launcher' "${missing_uv_log}" >/dev/null || die \
    "repo-owned release-smoke tools must provide an actionable pinned-uv bootstrap hint"

user_uv_stub_dir="${scenario_dir}/user-uv"
user_uv_scripts_dir="${scenario_dir}/user-uv-scripts"
mkdir -p "${user_uv_stub_dir}" "${user_uv_scripts_dir}"
ln -s "${preferred_stub_dir}/python3.12" "${user_uv_stub_dir}/python3.12"
cat > "${user_uv_stub_dir}/python3" <<EOF
#!/bin/bash
if [[ "\${1:-}" == "-" ]]; then
    shift
    read -r _script
    exit 1
fi
if [[ "\${1:-}" == "-c" ]]; then
    printf '%s\n' "${user_uv_scripts_dir}"
    exit 0
fi
exit 1
EOF
chmod +x "${user_uv_stub_dir}/python3"
cat > "${user_uv_scripts_dir}/uv" <<'EOF'
#!/bin/bash
if [[ "${1:-}" == "--version" ]]; then
    printf 'uv 0.12.0\n'
    exit 0
fi
printf 'uv invoked unexpectedly\n' >&2
exit 1
EOF
chmod +x "${user_uv_scripts_dir}/uv"
user_uv_log="${scenario_dir}/user-uv.log"
run_with_stub_path "${user_uv_stub_dir}" "${user_uv_log}"
grep -Fx "uv=${user_uv_scripts_dir}/uv" "${user_uv_log}" >/dev/null || die \
    "prepare_python_runtime_env must prefer the pinned user-installed uv launcher over a mismatched PATH launcher"

uv_stub_dir="${scenario_dir}/uv-managed"
mkdir -p "${uv_stub_dir}"
cat > "${uv_stub_dir}/python3" <<'EOF'
#!/bin/bash
if [[ "${1:-}" == "-" ]]; then
    shift
    read -r _script
    required_version="${1:-3.12}"
    if [[ "${required_version}" == "3.12" ]]; then
        exit 1
    fi
    exit 0
fi
printf 'unexpected ambient python3 stub\n' >&2
exit 1
EOF
chmod +x "${uv_stub_dir}/python3"
for interpreter_name in python3.12 python; do
    cat > "${uv_stub_dir}/${interpreter_name}" <<'EOF'
#!/bin/bash
if [[ "${1:-}" == "-" ]]; then
    shift
    read -r _script
    exit 1
fi
exit 1
EOF
    chmod +x "${uv_stub_dir}/${interpreter_name}"
done
cat > "${uv_stub_dir}/uv" <<EOF
#!/bin/bash
set -euo pipefail
printf '%s\n' "\$*" >> "${scenario_dir}/uv.log"
case "\${1:-} \${2:-}" in
    "--version ")
        printf 'uv 0.12.0\n'
        exit 0
        ;;
    "python find")
        if [[ "\${3:-}" == "3.12" ]]; then
            if [[ -f "${scenario_dir}/python-installed.flag" ]]; then
                printf '%s\n' "${scenario_dir}/managed-python3.12"
            fi
            exit 0
        fi
        ;;
    "python install")
        if [[ "\${3:-}" == "3.12" ]]; then
            : > "${scenario_dir}/python-installed.flag"
            exit 0
        fi
        ;;
esac
exit 1
EOF
chmod +x "${uv_stub_dir}/uv"
cat > "${scenario_dir}/managed-python3.12" <<'EOF'
#!/bin/bash
if [[ "${1:-}" == "-" ]]; then
    shift
    read -r _script
    required_version="${1:-3.12}"
    if [[ "${required_version}" == "3.12" ]]; then
        exit 0
    fi
    exit 1
fi
printf 'Managed Python 3.12 stub\n'
EOF
chmod +x "${scenario_dir}/managed-python3.12"
uv_log_output="${scenario_dir}/uv-managed.log"
run_with_stub_path "${uv_stub_dir}" "${uv_log_output}"
grep -F "python3-command=" "${uv_log_output}" >/dev/null || die \
    "prepare_python_runtime_env must publish the shell-visible python3 command for uv-managed fallback"
grep -Fx "python=${scenario_dir}/managed-python3.12" "${uv_log_output}" >/dev/null || die \
    "prepare_python_runtime_env must fall back to an exact uv-managed Python 3.12 runtime"
grep -Fx "uv=${uv_stub_dir}/uv" "${uv_log_output}" >/dev/null || die \
    "prepare_python_runtime_env must publish the uv launcher used for managed Python fallback"
grep -Fx 'python install 3.12' "${scenario_dir}/uv.log" >/dev/null || die \
    "prepare_python_runtime_env must ask uv to install the required Python runtime when no system interpreter qualifies"

printf 'python runtime support regression: success\n'
