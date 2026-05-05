#!/usr/bin/env bash
# Shared canonical owner for the single required CI Gate contract used by release verifiers and
# branch-protection documentation.

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    printf 'error: %s\n' "release-check-support.sh is a library and must be sourced by another script." >&2
    exit 1
fi

fingrind_required_ci_check_name() {
    printf '%s\n' 'Gate'
}

fingrind_required_ci_checks_csv() {
    fingrind_required_ci_check_name
}

fingrind_required_ci_check_contexts_json() {
    printf '["%s"]\n' "$(fingrind_required_ci_check_name)"
}
