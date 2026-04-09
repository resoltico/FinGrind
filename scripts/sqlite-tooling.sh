#!/usr/bin/env bash
# Shared helpers for FinGrind's pinned SQLite toolchain.

set -euo pipefail

sqlite_die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

sqlite_resolve_script_dir() {
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

readonly SQLITE_TOOLING_DIR="$(sqlite_resolve_script_dir)"
readonly SQLITE_REPO_ROOT="$(cd -P -- "${SQLITE_TOOLING_DIR}/.." && pwd)"
readonly SQLITE_GRADLE_PROPERTIES="${SQLITE_REPO_ROOT}/gradle.properties"

sqlite_read_gradle_property() {
    local key=$1
    local value
    value="$(sed -n "s/^${key}=//p" "${SQLITE_GRADLE_PROPERTIES}" | head -1)"
    [[ -n "${value}" ]] || sqlite_die "missing ${key} in gradle.properties"
    printf '%s\n' "${value}"
}

readonly FINGRIND_SQLITE_VERSION="$(sqlite_read_gradle_property "fingrindSqliteVersion")"
readonly FINGRIND_SQLITE_VERSION_ENCODED="$(sqlite_read_gradle_property "fingrindSqliteVersionEncoded")"
readonly FINGRIND_SQLITE_RELEASE_YEAR="$(sqlite_read_gradle_property "fingrindSqliteReleaseYear")"
readonly FINGRIND_SQLITE_AUTOCONF_SHA3_256="$(
    sqlite_read_gradle_property "fingrindSqliteAutoconfSha3_256"
)"
readonly FINGRIND_SQLITE_SOURCE_FILE="sqlite-autoconf-${FINGRIND_SQLITE_VERSION_ENCODED}.tar.gz"
readonly FINGRIND_SQLITE_SOURCE_URL="https://www.sqlite.org/${FINGRIND_SQLITE_RELEASE_YEAR}/${FINGRIND_SQLITE_SOURCE_FILE}"
readonly FINGRIND_SQLITE_DOWNLOAD_DIR="${SQLITE_REPO_ROOT}/.local/tooling/downloads"
readonly FINGRIND_SQLITE_DOWNLOAD_PATH="${FINGRIND_SQLITE_DOWNLOAD_DIR}/${FINGRIND_SQLITE_SOURCE_FILE}"
readonly FINGRIND_SQLITE_TOOL_ROOT="${SQLITE_REPO_ROOT}/.local/tooling/sqlite/${FINGRIND_SQLITE_VERSION}"

sqlite_detect_platform() {
    local os arch
    case "$(uname -s)" in
        Darwin) os='darwin' ;;
        Linux) os='linux' ;;
        *) sqlite_die "unsupported operating system: $(uname -s)" ;;
    esac

    case "$(uname -m)" in
        arm64 | aarch64) arch='arm64' ;;
        x86_64 | amd64) arch='x64' ;;
        *) sqlite_die "unsupported CPU architecture: $(uname -m)" ;;
    esac

    printf '%s-%s\n' "${os}" "${arch}"
}

readonly FINGRIND_SQLITE_PLATFORM="$(sqlite_detect_platform)"
readonly FINGRIND_SQLITE_INSTALL_DIR="${FINGRIND_SQLITE_TOOL_ROOT}/${FINGRIND_SQLITE_PLATFORM}"
readonly FINGRIND_SQLITE_BINARY_PATH="${FINGRIND_SQLITE_INSTALL_DIR}/bin/sqlite3"

sqlite_parallelism() {
    if command -v getconf >/dev/null 2>&1; then
        getconf NPROCESSORS_ONLN
        return
    fi
    if command -v sysctl >/dev/null 2>&1; then
        sysctl -n hw.ncpu
        return
    fi
    printf '4\n'
}

sqlite_require_command() {
    local command_name=$1
    command -v "${command_name}" >/dev/null 2>&1 || sqlite_die \
        "required command is missing: ${command_name}"
}

sqlite_require_build_prerequisites() {
    sqlite_require_command cc
    sqlite_require_command curl
    sqlite_require_command make
    sqlite_require_command openssl
    sqlite_require_command tar
}

sqlite_sha3_256() {
    local file_path=$1
    openssl dgst -sha3-256 "${file_path}" | awk '{print $NF}'
}

