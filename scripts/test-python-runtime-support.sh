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
    STUB_PATH="${stub_path}" ORIGINAL_PATH="${PATH}" LOG_PATH="${log_path}" PYTHON_RUNTIME_SUPPORT="${python_runtime_support}" bash <<'EOF'
set -euo pipefail
export PATH="${STUB_PATH}:${ORIGINAL_PATH}"
unset ORG_GRADLE_PROJECT_fingrindPythonExecutable
unset ORG_GRADLE_PROJECT_fingrindUvExecutable
unset FINGRIND_PYTHON_EXECUTABLE
# shellcheck source=/dev/null
source "${PYTHON_RUNTIME_SUPPORT}"
prepare_python_runtime_env
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
cat > "${preferred_stub_dir}/uv" <<'EOF'
#!/bin/bash
printf 'uv invoked unexpectedly\n' >&2
exit 1
EOF
chmod +x "${preferred_stub_dir}/uv"
preferred_log="${scenario_dir}/preferred.log"
run_with_stub_path "${preferred_stub_dir}" "${preferred_log}"
grep -Fx "python=${preferred_stub_dir}/python3.12" "${preferred_log}" >/dev/null || die \
    "prepare_python_runtime_env must prefer an on-path Python 3.12+ interpreter"
grep -Fx "uv=${preferred_stub_dir}/uv" "${preferred_log}" >/dev/null || die \
    "prepare_python_runtime_env must publish the on-path uv launcher when available"
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
printf 'Python 3.9 stub\n'
EOF
chmod +x "${uv_stub_dir}/python3"
for interpreter_name in python3.13 python3.12 python; do
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
case "\$1 \$2" in
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
grep -Fx "python=${scenario_dir}/managed-python3.12" "${uv_log_output}" >/dev/null || die \
    "prepare_python_runtime_env must fall back to a uv-managed Python 3.12+ runtime"
grep -Fx "uv=${uv_stub_dir}/uv" "${uv_log_output}" >/dev/null || die \
    "prepare_python_runtime_env must publish the uv launcher used for managed Python fallback"
grep -Fx 'python install 3.12' "${scenario_dir}/uv.log" >/dev/null || die \
    "prepare_python_runtime_env must ask uv to install the required Python runtime when no system interpreter qualifies"

printf 'python runtime support regression: success\n'
