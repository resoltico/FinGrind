#!/usr/bin/env bash
# Keep dependency automation and the distribution freshness canary aligned with the real release
# surfaces they are meant to protect.

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
readonly dependabot_config="${repo_root}/.github/dependabot.yml"
readonly wrapper_workflow="${repo_root}/.github/workflows/gradle-wrapper-validation.yml"
readonly freshness_workflow="${repo_root}/.github/workflows/distribution-freshness.yml"

[[ -f "${dependabot_config}" ]] || die "missing Dependabot config at ${dependabot_config}"
[[ -f "${wrapper_workflow}" ]] || die "missing wrapper validation workflow at ${wrapper_workflow}"
[[ -f "${freshness_workflow}" ]] || die "missing distribution freshness workflow at ${freshness_workflow}"

grep -Fq 'package-ecosystem: "pip"' "${dependabot_config}" || die \
    "Dependabot no longer tracks repo-owned Python tool pins"
grep -Fq 'package-ecosystem: "docker"' "${dependabot_config}" || die \
    "Dependabot no longer tracks the Docker publication surface"
grep -Fq 'package-ecosystem: "gradle"' "${dependabot_config}" || die \
    "Dependabot no longer tracks Gradle dependencies"
grep -Fq 'directory: "/jazzer"' "${dependabot_config}" || die \
    "Dependabot no longer tracks the Jazzer Gradle surface separately"

grep -Fq 'concurrency:' "${wrapper_workflow}" || die \
    "wrapper validation workflow no longer uses a concurrency group"
grep -Fq 'cancel-in-progress: true' "${wrapper_workflow}" || die \
    "wrapper validation workflow no longer cancels superseded runs"

grep -Fq 'name: Distribution freshness' "${freshness_workflow}" || die \
    "distribution freshness workflow no longer advertises the weekly canary"
grep -Fq 'cron: "27 4 * * 1"' "${freshness_workflow}" || die \
    "distribution freshness workflow no longer runs on the canonical weekly schedule"
grep -Fq 'workflow_dispatch:' "${freshness_workflow}" || die \
    "distribution freshness workflow no longer exposes an on-demand canary rerun path"
grep -Fq 'concurrency:' "${freshness_workflow}" || die \
    "distribution freshness workflow no longer uses a concurrency group"
grep -Fq 'cancel-in-progress: true' "${freshness_workflow}" || die \
    "distribution freshness workflow no longer cancels superseded runs"
grep -Fq "java_version=\"\$(grep '^fingrindJavaVersion=' gradle/fingrind-build.properties | cut -d= -f2)\"" "${freshness_workflow}" || die \
    "distribution freshness workflow no longer resolves the canonical Java version from build metadata"
grep -Fq "python_version=\"\$(grep '^fingrindPythonVersion=' gradle/fingrind-build.properties | cut -d= -f2)\"" "${freshness_workflow}" || die \
    "distribution freshness workflow no longer resolves the canonical Python version from build metadata"
grep -Fq 'cache-dependency-path: requirements-python-tools.txt' "${freshness_workflow}" || die \
    "distribution freshness workflow no longer caches the repo-owned Python tool surface"
grep -Fq "uv_version=\"\$(grep '^fingrindUvVersion=' gradle/fingrind-build.properties | cut -d= -f2)\"" "${freshness_workflow}" || die \
    "distribution freshness workflow no longer resolves the pinned uv launcher version from build metadata"
grep -Fq 'ORG_GRADLE_PROJECT_fingrindUvExecutable' "${freshness_workflow}" || die \
    "distribution freshness workflow no longer exports the uv launcher path for Gradle-owned Python tool tasks"
grep -Fq './gradlew :cli:bundleCliArchive --no-daemon --console=plain' "${freshness_workflow}" || die \
    "distribution freshness workflow no longer rebuilds the published bundle surface"
grep -Fq './scripts/bundle-smoke.sh' "${freshness_workflow}" || die \
    "distribution freshness workflow no longer smoke-tests the rebuilt bundle surface"
grep -Fq './scripts/bundle-smoke.sh --execution-surface compatibility-floor' "${freshness_workflow}" || die \
    "distribution freshness workflow no longer re-proves the rebuilt Linux bundle on the compatibility floor"
grep -Fq './scripts/docker-smoke.sh' "${freshness_workflow}" || die \
    "distribution freshness workflow no longer smoke-tests the Docker publication surface"

printf 'distribution freshness workflow regression: success\n'
