#!/usr/bin/env bash
# Build the local Docker image and verify the FinGrind CLI as a real office-worker acceptance
# surface from a mounted workspace with unusual paths.

set -euo pipefail

# shellcheck source=/dev/null
source "$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/release-smoke-support.sh"

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly gradlew="${repo_root}/gradlew"
readonly gradle_wrapper_support="${repo_root}/scripts/gradle-wrapper-support.sh"
readonly image_tag="fingrind-docker-acceptance:$$"
readonly smoke_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-docker-acceptance.XXXXXX")"
readonly docker_run_user="$(id -u):$(id -g)"
anonymous_docker_config=''
docker_endpoint=''
is_darwin=false
case "$(uname -s)" in
    Darwin) is_darwin=true ;;
esac

[[ -f "${gradle_wrapper_support}" ]] || die "missing Gradle wrapper support helper at ${gradle_wrapper_support}"
# shellcheck source=/dev/null
source "${gradle_wrapper_support}"
readonly cli_build_dir="$(fg_gradle_project_build_dir "${repo_root}" 'cli' "${is_darwin}")"
readonly repo_cli_build_dir="${repo_root}/cli/build"

resolve_docker_buildx_plugin() {
    local docker_binary=''
    local -a candidates=()
    local candidate=''

    if candidate="$(command -v docker-buildx 2>/dev/null || true)"; then
        if [[ -n "${candidate}" ]]; then
            candidates+=("${candidate}")
        fi
    fi

    docker_binary="$(command -v docker)"
    candidates+=(
        "${HOME}/.docker/cli-plugins/docker-buildx"
        "/Applications/Docker.app/Contents/Resources/cli-plugins/docker-buildx"
        "/usr/local/lib/docker/cli-plugins/docker-buildx"
        "/usr/local/libexec/docker/cli-plugins/docker-buildx"
        "/opt/homebrew/lib/docker/cli-plugins/docker-buildx"
        "/opt/homebrew/libexec/docker/cli-plugins/docker-buildx"
        "/usr/lib/docker/cli-plugins/docker-buildx"
        "/usr/libexec/docker/cli-plugins/docker-buildx"
        "/usr/lib64/docker/cli-plugins/docker-buildx"
        "/usr/share/docker/cli-plugins/docker-buildx"
        "$(cd -P -- "$(dirname -- "${docker_binary}")" && pwd)/docker-buildx"
    )

    for candidate in "${candidates[@]}"; do
        if [[ -n "${candidate}" && -x "${candidate}" ]]; then
            printf '%s\n' "${candidate}"
            return 0
        fi
    done

    return 1
}

docker_with_repo_config() {
    if [[ -n "${docker_endpoint}" ]]; then
        DOCKER_CONFIG="${anonymous_docker_config}" DOCKER_HOST="${docker_endpoint}" docker "$@"
        return
    fi
    DOCKER_CONFIG="${anonymous_docker_config}" docker "$@"
}

remove_tree_with_retries() {
    local target_dir=$1
    local attempt=0

    while (( attempt < 5 )); do
        if rm -rf "${target_dir}" >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
        attempt=$((attempt + 1))
    done
    return 1
}

cleanup() {
    local exit_code=$?
    if [[ -d "${smoke_root}" ]] && ! remove_tree_with_retries "${smoke_root}"; then
        warn "leaving Docker acceptance scratch directory at ${smoke_root} because mounted-workspace tombstones remained busy during cleanup"
    fi
    if command -v docker >/dev/null 2>&1 && [[ -n "${anonymous_docker_config}" ]]; then
        docker_with_repo_config image rm -f "${image_tag}" >/dev/null 2>&1 || true
        rm -rf "${anonymous_docker_config}" || true
    fi
    exit "${exit_code}"
}

trap cleanup EXIT

command -v docker >/dev/null 2>&1 || die "docker is required for the Docker acceptance gate"
docker buildx version >/dev/null 2>&1 || die "docker buildx is required for the Docker acceptance gate"
[[ -x "${gradlew}" ]] || die "missing Gradle wrapper at ${gradlew}"
[[ -f "${repo_root}/Dockerfile" ]] || die "missing Dockerfile at ${repo_root}/Dockerfile"

printf 'Docker acceptance: refreshing internal container build inputs\n'
"${gradlew}" :cli:shadowJar --console=plain

[[ -f "${cli_build_dir}/libs/fingrind.jar" ]] || die \
    "missing internal application JAR at ${cli_build_dir}/libs/fingrind.jar after :cli:shadowJar"
