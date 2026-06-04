#!/usr/bin/env bash
# Guard the repo-owned JaCoCo snapshot verifier against drift in the canonical build metadata.

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
readonly verifier="${script_dir}/verify-jacoco-snapshot.sh"
readonly version_catalog_path="${script_dir}/../gradle/libs.versions.toml"
readonly build_metadata_path="${script_dir}/../gradle/fingrind-build.properties"
readonly quality_gate_script="${script_dir}/run-quality-gates.sh"
readonly stage_contract_script="${script_dir}/check-stage-contract.sh"
readonly root_conventions_path="${script_dir}/../gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/FinGrindRootConventionsPlugin.kt"
readonly java_conventions_path="${script_dir}/../gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/FinGrindJavaConventionsPlugin.kt"
readonly pinned_artifacts_path="${script_dir}/../gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/FinGrindPinnedJacocoSnapshotArtifacts.kt"
readonly prepare_task_path="${script_dir}/../gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/PrepareJacocoSnapshotArtifactsTask.kt"

[[ -x "${verifier}" ]] || die "missing JaCoCo snapshot verifier at ${verifier}"
[[ -f "${version_catalog_path}" ]] || die "missing version catalog at ${version_catalog_path}"
[[ -f "${build_metadata_path}" ]] || die "missing build metadata at ${build_metadata_path}"
[[ -x "${quality_gate_script}" ]] || die "missing quality gate script at ${quality_gate_script}"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${root_conventions_path}" ]] || die "missing root conventions plugin at ${root_conventions_path}"
[[ -f "${java_conventions_path}" ]] || die "missing java conventions plugin at ${java_conventions_path}"
[[ -f "${pinned_artifacts_path}" ]] || die "missing pinned JaCoCo artifact owner at ${pinned_artifacts_path}"
[[ -f "${prepare_task_path}" ]] || die "missing JaCoCo snapshot preparation task at ${prepare_task_path}"

if grep -Fq 'jacoco = "' "${version_catalog_path}"; then
    die "version catalog must not own the JaCoCo version"
fi
grep -Fq 'fingrindJacocoSnapshotBaseVersion=0.8.15-SNAPSHOT' "${build_metadata_path}" || die \
    "build metadata no longer carries the pinned JaCoCo snapshot base version"
grep -Fq 'fingrindJacocoSnapshotBuildLabel=0.8.15.202606030734' "${build_metadata_path}" || die \
    "build metadata no longer carries the pinned JaCoCo snapshot build label"
grep -Fq 'fingrindJacocoSnapshotResolvedVersion=0.8.15-20260603.073432-117' "${build_metadata_path}" || die \
    "build metadata no longer carries the pinned JaCoCo resolved snapshot version"
grep -Fq 'readonly jacoco_snapshot_verifier="${repo_root}/scripts/verify-jacoco-snapshot.sh"' "${quality_gate_script}" || die \
    "quality gate no longer defines the JaCoCo snapshot verifier owner"
grep -Fq '"${jacoco_snapshot_verifier}"' "${quality_gate_script}" || die \
    "quality gate no longer runs the JaCoCo snapshot verifier"
grep -Fq 'readonly jacoco_snapshot_fetch_user_agent="FinGrind-JaCoCo-Snapshot-Verifier/1.0"' "${verifier}" || die \
    "JaCoCo snapshot verifier no longer declares the repo-owned fetch user agent"
grep -Fq -- '--retry-all-errors' "${verifier}" || die \
    "JaCoCo snapshot verifier no longer retries snapshot fetch errors"
grep -Fq -- '--user-agent "${jacoco_snapshot_fetch_user_agent}"' "${verifier}" || die \
    "JaCoCo snapshot verifier no longer sends the repo-owned fetch user agent"
grep -Fq 'scripts/test-verify-jacoco-snapshot.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the JaCoCo snapshot verifier regression"
grep -Fq 'configurePinnedJacocoSnapshotArtifacts(buildMetadata)' "${root_conventions_path}" || die \
    "root conventions no longer apply the pinned JaCoCo artifact owner"
grep -Fq 'configurePinnedJacocoSnapshotArtifacts(buildMetadata)' "${java_conventions_path}" || die \
    "java conventions no longer apply the pinned JaCoCo artifact owner"
grep -Fq 'prepareJacocoSnapshotArtifacts' "${pinned_artifacts_path}" || die \
    "pinned JaCoCo artifact owner no longer stages the deterministic artifact set"
grep -Fq 'JACOCO_SNAPSHOT_FETCH_USER_AGENT = "FinGrind-JaCoCo-Snapshot-Verifier/1.0"' "${prepare_task_path}" || die \
    "JaCoCo snapshot preparation task no longer sends the repo-owned fetch user agent"
grep -Fq 'DOWNLOAD_MAX_ATTEMPTS = 6' "${prepare_task_path}" || die \
    "JaCoCo snapshot preparation task no longer retries cold snapshot downloads"
"${verifier}" >/dev/null

printf 'JaCoCo snapshot verifier regression: success\n'
