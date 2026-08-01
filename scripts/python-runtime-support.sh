#!/usr/bin/env bash
# Keep Python bytecode artifacts out of the repository during shell-driven verification flows and
# resolve the repo-owned Python tooling runtime and pinned dependency environment.

resolve_python_runtime_support_dir() {
    local source_path=$1
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

if [[ -z "${python_runtime_support_dir+x}" ]]; then
    python_runtime_support_dir="$(resolve_python_runtime_support_dir "${BASH_SOURCE[0]}")"
    readonly python_runtime_support_dir
fi
if [[ -z "${python_runtime_support_repo_root+x}" ]]; then
    python_runtime_support_repo_root="$(cd -P -- "${python_runtime_support_dir}/.." && pwd)"
    readonly python_runtime_support_repo_root
fi
if [[ -z "${python_runtime_support_build_properties+x}" ]]; then
    python_runtime_support_build_properties=\
"${python_runtime_support_repo_root}/gradle/fingrind-build.properties"
    readonly python_runtime_support_build_properties
fi

fingrind_build_property() {
    local property_name=$1
    local fallback_value=$2
    if [[ -f "${python_runtime_support_build_properties}" ]]; then
        local property_value
        property_value="$(
            awk -F= -v property_name="${property_name}" \
                '$1 == property_name { print $2; exit }' \
                "${python_runtime_support_build_properties}"
        )"
        if [[ -n "${property_value}" ]]; then
            printf '%s\n' "${property_value}"
            return 0
        fi
    fi
    printf '%s\n' "${fallback_value}"
}

fingrind_required_python_version() {
    fingrind_build_property "fingrindPythonVersion" "3.12"
}

fingrind_required_uv_version() {
    fingrind_build_property "fingrindUvVersion" "0.12.0"
}

python_runtime_command_path() {
    command -v "$1" 2>/dev/null || true
}

python_runtime_version_satisfies() {
    local python_executable=$1
    local required_version=$2
    "${python_executable}" - "${required_version}" <<'PY' >/dev/null 2>&1
import sys

required = tuple(int(part) for part in sys.argv[1].split("."))
current = sys.version_info[: len(required)]
raise SystemExit(0 if current == required else 1)
PY
}

default_uv_bootstrap_python() {
    if command -v python3 >/dev/null 2>&1; then
        printf '%s\n' "python3"
    else
        printf '%s\n' "python"
    fi
}

uv_command_path() {
    local user_uv_executable
    user_uv_executable="$(user_uv_command_path || true)"
    if uv_version_matches_requirement "${user_uv_executable}"; then
        printf '%s\n' "${user_uv_executable}"
        return 0
    fi

    local path_uv_executable
    path_uv_executable="$(python_runtime_command_path uv)"
    if uv_version_matches_requirement "${path_uv_executable}"; then
        printf '%s\n' "${path_uv_executable}"
        return 0
    fi
    return 1
}

user_uv_command_path() {
    local bootstrap_python
    bootstrap_python="$(default_uv_bootstrap_python)"
    local scripts_directory
    scripts_directory="$("${bootstrap_python}" -c \
        'import sysconfig; print(sysconfig.get_path("scripts", scheme="posix_user") or "")' \
        2>/dev/null || true)"
    [[ -n "${scripts_directory}" ]] || return 1
    local uv_executable="${scripts_directory}/uv"
    [[ -x "${uv_executable}" ]] || return 1
    printf '%s\n' "${uv_executable}"
}

uv_version_matches_requirement() {
    local uv_executable=$1
    [[ -n "${uv_executable}" ]] || return 1
    local uv_version
    uv_version="$("${uv_executable}" --version 2>/dev/null || true)"
    case "${uv_version}" in
        "uv $(fingrind_required_uv_version)"|"uv $(fingrind_required_uv_version) "*) return 0 ;;
        *) return 1 ;;
    esac
}

python_runtime_bootstrap_hint() {
    local bootstrap_python
    bootstrap_python="$(default_uv_bootstrap_python)"
    printf 'Install the pinned uv launcher with `%s -m pip install --user uv==%s`.' \
        "${bootstrap_python}" \
        "$(fingrind_required_uv_version)"
}

fingrind_repo_uv_executable() {
    local configured_uv_executable="${ORG_GRADLE_PROJECT_fingrindUvExecutable:-}"
    if uv_version_matches_requirement "${configured_uv_executable}"; then
        printf '%s\n' "${configured_uv_executable}"
        return 0
    fi

    local resolved_uv_executable
    resolved_uv_executable="$(uv_command_path || true)"
    if [[ -n "${resolved_uv_executable}" ]]; then
        printf '%s\n' "${resolved_uv_executable}"
        return 0
    fi

    printf 'error: the repo-owned Python tools require the pinned uv launcher. %s\n' \
        "$(python_runtime_bootstrap_hint)" >&2
    return 1
}

fingrind_run_python_with_tools() {
    local requirements_file="${python_runtime_support_repo_root}/requirements-release-smoke-workflow.txt"
    [[ -n "${FINGRIND_PYTHON_EXECUTABLE:-}" ]] || {
        printf 'error: prepare_python_runtime_env must run before repo-owned Python tools.\n' >&2
        return 1
    }
    [[ -f "${requirements_file}" ]] || {
        printf 'error: missing repo-owned Python tool requirements at %s\n' "${requirements_file}" >&2
        return 1
    }

    local uv_executable
    uv_executable="$(fingrind_repo_uv_executable)" || return 1
    "${uv_executable}" run --no-project \
        --python "${FINGRIND_PYTHON_EXECUTABLE}" \
        --with-requirements "${requirements_file}" \
        python "$@"
}