[[ -f "${cli_build_dir}/docker/runtime-modules.txt" ]] || die \
    "missing Docker runtime module list at ${cli_build_dir}/docker/runtime-modules.txt after :cli:shadowJar"
[[ -f "${cli_build_dir}/docker/docker-entrypoint.sh" ]] || die \
    "missing Docker entrypoint script at ${cli_build_dir}/docker/docker-entrypoint.sh after :cli:shadowJar"

if [[ "${cli_build_dir}" != "${repo_cli_build_dir}" ]]; then
    printf 'Docker acceptance: staging relocated Docker build inputs into repository context\n'
    mkdir -p "${repo_cli_build_dir}/libs" "${repo_cli_build_dir}/docker"
    cp -f "${cli_build_dir}/libs/fingrind.jar" "${repo_cli_build_dir}/libs/fingrind.jar"
    cp -f "${cli_build_dir}/docker/runtime-modules.txt" "${repo_cli_build_dir}/docker/runtime-modules.txt"
    cp -f "${cli_build_dir}/docker/docker-entrypoint.sh" "${repo_cli_build_dir}/docker/docker-entrypoint.sh"
fi

docker_endpoint="${DOCKER_HOST:-}"
if [[ -z "${docker_endpoint}" ]]; then
    docker_endpoint="$(
        docker context inspect "$(docker context show 2>/dev/null || true)" \
            --format '{{.Endpoints.docker.Host}}' 2>/dev/null || true
    )"
fi
anonymous_docker_config="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-docker-config.XXXXXX")"
printf '{}\n' > "${anonymous_docker_config}/config.json"

if ! docker_with_repo_config buildx version >/dev/null 2>&1; then
    docker_buildx_plugin="$(resolve_docker_buildx_plugin)" || die \
        "docker buildx is available in the current shell, but no reusable docker-buildx plugin binary was found for the anonymous DOCKER_CONFIG"
    mkdir -p "${anonymous_docker_config}/cli-plugins"
    ln -s "${docker_buildx_plugin}" "${anonymous_docker_config}/cli-plugins/docker-buildx"
    docker_with_repo_config buildx version >/dev/null 2>&1 || die \
        "docker buildx is not reachable through the anonymous DOCKER_CONFIG even after staging ${docker_buildx_plugin}"
fi

printf 'Docker acceptance: building local image\n'
docker_with_repo_config buildx build --load -t "${image_tag}" "${repo_root}" >/dev/null

runtime_modules_output="$(
    docker_with_repo_config run --rm \
        --entrypoint /opt/fingrind/runtime/bin/java \
        "${image_tag}" \
        --list-modules | tr -d '\r'
)"
require_no_match "${runtime_modules_output}" '^jdk\.jlink@' \
    "container runtime still contains jdk.jlink"
require_no_match "${runtime_modules_output}" '^jdk\.jpackage@' \
    "container runtime still contains jdk.jpackage"
require_no_match "${runtime_modules_output}" '^jdk\.jdeps@' \
    "container runtime still contains jdk.jdeps"
require_match "${runtime_modules_output}" '^jdk\.unsupported@' \
    "container runtime omitted jdk.unsupported, which PDF export requires for a noise-free runtime"

export FINGRIND_RELEASE_SMOKE_LABEL="Docker acceptance"
export FINGRIND_RELEASE_SMOKE_REPO_ROOT="${repo_root}"
export FINGRIND_RELEASE_SMOKE_COMMAND_PREFIX_JSON="$(
    json_array_of_strings \
        docker \
        run \
        --rm \
        --user "${docker_run_user}" \
        -w /workdir \
        -v "${smoke_root}:/workdir" \
        "${image_tag}"
)"
export FINGRIND_RELEASE_SMOKE_RUNTIME_DISTRIBUTION_KEY="containerRuntimeDistribution"
export FINGRIND_RELEASE_SMOKE_EXPECT_LOADED_SQLITE_DETAILS="true"
export FINGRIND_RELEASE_SMOKE_EXPECT_BUNDLE_HOME_PROPERTY="false"
export FINGRIND_RELEASE_SMOKE_WORK_ROOT="${smoke_root}"
export FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE="relative-to-work-root"
export FINGRIND_RELEASE_SMOKE_SCENARIO_ID="docker-acceptance"
export FINGRIND_RELEASE_SMOKE_BOOK_KEY_OUTPUT_PERMISSIONS="0600"
export FINGRIND_RELEASE_SMOKE_OPEN_BOOK_MODE='book-key-file'

release_smoke_run_office_worker_acceptance
