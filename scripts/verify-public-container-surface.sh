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
readonly explicit_tag_ref="${3:-}"
readonly retry_count="${FINGRIND_PUBLICATION_VERIFY_RETRIES:-12}"
readonly retry_delay_seconds="${FINGRIND_PUBLICATION_VERIFY_DELAY_SECONDS:-10}"
readonly verify_latest_ref="${FINGRIND_VERIFY_PUBLIC_CONTAINER_LATEST:-true}"
readonly fixture_entity_name='Release Protocol Fixture'
readonly fixture_functional_currency='EUR'
readonly fixture_fiscal_year_start='01-01'
readonly fixture_attestation_custodian='file-pkcs8'
readonly fixture_attestation_founder_principal_id='4bc17dd7-145f-4ea7-bb55-167ca2f6ac11'
readonly fixture_attestation_founder_passphrase='release-protocol-fixture-attestation-passphrase'
readonly docker_run_user="$(id -u):$(id -g)"
docker_config_dir=''
report_root=''
readonly container_support_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly release_payload_root="${FINGRIND_RELEASE_PAYLOAD_ROOT:-$(pwd -P)}"
readonly reviewed_alpine_package_lock="${release_payload_root}/gradle/alpine-container-packages.lock.tsv"
primary_tag_ref=''

[[ -n "${image_name}" ]] || die "image name is required"
[[ -n "${expected_version}" ]] || die "expected version is required"
[[ "${verify_latest_ref}" == "true" || "${verify_latest_ref}" == "false" ]] || die \
    "FINGRIND_VERIFY_PUBLIC_CONTAINER_LATEST must be true or false"

source "${container_support_dir}/verify-public-container-run-support.sh"
source "${container_support_dir}/verify-public-container-book-surface-support.sh"

cleanup() {
    [[ -n "${report_root}" ]] && rm -rf "${report_root}"
    [[ -n "${docker_config_dir}" ]] && rm -rf "${docker_config_dir}"
}

trap cleanup EXIT

docker_config_dir="$(mktemp -d)"
report_root="$(mktemp -d)"

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

