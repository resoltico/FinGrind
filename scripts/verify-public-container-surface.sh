#!/usr/bin/env bash
# Verify the public container surface from anonymous Docker state, including a mounted-book
# bookkeeping loop and PDF artifact generation.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

require_match() {
    local text=$1
    local pattern=$2

    printf '%s\n' "${text}" | grep -Eq "${pattern}"
}

require_literal_block() {
    local text=$1
    local block=$2

    [[ "${text}" == *"${block}"* ]]
}

readonly image_name="${1:-}"
readonly expected_version="${2:-}"
readonly retry_count="${FINGRIND_PUBLICATION_VERIFY_RETRIES:-12}"
readonly retry_delay_seconds="${FINGRIND_PUBLICATION_VERIFY_DELAY_SECONDS:-10}"
readonly fixture_entity_name='Release Protocol Fixture'
readonly fixture_entity_form='COMPANY'
readonly fixture_owner_model='MULTI_OWNER'
readonly fixture_reporting_obligation_status='INTERNAL_MANAGEMENT_ONLY'
readonly fixture_business_activity_tag='consulting-services'
readonly fixture_functional_currency='EUR'
readonly fixture_fiscal_year_start='01-01'
readonly fixture_accounting_basis='ACCRUAL'
readonly docker_run_user="$(id -u):$(id -g)"
docker_config_dir=''
report_root=''

[[ -n "${image_name}" ]] || die "image name is required"
[[ -n "${expected_version}" ]] || die "expected version is required"

cleanup() {
    [[ -n "${report_root}" ]] && rm -rf "${report_root}"
    [[ -n "${docker_config_dir}" ]] && rm -rf "${docker_config_dir}"
}

trap cleanup EXIT

docker_config_dir="$(mktemp -d)"
report_root="$(mktemp -d)"

anonymous_docker() {
    docker --config "${docker_config_dir}" "$@"
}

mounted_container_run() {
    local image_ref=$1
    shift

    anonymous_docker run --rm --user "${docker_run_user}" -v "${report_root}:/work" "${image_ref}" "$@"
}

container_shell() {
    local image_ref=$1
    local shell_command=$2

    anonymous_docker run --rm --entrypoint /bin/sh "${image_ref}" -c "${shell_command}"
}

require_nonempty_container_file() {
    local image_ref=$1
    local container_path=$2
    local file_label=$3

    if ! container_shell "${image_ref}" "test -s '${container_path}'"; then
        die "published container ${image_ref} did not expose a non-empty ${file_label} at ${container_path}"
    fi
}

verify_ref() {
    local tag_ref=$1
    local image_ref="${image_name}:${tag_ref}"
    local attempt version_output

    for ((attempt = 1; attempt <= retry_count; attempt++)); do
        if anonymous_docker pull "${image_ref}" >/dev/null 2>&1; then
            version_output="$(anonymous_docker run --rm "${image_ref}" version --output json 2>/dev/null | tr -d '\r')"
            if require_match "${version_output}" '"status"[[:space:]]*:[[:space:]]*"ok"' \
                && require_match "${version_output}" '"application"[[:space:]]*:[[:space:]]*"FinGrind"' \
                && require_match "${version_output}" "\"version\"[[:space:]]*:[[:space:]]*\"${expected_version}\""; then
                printf 'Verified published container: %s\n' "${image_ref}"
                return
            fi
        fi

        if (( attempt < retry_count )); then
            sleep "${retry_delay_seconds}"
        fi
    done

    die "published container ${image_ref} did not report FinGrind version ${expected_version}"
}

seed_public_fixture() {
    cat > "${report_root}/declare-cash.json" <<'JSON'
{"accountCode":"1000","accountName":"Cash","accountType":"ASSET","accountRole":"ORDINARY","financialPositionLineClassification":"CURRENT_ASSET","profitAndLossLineClassification":null}
JSON

    cat > "${report_root}/declare-revenue.json" <<'JSON'
{"accountCode":"2000","accountName":"Revenue","accountType":"REVENUE","accountRole":"ORDINARY","financialPositionLineClassification":null,"profitAndLossLineClassification":"OPERATING_REVENUE"}
JSON

cat > "${report_root}/posting.json" <<'JSON'
{
  "postingKind": "STANDARD",
  "effectiveDate": "2026-04-08",
  "evidence": {
    "sourceDocuments": [
      {
        "sourceDocumentId": "release-protocol-invoice-1",
        "sourceDocumentType": "invoice"
      }
    ],
    "approvals": []
  },
  "lines": [
    {
      "accountCode":"1000",
      "side":"DEBIT",
      "amount":{"currencyCode":"EUR","minorUnits":"1000"}
    },
    {
      "accountCode":"2000",
      "side":"CREDIT",
      "amount":{"currencyCode":"EUR","minorUnits":"1000"}
    }
  ],
  "provenance": {
    "actorId": "release-protocol",
    "actorType": "AGENT",
    "commandId": "release-protocol-posting",
    "idempotencyKey": "release-protocol-idem-1",
    "causationId": "release-protocol-cause-1"
  }
}
JSON
}

