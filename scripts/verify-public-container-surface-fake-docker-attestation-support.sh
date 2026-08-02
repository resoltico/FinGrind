#!/usr/bin/env bash
# Attestation command fixtures owned separately from the public-container test harness.

fake_generate_attestation_key_file() {
    local attestation_custodian=''
    local attestation_key_file=''
    local attestation_passphrase_file=''

    while [[ $# -gt 0 ]]; do
        case "${1}" in
            --attestation-custodian)
                attestation_custodian="${2}"
                shift 2
                ;;
            --new-attestation-key-file)
                attestation_key_file="$(translate_path "${2}")"
                shift 2
                ;;
            --attestation-passphrase-file)
                attestation_passphrase_file="$(translate_path "${2}")"
                shift 2
                ;;
            *)
                printf 'unsupported generate-attestation-key-file argument: %s\n' "${1}" >&2
                exit 1
                ;;
        esac
    done
    [[ "${attestation_custodian}" == 'file-pkcs8' ]] || exit 1
    [[ -n "${attestation_key_file}" ]] || exit 1
    [[ -s "${attestation_passphrase_file}" ]] || exit 1
    printf 'fake-attestation-key\n' > "${attestation_key_file}"
    printf '{"status":"ok"}\n'
}

fake_open_book() {
    local entity_name=''
    local book_template_id=''
    local accounting_basis=''
    local functional_currency=''
    local fiscal_year_start=''
    local book_start_effective_date=''
    local book_file=''
    local book_key_file=''
    local attestation_custodian=''
    local attestation_founder_principal_id=''
    local attestation_founder_key_file=''
    local attestation_founder_passphrase_file=''

    while [[ $# -gt 0 ]]; do
        case "${1}" in
            --book-file)
                book_file="$(translate_path "${2}")"
                : > "${book_file}"
                shift 2
                ;;
            --book-key-file)
                book_key_file="$(translate_path "${2}")"
                shift 2
                ;;
            --entity-name)
                entity_name="${2}"
                shift 2
                ;;
            --book-template-id)
                book_template_id="${2}"
                shift 2
                ;;
            --accounting-basis)
                accounting_basis="${2}"
                shift 2
                ;;
            --functional-currency)
                functional_currency="${2}"
                shift 2
                ;;
            --fiscal-year-start)
                fiscal_year_start="${2}"
                shift 2
                ;;
            --book-start-effective-date)
                book_start_effective_date="${2}"
                shift 2
                ;;
            --attestation-custodian)
                attestation_custodian="${2}"
                shift 2
                ;;
            --attestation-founder-principal-id)
                attestation_founder_principal_id="${2}"
                shift 2
                ;;
            --attestation-founder-key-file)
                attestation_founder_key_file="$(translate_path "${2}")"
                shift 2
                ;;
            --attestation-founder-passphrase-file)
                attestation_founder_passphrase_file="$(translate_path "${2}")"
                shift 2
                ;;
            *)
                printf 'unsupported open-book argument: %s\n' "${1}" >&2
                exit 1
                ;;
        esac
    done
    [[ -n "${book_file}" ]] || exit 1
    [[ -n "${book_key_file}" ]] || exit 1
    [[ "${entity_name}" == 'Release Protocol Fixture' ]] || exit 1
    [[ "${book_template_id}" == 'OWNER_MANAGED_SERVICE' ]] || exit 1
    [[ "${accounting_basis}" == 'CASH' ]] || exit 1
    [[ "${functional_currency}" == 'EUR' ]] || exit 1
    [[ "${fiscal_year_start}" == '01-01' ]] || exit 1
    [[ "${book_start_effective_date}" == '2026-01-01' ]] || exit 1
    [[ "${attestation_custodian}" == 'file-pkcs8' ]] || exit 1
    [[ "${attestation_founder_principal_id}" == '4bc17dd7-145f-4ea7-bb55-167ca2f6ac11' ]] || exit 1
    [[ -s "${attestation_founder_key_file}" ]] || exit 1
    [[ -s "${attestation_founder_passphrase_file}" ]] || exit 1
    printf '{"status":"ok"}\n'
}
