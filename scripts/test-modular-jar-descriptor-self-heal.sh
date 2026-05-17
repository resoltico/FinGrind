#!/usr/bin/env bash
# Regress modular jar self-healing so incremental builds rebuild a damaged archive that
# lost module-info.class.

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
readonly scratch_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-modular-jar.XXXXXX")"
readonly project_cache_dir="${scratch_root}/project-cache"
readonly project_build_root="${scratch_root}/project-build"
readonly jacoco_root="${scratch_root}/jacoco"
readonly gradle_args=(
    --no-daemon
    --console=plain
    "--project-cache-dir=${project_cache_dir}"
    "-Dfingrind.gradle.project-build-root=${project_build_root}"
    "-Dfingrind.gradle.jacoco-root=${jacoco_root}"
)

cleanup() {
    rm -rf "${scratch_root}" 2>/dev/null || true
}
trap cleanup EXIT

cd "${repo_root}"

./gradlew :core:jar "${gradle_args[@]}" >/dev/null

readonly project_version="$(awk -F= '$1 == "version" { print $2 }' gradle.properties)"
readonly core_jar="${project_build_root}/core/libs/core-${project_version}.jar"

[[ -f "${core_jar}" ]] || die "expected core jar at ${core_jar}"

python3 - "${core_jar}" <<'PY'
import pathlib
import sys
import tempfile
import zipfile

jar_path = pathlib.Path(sys.argv[1])
with tempfile.NamedTemporaryFile(delete=False, suffix=".jar") as temp_file:
    temp_path = pathlib.Path(temp_file.name)

with zipfile.ZipFile(jar_path) as source, zipfile.ZipFile(temp_path, "w") as target:
    for entry in source.infolist():
        if entry.filename == "module-info.class":
            continue
        target.writestr(entry, source.read(entry.filename))

temp_path.replace(jar_path)
PY

if jar --describe-module --file "${core_jar}" 2>/dev/null | grep -q '^No module descriptor found\.'; then
    :
else
    die "expected corrupted core jar to present as an automatic module before self-heal"
fi

./gradlew :contract:compileJava "${gradle_args[@]}" >/dev/null

python3 - "${core_jar}" <<'PY'
import pathlib
import sys
import zipfile

jar_path = pathlib.Path(sys.argv[1])
with zipfile.ZipFile(jar_path) as jar_file:
    if "module-info.class" not in jar_file.namelist():
        raise SystemExit("module-info.class was not restored to the modular jar")
PY

jar --describe-module --file "${core_jar}" | grep -q '^dev\.erst\.fingrind\.core ' || die \
    "expected self-healed core jar to expose the declared module descriptor"

printf 'modular-jar-descriptor self-heal regression: success\n'
