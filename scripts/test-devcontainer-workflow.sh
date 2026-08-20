#!/usr/bin/env bash
# Keep the committed contributor-devcontainer surface wired through CI and docs.

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
readonly workflow_file="${repo_root}/.github/workflows/ci.yml"
readonly devcontainer_validator="${repo_root}/scripts/validate-devcontainer.sh"
readonly devcontainer_dockerfile="${repo_root}/.devcontainer/Dockerfile"
readonly devcontainer_config="${repo_root}/.devcontainer/devcontainer.json"
readonly dockerignore_file="${repo_root}/.dockerignore"
readonly powershell_metadata="${repo_root}/gradle/fingrind-build.properties"
readonly powershell_provisioner="${repo_root}/scripts/provision-powershell-runtime.py"
readonly powershell_runtime="${repo_root}/scripts/powershell_runtime.py"
readonly powershell_runtime_modules=(
    "${powershell_runtime}"
    "${repo_root}/scripts/powershell_provisioning_cli.py"
    "${repo_root}/scripts/powershell_provisioning_tree.py"
    "${repo_root}/scripts/powershell_runtime_archives.py"
    "${repo_root}/scripts/powershell_runtime_cache.py"
    "${repo_root}/scripts/powershell_runtime_download.py"
    "${repo_root}/scripts/powershell_runtime_installation.py"
    "${repo_root}/scripts/powershell_runtime_metadata.py"
    "${repo_root}/scripts/powershell_runtime_models.py"
)
readonly developer_devcontainer_doc="${repo_root}/docs/DEVELOPER_DEVCONTAINER.md"
readonly developer_docker_doc="${repo_root}/docs/DEVELOPER_DOCKER.md"
readonly developer_jazzer_doc="${repo_root}/docs/DEVELOPER_JAZZER_OPERATIONS.md"
readonly python_runtime_support="${repo_root}/scripts/python-runtime-support.sh"

[[ -f "${workflow_file}" ]] || die "missing CI workflow at ${workflow_file}"
[[ -x "${devcontainer_validator}" ]] || die \
    "missing executable contributor devcontainer validator at ${devcontainer_validator}"
[[ -f "${devcontainer_dockerfile}" ]] || die "missing contributor devcontainer Dockerfile at ${devcontainer_dockerfile}"
[[ -f "${devcontainer_config}" ]] || die "missing contributor devcontainer config at ${devcontainer_config}"
[[ -f "${dockerignore_file}" ]] || die "missing Docker build-context allowlist at ${dockerignore_file}"
[[ -f "${powershell_metadata}" ]] || die "missing canonical PowerShell metadata at ${powershell_metadata}"
[[ -f "${powershell_provisioner}" ]] || die "missing PowerShell provisioner at ${powershell_provisioner}"
for powershell_runtime_module in "${powershell_runtime_modules[@]}"; do
    [[ -f "${powershell_runtime_module}" ]] || die \
        "missing PowerShell provisioning module at ${powershell_runtime_module}"
done
[[ -f "${developer_devcontainer_doc}" ]] || die \
    "missing contributor devcontainer doc at ${developer_devcontainer_doc}"
[[ -f "${developer_docker_doc}" ]] || die "missing Docker doc at ${developer_docker_doc}"
[[ -f "${developer_jazzer_doc}" ]] || die "missing Jazzer operations doc at ${developer_jazzer_doc}"
[[ -f "${python_runtime_support}" ]] || die \
    "missing Python runtime support helper at ${python_runtime_support}"

# shellcheck source=/dev/null
source "${python_runtime_support}"
prepare_python_runtime_env
readonly python_executable="${FINGRIND_PYTHON_EXECUTABLE}"

required_pwsh_version="$(
    "${python_executable}" "${powershell_provisioner}" \
        --metadata "${powershell_metadata}" \
        --print-version
)"
readonly required_pwsh_version
[[ -n "${required_pwsh_version}" ]] || die "canonical PowerShell metadata has no exact version"
required_zulu_version="$(awk -F= '$1 == "fingrindZuluPackageVersion" { print $2; exit }' "${powershell_metadata}")"
readonly required_zulu_version
[[ -n "${required_zulu_version}" ]] || die "canonical Zulu metadata has no exact version"

grep -Fq 'name: Contributor devcontainer' "${workflow_file}" || die \
    "CI workflow no longer advertises the contributor devcontainer job"
grep -Fq './scripts/validate-devcontainer.sh' "${workflow_file}" || die \
    "CI workflow no longer runs the contributor devcontainer validator"
grep -Fq 'source "${python_runtime_support}"' "${devcontainer_validator}" || die \
    "devcontainer validator no longer resolves its repository-owned Python runtime"
grep -Fq 'readonly python_executable="${FINGRIND_PYTHON_EXECUTABLE}"' "${devcontainer_validator}" || die \
    "devcontainer validator no longer names its resolved Python executable"
grep -Fq '"${python_executable}" "${powershell_provisioner}"' "${devcontainer_validator}" || die \
    "devcontainer validator no longer reads PowerShell metadata through the resolved Python executable"