verify_human_trial_balance() {
    local human_output=$1
    local cash_block revenue_block

    cash_block="$(cat <<'TEXT'
1000 | Cash
-----------
Account type   : Asset
Account role   : Ordinary
Normal balance : Debit
Active         : Yes
Currency       : EUR
Debit total    : 10.00
Credit total   : 0.00
Net amount     : 10.00
Balance side   : Debit
TEXT
)"
    revenue_block="$(cat <<'TEXT'
2000 | Revenue
--------------
Account type   : Revenue
Account role   : Ordinary
Normal balance : Credit
Active         : Yes
Currency       : EUR
Debit total    : 0.00
Credit total   : 10.00
Net amount     : 10.00
Balance side   : Credit
TEXT
)"

    require_match "${human_output}" '^Trial Balance$' || die \
        "published human trial balance did not render the report header"
    require_match "${human_output}" '^Entity[[:space:]]+:[[:space:]]+Release Protocol Fixture$' || die \
        "published human trial balance did not render the expected entity header"
    require_literal_block "${human_output}" "${cash_block}" \
        || die "published human trial balance did not report the expected Cash trial-balance row"
    require_literal_block "${human_output}" "${revenue_block}" \
        || die "published human trial balance did not report the expected Revenue trial-balance row"
}

verify_mounted_book_surface() {
    local image_ref="${image_name}:${expected_version}"
    local human_output pdf_path="${report_root}/trial-balance.pdf" pdf_signature=''

    seed_public_fixture

    mounted_container_run "${image_ref}" \
        generate-book-key-file --book-key-file /work/book.key >/dev/null
    mounted_container_run "${image_ref}" \
        open-book \
        --book-file /work/book.sqlite \
        --book-key-file /work/book.key \
        --entity-name "${fixture_entity_name}" \
        --entity-form "${fixture_entity_form}" \
        --owner-model "${fixture_owner_model}" \
        --reporting-obligation-status "${fixture_reporting_obligation_status}" \
        --business-activity-tag "${fixture_business_activity_tag}" \
        --functional-currency "${fixture_functional_currency}" \
        --fiscal-year-start "${fixture_fiscal_year_start}" \
        --accounting-basis "${fixture_accounting_basis}" >/dev/null
    mounted_container_run "${image_ref}" \
        declare-account --book-file /work/book.sqlite --book-key-file /work/book.key --request-file /work/declare-cash.json >/dev/null
    mounted_container_run "${image_ref}" \
        declare-account --book-file /work/book.sqlite --book-key-file /work/book.key --request-file /work/declare-revenue.json >/dev/null
    mounted_container_run "${image_ref}" \
        post-entry --book-file /work/book.sqlite --book-key-file /work/book.key --request-file /work/posting.json >/dev/null

    human_output="$(
        mounted_container_run "${image_ref}" \
            trial-balance --book-file /work/book.sqlite --book-key-file /work/book.key \
            --effective-date-to 2026-04-08 --output human | tr -d '\r'
    )"
    verify_human_trial_balance "${human_output}"

    mounted_container_run "${image_ref}" \
        trial-balance --book-file /work/book.sqlite --book-key-file /work/book.key \
        --effective-date-to 2026-04-08 --output human --pdf-out /work/trial-balance.pdf >/dev/null

    [[ -f "${pdf_path}" ]] || die "published container did not write trial-balance.pdf"
    [[ -r "${pdf_path}" ]] || die \
        "published container wrote trial-balance.pdf without host-readable permissions"
    if ! pdf_signature="$(head -c 5 "${pdf_path}")"; then
        die "published container wrote trial-balance.pdf without host-readable permissions"
    fi
    [[ "${pdf_signature}" == '%PDF-' ]] || die \
        "published container wrote a non-PDF trial-balance artifact"

    printf 'Verified mounted public workflow: %s\n' "${image_ref}"
}

verify_native_provenance_surface() {
    local image_ref="${image_name}:${expected_version}"

    require_nonempty_container_file \
        "${image_ref}" \
        /opt/fingrind/lib/native/toolchain-fingerprint.json \
        "native toolchain fingerprint"
    require_nonempty_container_file \
        "${image_ref}" \
        /opt/fingrind/lib/native/build-contract.json \
        "native build contract"

    printf 'Verified native provenance surface: %s\n' "${image_ref}"
}

verify_ref "${expected_version}"
verify_ref latest
verify_native_provenance_surface
verify_mounted_book_surface
