#!/usr/bin/env bash
# Establish the exact local PowerShell and quality-tool runtime for the Windows contract preflight.

readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly gradlew="${repo_root}/gradlew"
readonly powershell_metadata="${repo_root}/gradle/fingrind-build.properties"
readonly powershell_provisioner="${repo_root}/scripts/provision-powershell-runtime.py"
readonly powershell_quality_tools_metadata="${repo_root}/scripts/powershell-quality-tools.properties"
readonly powershell_quality_tools_provisioner="${repo_root}/scripts/provision-powershell-quality-tools.py"
readonly powershell_quality_runner="${repo_root}/scripts/invoke-powershell-quality.ps1"
readonly python_runtime_support="${repo_root}/scripts/python-runtime-support.sh"
readonly git_executable="$(command -v git || true)"

[[ -f "${stage_contract_script}" ]] || die \
    "missing check stage contract helper at ${stage_contract_script}"
[[ -x "${gradlew}" ]] || die "missing executable Gradle wrapper at ${gradlew}"
[[ -f "${powershell_metadata}" ]] || die "missing PowerShell metadata at ${powershell_metadata}"
[[ -f "${powershell_provisioner}" ]] || die "missing PowerShell provisioner at ${powershell_provisioner}"
[[ -f "${powershell_quality_tools_metadata}" ]] || die \
    "missing PowerShell quality-tool metadata at ${powershell_quality_tools_metadata}"
[[ -f "${powershell_quality_tools_provisioner}" ]] || die \
    "missing PowerShell quality-tool provisioner at ${powershell_quality_tools_provisioner}"
[[ -f "${powershell_quality_runner}" ]] || die \
    "missing PowerShell quality runner at ${powershell_quality_runner}"
[[ -f "${python_runtime_support}" ]] || die \
    "missing Python runtime support at ${python_runtime_support}"
if [[ -z "${git_executable}" || ! -x "${git_executable}" ]]; then
    die 'check-windows-contract.sh requires git so it can inventory every owned PowerShell source'
fi
"${git_executable}" -C "${repo_root}" rev-parse --is-inside-work-tree >/dev/null 2>&1 || die \
    "check-windows-contract.sh requires a Git worktree at ${repo_root} to inventory every owned PowerShell source"
# shellcheck source=./python-runtime-support.sh
source "${python_runtime_support}"
prepare_python_runtime_env || die \
    'check-windows-contract.sh could not prepare the repository-owned exact Python runtime'
readonly python_executable="${FINGRIND_PYTHON_EXECUTABLE}"
required_pwsh_version="$(
    "${python_executable}" "${powershell_provisioner}" \
        --metadata "${powershell_metadata}" \
        --print-version
)" || die 'check-windows-contract.sh could not read the exact pinned PowerShell version'
readonly required_pwsh_version
if [[ "${FINGRIND_PWSH_EXECUTABLE+x}" == 'x' ]]; then
    pwsh_executable="${FINGRIND_PWSH_EXECUTABLE}"
else
    pwsh_executable="$(command -v pwsh || true)"
fi
readonly pwsh_executable
if [[ -z "${pwsh_executable}" || ! -f "${pwsh_executable}" || ! -x "${pwsh_executable}" ]]; then
    die "check-windows-contract.sh requires exact pinned pwsh ${required_pwsh_version}; provision it explicitly with '${python_executable} scripts/provision-powershell-runtime.py --install-root <safe-directory>' and set FINGRIND_PWSH_EXECUTABLE to the printed path before retrying"
fi

pwsh_version="$("${pwsh_executable}" -NoLogo -NoProfile -NonInteractive -Command '$PSVersionTable.PSVersion.ToString()')" || die \
    "check-windows-contract.sh could not determine the actual pwsh version at ${pwsh_executable}"
readonly pwsh_version
if [[ "${pwsh_version}" != "${required_pwsh_version}" ]]; then
    die "check-windows-contract.sh requires exact pinned PowerShell ${required_pwsh_version}; found ${pwsh_version}. Provision it with scripts/provision-powershell-runtime.py and set FINGRIND_PWSH_EXECUTABLE to the printed path"
fi
readonly pwsh_directory="$(cd -P -- "$(dirname -- "${pwsh_executable}")" && pwd)"
export FINGRIND_PWSH_EXECUTABLE="${pwsh_executable}"
export PATH="${pwsh_directory}:${PATH}"

if [[ "${FINGRIND_POWERSHELL_QUALITY_TOOLS_ROOT+x}" == 'x' ]]; then
    [[ -n "${FINGRIND_POWERSHELL_QUALITY_TOOLS_ROOT}" ]] || die \
        'FINGRIND_POWERSHELL_QUALITY_TOOLS_ROOT must not be blank when set'
    [[ "${FINGRIND_POWERSHELL_QUALITY_TOOLS_ROOT}" == /* ]] || die \
        'FINGRIND_POWERSHELL_QUALITY_TOOLS_ROOT must be an absolute path when set'
    quality_tools_root="${FINGRIND_POWERSHELL_QUALITY_TOOLS_ROOT}"
else
    quality_tools_root="${repo_root}/tmp/fingrind-powershell-quality-tools"
fi
readonly quality_tools_root
mkdir -p "${repo_root}/tmp"
quality_tools_installation_json="$(
    "${python_executable}" "${powershell_quality_tools_provisioner}" \
        --metadata "${powershell_quality_tools_metadata}" \
        --install-root "${quality_tools_root}"
)" || die 'check-windows-contract.sh could not provision checksum-pinned PowerShell quality tools'
quality_tool_values=()
while IFS= read -r -d '' quality_tool_value; do
    quality_tool_values+=("${quality_tool_value}")
done < <(
    "${python_executable}" - "${quality_tools_installation_json}" <<'PY'
import json
import sys

value = json.loads(sys.argv[1])
for item in (
    value["pester"]["manifest"],
    value["pester"]["version"],
    value["psScriptAnalyzer"]["manifest"],
    value["psScriptAnalyzer"]["version"],
):
    if not isinstance(item, str) or not item:
        raise SystemExit("PowerShell quality-tool provisioner returned an incomplete installation record")
    sys.stdout.buffer.write(item.encode("utf-8") + b"\0")
PY
)
[[ ${#quality_tool_values[@]} -eq 4 ]] || die \
    'check-windows-contract.sh could not decode the pinned PowerShell quality-tool installation'
readonly pester_manifest="${quality_tool_values[0]}"
readonly pester_version="${quality_tool_values[1]}"
readonly script_analyzer_manifest="${quality_tool_values[2]}"
readonly script_analyzer_version="${quality_tool_values[3]}"
