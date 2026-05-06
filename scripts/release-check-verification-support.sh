#!/usr/bin/env bash
# Shared helpers for release-blocking GitHub check-run verification.

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    printf 'error: %s\n' \
        "release-check-verification-support.sh is a library and must be sourced by another script." >&2
    exit 1
fi

fingrind_trimmed_csv_entries() {
    local csv_value=$1
    while IFS= read -r raw_entry || [[ -n "${raw_entry}" ]]; do
        printf '%s\n' "${raw_entry}" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//'
    done < <(printf '%s' "${csv_value}" | tr ',' '\n')
}

fingrind_format_observed_checks() {
    local checks_tsv=$1
    if [[ -z "${checks_tsv}" ]]; then
        printf 'none'
        return
    fi
    printf '%s\n' "${checks_tsv}" | awk -F '\t' '
        BEGIN { separator = "" }
        {
            printf "%s%s[%s/%s]", separator, $1, $2, $3
            separator = ", "
        }
    '
}

fingrind_blocking_check_state() {
    local checks_tsv=$1
    local blocking_check_name=$2
    printf '%s\n' "${checks_tsv}" | awk -F '\t' -v target="${blocking_check_name}" '
        BEGIN {
            has_success = 0
            has_pending = 0
            has_failure = 0
        }
        $1 == target {
            if ($2 == "completed" && $3 == "success") {
                has_success = 1
            } else if ($2 == "completed") {
                has_failure = 1
            } else {
                has_pending = 1
            }
        }
        END {
            if (has_success) {
                print "success"
            } else if (has_pending) {
                print "pending"
            } else if (has_failure) {
                print "failure"
            } else {
                print "missing"
            }
        }
    '
}

fingrind_require_non_negative_integer() {
    local value=$1
    local name=$2
    [[ "${value}" =~ ^[0-9]+$ ]] || die "${name} must be a non-negative integer, got '${value}'"
}

fingrind_blocking_check_names_csv() {
    local verifier_blocking_checks_csv_value=$1
    local -n output_names_ref=$2
    output_names_ref=()
    while IFS= read -r trimmed_check_name || [[ -n "${trimmed_check_name}" ]]; do
        [[ -n "${trimmed_check_name}" ]] || continue
        output_names_ref+=("${trimmed_check_name}")
    done < <(fingrind_trimmed_csv_entries "${verifier_blocking_checks_csv_value}")
    ((${#output_names_ref[@]} > 0)) || die "no release-blocking checks configured"
}

fingrind_wait_for_release_blocking_checks() {
    local verifier_repo_full_name=$1
    local verifier_target_commit_sha=$2
    local verifier_blocking_checks_csv=$3
    local verifier_poll_interval_seconds=$4
    local verifier_timeout_seconds=$5
    local verifier_success_scope=$6
    local verifier_wait_scope=$7
    local verifier_failure_scope=$8

    fingrind_require_non_negative_integer \
        "${verifier_poll_interval_seconds}" \
        "FINGRIND_RELEASE_CHECK_POLL_INTERVAL_SECONDS"
    fingrind_require_non_negative_integer \
        "${verifier_timeout_seconds}" \
        "FINGRIND_RELEASE_CHECK_TIMEOUT_SECONDS"

    local -a blocking_check_names
    fingrind_blocking_check_names_csv "${verifier_blocking_checks_csv}" blocking_check_names

    local deadline_epoch="$((SECONDS + verifier_timeout_seconds))"
    while true; do
        local check_runs_tsv
        check_runs_tsv="$(
            gh api \
                "/repos/${verifier_repo_full_name}/commits/${verifier_target_commit_sha}/check-runs?per_page=100" \
                --jq '.check_runs[]? | [.name, .status, .conclusion] | @tsv'
        )"

        local -a pending_checks=()
        local -a failed_checks=()
        local blocking_check_name
        for blocking_check_name in "${blocking_check_names[@]}"; do
            case "$(fingrind_blocking_check_state "${check_runs_tsv}" "${blocking_check_name}")" in
                success) ;;
                pending|missing)
                    pending_checks+=("${blocking_check_name}")
                    ;;
                failure)
                    failed_checks+=("${blocking_check_name}")
                    ;;
                *)
                    die "unsupported release-blocking-check state for ${blocking_check_name}"
                    ;;
            esac
        done

        local observed_checks
        observed_checks="$(fingrind_format_observed_checks "${check_runs_tsv}")"

        if ((${#failed_checks[@]} > 0)); then
            die \
                "${verifier_failure_scope} failed for ${verifier_target_commit_sha}; release-blocking checks failed (${failed_checks[*]}). Observed check runs: ${observed_checks}"
        fi

        if ((${#pending_checks[@]} == 0)); then
            printf 'Verified %s at %s with release-blocking checks: %s\n' \
                "${verifier_success_scope}" \
                "${verifier_target_commit_sha}" \
                "$(IFS=', '; printf '%s' "${blocking_check_names[*]}")"
            return 0
        fi

        if ((SECONDS >= deadline_epoch)); then
            die \
                "timed out waiting for ${verifier_wait_scope} release-blocking checks (${pending_checks[*]}) on ${verifier_target_commit_sha}. Observed check runs: ${observed_checks}"
        fi

        local remaining_seconds="$((deadline_epoch - SECONDS))"
        if ((remaining_seconds < 0)); then
            remaining_seconds=0
        fi

        printf 'Waiting for %s on %s (%ss remaining). Pending: %s. Observed: %s\n' \
            "${verifier_wait_scope}" \
            "${verifier_target_commit_sha}" \
            "${remaining_seconds}" \
            "$(IFS=', '; printf '%s' "${pending_checks[*]}")" \
            "${observed_checks}"
        sleep "${verifier_poll_interval_seconds}"
    done
}
