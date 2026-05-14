#!/usr/bin/env bash
# Reproduce and guard the source-checkout installed launcher contract.

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
readonly contract_values_reader="${repo_root}/scripts/read-contract-values.py"
readonly gradle_wrapper_support="${repo_root}/scripts/gradle-wrapper-support.sh"
readonly launcher_wrapper="${repo_root}/scripts/source-checkout-cli.sh"
readonly launcher_wrapper_ps1="${repo_root}/scripts/source-checkout-cli.ps1"
readonly raw_java_wrapper="${repo_root}/scripts/direct-java-cli.sh"
readonly raw_java_wrapper_ps1="${repo_root}/scripts/direct-java-cli.ps1"
readonly gradle_wrapper_support_ps1="${repo_root}/scripts/gradle-wrapper-support.ps1"
readonly repo_tmp_dir="${repo_root}/tmp"

[[ -f "${contract_values_reader}" ]] || die "missing contract-values reader"
[[ -f "${gradle_wrapper_support}" ]] || die "missing Gradle wrapper support helper"
[[ -x "${launcher_wrapper}" ]] || die "missing POSIX source-checkout launcher wrapper"
[[ -f "${launcher_wrapper_ps1}" ]] || die "missing PowerShell source-checkout launcher wrapper"
[[ -x "${raw_java_wrapper}" ]] || die "missing POSIX direct-Java wrapper"
[[ -f "${raw_java_wrapper_ps1}" ]] || die "missing PowerShell direct-Java wrapper"
[[ -f "${gradle_wrapper_support_ps1}" ]] || die "missing PowerShell Gradle wrapper support helper"
grep -Fq 'gradle-wrapper-support.ps1' "${launcher_wrapper_ps1}" || die \
    "PowerShell source-checkout launcher wrapper no longer sources the shared Gradle wrapper helper"
grep -Fq 'gradle-wrapper-support.ps1' "${raw_java_wrapper_ps1}" || die \
    "PowerShell direct-Java wrapper no longer sources the shared Gradle wrapper helper"

# shellcheck source=/dev/null
source "${gradle_wrapper_support}"

is_darwin=false
case "$(uname -s)" in
    Darwin) is_darwin=true ;;
esac

readonly cli_build_dir="$(fg_gradle_project_build_dir "${repo_root}" 'cli' "${is_darwin}")"
readonly launcher="${cli_build_dir}/install/cli-shadow/bin/cli"
readonly raw_jar="${cli_build_dir}/libs/fingrind.jar"

mkdir -p "${repo_tmp_dir}"
tmp_dir="$(mktemp -d "${repo_tmp_dir}/source-checkout-launcher.XXXXXX")"
cleanup() {
    rm -rf "${tmp_dir}"
}
trap cleanup EXIT

"${repo_root}/gradlew" :cli:installShadowDist prepareManagedSqlite --no-daemon --console=plain >/dev/null

[[ -x "${launcher}" ]] || die "missing generated source-checkout launcher"

readonly expected_runtime_distribution="$(
    FINGRIND_CONTRACT_VALUES_JSON="$(python3 "${contract_values_reader}")" python3 - <<'PY'
import json
import os

contract = json.loads(os.environ["FINGRIND_CONTRACT_VALUES_JSON"])
print(contract["runtimeSurface"]["sourceCheckoutRuntimeDistribution"])
PY
)"
readonly expected_direct_java_runtime_distribution="$(
    FINGRIND_CONTRACT_VALUES_JSON="$(python3 "${contract_values_reader}")" python3 - <<'PY'
import json
import os

contract = json.loads(os.environ["FINGRIND_CONTRACT_VALUES_JSON"])
print(contract["runtimeSurface"]["directJavaRuntimeDistribution"])
PY
)"

