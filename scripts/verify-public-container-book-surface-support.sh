#!/usr/bin/env bash
# Shared mounted-book and report-surface verification helpers for public container publication.

verify_text_trial_balance() {
    local text_output=$1
    local status_block accounts_block totals_block context_block

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
EUR      |   EUR 10.00 |    EUR 10.00 |   EUR 0.00 | Zero
TEXT
)"

    accounts_block="$(cat <<'TEXT'
Account         | Name            | Currency | Debit total | Credit total | Net amount | Balance side
----------------+-----------------+----------+-------------+--------------+------------+-------------
cash            | Cash            | EUR      |   EUR 10.00 |     EUR 0.00 |  EUR 10.00 | Debit
service-revenue | Service Revenue | EUR      |    EUR 0.00 |    EUR 10.00 |  EUR 10.00 | Credit
TEXT
)"
    context_block="$(cat <<'TEXT'
Context
-------
Entity                    : Release Protocol Fixture
Seed template             : Owner-managed service seed template
Accounting basis          : Cash basis
Functional currency       : EUR
Fiscal year start         : 01-01
Book start effective date : 2026-01-01
Posting coverage          : All posting kinds
As of                     : 2026-04-08
TEXT
)"

    require_match "${text_output}" '^Trial Balance$' || die \
        "published text trial balance did not render the report header"
    require_literal_block "${text_output}" "${status_block}" \
        || die "published text trial balance did not render the expected status block"
    require_literal_block "${text_output}" "${totals_block}" \
        || die "published text trial balance did not render the expected totals block"
    require_literal_block "${text_output}" "${accounts_block}" \
        || die "published text trial balance did not render the expected account summary rows"
    require_literal_block "${text_output}" "${context_block}" \
        || die "published text trial balance did not render the expected context block"
}

count_pattern_matches() {
    local text=$1
    local pattern=$2

    printf '%s\n' "${text}" | grep -Ec "${pattern}" || true
}

extract_artifact_confirmation_path() {
    local text=$1

    printf '%s\n' "${text}" | sed -n 's/^Path[[:space:]]*:[[:space:]]*//p' | head -n1
}

artifact_path_matches() {
    local reported_path=$1
    local expected_path=$2

    [[ -n "${reported_path}" ]] || return 1
    case "${reported_path}" in
        "${expected_path}"|"<redacted>${expected_path}")
            return 0
            ;;
    esac
    return 1
}

verify_text_pdf_artifact_output() {
    local text_output=$1
    local reported_path=$2
    local artifact_heading_count artifact_format_count artifact_path_count reported_artifact_path

    artifact_heading_count="$(count_pattern_matches "${text_output}" '^Artifact$')"
    [[ "${artifact_heading_count}" == '1' ]] || die \
        "published text PDF export did not emit one artifact confirmation heading"
    artifact_format_count="$(count_pattern_matches "${text_output}" '^Format[[:space:]]+:[[:space:]]+pdf$')"
    [[ "${artifact_format_count}" == '1' ]] || die \
        "published text PDF export did not emit one PDF artifact format line"
    artifact_path_count="$(count_pattern_matches "${text_output}" '^Path[[:space:]]+:[[:space:]]+.+$')"
    [[ "${artifact_path_count}" == '1' ]] || die \
        "published text PDF export did not emit one artifact confirmation path line"
    reported_artifact_path="$(extract_artifact_confirmation_path "${text_output}")"
    artifact_path_matches "${reported_artifact_path}" "${reported_path}" || die \
        "published text PDF export did not report the expected public artifact path"
    ! require_match "${text_output}" '^Trial Balance$' || die \
        "published text PDF export leaked the full trial-balance report body onto stdout"
}

