#!/usr/bin/env bash
# Reproduce and guard the operator-side public container surface verifier with a fake anonymous
# Docker client so the release protocol cannot drift back to ambiguous manual parsing.

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
readonly verifier="${script_dir}/verify-public-container-surface.sh"
readonly verifier_support="${script_dir}/verify-public-container-book-surface-support.sh"
readonly fake_docker_attestation_support="${script_dir}/verify-public-container-surface-fake-docker-attestation-support.sh"
readonly fake_docker_legal_support="${script_dir}/verify-public-container-surface-fake-docker-legal-support.sh"
readonly retired_context_label='Starter'" chart       :"
readonly canonical_context_label='Seed'" template             :"

[[ -f "${verifier}" ]] || die "missing public container surface verifier"
[[ -f "${verifier_support}" ]] || die "missing public container surface verifier support"
[[ -f "${fake_docker_attestation_support}" ]] || die "missing public container fake-Docker attestation support"
[[ -f "${fake_docker_legal_support}" ]] || die "missing public container fake-Docker legal support"

if grep -Fq "${retired_context_label}" "${verifier_support}" "${BASH_SOURCE[0]}"; then
    die "public container verifier sources must not use the retired starter-chart label"
fi

if ! grep -Fq "${canonical_context_label}" "${verifier_support}" || ! grep -Fq "${canonical_context_label}" "${BASH_SOURCE[0]}"; then
    die "public container verifier sources must publish the canonical seed-template label"
fi

fixture_root="$(mktemp -d)"
cleanup() {
    rm -rf "${fixture_root}"
}
trap cleanup EXIT

mkdir -p "${fixture_root}/bin"

cat > "${fixture_root}/bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

mode="${FAKE_DOCKER_MODE:-success}"
expected_version="${FAKE_DOCKER_EXPECTED_VERSION:-0.24.0}"
expected_mount_user="${FAKE_DOCKER_EXPECTED_MOUNT_USER:-}"
expected_mount_workdir="${FAKE_DOCKER_EXPECTED_MOUNT_WORKDIR:-}"
mount_root=''

