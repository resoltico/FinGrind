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

[[ -f "${verifier}" ]] || die "missing public container surface verifier"

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
    run)
        entrypoint=''
        requested_user=''
        while [[ $# -gt 0 ]]; do
            case "${1}" in
                --rm)
                    shift
                    ;;
                --user)
                    requested_user="${2:-}"
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
                        --book-key-file)
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
            open-book)
                entity_name=''
                functional_currency=''
                fiscal_year_start=''
                book_file=''
                book_key_file=''
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
                        --functional-currency)
                            functional_currency="${2}"
                            shift 2
                            ;;
                        --fiscal-year-start)
                            fiscal_year_start="${2}"
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
                [[ "${functional_currency}" == 'EUR' ]] || exit 1
                [[ "${fiscal_year_start}" == '01-01' ]] || exit 1
                printf '{"status":"ok"}\n'
                ;;
            declare-account)
                request_file=''
                while [[ $# -gt 0 ]]; do
                    case "${1}" in
                        --book-file|--book-key-file)
                            shift 2
                            ;;
                        --request-file)
                            request_file="$(translate_path "${2}")"
                            shift 2
                            ;;
                        *)
                            printf 'unsupported declare-account argument: %s\n' "${1}" >&2
                            exit 1
                            ;;
                    esac
                done
                [[ -n "${request_file}" ]] || exit 1
                if grep -Fq 'cash-reserve' "${request_file}"; then
                    printf '{"status":"ok","payload":{"accountCode":"cash-reserve"}}\n'
                elif grep -Fq 'misc-revenue' "${request_file}"; then
                    printf '{"status":"ok","payload":{"accountCode":"misc-revenue"}}\n'
                else
                    printf 'unexpected declare-account fixture\n' >&2
                    exit 1
                fi
                ;;
            post-entry)
                request_file=''
                while [[ $# -gt 0 ]]; do
                    case "${1}" in
                        --book-file|--book-key-file)
                            shift 2
                            ;;
                        --request-file)
                            request_file="$(translate_path "${2}")"
                            shift 2
                            ;;
                        *)
                            printf 'unsupported post-entry argument: %s\n' "${1}" >&2
                            exit 1
                            ;;
                    esac
                done
                [[ -n "${request_file}" ]] || exit 1
                grep -Fq '"entryKind": "CASH_REVENUE"' "${request_file}" || exit 1
                grep -Fq '"cashAccountCode": "cash"' "${request_file}" || exit 1
                grep -Fq '"revenueAccountCode": "service-revenue"' "${request_file}" || exit 1
                grep -Fq '"sourceDocumentId": "release-protocol-cash-receipt-1"' "${request_file}" || exit 1
                grep -Fq '"sourceDocumentType": "cash-receipt"' "${request_file}" || exit 1
                grep -Fq '"documentDate": "2026-04-08"' "${request_file}" || exit 1
                grep -Fq '"capturedAt": "2026-04-08T10:15:30Z"' "${request_file}" || exit 1
                grep -Fq '"storageLocator": "vault://release-protocol/cash-receipt-1"' "${request_file}" || exit 1
                grep -Fq '"contentSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"' "${request_file}" || exit 1
                grep -Fq '"approvals": []' "${request_file}" || exit 1
                printf '{"status":"ok","payload":{"postingId":"01963c70-8d65-7b56-8a64-3c92745d8f72","idempotencyKey":"idem-basic-1","effectiveDate":"2026-04-08","recordedAt":"2026-04-08T12:00:00Z"}}\n'
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
                    printf '%%PDF-' > "${pdf_out}"
                    if [[ "${mode}" == 'unreadable-pdf' ]]; then
                        chmod 000 "${pdf_out}"
                    fi
                fi

                if [[ "${mode}" == 'bad-report' ]]; then
                    cat <<TEXT
Trial Balance
=============

As of         : 2026-04-08
Balance state : Balanced

Current totals
--------------
Currency | Debit total | Credit total | Net amount | Balance side
---------+-------------+--------------+------------+-------------
EUR      |       10.00 |        10.00 |       0.00 | Zero

Accounts
--------
Account         | Name            | Currency | Debit total | Credit total | Net amount | Balance side
----------------+-----------------+----------+-------------+--------------+------------+-------------
cash            | Cash            | USD      |       10.00 |         0.00 |      10.00 | Debit
service-revenue | Service Revenue | EUR      |        0.00 |        10.00 |      10.00 | Credit

Context
-------
Entity              : Release Protocol Fixture
Starter chart       : Owner-managed service starter chart
Functional currency : EUR
Fiscal year start   : 01-01
Posting coverage    : All posting kinds
TEXT
                else
                    cat <<TEXT
