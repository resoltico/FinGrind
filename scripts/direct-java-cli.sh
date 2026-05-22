#!/usr/bin/env bash
# Resolve the active CLI build directory and invoke the developer raw JAR surface.

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
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly gradle_wrapper_support="${repo_root}/scripts/gradle-wrapper-support.sh"
is_darwin=false
case "$(uname -s)" in
    Darwin) is_darwin=true ;;
esac

[[ -f "${gradle_wrapper_support}" ]] || die \
    "missing Gradle wrapper support helper at ${gradle_wrapper_support}"

# shellcheck source=/dev/null
source "${gradle_wrapper_support}"

readonly cli_build_dir="$(fg_gradle_project_build_dir "${repo_root}" 'cli' "${is_darwin}")"
readonly root_build_dir="$(fg_gradle_project_build_dir "${repo_root}" 'root' "${is_darwin}")"
readonly raw_jar="${cli_build_dir}/libs/fingrind.jar"
readonly source_checkout_artifact_manifest="$(
    fg_gradle_source_checkout_artifact_manifest_path "${repo_root}" 'cli' "${is_darwin}"
)"
readonly application_module='fingrind/dev.erst.fingrind.cli.App'

if fg_gradle_source_checkout_artifact_needs_refresh \
    "${repo_root}" \
    "${source_checkout_artifact_manifest}" \
    "${raw_jar}"; then
    (
        cd "${repo_root}"
        ./gradlew :cli:writeSourceCheckoutArtifactManifest prepareManagedSqlite --no-daemon --quiet >/dev/null
    ) || die "failed to refresh the developer raw JAR from the current checkout"
fi

[[ -f "${raw_jar}" ]] || die \
    "missing developer raw JAR at ${raw_jar}; run ./gradlew :cli:shadowJar prepareManagedSqlite"
if fg_gradle_source_checkout_artifact_needs_refresh \
    "${repo_root}" \
    "${source_checkout_artifact_manifest}" \
    "${raw_jar}"; then
    die \
        "developer raw JAR at ${raw_jar} is not synchronized with the current checkout; rerun ./gradlew :cli:shadowJar prepareManagedSqlite"
fi

exec java \
    --enable-native-access=fingrind \
    -Dfingrind.runtime.distribution=direct-java-invocation \
    -Dfingrind.source-checkout.root="${repo_root}" \
    -Dfingrind.source-checkout.build-root="${root_build_dir}" \
    --module-path "${raw_jar}" \
    --module "${application_module}" \
    "$@"
