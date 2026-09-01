#!/usr/bin/env bash
# Build the local Docker image and verify the FinGrind CLI as a real office-worker acceptance
# surface from a mounted workspace with unusual paths.

set -euo pipefail

print_usage() {
    printf '%s\n' \
        'Usage: ./scripts/docker-smoke.sh' \
        '' \
        'Builds the local FinGrind Docker image and runs the mounted-workspace office-worker acceptance workflow.'
}

for argument in "$@"; do
    case "${argument}" in
        -h|--help)
            print_usage
            exit 0
            ;;
    esac
done

# shellcheck source=/dev/null
source "$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/release-smoke-support.sh"

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly gradlew="${repo_root}/gradlew"
readonly gradle_wrapper_support="${repo_root}/scripts/gradle-wrapper-support.sh"
readonly repo_lock_support="${repo_root}/scripts/repo-verification-lock-support.sh"
readonly python_runtime_support="${repo_root}/scripts/python-runtime-support.sh"
readonly docker_context_verifier="${repo_root}/scripts/verify-docker-build-context.py"
readonly image_tag="fingrind-docker-acceptance:$$"
readonly smoke_root="$(resolve_existing_physical_directory "$(mktemp -d "${TMPDIR:-/tmp}/fingrind-docker-acceptance.XXXXXX")")"
readonly docker_run_user="$(id -u):$(id -g)"
anonymous_docker_config=''
docker_endpoint=''
is_darwin=false
case "$(uname -s)" in
    Darwin) is_darwin=true ;;
esac

[[ -f "${gradle_wrapper_support}" ]] || die "missing Gradle wrapper support helper at ${gradle_wrapper_support}"
[[ -f "${repo_lock_support}" ]] || die "missing repo verification lock helper at ${repo_lock_support}"
[[ -f "${python_runtime_support}" ]] || die "missing Python runtime support helper at ${python_runtime_support}"
[[ -f "${docker_context_verifier}" ]] || die \
    "missing Docker build-context verifier at ${docker_context_verifier}"
# shellcheck source=/dev/null
source "${gradle_wrapper_support}"
# shellcheck source=/dev/null
source "${repo_lock_support}"
# shellcheck source=/dev/null
source "${python_runtime_support}"

readonly gradle_user_home="${FINGRIND_GRADLE_USER_HOME:-$(fg_gradle_user_home_dir "${repo_root}" "${is_darwin}")}"

prepare_python_runtime_env
readonly cli_docker_context_dir="$(fg_gradle_docker_context_dir "${repo_root}" 'cli' "${is_darwin}")"

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
    cleanup_lock
    exit "${exit_code}"
}

trap cleanup EXIT

command -v docker >/dev/null 2>&1 || die "docker is required for the Docker acceptance gate"
docker buildx version >/dev/null 2>&1 || die "docker buildx is required for the Docker acceptance gate"
[[ -x "${gradlew}" ]] || die "missing Gradle wrapper at ${gradlew}"

mkdir -p "${gradle_user_home}"
acquire_lock

printf 'Docker acceptance: refreshing staged Docker build context\n'
env GRADLE_USER_HOME="${gradle_user_home}" \
    "${gradlew}" :cli:stageDockerBuildContext --console=plain --no-daemon

[[ -d "${cli_docker_context_dir}" ]] || die \
    "missing staged Docker build context at ${cli_docker_context_dir} after :cli:stageDockerBuildContext"
[[ -f "${cli_docker_context_dir}/Dockerfile" ]] || die \
    "missing staged Dockerfile at ${cli_docker_context_dir}/Dockerfile after :cli:stageDockerBuildContext"
python3 "${docker_context_verifier}" --context-dir "${cli_docker_context_dir}" --source-root "${repo_root}"

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
docker_with_repo_config buildx build \
    --build-arg FINGRIND_IMAGE_VERSION=development \
    --build-arg "FINGRIND_IMAGE_REVISION=$(git -C "${repo_root}" rev-parse HEAD)" \
    --load \
    -t "${image_tag}" \
    "${cli_docker_context_dir}" >/dev/null

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
require_match "${runtime_modules_output}" '^jdk\.crypto\.ec@' \
    "container runtime omitted jdk.crypto.ec, which Ed25519 attestation credentials require"
require_match "${runtime_modules_output}" '^jdk\.unsupported@' \
    "container runtime omitted jdk.unsupported, which PDF export requires for a noise-free runtime"

for legal_path in \
    /opt/fingrind/doc/LICENSE-ALPINE-CONTAINER-COMPONENTS \
    /opt/fingrind/doc/LICENSE-GPL-2.0 \
    /opt/fingrind/doc/LICENSE-MPL-2.0 \
    /opt/fingrind/doc/LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY \
    /opt/fingrind/doc/NOTICE \
    /opt/fingrind/doc/NOTICE-ZULU-26.32.203 \
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
    docker_with_repo_config run --rm --entrypoint /bin/sh "${image_tag}" -c "test -s '${legal_path}'" ||
        die "container omitted non-empty legal payload ${legal_path}"
done
actual_alpine_inventory="$(
    docker_with_repo_config run --rm --entrypoint /bin/sh "${image_tag}" -c \
        'cat /opt/fingrind/doc/ALPINE-PACKAGES.tsv'
)"
expected_alpine_inventory="$(cat "${repo_root}/gradle/alpine-container-packages.lock.tsv")"
[[ "${actual_alpine_inventory}" == "${expected_alpine_inventory}" ]] || die \
    "container Alpine package inventory differed from the reviewed lock"
docker_with_repo_config run --rm --entrypoint /bin/sh "${image_tag}" -c \
    'cd /opt/fingrind/runtime/legal && sha256sum -c INDEX.sha256 >/dev/null' || die \
    "container runtime legal tree differed from its complete hash index"

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
export FINGRIND_RELEASE_SMOKE_NATIVE_SQLITE_JAVA_PREFIX_JSON="$(
    json_array_of_strings \
        docker \
        run \
        --rm \
        --user "${docker_run_user}" \
        -w /workdir \
        -v "${smoke_root}:/workdir" \
        --entrypoint /opt/fingrind/runtime/bin/java \
        "${image_tag}"
)"
export FINGRIND_RELEASE_SMOKE_RUNTIME_DISTRIBUTION_KEY="containerRuntimeDistribution"
export FINGRIND_RELEASE_SMOKE_EXPECT_LOADED_SQLITE_DETAILS="true"
export FINGRIND_RELEASE_SMOKE_EXPECT_BUNDLE_HOME_PROPERTY="true"
export FINGRIND_RELEASE_SMOKE_WORK_ROOT="${smoke_root}"
export FINGRIND_RELEASE_SMOKE_REPORTED_WORK_ROOT="/workdir"
export FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE="relative-to-work-root"
export FINGRIND_RELEASE_SMOKE_SCENARIO_ID="docker-acceptance"
export FINGRIND_RELEASE_SMOKE_BOOK_KEY_OUTPUT_PERMISSIONS="0600"
export FINGRIND_RELEASE_SMOKE_OPEN_BOOK_MODE='book-key-file'
export FINGRIND_RELEASE_SMOKE_NATIVE_SQLITE_PROBE_CLASSPATH='/opt/fingrind/lib/release-smoke/native-sqlite-format-boundary-probe.jar'

release_smoke_run_office_worker_acceptance