Trial Balance
=============

As of         : 2026-04-08
Balance state : Balanced

Current totals
--------------
Currency | Debit total | Credit total | Net amount | Balance side
---------+-------------+--------------+------------+-------------
EUR      |       10.00 |        10.00 |       0.00 | Zero

Accounts
--------
Account         | Name            | Currency | Debit total | Credit total | Net amount | Balance side
----------------+-----------------+----------+-------------+--------------+------------+-------------
cash            | Cash            | EUR      |       10.00 |         0.00 |      10.00 | Debit
service-revenue | Service Revenue | EUR      |        0.00 |        10.00 |      10.00 | Credit

Context
-------
Entity              : Release Protocol Fixture
Starter chart       : Owner-managed service starter chart
Functional currency : EUR
Fiscal year start   : 01-01
Posting coverage    : All posting kinds
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

PATH="${fixture_root}/bin:${PATH}" FAKE_DOCKER_EXPECTED_VERSION='0.24.0' \
    FAKE_DOCKER_EXPECTED_MOUNT_USER="$(id -u):$(id -g)" \
    bash "${verifier}" ghcr.io/resoltico/fingrind 0.24.0 >/dev/null

set +e
provenance_failure_output="$(
    PATH="${fixture_root}/bin:${PATH}" \
        FAKE_DOCKER_EXPECTED_VERSION='0.24.0' \
        FAKE_DOCKER_EXPECTED_MOUNT_USER="$(id -u):$(id -g)" \
        FAKE_DOCKER_MODE='missing-provenance' \
        bash "${verifier}" ghcr.io/resoltico/fingrind 0.24.0 2>&1
)"
provenance_failure_exit=$?
set -e

if [[ ${provenance_failure_exit} -eq 0 ]]; then
    die "public container surface verifier accepted a container without native provenance files"
fi
printf '%s\n' "${provenance_failure_output}" | grep -Fq \
    'did not expose a non-empty native toolchain fingerprint' || die \
    "public container surface verifier did not report the missing native provenance surface"

set +e
failure_output="$(
    PATH="${fixture_root}/bin:${PATH}" \
        FAKE_DOCKER_EXPECTED_VERSION='0.24.0' \
        FAKE_DOCKER_EXPECTED_MOUNT_USER="$(id -u):$(id -g)" \
        FAKE_DOCKER_MODE='bad-report' \
        bash "${verifier}" ghcr.io/resoltico/fingrind 0.24.0 2>&1
)"
failure_exit=$?
set -e

if [[ ${failure_exit} -eq 0 ]]; then
    die "public container surface verifier accepted a broken trial-balance report"
fi
printf '%s\n' "${failure_output}" | grep -Fq \
    'published text trial balance did not render the expected account summary rows' || die \
    "public container surface verifier did not report the broken text trial-balance row"

set +e
permission_failure_output="$(
    PATH="${fixture_root}/bin:${PATH}" \
        FAKE_DOCKER_EXPECTED_VERSION='0.24.0' \
        FAKE_DOCKER_EXPECTED_MOUNT_USER="$(id -u):$(id -g)" \
        FAKE_DOCKER_MODE='unreadable-pdf' \
        bash "${verifier}" ghcr.io/resoltico/fingrind 0.24.0 2>&1
)"
permission_failure_exit=$?
set -e

if [[ ${permission_failure_exit} -eq 0 ]]; then
    die "public container surface verifier accepted an unreadable mounted PDF artifact"
fi
printf '%s\n' "${permission_failure_output}" | grep -Fq \
    'published container wrote trial-balance.pdf without owner-readable mounted permissions' || die \
    "public container surface verifier did not report unreadable mounted PDF permissions"

set +e
head_failure_output="$(
    PATH="${fixture_root}/bin:${PATH}" \
        FAKE_DOCKER_EXPECTED_VERSION='0.24.0' \
        FAKE_DOCKER_EXPECTED_MOUNT_USER="$(id -u):$(id -g)" \
        FAKE_HEAD_MODE='deny-pdf-read' \
        bash "${verifier}" ghcr.io/resoltico/fingrind 0.24.0 2>&1
)"
head_failure_exit=$?
set -e

if [[ ${head_failure_exit} -eq 0 ]]; then
    die "public container surface verifier accepted a mounted PDF whose bytes could not be read"
fi
printf '%s\n' "${head_failure_output}" | grep -Fq \
    'published container wrote trial-balance.pdf without owner-readable mounted permissions' || die \
    "public container surface verifier misclassified one mounted PDF read failure"

printf 'public container surface verifier regression: success\n'
