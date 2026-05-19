#!/usr/bin/env bash
# Reproduce and guard the PowerShell SQLite runtime verifiers so Windows workflow drift is caught
# before the hosted runner gate.

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
readonly environment_verifier_ps1="${repo_root}/scripts/verify-environment-configured-sqlite-runtime.ps1"
readonly source_checkout_verifier_ps1="${repo_root}/scripts/verify-source-checkout-sqlite-runtime.ps1"

[[ -f "${environment_verifier_ps1}" ]] || die \
    "missing PowerShell environment-configured runtime verifier at ${environment_verifier_ps1}"
[[ -f "${source_checkout_verifier_ps1}" ]] || die \
    "missing PowerShell source-checkout runtime verifier at ${source_checkout_verifier_ps1}"
grep -Fq 'direct-java-cli.ps1' "${environment_verifier_ps1}" || die \
    "PowerShell environment-configured runtime verifier no longer delegates to the direct-Java wrapper owner"
grep -Fq 'JAVA_TOOL_OPTIONS' "${environment_verifier_ps1}" || die \
    "PowerShell environment-configured runtime verifier no longer publishes the operator-trust JVM property seam"
grep -Fq 'source-checkout-cli.ps1' "${source_checkout_verifier_ps1}" || die \
    "PowerShell source-checkout runtime verifier no longer delegates to the source-checkout launcher owner"
grep -Fq 'verify-sqlite-runtime-contract.py' "${environment_verifier_ps1}" || die \
    "PowerShell environment-configured runtime verifier no longer delegates to the canonical Python verifier"
grep -Fq 'verify-sqlite-runtime-contract.py' "${source_checkout_verifier_ps1}" || die \
    "PowerShell source-checkout runtime verifier no longer delegates to the canonical Python verifier"
grep -Fq 'environment --output json' "${environment_verifier_ps1}" || die \
    "PowerShell environment-configured runtime verifier no longer probes the canonical environment command"
grep -Fq 'environment --output json' "${source_checkout_verifier_ps1}" || die \
    "PowerShell source-checkout runtime verifier no longer probes the canonical environment command"
if grep -Fq ':cli:run "--args=capabilities --output json"' "${environment_verifier_ps1}"; then
    die "PowerShell environment-configured runtime verifier regressed to the retired Gradle run seam"
fi

if ! command -v pwsh >/dev/null 2>&1; then
    printf 'PowerShell SQLite runtime verifier regression: skipped (pwsh unavailable)\n'
    exit 0
fi

pwsh -NoLogo -NoProfile -ExecutionPolicy Bypass -File "${environment_verifier_ps1}"
pwsh -NoLogo -NoProfile -ExecutionPolicy Bypass -File "${source_checkout_verifier_ps1}"

printf 'PowerShell SQLite runtime verifier regression: success\n'
