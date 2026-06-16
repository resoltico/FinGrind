#!/usr/bin/env bash
# Verify that the public CI workflow invokes the canonical root verification gate.

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

[[ -f "${workflow_file}" ]] || die "missing CI workflow at ${workflow_file}"
grep -Fq 'Run the canonical root verification gate' "${workflow_file}" || die \
    "CI workflow no longer advertises the canonical root verification gate"
grep -Fq './check.sh --no-daemon --console=plain' "${workflow_file}" || die \
    "CI workflow no longer runs the canonical root verification gate"
grep -Fq 'fingrindUvVersion=' "${workflow_file}" || die \
    "CI workflow no longer resolves the pinned uv launcher version from build metadata"
grep -Fq 'ORG_GRADLE_PROJECT_fingrindUvExecutable' "${workflow_file}" || die \
    "CI workflow no longer exports the pinned uv launcher path for Gradle-owned Python tool tasks"
grep -Fq 'sysconfig.get_path' "${workflow_file}" || die \
    "CI workflow no longer resolves the uv launcher scripts path through Python sysconfig"
grep -Fq 'Run included build-logic tests on Windows' "${workflow_file}" || die \
    "CI workflow no longer keeps Windows build-logic verification as a separate step"
grep -Fq 'prepare-published-bundle-smoke-matrix:' "${workflow_file}" || die \
    "CI workflow no longer prepares the published bundle smoke matrix from the canonical release plan"
grep -Fq 'read-release-publication-plan.py' "${workflow_file}" || die \
    "CI workflow no longer derives the published bundle smoke matrix from the canonical release-plan reader"
grep -Fq 'bundle-matrix-json=%s\n' "${workflow_file}" || die \
    "CI workflow no longer exports the published bundle smoke matrix through one explicit workflow output"
grep -Fq 'include: ${{ fromJson(needs.prepare-published-bundle-smoke-matrix.outputs.bundle-matrix-json) }}' "${workflow_file}" || die \
    "CI workflow no longer drives bundle-smoke runners from the prepared publication matrix"
grep -Fq 'Published bundle smoke (${{ matrix.classifier }})' "${workflow_file}" || die \
    "CI workflow no longer publishes pre-merge smoke coverage for every published bundle classifier"
grep -Fq './scripts/verify-runner-identity.py' "${workflow_file}" || die \
    "CI workflow no longer delegates runner-identity normalization to the canonical verifier"
grep -Fq 'Smoke test the published Unix CLI bundle on the host runner' "${workflow_file}" || die \
    "CI workflow no longer smoke-tests the non-Windows published bundle classifiers before release"
grep -Fq './scripts/bundle-smoke.sh "${{ steps.unix-bundle-build.outputs.archive-path }}"' "${workflow_file}" || die \
    "CI workflow no longer delegates non-Windows published bundle smoke to the canonical Bash owner"
grep -Fq -- '--execution-surface compatibility-floor' "${workflow_file}" || die \
    "CI workflow no longer re-proves the published Linux bundle on the compatibility floor"
grep -Fq 'source ./scripts/gradle-wrapper-support.sh' "${workflow_file}" || die \
    "CI workflow no longer sources the canonical Gradle wrapper helper before reading the bundle manifest"
grep -Fq "manifest_path=\"\$(fg_gradle_bundle_archive_manifest_path \"\$PWD\" 'cli' \"\${is_darwin}\")\"" "${workflow_file}" || die \
    "CI workflow no longer resolves the canonical bundle manifest path for published bundle smoke"
grep -Fq 'python3 -c '\''import json, sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["archivePath"])'\''' "${workflow_file}" || die \
    "CI workflow no longer reads the bundle archive path from the generated manifest"
grep -Fq 'python3 -c '\''import json, sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["checksumPath"])'\''' "${workflow_file}" || die \
    "CI workflow no longer reads the bundle checksum path from the generated manifest"
grep -Fq 'uv.exe' "${workflow_file}" || die \
    "CI workflow no longer bootstraps the pinned uv launcher on Windows before Gradle-owned Python tool tasks"