verify_mounted_book_surface() {
    local image_ref="${image_name}:${primary_tag_ref}"
    local text_output pdf_stdout pdf_stderr pdf_stdout_path pdf_stderr_path
    local pdf_parent="${report_root}/private-reports"
    local pdf_path="${pdf_parent}/trial-balance.pdf" pdf_signature=''
    local raw_post_output raw_posting_id raw_get_output
    local founder_key_path="${report_root}/founder.fgatk"
    local founder_passphrase_path="${report_root}/founder.passphrase"
    local -a founder_credential_arguments=(
        --attestation-custodian "${fixture_attestation_custodian}"
        --attestation-principal-id "${fixture_attestation_founder_principal_id}"
        --attestation-key-file /work/founder.fgatk
        --attestation-passphrase-file /work/founder.passphrase
    )

    seed_public_fixture
    mkdir -p "${pdf_parent}"
    chmod 700 "${pdf_parent}"

    mounted_container_run "${image_ref}" \
        generate-book-key-file --new-book-key-file /work/book.key >/dev/null
    printf '%s\n' "${fixture_attestation_founder_passphrase}" > "${founder_passphrase_path}"
    chmod 600 "${founder_passphrase_path}"
    mounted_container_run "${image_ref}" \
        generate-attestation-key-file \
        --attestation-custodian "${fixture_attestation_custodian}" \
        --new-attestation-key-file /work/founder.fgatk \
        --attestation-passphrase-file /work/founder.passphrase >/dev/null
    [[ -s "${founder_key_path}" ]] || die \
        "published container did not create the mounted attestation founder credential"
    mounted_container_run "${image_ref}" \
        open-book \
        --book-file /work/book.sqlite \
        --book-key-file /work/book.key \
        --entity-name "${fixture_entity_name}" \
        --book-template-id OWNER_MANAGED_SERVICE \
        --accounting-basis CASH \
        --functional-currency "${fixture_functional_currency}" \
        --fiscal-year-start "${fixture_fiscal_year_start}" --book-start-effective-date 2026-01-01 \
        --attestation-custodian "${fixture_attestation_custodian}" \
        --attestation-founder-principal-id "${fixture_attestation_founder_principal_id}" \
        --attestation-founder-key-file /work/founder.fgatk \
        --attestation-founder-passphrase-file /work/founder.passphrase >/dev/null
    mounted_container_run "${image_ref}" \
        declare-account --book-file /work/book.sqlite --book-key-file /work/book.key --request-file /work/declare-bank.json \
        "${founder_credential_arguments[@]}" >/dev/null
    mounted_container_run "${image_ref}" \
        declare-account --book-file /work/book.sqlite --book-key-file /work/book.key --request-file /work/declare-revenue.json \
        "${founder_credential_arguments[@]}" >/dev/null
    mounted_container_run "${image_ref}" \
        record-sale-settled --book-file /work/book.sqlite --book-key-file /work/book.key --request-file /work/posting.json \
        "${founder_credential_arguments[@]}" >/dev/null

    text_output="$(
        mounted_container_run "${image_ref}" \
            trial-balance --book-file /work/book.sqlite --book-key-file /work/book.key \
            --effective-date-as-of 2026-04-08 --output text | tr -d '\r'
    )"
    verify_text_trial_balance "${text_output}"

    pdf_stdout_path="$(mktemp "${report_root}/trial-balance-stdout.XXXXXX")"
    pdf_stderr_path="$(mktemp "${report_root}/trial-balance-stderr.XXXXXX")"
    mounted_container_run_split_streams \
        "${image_ref}" \
        "${pdf_stdout_path}" \
        "${pdf_stderr_path}" \
        trial-balance --book-file /work/book.sqlite --book-key-file /work/book.key \
        --effective-date-as-of 2026-04-08 --output text \
        --pdf-out /work/private-reports/trial-balance.pdf
    pdf_stdout="$(tr -d '\r' < "${pdf_stdout_path}")"
    pdf_stderr="$(tr -d '\r' < "${pdf_stderr_path}")"
    rm -f "${pdf_stdout_path}" "${pdf_stderr_path}"
    [[ -z "${pdf_stderr}" ]] || die \
        "published successful PDF export wrote diagnostics on stderr: ${pdf_stderr}"
    verify_text_pdf_artifact_output "${pdf_stdout}" '/work/private-reports/trial-balance.pdf'

    [[ -f "${pdf_path}" ]] || die "published container did not write the private report artifact"
    [[ -r "${pdf_path}" ]] || die \
        "published container wrote the private report artifact without owner-readable mounted permissions"
    if ! pdf_signature="$(head -c 5 "${pdf_path}")"; then
        die "published container wrote the private report artifact without owner-readable mounted permissions"
    fi
    [[ "${pdf_signature}" == '%PDF-' ]] || die \
        "published container wrote a non-PDF private report artifact"

    raw_post_output="$(
        mounted_container_run "${image_ref}" \
            post-entry --book-file /work/book.sqlite --book-key-file /work/book.key \
            --request-file /work/raw-transfer.json --output json "${founder_credential_arguments[@]}" | tr -d '\r'
    )"
    require_match "${raw_post_output}" '"status"[[:space:]]*:[[:space:]]*"ok"' || die \
        "published container did not commit the direct journal transfer fixture"
    raw_posting_id="$(
        printf '%s\n' "${raw_post_output}" \
            | sed -n 's/.*"postingId"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
    )"
    [[ -n "${raw_posting_id}" ]] || die \
        "published container did not report the direct journal transfer posting id"

    raw_get_output="$(
        mounted_container_run "${image_ref}" \
            get-posting --book-file /work/book.sqlite --book-key-file /work/book.key \
            --posting-id "${raw_posting_id}" --output json | tr -d '\r'
    )"
    require_match "${raw_get_output}" '"postingOriginKind"[[:space:]]*:[[:space:]]*"DIRECT_JOURNAL"' \
        || die "published container did not read back the direct journal posting as DIRECT_JOURNAL origin"
    require_match "${raw_get_output}" '"sourceDocumentType"[[:space:]]*:[[:space:]]*"bank-deposit"' \
        || die "published container did not preserve direct journal evidence on read-back"
    require_match "${raw_get_output}" '"accountCode"[[:space:]]*:[[:space:]]*"operating-bank"' \
        || die "published container did not preserve the declared bank transfer destination account"
    require_match "${raw_get_output}" '"accountCode"[[:space:]]*:[[:space:]]*"cash"' \
        || die "published container did not preserve the starter cash account in the direct journal"

    printf 'Verified mounted public workflow: %s\n' "${image_ref}"
}
