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
readonly fixture_business_activity_tag='consulting-services'
readonly fixture_functional_currency='EUR'
readonly fixture_fiscal_year_start='01-01'
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
{"accountCode":"1000","accountName":"Cash","accountType":"ASSET","accountRole":"ORDINARY","accountNodeKind":"POSTABLE","financialPositionLineClassification":"CURRENT_ASSET","profitAndLossLineClassification":null}
JSON

    cat > "${report_root}/declare-revenue.json" <<'JSON'
{"accountCode":"2000","accountName":"Revenue","accountType":"REVENUE","accountRole":"ORDINARY","accountNodeKind":"POSTABLE","financialPositionLineClassification":null,"profitAndLossLineClassification":"OPERATING_REVENUE"}
JSON

cat > "${report_root}/posting.json" <<'JSON'
{
  "entryKind": "CASH_REVENUE",
  "effectiveDate": "2026-04-08",
  "cashAccountCode": "1000",
  "revenueAccountCode": "2000",
  "amount": {
    "currencyCode": "EUR",
    "minorUnits": "1000"
  },
  "evidence": {
    "sourceDocuments": [
      {
        "sourceDocumentId": "release-protocol-cash-receipt-1",
        "sourceDocumentType": "cash-receipt",
        "documentDate": "2026-04-08",
        "capturedAt": "2026-04-08T10:15:30Z",
        "storageLocator": "vault://release-protocol/cash-receipt-1",
        "contentSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
      }
    ],
    "approvals": []
  },
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

verify_text_trial_balance() {
    local text_output=$1
    local status_block totals_block account_table_block context_block

    status_block="$(cat <<'TEXT'
As of         : 2026-04-08
Balance state : Balanced
TEXT
)"
    totals_block="$(cat <<'TEXT'
Current totals
--------------
Currency | Debit total | Credit total | Net amount | Balance side
---------+-------------+--------------+------------+-------------
EUR      |       10.00 |        10.00 |       0.00 | Zero
TEXT
)"

    account_table_block="$(cat <<'TEXT'
Accounts
--------
Account | Name    | Currency | Debit total | Credit total | Net amount | Balance side
--------+---------+----------+-------------+--------------+------------+-------------
1000    | Cash    | EUR      |       10.00 |         0.00 |      10.00 | Debit
2000    | Revenue | EUR      |        0.00 |        10.00 |      10.00 | Credit
TEXT
)"
    context_block="$(cat <<'TEXT'
Context
-------
Book             : Release Protocol Fixture | Currency EUR | FY 01-01
Posting coverage : All posting kinds
TEXT
)"

    require_match "${text_output}" '^Trial Balance$' || die \
        "published text trial balance did not render the report header"
    require_literal_block "${text_output}" "${status_block}" \
        || die "published text trial balance did not render the expected status block"
    require_literal_block "${text_output}" "${totals_block}" \
        || die "published text trial balance did not render the expected totals block"
    require_literal_block "${text_output}" "${account_table_block}" \
        || die "published text trial balance did not render the expected account summary rows"
    require_literal_block "${text_output}" "${context_block}" \
        || die "published text trial balance did not render the expected context block"
}

verify_mounted_book_surface() {
    local image_ref="${image_name}:${expected_version}"
    local text_output pdf_path="${report_root}/trial-balance.pdf" pdf_signature=''

    seed_public_fixture

    mounted_container_run "${image_ref}" \
        generate-book-key-file --book-key-file /work/book.key >/dev/null
    mounted_container_run "${image_ref}" \
        open-book \
        --book-file /work/book.sqlite \
        --book-key-file /work/book.key \
        --entity-name "${fixture_entity_name}" \
        --business-activity-tag "${fixture_business_activity_tag}" \
        --functional-currency "${fixture_functional_currency}" \
        --fiscal-year-start "${fixture_fiscal_year_start}" >/dev/null
    mounted_container_run "${image_ref}" \
        declare-account --book-file /work/book.sqlite --book-key-file /work/book.key --request-file /work/declare-cash.json >/dev/null
    mounted_container_run "${image_ref}" \
        declare-account --book-file /work/book.sqlite --book-key-file /work/book.key --request-file /work/declare-revenue.json >/dev/null
    mounted_container_run "${image_ref}" \
        post-entry --book-file /work/book.sqlite --book-key-file /work/book.key --request-file /work/posting.json >/dev/null

    text_output="$(
        mounted_container_run "${image_ref}" \
            trial-balance --book-file /work/book.sqlite --book-key-file /work/book.key \
            --effective-date-as-of 2026-04-08 --output text | tr -d '\r'
    )"
    verify_text_trial_balance "${text_output}"

    mounted_container_run "${image_ref}" \
        trial-balance --book-file /work/book.sqlite --book-key-file /work/book.key \
        --effective-date-as-of 2026-04-08 --output text --pdf-out /work/trial-balance.pdf >/dev/null

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
