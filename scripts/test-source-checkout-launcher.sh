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
readonly launcher="${repo_root}/cli/build/install/cli-shadow/bin/cli"
readonly raw_jar="${repo_root}/cli/build/libs/fingrind.jar"
readonly contract_values_reader="${repo_root}/scripts/read-contract-values.py"

[[ -f "${contract_values_reader}" ]] || die "missing contract-values reader"

tmp_dir="$(mktemp -d "${repo_root}/tmp/source-checkout-launcher.XXXXXX")"
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
raw_open_stdout="${tmp_dir}/raw-open.out"
raw_open_stderr="${tmp_dir}/raw-open.err"

readonly book_file="${tmp_dir}/Nested Dir/Books/ledger launcher.db"
readonly key_file="${tmp_dir}/Keys/book key.txt"
mkdir -p "$(dirname "${book_file}")" "$(dirname "${key_file}")"

"${launcher}" help >"${help_stdout}" 2>"${help_stderr}" ||
    die "source-checkout launcher help failed"

[[ ! -s "${help_stderr}" ]] || die "source-checkout launcher help wrote diagnostics"
grep -Fq 'Commands' "${help_stdout}" || die "source-checkout launcher help did not render command help"
if grep -Fq 'Unsupported runtime distribution: null' "${help_stdout}"; then
    die "source-checkout launcher baked a null runtime distribution into help output"
fi
if grep -Fq 'A restricted method in java.lang.foreign.SymbolLookup has been called' "${help_stdout}"; then
    die "source-checkout launcher help leaked the Java native-access warning"
fi

"${launcher}" capabilities --output json >"${capabilities_stdout}" 2>"${capabilities_stderr}" ||
    die "source-checkout launcher capabilities failed"

[[ ! -s "${capabilities_stderr}" ]] || die "source-checkout launcher capabilities wrote diagnostics"
python3 - "${capabilities_stdout}" "${expected_runtime_distribution}" <<'PY'
import json
import pathlib
import sys

document = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
expected_runtime_distribution = sys.argv[2]
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
PY

"${launcher}" generate-book-key-file --book-key-file "${key_file}" >"${key_stdout}" 2>"${key_stderr}" ||
    die "source-checkout launcher key generation failed"

[[ ! -s "${key_stderr}" ]] || die "source-checkout launcher key generation wrote diagnostics"
grep -Fq '"status":"ok"' "${key_stdout}" || die "source-checkout launcher key generation did not return ok"

"${launcher}" open-book --book-file "${book_file}" --book-key-file "${key_file}" >"${open_stdout}" 2>"${open_stderr}" ||
    die "source-checkout launcher open-book failed"

[[ ! -s "${open_stderr}" ]] || die "source-checkout launcher open-book wrote diagnostics"
grep -Fq '"status":"ok"' "${open_stdout}" || die "source-checkout launcher open-book did not return ok"

[[ -f "${raw_jar}" ]] || die "missing raw developer JAR"

java -jar "${raw_jar}" help >"${raw_help_stdout}" 2>"${raw_help_stderr}" ||
    die "developer raw JAR help failed"

[[ ! -s "${raw_help_stderr}" ]] || die "developer raw JAR help wrote diagnostics"
grep -Fq 'Developer Raw JAR' "${raw_help_stdout}" ||
    die "developer raw JAR help did not render the direct-Java quick start"
grep -Fq 'java -jar ./cli/build/libs/fingrind.jar' "${raw_help_stdout}" ||
    die "developer raw JAR help did not publish the direct Java launcher command"
if grep -Fq 'A restricted method in java.lang.foreign.SymbolLookup has been called' "${raw_help_stderr}"; then
    die "developer raw JAR help leaked the Java native-access warning"
fi

java -jar "${raw_jar}" \
    open-book \
    --book-file "${tmp_dir}/raw-jar.sqlite" \
    --book-key-file "${key_file}" >"${raw_open_stdout}" 2>"${raw_open_stderr}" ||
    die "developer raw JAR open-book failed"

[[ ! -s "${raw_open_stderr}" ]] || die "developer raw JAR open-book wrote diagnostics"
grep -Fq '"status":"ok"' "${raw_open_stdout}" || die "developer raw JAR open-book did not return ok"

printf 'source-checkout launcher regression: success\n'
