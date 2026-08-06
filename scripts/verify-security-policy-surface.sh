#!/usr/bin/env bash
# Verify the live repository security-policy surface that checked-in docs promise publicly.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

resolve_repository_slug() {
    if [[ -n "${GITHUB_REPOSITORY:-}" ]]; then
        printf '%s\n' "${GITHUB_REPOSITORY}"
        return
    fi
    gh repo view --json nameWithOwner --jq '.nameWithOwner' 2>/dev/null
}

repo_full_name="${1:-}"
if [[ -z "${repo_full_name}" ]]; then
    repo_full_name="$(resolve_repository_slug)"
fi

[[ -n "${repo_full_name}" ]] || die "failed to resolve repository slug for security-policy verification"

private_reporting_enabled="$(
    gh api "/repos/${repo_full_name}/private-vulnerability-reporting" --jq '.enabled' 2>/dev/null
)" || die "failed to read GitHub private vulnerability reporting state for ${repo_full_name}"

[[ "${private_reporting_enabled}" == "true" ]] || die \
    "GitHub private vulnerability reporting is disabled for ${repo_full_name}"

dependabot_security_updates_status="$(
    gh api "/repos/${repo_full_name}" --jq '.security_and_analysis.dependabot_security_updates.status' 2>/dev/null
)" || die "failed to read GitHub Dependabot security-update state for ${repo_full_name}"

[[ "${dependabot_security_updates_status}" == "enabled" ]] || die \
    "GitHub Dependabot security updates are disabled for ${repo_full_name}"

dependabot_alerts_status="$(
    gh api "/repos/${repo_full_name}/dependabot/alerts?state=open&per_page=1" \
        --jq 'if type == "array" then "enabled" else "invalid" end' 2>/dev/null
)" || die "failed to prove GitHub Dependabot alerts are enabled for ${repo_full_name}"

[[ "${dependabot_alerts_status}" == "enabled" ]] || die \
    "GitHub Dependabot alerts are disabled for ${repo_full_name}"

printf '%s\n' "Verified repository security-policy surface: private vulnerability reporting, Dependabot alerts, and Dependabot security updates enabled for ${repo_full_name}"
