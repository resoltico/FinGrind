#!/usr/bin/env bash
# Guard the repo-owned JaCoCo artifact verifier against drift in the canonical dependency pin.

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
readonly verifier="${script_dir}/verify-jacoco-artifacts.sh"
readonly verifier_implementation="${script_dir}/jacoco_artifact_verification.py"
readonly verifier_regression="${script_dir}/test-jacoco-artifact-verification.py"
readonly python_runtime_support="${script_dir}/python-runtime-support.sh"
readonly version_catalog_path="${script_dir}/../gradle/libs.versions.toml"
readonly build_metadata_path="${script_dir}/../gradle/fingrind-build.properties"
readonly quality_gate_script="${script_dir}/run-quality-gates.sh"
readonly stage_contract_script="${script_dir}/check-stage-contract.sh"
readonly root_conventions_path="${script_dir}/../gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/FinGrindRootConventionsPlugin.kt"
readonly java_conventions_path="${script_dir}/../gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/FinGrindJavaConventionsPlugin.kt"
readonly pinned_version_path="${script_dir}/../gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/FinGrindPinnedJacocoVersion.kt"
readonly legacy_snapshot_path="${script_dir}/../gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/FinGrindPinnedJacocoSnapshotArtifacts.kt"
readonly legacy_prepare_task_path="${script_dir}/../gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/PrepareJacocoSnapshotArtifactsTask.kt"

[[ -x "${verifier}" ]] || die "missing JaCoCo artifact verifier at ${verifier}"
[[ -f "${verifier_implementation}" ]] || die \
    "missing JaCoCo artifact verifier implementation at ${verifier_implementation}"
[[ -f "${verifier_regression}" ]] || die \
    "missing JaCoCo artifact verifier regression at ${verifier_regression}"
[[ -f "${python_runtime_support}" ]] || die \
    "missing Python runtime support helper at ${python_runtime_support}"
[[ -f "${version_catalog_path}" ]] || die "missing version catalog at ${version_catalog_path}"
[[ -f "${build_metadata_path}" ]] || die "missing build metadata at ${build_metadata_path}"
[[ -x "${quality_gate_script}" ]] || die "missing quality gate script at ${quality_gate_script}"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${root_conventions_path}" ]] || die "missing root conventions plugin at ${root_conventions_path}"
[[ -f "${java_conventions_path}" ]] || die "missing java conventions plugin at ${java_conventions_path}"
[[ -f "${pinned_version_path}" ]] || die "missing pinned JaCoCo version owner at ${pinned_version_path}"
[[ ! -e "${legacy_snapshot_path}" ]] || die "legacy JaCoCo snapshot owner must be removed"
[[ ! -e "${legacy_prepare_task_path}" ]] || die "legacy JaCoCo snapshot prepare task must be removed"

grep -Fq 'jacoco = "0.8.15"' "${version_catalog_path}" || die \
    "version catalog no longer carries the pinned JaCoCo GA version"
if grep -Fq 'fingrindJacoco' "${build_metadata_path}"; then
    die "build metadata must not retain JaCoCo-specific properties"
fi
grep -Fq 'readonly jacoco_artifacts_verifier="${repo_root}/scripts/verify-jacoco-artifacts.sh"' "${quality_gate_script}" || die \
    "quality gate no longer defines the JaCoCo artifact verifier owner"
grep -Fq '"${jacoco_artifacts_verifier}"' "${quality_gate_script}" || die \
    "quality gate no longer runs the JaCoCo artifact verifier"
grep -Fq 'scripts/test-verify-jacoco-artifacts.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the JaCoCo artifact verifier regression"
grep -Fq 'configurePinnedJacocoVersion()' "${root_conventions_path}" || die \
    "root conventions no longer apply the pinned JaCoCo version owner"
grep -Fq 'configurePinnedJacocoVersion()' "${java_conventions_path}" || die \
    "java conventions no longer apply the pinned JaCoCo version owner"
grep -Fq 'toolVersion = jacocoVersion' "${pinned_version_path}" || die \
    "pinned JaCoCo version owner no longer wires the exact tool version"
grep -Fq 'exec "${FINGRIND_PYTHON_EXECUTABLE}" "${script_dir}/jacoco_artifact_verification.py"' "${verifier}" || die \
    "JaCoCo artifact verifier no longer delegates to the bounded retrieval implementation"
# shellcheck source=/dev/null
source "${python_runtime_support}"
prepare_python_runtime_env
"${FINGRIND_PYTHON_EXECUTABLE}" "${verifier_regression}"
"${verifier}" >/dev/null

printf 'JaCoCo artifact verifier regression: success\n'