resolve_system_python_runtime() {
    local required_version=$1
    local candidate_path
    local candidate
    for candidate in python3.12 python3 python; do
        candidate_path="$(python_runtime_command_path "${candidate}")"
        if [[ -n "${candidate_path}" ]] && python_runtime_version_satisfies "${candidate_path}" "${required_version}"; then
            printf '%s\n' "${candidate_path}"
            return 0
        fi
    done
    return 1
}

resolve_uv_managed_python_runtime() {
    local required_version=$1
    local uv_executable
    uv_executable="$(uv_command_path)"
    [[ -n "${uv_executable}" ]] || return 1

    local python_path
    python_path="$("${uv_executable}" python find "${required_version}" 2>/dev/null || true)"
    if [[ -n "${python_path}" ]] && python_runtime_version_satisfies "${python_path}" "${required_version}"; then
        printf '%s\n' "${python_path}"
        return 0
    fi

    "${uv_executable}" python install "${required_version}" >/dev/null
    python_path="$("${uv_executable}" python find "${required_version}" 2>/dev/null || true)"
    if [[ -n "${python_path}" ]] && python_runtime_version_satisfies "${python_path}" "${required_version}"; then
        printf '%s\n' "${python_path}"
        return 0
    fi

    return 1
}

ensure_repo_python_shims() {
    local resolved_python=$1
    local shims_dir="${FINGRIND_PYTHON_SHIMS_DIR:-${TMPDIR:-/tmp}/fingrind-python-shims.$$}"

    mkdir -p "${shims_dir}"
    ln -sfn "${resolved_python}" "${shims_dir}/python3"
    ln -sfn "${resolved_python}" "${shims_dir}/python"

    export FINGRIND_PYTHON_SHIMS_DIR="${shims_dir}"
    local path_without_shims=":${PATH-}:"
    path_without_shims="${path_without_shims//:${shims_dir}:/:}"
    path_without_shims="${path_without_shims#:}"
    path_without_shims="${path_without_shims%:}"
    if [[ -n "${path_without_shims}" ]]; then
        export PATH="${shims_dir}:${path_without_shims}"
    else
        export PATH="${shims_dir}"
    fi
    hash -r
}

resolve_repo_python_runtime() {
    local required_version=$1

    if [[ -n "${FINGRIND_PYTHON_EXECUTABLE:-}" ]]; then
        if python_runtime_version_satisfies "${FINGRIND_PYTHON_EXECUTABLE}" "${required_version}"; then
            printf '%s\n' "${FINGRIND_PYTHON_EXECUTABLE}"
            return 0
        fi
        printf 'error: FINGRIND_PYTHON_EXECUTABLE=%s does not match exact Python %s\n' \
            "${FINGRIND_PYTHON_EXECUTABLE}" \
            "${required_version}" >&2
        return 1
    fi

    local system_python
    system_python="$(resolve_system_python_runtime "${required_version}" || true)"
    if [[ -n "${system_python}" ]]; then
        printf '%s\n' "${system_python}"
        return 0
    fi

    local uv_managed_python
    uv_managed_python="$(resolve_uv_managed_python_runtime "${required_version}" || true)"
    if [[ -n "${uv_managed_python}" ]]; then
        printf '%s\n' "${uv_managed_python}"
        return 0
    fi

    printf 'error: no exact Python %s runtime is available for repo-owned tooling. %s\n' \
        "${required_version}" \
        "$(python_runtime_bootstrap_hint)" >&2
    return 1
}

prepare_python_runtime_env() {
    export PYTHONDONTWRITEBYTECODE=1
    if [[ -z "${PYTHONPYCACHEPREFIX:-}" ]]; then
        export PYTHONPYCACHEPREFIX="${TMPDIR:-/tmp}/fingrind-python-pycache.$$.$RANDOM"
    fi

    local required_python_version
    required_python_version="$(fingrind_required_python_version)"

    local resolved_uv_executable=
    if [[ -z "${ORG_GRADLE_PROJECT_fingrindUvExecutable:-}" ]]; then
        resolved_uv_executable="$(uv_command_path || true)"
    fi

    local resolved_python="${FINGRIND_PYTHON_EXECUTABLE:-${ORG_GRADLE_PROJECT_fingrindPythonExecutable:-}}"
    if [[ -z "${resolved_python}" ]]; then
        resolved_python="$(resolve_repo_python_runtime "${required_python_version}")" || return 1
    fi
    if ! python_runtime_version_satisfies "${resolved_python}" "${required_python_version}"; then
        printf 'error: resolved Python runtime %s does not match exact Python %s\n' \
            "${resolved_python}" \
            "${required_python_version}" >&2
        return 1
    fi
    export ORG_GRADLE_PROJECT_fingrindPythonExecutable="${resolved_python}"
    export FINGRIND_PYTHON_EXECUTABLE="${resolved_python}"
    ensure_repo_python_shims "${resolved_python}"

    if [[ -z "${ORG_GRADLE_PROJECT_fingrindUvExecutable:-}" \
        && -n "${resolved_uv_executable}" ]]; then
        export ORG_GRADLE_PROJECT_fingrindUvExecutable="${resolved_uv_executable}"
    fi
}