translate_path() {
    local container_path=$1
    case "${container_path}" in
        /work/*)
            printf '%s/%s\n' "${mount_root}" "${container_path#/work/}"
            ;;
        *)
            printf '%s\n' "${container_path}"
            ;;
    esac
}

private_directory_mode() {
    case "$(uname -s)" in
        Darwin|FreeBSD|NetBSD|OpenBSD) stat -f '%Lp' "$1" ;;
        *) stat -c '%a' "$1" ;;
    esac
}

require_founder_attestation_credential() {
    local attestation_custodian=$1
    local attestation_principal_id=$2
    local attestation_key_file=$3
    local attestation_passphrase_file=$4

    [[ "${attestation_custodian}" == 'file-pkcs8' ]] || exit 1
    [[ "${attestation_principal_id}" == '4bc17dd7-145f-4ea7-bb55-167ca2f6ac11' ]] || exit 1
    [[ -s "${attestation_key_file}" ]] || exit 1
    [[ -s "${attestation_passphrase_file}" ]] || exit 1
}

if [[ -n "${FAKE_DOCKER_ATTESTATION_SUPPORT:-}" ]]; then
    # shellcheck source=/dev/null
    source "${FAKE_DOCKER_ATTESTATION_SUPPORT}"
fi
if [[ -n "${FAKE_DOCKER_LEGAL_SUPPORT:-}" ]]; then
    # shellcheck source=/dev/null
    source "${FAKE_DOCKER_LEGAL_SUPPORT}"
fi

if [[ "${1:-}" == "--config" ]]; then
    shift 2
fi

command_name="${1:-}"
shift || true

case "${command_name}" in
    pull)
        image_ref="${1:-}"
        [[ -n "${image_ref}" ]] || exit 1
        printf 'Pulled %s\n' "${image_ref}"
        ;;
    image)
        [[ "${1:-}" == 'inspect' && "${2:-}" == '--format' ]] || exit 1
        fake_container_image_inspect "${3:-}"
        ;;
    run)
        entrypoint=''
        requested_user=''
        requested_workdir=''
        while [[ $# -gt 0 ]]; do
            case "${1}" in
                --rm)
                    shift
                    ;;
                --user)
                    requested_user="${2:-}"
                    shift 2
                    ;;
                -w|--workdir)
                    requested_workdir="${2:-}"
                    shift 2
                    ;;
                --entrypoint)
                    entrypoint="${2:-}"
                    shift 2
                    ;;
                -v)
                    mount_root="${2%%:*}"
                    shift 2
                    ;;
                *)
                    break
                    ;;
            esac
        done

        if [[ -n "${mount_root}" && -n "${expected_mount_user}" && "${requested_user}" != "${expected_mount_user}" ]]; then
            printf 'expected docker run --user %s for mounted workflow, got %s\n' \
                "${expected_mount_user}" "${requested_user:-<missing>}" >&2
            exit 1
        fi
        if [[ -n "${mount_root}" && -n "${expected_mount_workdir}" && "${requested_workdir}" != "${expected_mount_workdir}" ]]; then
            printf 'expected docker run working directory %s for mounted workflow, got %s\n' \
                "${expected_mount_workdir}" "${requested_workdir:-<missing>}" >&2
            exit 1
        fi

        image_ref="${1:-}"
        shift || true
        subcommand="${1:-}"
        shift || true

        if [[ -n "${entrypoint}" ]]; then
            [[ "${entrypoint}" == '/bin/sh' ]] || {
                printf 'unsupported docker entrypoint: %s\n' "${entrypoint}" >&2
                exit 1
            }
            [[ "${subcommand}" == '-c' ]] || {
                printf 'unsupported shell entrypoint command: %s\n' "${subcommand}" >&2
                exit 1
            }
            shell_command="${1:-}"
            if [[ "${shell_command}" =~ ^test[[:space:]]+-s[[:space:]]+\'?(/opt/fingrind/lib/native/(toolchain-fingerprint\.json|build-contract\.json))\'?$ ]]; then
                target_path="${BASH_REMATCH[1]}"
                if [[ "${mode}" == 'missing-provenance' ]]; then
                    exit 1
                fi
                case "${target_path}" in
                    /opt/fingrind/lib/native/toolchain-fingerprint.json|/opt/fingrind/lib/native/build-contract.json)
                        exit 0
                        ;;
                esac
            fi
            if fake_container_shell_probe "${shell_command}"; then
                exit 0
            fi
            printf 'unsupported shell probe command: %s\n' "${shell_command}" >&2
            exit 1
        fi

        case "${subcommand}" in
            version)
                output_mode='text'
                while [[ $# -gt 0 ]]; do
                    case "${1}" in
                        --output)
                            output_mode="${2}"
                            shift 2
                            ;;
                        *)
                            shift
                            ;;
                    esac
                done
                if [[ "${output_mode}" == 'json' ]]; then
                    version_value="${expected_version}"
                    if [[ "${mode}" == 'bad-version-latest' && "${image_ref}" == *:latest ]]; then
                        version_value='9.9.9'
                    fi
                    printf '{"status":"ok","application":"FinGrind","version":"%s"}\n' "${version_value}"
                else
                    cat <<TEXT
FinGrind
========

Version     : ${expected_version}
Description : Finance-grade bookkeeping kernel with an agent-first CLI and SQLite-first persistence
TEXT
                fi
                ;;
            generate-book-key-file)
                while [[ $# -gt 0 ]]; do
                    case "${1}" in
                        --new-book-key-file)
                            printf 'fake-key\n' > "$(translate_path "${2}")"
                            shift 2
                            ;;
                        *)
                            shift
                            ;;
                    esac
                done
                printf '{"status":"ok"}\n'
                ;;
            generate-attestation-key-file)
                fake_generate_attestation_key_file "$@"
                ;;
            open-book)
                fake_open_book "$@"
                ;;
            declare-account)
                request_file=''
                attestation_custodian=''
                attestation_principal_id=''
                attestation_key_file=''
                attestation_passphrase_file=''
                while [[ $# -gt 0 ]]; do
                    case "${1}" in
                        --book-file|--book-key-file)
                            shift 2
                            ;;
                        --request-file)
                            request_file="$(translate_path "${2}")"
                            shift 2
                            ;;
                        --attestation-custodian)
                            attestation_custodian="${2}"
                            shift 2
                            ;;
                        --attestation-principal-id)
                            attestation_principal_id="${2}"
                            shift 2
                            ;;
                        --attestation-key-file)
                            attestation_key_file="$(translate_path "${2}")"
                            shift 2
                            ;;
                        --attestation-passphrase-file)
                            attestation_passphrase_file="$(translate_path "${2}")"
                            shift 2
                            ;;
                        *)
                            printf 'unsupported declare-account argument: %s\n' "${1}" >&2
                            exit 1
                            ;;
                    esac
                done
                [[ -n "${request_file}" ]] || exit 1
                require_founder_attestation_credential \
                    "${attestation_custodian}" \
                    "${attestation_principal_id}" \
                    "${attestation_key_file}" \
                    "${attestation_passphrase_file}"
                if grep -Fq 'operating-bank' "${request_file}"; then
                    grep -Fq '"cashFlowAssetClassification":"CASH_AND_CASH_EQUIVALENT"' "${request_file}" || exit 1
                    printf '{"status":"ok","payload":{"accountCode":"operating-bank"}}\n'
                elif grep -Fq 'misc-revenue' "${request_file}"; then
                    printf '{"status":"ok","payload":{"accountCode":"misc-revenue"}}\n'
                else
                    printf 'unexpected declare-account fixture\n' >&2
                    exit 1
                fi
                ;;
            record-sale-settled|post-entry)
                request_file=''
                attestation_custodian=''
                attestation_principal_id=''
                attestation_key_file=''
                attestation_passphrase_file=''
                while [[ $# -gt 0 ]]; do
                    case "${1}" in
                        --book-file|--book-key-file|--output)
                            shift 2
                            ;;
                        --request-file)
                            request_file="$(translate_path "${2}")"
                            shift 2
                            ;;
                        --attestation-custodian)
                            attestation_custodian="${2}"
                            shift 2
                            ;;
                        --attestation-principal-id)
                            attestation_principal_id="${2}"
                            shift 2
                            ;;
                        --attestation-key-file)
                            attestation_key_file="$(translate_path "${2}")"
                            shift 2
                            ;;
                        --attestation-passphrase-file)
                            attestation_passphrase_file="$(translate_path "${2}")"
                            shift 2
                            ;;
                        *)
                            printf 'unsupported post-entry argument: %s\n' "${1}" >&2
                            exit 1
                            ;;
                    esac
                done
                [[ -n "${request_file}" ]] || exit 1
                require_founder_attestation_credential \
                    "${attestation_custodian}" \
                    "${attestation_principal_id}" \
                    "${attestation_key_file}" \
                    "${attestation_passphrase_file}"
                if [[ "${subcommand}" == 'record-sale-settled' ]]; then
                    grep -Fq '"entryKind": "SALE_SETTLED"' "${request_file}" || exit 1
                    grep -Fq '"cashAccountCode": "cash"' "${request_file}" || exit 1
                    grep -Fq '"revenueAccountCode": "service-revenue"' "${request_file}" || exit 1
                    grep -Fq '"sourceDocumentId": "release-protocol-cash-receipt-1"' "${request_file}" || exit 1
                    grep -Fq '"sourceDocumentType": "cash-receipt"' "${request_file}" || exit 1
                    grep -Fq '"documentDate": "2026-04-08"' "${request_file}" || exit 1
                    grep -Fq '"approvals": []' "${request_file}" || exit 1
                    printf '{"status":"ok","payload":{"postingId":"018f0000-0000-7000-8000-000000000002","idempotencyKey":"idem-basic-1","effectiveDate":"2026-04-08","recordedAt":"2026-04-08T12:00:00Z"}}\n'
                elif grep -Fq '"entryKind": "DIRECT_JOURNAL"' "${request_file}" \
                    && grep -Fq '"sourceDocumentType": "bank-deposit"' "${request_file}"; then
                    grep -Fq '"accountCode": "operating-bank"' "${request_file}" || exit 1
                    grep -Fq '"accountCode": "cash"' "${request_file}" || exit 1
                    grep -Fq '"side": "DEBIT"' "${request_file}" || exit 1
                    grep -Fq '"side": "CREDIT"' "${request_file}" || exit 1
                    grep -Fq '"approvals": []' "${request_file}" || exit 1
                    printf '{"status":"ok","payload":{"postingId":"018f0000-0000-7000-8000-000000000002","idempotencyKey":"release-protocol-idem-transfer","effectiveDate":"2026-04-08","recordedAt":"2026-04-08T12:05:00Z"}}\n'
                else
                    printf 'unexpected post-entry fixture\n' >&2
                    exit 1
                fi
                ;;
            get-posting)
                posting_id=''
                while [[ $# -gt 0 ]]; do
                    case "${1}" in
                        --book-file|--book-key-file|--output)
                            shift 2
                            ;;
                        --posting-id)
                            posting_id="${2:-}"
                            shift 2
                            ;;
                        *)
                            printf 'unsupported get-posting argument: %s\n' "${1}" >&2
                            exit 1
                            ;;
                    esac
                done
                [[ "${posting_id}" == '018f0000-0000-7000-8000-000000000002' ]] || exit 1
                cat <<JSON
{"status":"ok","payload":{"posting":{"postingId":"018f0000-0000-7000-8000-000000000002","postingKind":"STANDARD","postingOriginKind":"DIRECT_JOURNAL","reversalState":"direct","effectiveDate":"2026-04-08","recordedAt":"2026-04-08T12:05:00Z","commandId":"018f0000-0000-7000-8000-000000000001","idempotencyKey":"release-protocol-idem-transfer","causationId":"release-protocol-cause-transfer","sourceChannel":"CLI","evidence":{"sourceDocuments":[{"sourceDocumentId":"release-protocol-bank-deposit-1","sourceDocumentType":"bank-deposit","documentDate":"2026-04-08"}],"approvals":[]},"lines":[{"accountCode":"operating-bank","side":"DEBIT","amount":{"currencyCode":"EUR","minorUnits":"250"}},{"accountCode":"cash","side":"CREDIT","amount":{"currencyCode":"EUR","minorUnits":"250"}}]}}}
JSON
                ;;
            trial-balance)
                pdf_out=''
                while [[ $# -gt 0 ]]; do
                    case "${1}" in
                        --book-file|--book-key-file|--effective-date-as-of|--output)
                            shift 2
                            ;;
                        --pdf-out)
                            pdf_out="$(translate_path "${2}")"
                            shift 2
                            ;;
                        *)
                            printf 'unsupported trial-balance argument: %s\n' "${1}" >&2
                            exit 1
                            ;;
                    esac
                done

                if [[ -n "${pdf_out}" ]]; then
                    pdf_parent="$(dirname "${pdf_out}")"
                    [[ -d "${pdf_parent}" ]] || {
                        printf 'missing pre-created PDF report parent: %s\n' "${pdf_parent}" >&2
                        exit 1
                    }
                    pdf_parent_mode="$(private_directory_mode "${pdf_parent}")"
                    [[ "${pdf_parent_mode}" == '700' ]] || {
                        printf 'PDF report parent is not owner-only: %s (%s)\n' \
                            "${pdf_parent}" "${pdf_parent_mode}" >&2
                        exit 1
                    }
                    printf '%%PDF-' > "${pdf_out}"
                    if [[ "${mode}" == 'unreadable-pdf' ]]; then
                        chmod 000 "${pdf_out}"
                    fi
                    if [[ "${mode}" == 'pdf-stderr' ]]; then
                        printf 'simulated pdf export warning\n' >&2
                    fi
                    if [[ "${mode}" == 'bad-pdf-stdout' ]]; then
                        cat <<TEXT
Trial Balance
=============

As of         : 2026-04-08
Balance state : Balanced
TEXT
                    elif [[ "${mode}" == 'bad-pdf-path' ]]; then
                        cat <<TEXT
Artifact
========

Format : pdf
Path   : /tmp/not-the-mounted-report.pdf
TEXT
                    else
                        cat <<TEXT
Artifact
========

Format : pdf
Path   : <redacted>/work/private-reports/trial-balance.pdf
TEXT
                    fi
                elif [[ "${mode}" == 'bad-report' ]]; then
                    cat <<TEXT
Trial Balance
=============

As of         : 2026-04-08
Balance state : Balanced

Accounts
--------
cash | Cash
-----------
Type         : Asset
Normal       : Debit
Active       : Yes
Currency     : USD
Debit total  : EUR 10.00
Credit total : EUR 0.00
Net amount   : EUR 10.00
Balance side : Debit

service-revenue | Service Revenue
---------------------------------
Type         : Revenue
Normal       : Credit
Active       : Yes
Currency     : EUR
Debit total  : EUR 0.00
Credit total : EUR 10.00
Net amount   : EUR 10.00
Balance side : Credit

Current totals
--------------
Currency | Debit total | Credit total | Net amount | Balance side
---------+-------------+--------------+------------+-------------
EUR      |   EUR 10.00 |    EUR 10.00 |   EUR 0.00 | Zero

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
                else
                    cat <<TEXT
Trial Balance
=============

As of         : 2026-04-08
Balance state : Balanced

Accounts
--------
Account         | Name            | Currency | Debit total | Credit total | Net amount | Balance side
----------------+-----------------+----------+-------------+--------------+------------+-------------
cash            | Cash            | EUR      |   EUR 10.00 |     EUR 0.00 |  EUR 10.00 | Debit
service-revenue | Service Revenue | EUR      |    EUR 0.00 |    EUR 10.00 |  EUR 10.00 | Credit

Current totals
--------------
Currency | Debit total | Credit total | Net amount | Balance side
---------+-------------+--------------+------------+-------------
EUR      |   EUR 10.00 |    EUR 10.00 |   EUR 0.00 | Zero

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
                fi
                ;;
            *)
                printf 'unsupported docker run subcommand: %s\n' "${subcommand}" >&2
                exit 1
                ;;
        esac
        ;;
    *)
        printf 'unsupported docker command: %s\n' "${command_name}" >&2
        exit 1
        ;;
esac
EOF
chmod +x "${fixture_root}/bin/docker"

cp "${fake_docker_attestation_support}" "${fixture_root}/bin/fake-docker-attestation-support.sh"
cp "${fake_docker_legal_support}" "${fixture_root}/bin/fake-docker-legal-support.sh"
export FAKE_DOCKER_ATTESTATION_SUPPORT="${fixture_root}/bin/fake-docker-attestation-support.sh"
export FAKE_DOCKER_LEGAL_SUPPORT="${fixture_root}/bin/fake-docker-legal-support.sh"
fake_docker_expected_mount_user="$(id -u):$(id -g)"
export FAKE_DOCKER_EXPECTED_VERSION='0.24.0'
export FAKE_DOCKER_EXPECTED_MOUNT_USER="${fake_docker_expected_mount_user}"
export FAKE_DOCKER_EXPECTED_MOUNT_WORKDIR='/work'
export FAKE_DOCKER_RELEASE_PAYLOAD_ROOT="${script_dir}/.."

cat > "${fixture_root}/bin/head" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

if [[ "${FAKE_HEAD_MODE:-pass-through}" == 'deny-pdf-read' ]]; then
    target_path="${*: -1}"
    if [[ "${target_path}" == */trial-balance.pdf ]]; then
        printf "head: cannot open '%s' for reading: Permission denied\n" "${target_path}" >&2
        exit 1
    fi