sqlite_verify_source_archive() {
    local actual_sha3
    actual_sha3="$(sqlite_sha3_256 "${FINGRIND_SQLITE_DOWNLOAD_PATH}")"
    [[ "${actual_sha3}" == "${FINGRIND_SQLITE_AUTOCONF_SHA3_256}" ]] || sqlite_die \
        "SQLite source archive SHA3-256 mismatch: expected ${FINGRIND_SQLITE_AUTOCONF_SHA3_256}, got ${actual_sha3}"
}

sqlite_download_source_archive() {
    mkdir -p "${FINGRIND_SQLITE_DOWNLOAD_DIR}"
    if [[ -f "${FINGRIND_SQLITE_DOWNLOAD_PATH}" ]]; then
        sqlite_verify_source_archive
        return
    fi

    local temp_download_path="${FINGRIND_SQLITE_DOWNLOAD_PATH}.tmp.$$"
    curl --fail --location --silent --show-error \
        --output "${temp_download_path}" \
        "${FINGRIND_SQLITE_SOURCE_URL}"
    mv "${temp_download_path}" "${FINGRIND_SQLITE_DOWNLOAD_PATH}"
    sqlite_verify_source_archive
}

sqlite_verify_binary_version() {
    local binary_path=$1
    local version_output
    version_output="$("${binary_path}" --version | tr -d '\r')"
    [[ "${version_output}" == "${FINGRIND_SQLITE_VERSION}"* ]] || sqlite_die \
        "SQLite binary at ${binary_path} does not report version ${FINGRIND_SQLITE_VERSION}"
}

sqlite_acquire_lock() {
    local lock_dir=$1
    local waited_seconds=0
    while ! mkdir "${lock_dir}" 2>/dev/null; do
        if (( waited_seconds >= 120 )); then
            sqlite_die "timed out waiting for SQLite tooling lock: ${lock_dir}"
        fi
        sleep 1
        waited_seconds=$((waited_seconds + 1))
    done
}

sqlite_release_lock() {
    local lock_dir=$1
    rmdir "${lock_dir}" >/dev/null 2>&1 || true
}

sqlite_install_if_missing() {
    local lock_dir="${FINGRIND_SQLITE_INSTALL_DIR}.lock"
    local build_temp=''
    local install_temp=''
    local source_root=''
    local source_dir=''

    if [[ -x "${FINGRIND_SQLITE_BINARY_PATH}" ]]; then
        sqlite_verify_binary_version "${FINGRIND_SQLITE_BINARY_PATH}"
        return
    fi

    sqlite_require_build_prerequisites
    mkdir -p "${FINGRIND_SQLITE_TOOL_ROOT}"
    sqlite_acquire_lock "${lock_dir}"

    sqlite_cleanup_install() {
        [[ -n "${build_temp}" ]] && rm -rf "${build_temp}"
        [[ -n "${install_temp}" ]] && rm -rf "${install_temp}"
        sqlite_release_lock "${lock_dir}"
    }

    trap sqlite_cleanup_install RETURN

    if [[ -x "${FINGRIND_SQLITE_BINARY_PATH}" ]]; then
        sqlite_verify_binary_version "${FINGRIND_SQLITE_BINARY_PATH}"
        return
    fi

    sqlite_download_source_archive
    build_temp="$(mktemp -d "${SQLITE_REPO_ROOT}/.tmp-sqlite-build.XXXXXX")"
    install_temp="${FINGRIND_SQLITE_INSTALL_DIR}.tmp.$$"
    source_root="${build_temp}/src"
    source_dir="${source_root}/sqlite-autoconf-${FINGRIND_SQLITE_VERSION_ENCODED}"

    mkdir -p "${source_root}" "${install_temp}/bin"
    tar -xzf "${FINGRIND_SQLITE_DOWNLOAD_PATH}" -C "${source_root}"

    (
        cd "${source_dir}"
        env -u CC -u CFLAGS -u CPPFLAGS -u LDFLAGS \
            CC="$(command -v cc)" \
            ./configure --disable-shared --enable-static >/dev/null
        env -u CC -u CFLAGS -u CPPFLAGS -u LDFLAGS \
            make -j"$(sqlite_parallelism)" sqlite3 >/dev/null
        install -m 755 sqlite3 "${install_temp}/bin/sqlite3"
    )

    sqlite_verify_binary_version "${install_temp}/bin/sqlite3"
    rm -rf "${FINGRIND_SQLITE_INSTALL_DIR}"
    mv "${install_temp}" "${FINGRIND_SQLITE_INSTALL_DIR}"
}