help_stdout="${tmp_dir}/help.out"
help_stderr="${tmp_dir}/help.err"
capabilities_stdout="${tmp_dir}/capabilities.out"
capabilities_stderr="${tmp_dir}/capabilities.err"
key_stdout="${tmp_dir}/key.out"
key_stderr="${tmp_dir}/key.err"
open_stdout="${tmp_dir}/open.out"
open_stderr="${tmp_dir}/open.err"
raw_help_stdout="${tmp_dir}/raw-help.out"
raw_help_stderr="${tmp_dir}/raw-help.err"
raw_command_help_stdout="${tmp_dir}/raw-command-help.out"
raw_command_help_stderr="${tmp_dir}/raw-command-help.err"
raw_capabilities_stdout="${tmp_dir}/raw-capabilities.out"
raw_capabilities_stderr="${tmp_dir}/raw-capabilities.err"
raw_open_stdout="${tmp_dir}/raw-open.out"
raw_open_stderr="${tmp_dir}/raw-open.err"

readonly book_file="${tmp_dir}/Nested Dir/Books/ledger launcher.db"
readonly key_file="${tmp_dir}/Keys/book key.txt"
readonly entity_name='Launcher Smoke Co'
readonly functional_currency='EUR'
readonly fiscal_year_start='01-01'
mkdir -p "$(dirname "${book_file}")" "$(dirname "${key_file}")"

"${launcher_wrapper}" help --output human >"${help_stdout}" 2>"${help_stderr}" ||
    die "source-checkout launcher help failed"

[[ ! -s "${help_stderr}" ]] || die "source-checkout launcher help wrote diagnostics"
grep -Fq 'Commands' "${help_stdout}" || die "source-checkout launcher help did not render command help"
if grep -Fq 'Unsupported runtime distribution: null' "${help_stdout}"; then
    die "source-checkout launcher baked a null runtime distribution into help output"
fi
if grep -Fq 'A restricted method in java.lang.foreign.SymbolLookup has been called' "${help_stdout}"; then
    die "source-checkout launcher help leaked the Java native-access warning"
fi

"${launcher_wrapper}" capabilities --output json >"${capabilities_stdout}" 2>"${capabilities_stderr}" ||
    die "source-checkout launcher capabilities failed"

[[ ! -s "${capabilities_stderr}" ]] || die "source-checkout launcher capabilities wrote diagnostics"
python3 - "${capabilities_stdout}" "${expected_runtime_distribution}" <<'PY'
import json
import pathlib
import sys

document = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
expected_runtime_distribution = sys.argv[2]
sqlite = document["payload"]["environment"]["sqlite"]
runtime = sqlite["runtime"]
actual_runtime_distribution = (
    document["payload"]["environment"]["distribution"]["runtimeDistribution"]
)
if actual_runtime_distribution != expected_runtime_distribution:
    raise SystemExit(
        "unexpected runtime distribution: "
        + actual_runtime_distribution
        + " != "
        + expected_runtime_distribution
    )
if runtime["runtimeProvenance"] != "source-checkout-managed":
    raise SystemExit(
        "unexpected runtime provenance: "
        + runtime["runtimeProvenance"]
        + " != source-checkout-managed"
    )
PY

"${launcher_wrapper}" generate-book-key-file --book-key-file "${key_file}" >"${key_stdout}" 2>"${key_stderr}" ||
    die "source-checkout launcher key generation failed"

[[ ! -s "${key_stderr}" ]] || die "source-checkout launcher key generation wrote diagnostics"
grep -Fq '"status":"ok"' "${key_stdout}" || die "source-checkout launcher key generation did not return ok"

"${launcher_wrapper}" \
    open-book \
    --book-file "${book_file}" \
    --book-key-file "${key_file}" \
    --entity-name "${entity_name}" \
    --functional-currency "${functional_currency}" \
    --fiscal-year-start "${fiscal_year_start}" >"${open_stdout}" 2>"${open_stderr}" ||
    die "source-checkout launcher open-book failed"

[[ ! -s "${open_stderr}" ]] || die "source-checkout launcher open-book wrote diagnostics"
python3 - "${open_stdout}" "${entity_name}" "${functional_currency}" "${fiscal_year_start}" <<'PY'
import json
import pathlib
import sys

document = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
payload = document["payload"]
book_identity = payload["bookIdentity"]
if document["status"] != "ok":
    raise SystemExit("source-checkout launcher open-book did not return ok")
if book_identity["entityName"] != sys.argv[2]:
    raise SystemExit("source-checkout launcher open-book returned the wrong entity name")