fi

exec /usr/bin/head "$@"
EOF
chmod +x "${fixture_root}/bin/head"

assert_verifier_refusal() {
    local accepted_description=$1
    local expected_message=$2
    shift 2
    local output=''
    local exit_code=0

    set +e
    output="$(
        env "PATH=${fixture_root}/bin:${PATH}" "$@" \
            bash "${verifier}" ghcr.io/resoltico/fingrind 0.24.0 2>&1
    )"
    exit_code=$?
    set -e
    if [[ ${exit_code} -eq 0 ]]; then
        die "${accepted_description}"
    fi
    printf '%s\n' "${output}" | grep -Fq "${expected_message}" || die \
        "public container surface verifier did not report: ${expected_message}"
}

PATH="${fixture_root}/bin:${PATH}" bash "${verifier}" ghcr.io/resoltico/fingrind 0.24.0 >/dev/null

assert_verifier_refusal \
    "public container surface verifier accepted a container without native provenance files" \
    'did not expose a non-empty native toolchain fingerprint' \
    FAKE_DOCKER_MODE=missing-provenance

assert_verifier_refusal \
    "public container surface verifier accepted a broken trial-balance report" \
    'published text trial balance did not render the expected account summary rows' \
    FAKE_DOCKER_MODE=bad-report

assert_verifier_refusal \
    "public container surface verifier accepted an unreadable mounted PDF artifact" \
    'published container wrote the private report artifact without owner-readable mounted permissions' \
    FAKE_DOCKER_MODE=unreadable-pdf

assert_verifier_refusal \
    "public container surface verifier accepted a mounted PDF whose bytes could not be read" \
    'published container wrote the private report artifact without owner-readable mounted permissions' \
    FAKE_HEAD_MODE=deny-pdf-read

assert_verifier_refusal \
    "public container surface verifier accepted a text PDF export without the artifact confirmation block" \
    'published text PDF export did not emit one artifact confirmation heading' \
    FAKE_DOCKER_MODE=bad-pdf-stdout

assert_verifier_refusal \
    "public container surface verifier accepted a text PDF export with the wrong reported artifact path" \
    'published text PDF export did not report the expected public artifact path' \
    FAKE_DOCKER_MODE=bad-pdf-path

assert_verifier_refusal \
    "public container surface verifier accepted a successful PDF export that wrote stderr diagnostics" \
    'published successful PDF export wrote diagnostics on stderr: simulated pdf export warning' \
    FAKE_DOCKER_MODE=pdf-stderr

printf 'public container surface verifier regression: success\n'
