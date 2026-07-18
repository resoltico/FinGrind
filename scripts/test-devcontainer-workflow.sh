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
readonly devcontainer_dockerfile="${repo_root}/.devcontainer/Dockerfile"
readonly developer_devcontainer_doc="${repo_root}/docs/DEVELOPER_DEVCONTAINER.md"
readonly developer_docker_doc="${repo_root}/docs/DEVELOPER_DOCKER.md"
readonly developer_jazzer_doc="${repo_root}/docs/DEVELOPER_JAZZER_OPERATIONS.md"

[[ -f "${workflow_file}" ]] || die "missing CI workflow at ${workflow_file}"
[[ -f "${devcontainer_dockerfile}" ]] || die "missing contributor devcontainer Dockerfile at ${devcontainer_dockerfile}"
[[ -f "${developer_devcontainer_doc}" ]] || die \
    "missing contributor devcontainer doc at ${developer_devcontainer_doc}"
[[ -f "${developer_docker_doc}" ]] || die "missing Docker doc at ${developer_docker_doc}"
[[ -f "${developer_jazzer_doc}" ]] || die "missing Jazzer operations doc at ${developer_jazzer_doc}"

grep -Fq 'name: Contributor devcontainer' "${workflow_file}" || die \
    "CI workflow no longer advertises the contributor devcontainer job"
grep -Fq './scripts/validate-devcontainer.sh' "${workflow_file}" || die \
    "CI workflow no longer runs the contributor devcontainer validator"
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
grep -Fq 'docker build --pull -f .devcontainer/Dockerfile -t fingrind-fuzz-dev:local .devcontainer' "${developer_jazzer_doc}" || die \
    "Jazzer operations doc no longer documents the Docker-only contributor-image build step"
grep -Fq 'When the gate is skipped' "${developer_devcontainer_doc}" || die \
    "developer devcontainer doc no longer explains the Gate skip contract"
if grep -Fq 'ca-certificates \\' "${devcontainer_dockerfile}"; then
    die "contributor devcontainer redundantly reinstalls ca-certificates already supplied by its base image"
fi

printf 'devcontainer workflow regression: success\n'