if book_identity["functionalCurrency"] != sys.argv[3]:
    raise SystemExit("source-checkout launcher open-book returned the wrong functional currency")
if book_identity["fiscalYearStart"] != sys.argv[4]:
    raise SystemExit("source-checkout launcher open-book returned the wrong fiscal year start")
PY

[[ -f "${raw_jar}" ]] || die "missing raw developer JAR"

java -jar "${raw_jar}" help --output human >"${raw_help_stdout}" 2>"${raw_help_stderr}" ||
    die "developer raw JAR help failed"

[[ ! -s "${raw_help_stderr}" ]] || die "developer raw JAR help wrote diagnostics"
grep -Fq 'Getting Started' "${raw_help_stdout}" ||
    die "developer raw JAR help did not render the front-door guidance section"
if grep -Fq 'Developer Raw JAR' "${raw_help_stdout}"; then
    die "developer raw JAR help regressed back to the retired runtime-specific quick-start block"
fi
if grep -Fq 'A restricted method in java.lang.foreign.SymbolLookup has been called' "${raw_help_stderr}"; then
    die "developer raw JAR help leaked the Java native-access warning"
fi

java -jar "${raw_jar}" help open-book --output human >"${raw_command_help_stdout}" \
    2>"${raw_command_help_stderr}" || die "developer raw JAR command help failed"

[[ ! -s "${raw_command_help_stderr}" ]] || die "developer raw JAR command help wrote diagnostics"
grep -Fq './scripts/direct-java-cli.sh open-book' "${raw_command_help_stdout}" ||
    die "developer raw JAR command help did not publish the direct Java launcher command"
if grep -Fq 'fingrind open-book' "${raw_command_help_stdout}"; then
    die "developer raw JAR command help leaked the generic launcher token"
fi

"${raw_java_wrapper}" capabilities --output json >"${raw_capabilities_stdout}" 2>"${raw_capabilities_stderr}" ||
    die "developer raw JAR capabilities failed"

[[ ! -s "${raw_capabilities_stderr}" ]] || die "developer raw JAR capabilities wrote diagnostics"
python3 - "${raw_capabilities_stdout}" "${expected_direct_java_runtime_distribution}" <<'PY'
import json
import pathlib
import sys

document = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
expected_distribution = sys.argv[2]
distribution = document["payload"]["environment"]["distribution"]["runtimeDistribution"]
provenance = document["payload"]["environment"]["sqlite"]["runtime"]["runtimeProvenance"]
if distribution != expected_distribution:
    raise SystemExit(
        "unexpected direct-Java runtime distribution: "
        + distribution
        + " != "
        + expected_distribution
    )
if provenance != "source-checkout-managed":
    raise SystemExit(
        "unexpected direct-Java runtime provenance: "
        + provenance
        + " != source-checkout-managed"
    )
PY

"${raw_java_wrapper}" \
    open-book \
    --book-file "${tmp_dir}/raw-jar.sqlite" \
    --book-key-file "${key_file}" \
    --entity-name "${entity_name}" \
    --functional-currency "${functional_currency}" \
    --fiscal-year-start "${fiscal_year_start}" >"${raw_open_stdout}" 2>"${raw_open_stderr}" ||
    die "developer raw JAR open-book failed"

[[ ! -s "${raw_open_stderr}" ]] || die "developer raw JAR open-book wrote diagnostics"
python3 - "${raw_open_stdout}" "${entity_name}" "${functional_currency}" "${fiscal_year_start}" <<'PY'
import json
import pathlib
import sys

document = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
payload = document["payload"]
book_identity = payload["bookIdentity"]
if document["status"] != "ok":
    raise SystemExit("developer raw JAR open-book did not return ok")
if book_identity["entityName"] != sys.argv[2]:
    raise SystemExit("developer raw JAR open-book returned the wrong entity name")
if book_identity["functionalCurrency"] != sys.argv[3]:
    raise SystemExit("developer raw JAR open-book returned the wrong functional currency")
if book_identity["fiscalYearStart"] != sys.argv[4]:
    raise SystemExit("developer raw JAR open-book returned the wrong fiscal year start")
PY

printf 'source-checkout launcher regression: success\n'
