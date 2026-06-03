#!/usr/bin/env bash
# Verify that the included build publishes a complete Gradle plugin jar rather than a
# descriptor-only artifact.

set -euo pipefail

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

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly gradlew="${repo_root}/gradlew"
readonly wrapper_support="${repo_root}/scripts/gradle-wrapper-support.sh"

[[ -x "${gradlew}" ]] || die "missing executable Gradle wrapper at ${gradlew}"
[[ -f "${wrapper_support}" ]] || die "missing wrapper support helper at ${wrapper_support}"

# shellcheck source=/dev/null
source "${wrapper_support}"

is_darwin=false
case "$(uname -s)" in
    Darwin) is_darwin=true ;;
esac

build_logic_root="$(
    fg_gradle_build_logic_dir "${repo_root}" "${is_darwin}"
)"
jar_candidates=( "${build_logic_root}"/libs/*.jar )

"${gradlew}" -p "${repo_root}/gradle/build-logic" jar --no-daemon --console=plain >/dev/null

[[ -e "${jar_candidates[0]}" ]] || die "missing included-build plugin jar under ${build_logic_root}/libs"
[[ ${#jar_candidates[@]} -eq 1 ]] || die \
    "expected exactly one included-build plugin jar under ${build_logic_root}/libs"
jar_path="${jar_candidates[0]}"

python3 - <<'PY' "${jar_path}"
from __future__ import annotations

from pathlib import Path
import sys
import zipfile

jar_path = Path(sys.argv[1])
expected_plugins = {
    "dev.erst.fingrind.java-conventions": "dev.erst.fingrind.buildlogic.FinGrindJavaConventionsPlugin",
    "dev.erst.fingrind.root-conventions": "dev.erst.fingrind.buildlogic.FinGrindRootConventionsPlugin",
    "dev.erst.fingrind.jazzer-conventions": "dev.erst.fingrind.buildlogic.FinGrindJazzerConventionsPlugin",
    "dev.erst.fingrind.cli-distribution": "dev.erst.fingrind.buildlogic.FinGrindCliDistributionPlugin",
    "dev.erst.fingrind.managed-sqlite-consumer": "dev.erst.fingrind.buildlogic.FinGrindManagedSqliteConsumerPlugin",
}

with zipfile.ZipFile(jar_path) as jar_file:
    entries = set(jar_file.namelist())
    for plugin_id, implementation_class in expected_plugins.items():
        descriptor_path = f"META-INF/gradle-plugins/{plugin_id}.properties"
        if descriptor_path not in entries:
            raise SystemExit(f"missing plugin descriptor {descriptor_path} in {jar_path}")
        descriptor_text = jar_file.read(descriptor_path).decode("utf-8")
        expected_line = f"implementation-class={implementation_class}"
        if expected_line not in descriptor_text:
            raise SystemExit(
                f"descriptor {descriptor_path} did not declare {expected_line!r}: {descriptor_text!r}",
            )
        class_path = implementation_class.replace(".", "/") + ".class"
        if class_path not in entries:
            raise SystemExit(f"missing implementation class {class_path} in {jar_path}")

    compiled_classes = [entry for entry in entries if entry.endswith(".class")]
    if len(compiled_classes) < 50:
        raise SystemExit(
            f"unexpectedly small build-logic jar at {jar_path}: only {len(compiled_classes)} class files",
        )
PY

printf 'build-logic plugin jar verifier: success\n'