grep -Fq '"${python_executable}" - <<' "${devcontainer_validator}" || die \
    "devcontainer validator no longer reads devcontainer JSON through the resolved Python executable"
grep -Fq "'.dockerignore'" "${workflow_file}" || die \
    "CI workflow no longer treats the root Docker build-context allowlist as a devcontainer input"
grep -Fq './scripts/validate-devcontainer.sh' "${developer_devcontainer_doc}" || die \
    "developer devcontainer doc no longer points at the validator"
grep -Fq 'devcontainer up --workspace-folder .' "${developer_devcontainer_doc}" || die \
    "developer devcontainer doc no longer documents the tooling-agnostic devcontainer CLI workflow"
grep -Fq 'VS Code is not mandatory.' "${developer_devcontainer_doc}" || die \
    "developer devcontainer doc no longer states that VS Code is optional"
grep -Fq '[DEVELOPER_DEVCONTAINER.md]' "${developer_docker_doc}" || die \
    "Docker doc no longer distinguishes the contributor devcontainer companion reference"
grep -Fq 'Run One Docker-Only Fuzz Session From A Fresh Terminal' "${developer_jazzer_doc}" || die \
    "Jazzer operations doc no longer keeps the Docker-only fuzz workflow"
grep -Fq 'docker build --pull -f .devcontainer/Dockerfile -t fingrind-fuzz-dev:local .' "${developer_jazzer_doc}" || die \
    "Jazzer operations doc no longer documents the Docker-only contributor-image build step"
grep -Fq 'When the gate is skipped' "${developer_devcontainer_doc}" || die \
    "developer devcontainer doc no longer explains the Gate skip contract"
"${python_executable}" - "${devcontainer_config}" <<'PY'
import json
import sys
from pathlib import Path

build = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8")).get("build", {})
if build.get("context") != "..":
    raise SystemExit("devcontainer must use the repository root as its allowlisted Docker context")
PY
grep -Fq 'COPY gradle/fingrind-build.properties /tmp/fingrind-build.properties' "${devcontainer_dockerfile}" || die \
    "devcontainer no longer copies the canonical toolchain metadata into its image build"
grep -Fq 'fingrindZuluPackageVersion' "${devcontainer_dockerfile}" || die \
    "devcontainer no longer derives its exact Zulu version from canonical toolchain metadata"
for powershell_runtime_module in \
    'powershell_provisioning_cli.py' \
    'powershell_provisioning_tree.py' \
    'powershell_runtime.py' \
    'powershell_runtime_archives.py' \
    'powershell_runtime_cache.py' \
    'powershell_runtime_download.py' \
    'powershell_runtime_installation.py' \
    'powershell_runtime_metadata.py' \
    'powershell_runtime_models.py'; do
    grep -Fq "scripts/${powershell_runtime_module}" "${devcontainer_dockerfile}" || die \
        "devcontainer no longer copies ${powershell_runtime_module} into its image build"
    grep -Fq "!scripts/${powershell_runtime_module}" "${dockerignore_file}" || die \
        "Docker build-context allowlist no longer includes ${powershell_runtime_module}"
done
grep -Fq 'COPY scripts/provision-powershell-runtime.py /tmp/provision-powershell-runtime.py' "${devcontainer_dockerfile}" || die \
    "devcontainer no longer copies the PowerShell provisioner into its image build"
grep -Fq -- '--install-root /opt/fingrind/powershell' "${devcontainer_dockerfile}" || die \
    "devcontainer no longer provisions its exact PowerShell runtime into its immutable runtime root"
grep -Fq 'test ! -e /usr/local/bin/pwsh' "${devcontainer_dockerfile}" || die \
    "devcontainer no longer refuses an ambiguous pre-existing pwsh command"
grep -Fq '!gradle/fingrind-build.properties' "${dockerignore_file}" || die \
    "Docker build-context allowlist no longer includes canonical PowerShell metadata"
grep -Fq '!scripts/provision-powershell-runtime.py' "${dockerignore_file}" || die \
    "Docker build-context allowlist no longer includes the PowerShell provisioner"
grep -Fq "PowerShell \`${required_pwsh_version}\`" "${developer_devcontainer_doc}" || die \
    "developer devcontainer doc no longer states its exact built-in PowerShell release"
grep -Fq "Zulu ${required_zulu_version}" "${developer_devcontainer_doc}" || die \
    "developer devcontainer doc no longer states its exact built-in Zulu release"
grep -Fq 'repository root as its Docker build context' "${developer_devcontainer_doc}" || die \
    "developer devcontainer doc no longer explains the repository-root build context"
grep -Fq '.dockerignore' "${developer_devcontainer_doc}" || die \
    "developer devcontainer doc no longer identifies the root Docker build-context allowlist"
if grep -Fq 'ca-certificates \\' "${devcontainer_dockerfile}"; then
    die "contributor devcontainer redundantly reinstalls ca-certificates already supplied by its base image"
fi

printf 'devcontainer workflow regression: success\n'
