#!/usr/bin/env bash
# Legal-payload and OCI-label behavior for the public-container verifier's fake Docker client.

fake_container_shell_probe() {
    local shell_command=$1
    local release_payload_root="${FAKE_DOCKER_RELEASE_PAYLOAD_ROOT:?}"
    local document_name=''

    if [[ "${shell_command}" == "test -s '/opt/fingrind/doc/"*"'" ]]; then
        document_name="${shell_command#"test -s '/opt/fingrind/doc/"}"
        document_name="${document_name%"'"}"
        if [[ "${document_name}" == 'ALPINE-PACKAGES.tsv' || "${document_name}" == 'ALPINE-PACKAGES.lock.tsv' ]]; then
            [[ -s "${release_payload_root}/gradle/alpine-container-packages.lock.tsv" ]]
            return
        fi
        [[ -s "${release_payload_root}/${document_name}" ]]
        return
    fi
    if [[ "${shell_command}" == "cat '/opt/fingrind/doc/"*"'" ]]; then
        document_name="${shell_command#"cat '/opt/fingrind/doc/"}"
        document_name="${document_name%"'"}"
        if [[ "${document_name}" == 'ALPINE-PACKAGES.tsv' || "${document_name}" == 'ALPINE-PACKAGES.lock.tsv' ]]; then
            cat "${release_payload_root}/gradle/alpine-container-packages.lock.tsv"
            return
        fi
        cat "${release_payload_root}/${document_name}"
        return
    fi
    if [[ "${shell_command}" == "test -s '/opt/fingrind/runtime/"*"'" ]]; then
        return 0
    fi
    if [[ "${shell_command}" == 'cat /opt/fingrind/doc/ALPINE-PACKAGES.tsv' ]]; then
        cat "${release_payload_root}/gradle/alpine-container-packages.lock.tsv"
        return
    fi
    if [[ "${shell_command}" == 'cat /opt/fingrind/runtime/provenance/input-jdk-binary-archive.sha256' ]]; then
        printf '%s\n' '153f5166055270744c2fe70716d68c0a5f49c643552ae0c8e3b49708a5f3accd  zulu26.32.203-ca-jdk26.0.2.1-linux_musl_aarch64.tar.gz'
        return
    fi
    case "${shell_command}" in
        grep\ *|cmp\ *|cd\ *|modules=*|test\ -s\ /opt/fingrind/runtime/*) return 0 ;;
    esac
    return 1
}

fake_container_image_inspect() {
    local format=$1
    local release_payload_root="${FAKE_DOCKER_RELEASE_PAYLOAD_ROOT:?}"

    case "${format}" in
        *org.opencontainers.image.source*) printf '%s\n' 'https://github.com/resoltico/FinGrind' ;;
        *org.opencontainers.image.version*) printf '%s\n' "${FAKE_DOCKER_EXPECTED_VERSION:-0.24.0}" ;;
        *org.opencontainers.image.revision*) git -C "${release_payload_root}" rev-parse HEAD ;;
        *org.opencontainers.image.documentation*)
            printf 'https://github.com/resoltico/FinGrind/blob/%s/NOTICE\n' \
                "$(git -C "${release_payload_root}" rev-parse HEAD)"
            ;;
        *) return 1 ;;
    esac
}
