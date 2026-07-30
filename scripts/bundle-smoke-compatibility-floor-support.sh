#!/usr/bin/env bash
# Owns the Linux compatibility-floor rerun used by the bundle smoke entrypoint.

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    printf 'error: %s\n' "bundle-smoke-compatibility-floor-support.sh is a library and must be sourced by bundle-smoke.sh." >&2
    exit 1
fi

bundle_platform_for_architecture() {
    case "$1" in
        x86_64) printf '%s\n' 'linux/amd64' ;;
        aarch64) printf '%s\n' 'linux/arm64' ;;
        *) die "unsupported Linux bundle architecture for compatibility-floor smoke: $1" ;;
    esac
}

run_compatibility_floor_acceptance() {
    local compatibility_bundle_classifier="$1"
    local compatibility_bundle_operating_system_id="$2"
    local compatibility_bundle_architecture_id="$3"
    local compatibility_bundle_launcher_relative_path="$4"
    local compatibility_container_image="$5"
    local compatibility_repo_root="$6"
    local compatibility_bundle_root="$7"
    local compatibility_smoke_root="$8"
    local compatibility_bundle_platform
    local compatibility_caller_gid
    local compatibility_caller_uid
    local compatibility_entrypoint
    local compatibility_home_root
    local compatibility_work_root="${compatibility_smoke_root}/compatibility-floor-workspace"

    [[ "${compatibility_bundle_operating_system_id}" == "linux" ]] || die \
        "compatibility-floor smoke requires one Linux bundle, got ${compatibility_bundle_classifier}"
    [[ -n "${compatibility_container_image}" ]] || die \
        "bundle layout contract did not declare compatibilitySmokeContainerImage for ${compatibility_bundle_classifier}"
    command -v docker >/dev/null 2>&1 || die \
        "docker is required for the compatibility-floor bundle smoke"
    compatibility_bundle_platform="$(bundle_platform_for_architecture "${compatibility_bundle_architecture_id}")"
    compatibility_caller_uid="$(id -u)"
    compatibility_caller_gid="$(id -g)"
    compatibility_entrypoint="${compatibility_smoke_root}/compatibility-floor-entrypoint.sh"
    compatibility_home_root="${compatibility_smoke_root}/compatibility-floor-home"
    mkdir -p "${compatibility_work_root}" "${compatibility_home_root}"
    chmod 700 "${compatibility_home_root}"
    cat >"${compatibility_entrypoint}" <<'SH'
#!/usr/bin/env bash
set -eu
if ! command -v python3 >/dev/null 2>&1; then
  command -v dnf >/dev/null 2>&1 || {
    echo "error: compatibility-floor image does not provide python3 or dnf" >&2
    exit 1
  }
  dnf install -y python3 python3-pip >/dev/null
elif ! python3 -m pip --version >/dev/null 2>&1; then
  command -v dnf >/dev/null 2>&1 || {
    echo "error: compatibility-floor image does not provide Python pip or dnf" >&2
    exit 1
  }
  dnf install -y python3-pip >/dev/null
fi
uv_version="$(awk -F= '$1 == "fingrindUvVersion" { print $2; exit }' /repo/gradle/fingrind-build.properties)"
test -n "${uv_version}"
case "${FINGRIND_COMPATIBILITY_CALLER_UID:-}" in
  ''|*[!0-9]*)
    echo "error: compatibility-floor caller UID must be a non-negative integer" >&2
    exit 1
    ;;
esac
case "${FINGRIND_COMPATIBILITY_CALLER_GID:-}" in
  ''|*[!0-9]*)
    echo "error: compatibility-floor caller GID must be a non-negative integer" >&2
    exit 1
    ;;
esac
command -v setpriv >/dev/null 2>&1 || {
  echo "error: compatibility-floor image does not provide setpriv for unprivileged execution" >&2
  exit 1
}
exec setpriv \
  --reuid "${FINGRIND_COMPATIBILITY_CALLER_UID}" \
  --regid "${FINGRIND_COMPATIBILITY_CALLER_GID}" \
  --clear-groups \
  /bin/bash -c '
    set -eu
    export HOME=/home/fingrind
    export PATH="${HOME}/.local/bin:/usr/bin:/bin"
    python3 -m pip install --user --disable-pip-version-check "uv==${1}"
    source /repo/scripts/release-smoke-support.sh
    python3 /repo/scripts/verify-bundle-archive-contract.py --repo-root /repo --bundle-root /bundle
    release_smoke_run_office_worker_acceptance
  ' -- "${uv_version}"
SH
    chmod +x "${compatibility_entrypoint}"

    docker run --rm \
        --platform "${compatibility_bundle_platform}" \
        -w /work \
        -e FINGRIND_COMPATIBILITY_CALLER_UID="${compatibility_caller_uid}" \
        -e FINGRIND_COMPATIBILITY_CALLER_GID="${compatibility_caller_gid}" \
        -e HOME=/home/fingrind \
        -e FINGRIND_RELEASE_SMOKE_LABEL='Bundle compatibility-floor acceptance' \
        -e FINGRIND_RELEASE_SMOKE_REPO_ROOT=/repo \
        -e FINGRIND_RELEASE_SMOKE_COMMAND_PREFIX_JSON="$(json_array_of_strings "/bundle/${compatibility_bundle_launcher_relative_path}")" \
        -e FINGRIND_RELEASE_SMOKE_COMMAND_CWD=/work \
        -e FINGRIND_RELEASE_SMOKE_COMMAND_ENV_DROP_JSON='["JAVA_HOME"]' \
        -e FINGRIND_RELEASE_SMOKE_COMMAND_ENV_SET_JSON='{"PATH":"/usr/bin:/bin"}' \
        -e FINGRIND_RELEASE_SMOKE_RUNTIME_DISTRIBUTION_KEY=bundleRuntimeDistribution \
        -e FINGRIND_RELEASE_SMOKE_EXPECT_LOADED_SQLITE_DETAILS=true \
        -e FINGRIND_RELEASE_SMOKE_EXPECT_BUNDLE_HOME_PROPERTY=true \
        -e FINGRIND_RELEASE_SMOKE_WORK_ROOT=/work \
        -e FINGRIND_RELEASE_SMOKE_REPORTED_WORK_ROOT=/work \
        -e FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE=relative-to-work-root \
        -e FINGRIND_RELEASE_SMOKE_SCENARIO_ID=bundle-compatibility-floor \
        -e FINGRIND_RELEASE_SMOKE_BOOK_KEY_OUTPUT_PERMISSIONS=0600 \
        -e FINGRIND_RELEASE_SMOKE_OPEN_BOOK_MODE=generated-key-stdin \
        -e FINGRIND_RELEASE_SMOKE_NATIVE_SQLITE_PROBE_CLASSPATH=/bundle/lib/release-smoke/native-sqlite-format-boundary-probe.jar \
        -v "${compatibility_repo_root}:/repo:ro" \
        -v "${compatibility_bundle_root}:/bundle:ro" \
        -v "${compatibility_work_root}:/work" \
        -v "${compatibility_home_root}:/home/fingrind" \
        -v "${compatibility_entrypoint}:/compatibility-floor-entrypoint.sh:ro" \
        "${compatibility_container_image}" \
        /bin/bash /compatibility-floor-entrypoint.sh
}