grep -Fq '.\scripts\setup-msvc-dev-cmd.ps1 -Arch x64' "${workflow_file}" || die \
    "CI workflow no longer bootstraps the Windows MSVC environment through the repo-owned PowerShell owner"
grep -Fq '.\scripts\configure-windows-defender-build-exclusions.ps1' "${workflow_file}" || die \
    "CI workflow no longer delegates Windows Defender build exclusions to the repo-owned PowerShell owner"
if grep -Fq 'Add-MpPreference -ExclusionPath' "${workflow_file}"; then
    die "CI workflow carries inline Windows Defender exclusion calls instead of the repo-owned PowerShell owner"
fi
if grep -Fq 'site.USER_BASE' "${workflow_file}"; then
    die "CI workflow computes the uv launcher path from site.USER_BASE instead of Python's scripts scheme"
fi
if grep -Fq '.\gradlew.bat check --no-daemon --console=plain' "${workflow_file}"; then
    die "CI workflow reruns the canonical root gate inside the Windows bundle publication lane"
fi
if grep -Fq 'resolve_bundle_path()' "${workflow_file}"; then
    die "CI workflow scrapes published bundle smoke artifact paths from Gradle console output instead of the canonical bundle manifest"
fi
grep -Fq '.\scripts\verify-direct-java-sqlite-runtime.ps1' "${workflow_file}" || die \
    "CI workflow no longer delegates Windows direct-Java runtime verification to the canonical PowerShell owner"
grep -Fq '.\scripts\verify-source-checkout-sqlite-runtime.ps1' "${workflow_file}" || die \
    "CI workflow no longer delegates Windows source-checkout runtime verification to the canonical PowerShell owner"
grep -Fq 'Verify managed SQLite CLI runtimes on Unix' "${workflow_file}" || die \
    "CI workflow no longer verifies the managed Unix runtime surfaces before bundle publication smoke"
grep -Fq '.\scripts\bundle-smoke.ps1 "${{ steps.windows-bundle-build.outputs.archive-path }}"' "${workflow_file}" || die \
    "CI workflow no longer smoke-tests the published Windows bundle through the canonical PowerShell owner"
if grep -Fq 'continue-on-error: true' "${workflow_file}"; then
    die "CI workflow still marks the published bundle smoke surface as observational"
fi
if grep -Fq 'matrix.expectedOs' "${workflow_file}" || grep -Fq 'matrix.expectedArch' "${workflow_file}"; then
    die "CI workflow still depends on retired runner-spelling matrix fields"
fi
if grep -Fq 'windows-bundle-smoke:' "${workflow_file}"; then
    die "CI workflow carries the retired release-blocking Windows bundle-smoke job key"
fi
if ! grep -A12 -F 'gate:' "${workflow_file}" | grep -Fq 'published-bundle-smoke'; then
    die "CI workflow no longer requires the aggregate Gate job to wait for the canonical published bundle smoke matrix"
fi
if ! grep -A12 -F 'gate:' "${workflow_file}" | grep -Fq 'prepare-published-bundle-smoke-matrix'; then
    die "CI workflow no longer requires the aggregate Gate job to wait for the matrix-preparation owner"
fi
if grep -Fq 'Run root quality gates and included build-logic tests on Windows' "${workflow_file}"; then
    die "CI workflow combines Windows root verification and build-logic verification in one non-fail-fast step"
fi
if grep -Fq '.\gradlew.bat -q :cli:run "--args=capabilities --output json"' "${workflow_file}"; then
    die "CI workflow carries the retired ad hoc Windows direct-Java runtime probe"
fi
if grep -Fq '.\scripts\source-checkout-cli.ps1 capabilities --output json' "${workflow_file}"; then
    die "CI workflow carries the retired ad hoc Windows source-checkout runtime probe"
fi
if grep -Fq 'ilammy/msvc-dev-cmd' "${workflow_file}"; then
    die "CI workflow depends on the deprecated third-party msvc-dev-cmd action"
fi

printf 'CI release-surface workflow regression: success\n'
