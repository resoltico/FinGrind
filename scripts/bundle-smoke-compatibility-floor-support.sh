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
    local compatibility_entrypoint
    local compatibility_work_root="${compatibility_smoke_root}/compatibility-floor-workspace"

    [[ "${compatibility_bundle_operating_system_id}" == "linux" ]] || die \
        "compatibility-floor smoke requires one Linux bundle, got ${compatibility_bundle_classifier}"
    [[ -n "${compatibility_container_image}" ]] || die \
        "bundle layout contract did not declare compatibilitySmokeContainerImage for ${compatibility_bundle_classifier}"
    command -v docker >/dev/null 2>&1 || die \
        "docker is required for the compatibility-floor bundle smoke"
    compatibility_bundle_platform="$(bundle_platform_for_architecture "${compatibility_bundle_architecture_id}")"
    compatibility_entrypoint="${compatibility_smoke_root}/compatibility-floor-entrypoint.sh"
    mkdir -p "${compatibility_work_root}"
    cat >"${compatibility_entrypoint}" <<'SH'
#!/bin/sh
set -eu
if ! command -v python3 >/dev/null 2>&1; then
  command -v dnf >/dev/null 2>&1 || {
    echo "error: compatibility-floor image does not provide python3 or dnf" >&2
    exit 1
  }
  dnf install -y python3 >/dev/null
fi
python3 /repo/scripts/verify-bundle-archive-contract.py --repo-root /repo --bundle-root /bundle
python3 /repo/scripts/release-smoke-workflow.py
SH
    chmod +x "${compatibility_entrypoint}"

    docker run --rm \
        --platform "${compatibility_bundle_platform}" \
        -w /work \
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
        -v "${compatibility_repo_root}:/repo:ro" \
        -v "${compatibility_bundle_root}:/bundle:ro" \
        -v "${compatibility_work_root}:/work" \
        -v "${compatibility_entrypoint}:/compatibility-floor-entrypoint.sh:ro" \
        "${compatibility_container_image}" \
        /bin/sh /compatibility-floor-entrypoint.sh
}