verify_native_provenance_surface() {
    local image_ref="${image_name}:${primary_tag_ref}"

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

verify_legal_surface() {
    local image_ref="${image_name}:${primary_tag_ref}"
    local actual_contents
    local expected_contents
    local legal_name
    local legal_path
    local binary_archive_record
    local documentation_label
    local expected_revision
    local revision_label
    local source_label
    local version_label
    [[ -s "${reviewed_alpine_package_lock}" ]] || die \
        "missing candidate-owned Alpine package lock at ${reviewed_alpine_package_lock}"
    expected_revision="$(git -C "${release_payload_root}" rev-parse HEAD)"
    [[ "${expected_revision}" =~ ^[0-9a-f]{40}$ ]] || die \
        "candidate release revision was not one full Git commit"
    for legal_path in \
        /opt/fingrind/doc/LICENSE \
        /opt/fingrind/doc/LICENSE-ALPINE-CONTAINER-COMPONENTS \
        /opt/fingrind/doc/LICENSE-APACHE-2.0 \
        /opt/fingrind/doc/LICENSE-CC0-1.0 \
        /opt/fingrind/doc/LICENSE-GPL-2.0 \
        /opt/fingrind/doc/LICENSE-MPL-2.0 \
        /opt/fingrind/doc/LICENSE-SIL-OFL-1.1 \
        /opt/fingrind/doc/LICENSE-SQLITE3MULTIPLECIPHERS \
        /opt/fingrind/doc/LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY \
        /opt/fingrind/doc/NOTICE \
        /opt/fingrind/doc/NOTICE-ZULU-26.32.203 \
        /opt/fingrind/doc/PATENTS.md \
        /opt/fingrind/doc/SOURCE_OFFER.md \
        /opt/fingrind/doc/ALPINE-PACKAGES.tsv \
        /opt/fingrind/doc/ALPINE-PACKAGES.lock.tsv \
        /opt/fingrind/runtime/release \
        /opt/fingrind/runtime/provenance/source-jdk-release \
        /opt/fingrind/runtime/provenance/input-jdk-binary-archive.sha256 \
        /opt/fingrind/runtime/provenance/requested-modules.txt \
        /opt/fingrind/runtime/legal/java.base/LICENSE \
        /opt/fingrind/runtime/legal/java.base/ADDITIONAL_LICENSE_INFO \
        /opt/fingrind/runtime/legal/java.base/ASSEMBLY_EXCEPTION \
        /opt/fingrind/runtime/legal/INDEX.sha256
    do
        require_nonempty_container_file "${image_ref}" "${legal_path}" "legal payload"
    done
    for legal_name in \
        LICENSE \
        LICENSE-ALPINE-CONTAINER-COMPONENTS \
        LICENSE-APACHE-2.0 \
        LICENSE-CC0-1.0 \
        LICENSE-GPL-2.0 \
        LICENSE-MPL-2.0 \
        LICENSE-SIL-OFL-1.1 \
        LICENSE-SQLITE3MULTIPLECIPHERS \
        LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY \
        NOTICE \
        NOTICE-ZULU-26.32.203 \
        PATENTS.md \
        SOURCE_OFFER.md
    do
        actual_contents="$(container_shell "${image_ref}" "cat '/opt/fingrind/doc/${legal_name}'")"
        expected_contents="$(cat "${release_payload_root}/${legal_name}")"
        [[ "${actual_contents}" == "${expected_contents}" ]] || die \
            "published container ${image_ref} legal payload differed from candidate ${legal_name}"
    done
    actual_contents="$(container_shell "${image_ref}" 'cat /opt/fingrind/doc/ALPINE-PACKAGES.tsv')"
    expected_contents="$(cat "${reviewed_alpine_package_lock}")"
    [[ "${actual_contents}" == "${expected_contents}" ]] || die \
        "published container ${image_ref} Alpine inventory differed from the candidate lock"
    container_shell "${image_ref}" "grep -Fq 'Olivier Gay' /opt/fingrind/doc/LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY" || die \
        "published container ${image_ref} omitted the SQLite3MC embedded-code notices"
    container_shell "${image_ref}" "grep -Fq 'musl 1.2.6 COPYRIGHT' /opt/fingrind/doc/LICENSE-ALPINE-CONTAINER-COMPONENTS" || die \
        "published container ${image_ref} omitted the reviewed Alpine component notices"
    container_shell "${image_ref}" "grep -Fq 'GNU GENERAL PUBLIC LICENSE' /opt/fingrind/doc/LICENSE-GPL-2.0" || die \
        "published container ${image_ref} omitted the GPLv2 text"
    container_shell "${image_ref}" "grep -Fq 'Mozilla Public License Version 2.0' /opt/fingrind/doc/LICENSE-MPL-2.0" || die \
        "published container ${image_ref} omitted the MPLv2 text"
    container_shell "${image_ref}" "grep -Fq 'JAVA_VERSION=' /opt/fingrind/runtime/release" || die \
        "published container ${image_ref} omitted the linked runtime identity"
    container_shell "${image_ref}" "cmp /opt/fingrind/doc/ALPINE-PACKAGES.lock.tsv /opt/fingrind/doc/ALPINE-PACKAGES.tsv" || die \
        "published container ${image_ref} differed from its reviewed Alpine package lock"
    container_shell "${image_ref}" "cd /opt/fingrind/runtime/legal && sha256sum -c INDEX.sha256 >/dev/null" || die \
        "published container ${image_ref} runtime legal tree differed from its hash index"
    container_shell "${image_ref}" "modules=\$(sed -n 's/^MODULES=\"\(.*\)\"$/\1/p' /opt/fingrind/runtime/release); test -n \"\${modules}\"; for module in \${modules}; do test -s /opt/fingrind/runtime/legal/\${module}/LICENSE && test -s /opt/fingrind/runtime/legal/\${module}/ADDITIONAL_LICENSE_INFO && test -s /opt/fingrind/runtime/legal/\${module}/ASSEMBLY_EXCEPTION || exit 1; done" || die \
        "published container ${image_ref} omitted controlling legal files for a linked JDK module"
    container_shell "${image_ref}" "grep -Fq 'IMPLEMENTOR=\"Azul Systems, Inc.\"' /opt/fingrind/runtime/provenance/source-jdk-release && grep -Fq 'IMPLEMENTOR_VERSION=\"Zulu26.32+203-CA\"' /opt/fingrind/runtime/provenance/source-jdk-release && grep -Fq 'JAVA_RUNTIME_VERSION=\"26.0.2.1+1\"' /opt/fingrind/runtime/provenance/source-jdk-release && grep -Eq '^SOURCE=\"[^\"]+\"$' /opt/fingrind/runtime/provenance/source-jdk-release" || die \
        "published container ${image_ref} did not identify the exact Azul Zulu source JDK build"
    binary_archive_record="$(container_shell "${image_ref}" 'cat /opt/fingrind/runtime/provenance/input-jdk-binary-archive.sha256')"
    case "${binary_archive_record}" in
        'aadcca0249b6e07b06747d475ce5a0d3ab1aaaadd5acb4ae3eed0c9f942dac2e  zulu26.32.203-ca-jdk26.0.2.1-linux_musl_x64.tar.gz'|\
        '153f5166055270744c2fe70716d68c0a5f49c643552ae0c8e3b49708a5f3accd  zulu26.32.203-ca-jdk26.0.2.1-linux_musl_aarch64.tar.gz') ;;
        *) die "published container ${image_ref} did not identify its verified input JDK binary archive" ;;
    esac
    source_label="$(anonymous_docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.source" }}' "${image_ref}")"
    [[ "${source_label}" == "https://github.com/resoltico/FinGrind" ]] || die \
        "published container ${image_ref} omitted its canonical OCI source label"
    version_label="$(anonymous_docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.version" }}' "${image_ref}")"
    [[ "${version_label}" == "${expected_version}" ]] || die \
        "published container ${image_ref} omitted its exact OCI version label"
    revision_label="$(anonymous_docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "${image_ref}")"
    [[ "${revision_label}" == "${expected_revision}" ]] || die \
        "published container ${image_ref} omitted its exact OCI revision label"
    documentation_label="$(anonymous_docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.documentation" }}' "${image_ref}")"
    [[ "${documentation_label}" == "https://github.com/resoltico/FinGrind/blob/${expected_revision}/NOTICE" ]] || die \
        "published container ${image_ref} did not pin its OCI documentation label to the release commit"

    printf 'Verified legal payload: %s\n' "${image_ref}"
}

if [[ -n "${explicit_tag_ref}" ]]; then
    primary_tag_ref="${explicit_tag_ref}"
else
    primary_tag_ref="${expected_version}"
fi
verify_ref "${primary_tag_ref}"
if [[ -z "${explicit_tag_ref}" && "${verify_latest_ref}" == "true" ]]; then
    verify_ref latest
fi
verify_native_provenance_surface
verify_legal_surface
verify_mounted_book_surface
