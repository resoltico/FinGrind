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
        while [[ $# -gt 0 ]]; do
            case "${1}" in
                --rm)
                    shift
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

        image_ref="${1:-}"
        shift || true
        subcommand="${1:-}"
        shift || true

        case "${subcommand}" in
            version)
                output_mode='human'
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
                entity_form=''
                functional_currency=''
                fiscal_year_start=''
                accounting_basis=''
                while [[ $# -gt 0 ]]; do
                    case "${1}" in
                        --book-file)
                            : > "$(translate_path "${2}")"
                            shift 2
                            ;;
                        --entity-name)
                            entity_name="${2}"
                            shift 2
                            ;;
                        --entity-form)
                            entity_form="${2}"
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
                        --accounting-basis)
                            accounting_basis="${2}"
                            shift 2
                            ;;
                        *)
                            shift
                            ;;
                    esac
                done
                [[ "${entity_name}" == 'Release Protocol Fixture' ]] || exit 1
                [[ "${entity_form}" == 'COMPANY' ]] || exit 1
                [[ "${functional_currency}" == 'EUR' ]] || exit 1
                [[ "${fiscal_year_start}" == '01-01' ]] || exit 1
                [[ "${accounting_basis}" == 'ACCRUAL' ]] || exit 1
                printf '{"status":"ok"}\n'
                ;;
            declare-account)
                printf '{"status":"ok","payload":{"accountCode":"1000"}}\n'
                ;;
            post-entry)
                request_file=''
                while [[ $# -gt 0 ]]; do
                    case "${1}" in
                        --request-file)
                            request_file="$(translate_path "${2}")"
                            shift 2
                            ;;
                        *)
                            shift
                            ;;
                    esac
                done
                [[ -n "${request_file}" ]] || exit 1
                grep -Fq '"postingKind": "STANDARD"' "${request_file}" || exit 1
                printf '{"status":"ok","payload":{"postingId":"01963c70-8d65-7b56-8a64-3c92745d8f72","idempotencyKey":"idem-basic-1","effectiveDate":"2026-04-08","recordedAt":"2026-04-08T12:00:00Z"}}\n'
                ;;
            trial-balance)
                pdf_out=''
                while [[ $# -gt 0 ]]; do
                    case "${1}" in
                        --pdf-out)
                            pdf_out="$(translate_path "${2}")"
                            shift 2
                            ;;
                        *)
                            shift
                            ;;
                    esac
                done

                if [[ -n "${pdf_out}" ]]; then
                    printf '%%PDF-' > "${pdf_out}"
                fi

                if [[ "${mode}" == 'bad-report' ]]; then
                    cat <<TEXT
Trial Balance
=============

Effective date to : 2026-04-08

Account | Name | Account type | Account role | Normal balance | Active | Currency | Debit total | Credit total | Net amount | Balance side
--------+------+--------------+--------------+----------------+--------+----------+-------------+--------------+------------+-------------
1000    | Cash | Asset        | Ordinary     | Debit          | Yes    | USD      |       10.00 |         0.00 |      10.00 | Debit
TEXT
                else
                    cat <<TEXT
Trial Balance
=============

Effective date to : 2026-04-08

Account | Name    | Account type | Account role | Normal balance | Active | Currency | Debit total | Credit total | Net amount | Balance side
--------+---------+--------------+--------------+----------------+--------+----------+-------------+--------------+------------+-------------
1000    | Cash    | Asset        | Ordinary     | Debit          | Yes    | EUR      |       10.00 |         0.00 | 10.00      | Debit
2000    | Revenue | Revenue      | Ordinary     | Credit         | Yes    | EUR      |        0.00 |        10.00 | 10.00      | Credit
TEXT
                fi
                ;;
            test)
                if [[ "${1:-}" != '-s' ]]; then
                    printf 'unsupported docker test flag: %s\n' "${1:-}" >&2
                    exit 1
                fi
                target_path="${2:-}"
                [[ -n "${target_path}" ]] || exit 1
                case "${target_path}" in
                    /opt/fingrind/lib/native/toolchain-fingerprint.json|/opt/fingrind/lib/native/build-contract.json)
                        if [[ "${mode}" == 'missing-provenance' ]]; then
                            exit 1
                        fi
                        ;;
                    *)
                        printf 'unsupported docker test path: %s\n' "${target_path}" >&2
                        exit 1
                        ;;
                esac
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

PATH="${fixture_root}/bin:${PATH}" FAKE_DOCKER_EXPECTED_VERSION='0.24.0' \
    bash "${verifier}" ghcr.io/resoltico/fingrind 0.24.0 >/dev/null

set +e
provenance_failure_output="$(
    PATH="${fixture_root}/bin:${PATH}" \
        FAKE_DOCKER_EXPECTED_VERSION='0.24.0' \
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
        FAKE_DOCKER_MODE='bad-report' \
        bash "${verifier}" ghcr.io/resoltico/fingrind 0.24.0 2>&1
)"
failure_exit=$?
set -e

if [[ ${failure_exit} -eq 0 ]]; then
    die "public container surface verifier accepted a broken trial-balance report"
fi
printf '%s\n' "${failure_output}" | grep -Fq \
    'published human trial balance did not report the expected Cash trial-balance row' || die \
    "public container surface verifier did not report the broken human trial-balance row"

printf 'public container surface verifier regression: success\n'
