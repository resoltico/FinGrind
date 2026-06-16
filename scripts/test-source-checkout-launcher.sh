#!/usr/bin/env bash
# Reproduce and guard the source-checkout installed launcher contract.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

progress() {
    printf 'source-checkout launcher check: %s\n' "$1"
}

normalized_file_contains() {
    local needle="$1"
    local file_path="$2"
    python3 "${launcher_contract_test_support}" normalized-contains "$needle" "$file_path"
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
readonly launcher_contract_test_support="${repo_root}/scripts/source_checkout_launcher_contract_test_support.py"
readonly gradle_wrapper_support="${repo_root}/scripts/gradle-wrapper-support.sh"
readonly launcher_wrapper="${repo_root}/scripts/source-checkout-cli.sh"
readonly launcher_wrapper_entrypoint="${repo_root}/scripts/source-checkout-cli-entrypoint.sh"
readonly launcher_wrapper_common="${repo_root}/scripts/source-checkout-cli-common.sh"
readonly launcher_wrapper_ps1="${repo_root}/scripts/source-checkout-cli.ps1"
readonly launcher_wrapper_common_ps1="${repo_root}/scripts/source-checkout-cli-common.ps1"
readonly raw_java_wrapper="${repo_root}/scripts/direct-java-cli.sh"
readonly raw_java_wrapper_ps1="${repo_root}/scripts/direct-java-cli.ps1"
readonly gradle_wrapper_support_ps1="${repo_root}/scripts/gradle-wrapper-support.ps1"
readonly repo_tmp_dir="${repo_root}/tmp"

[[ -f "${contract_values_reader}" ]] || die "missing contract-values reader"
[[ -f "${launcher_contract_test_support}" ]] || die "missing source-checkout launcher test support helper"
[[ -f "${gradle_wrapper_support}" ]] || die "missing Gradle wrapper support helper"
[[ -x "${launcher_wrapper}" ]] || die "missing POSIX source-checkout launcher wrapper"
[[ -f "${launcher_wrapper_entrypoint}" ]] || die "missing POSIX source-checkout launcher entrypoint helper"
[[ -f "${launcher_wrapper_common}" ]] || die "missing POSIX source-checkout launcher shared helper"
[[ -f "${launcher_wrapper_ps1}" ]] || die "missing PowerShell source-checkout launcher wrapper"
[[ -f "${launcher_wrapper_common_ps1}" ]] || die "missing PowerShell source-checkout launcher shared helper"
[[ -x "${raw_java_wrapper}" ]] || die "missing POSIX direct-Java wrapper"
[[ -f "${raw_java_wrapper_ps1}" ]] || die "missing PowerShell direct-Java wrapper"
[[ -f "${gradle_wrapper_support_ps1}" ]] || die "missing PowerShell Gradle wrapper support helper"
grep -Fq 'source-checkout-cli-common.ps1' "${launcher_wrapper_ps1}" || die \
    "PowerShell source-checkout launcher wrapper no longer delegates through the shared wrapper owner"
grep -Fq 'source-checkout-cli-common.ps1' "${raw_java_wrapper_ps1}" || die \
    "PowerShell direct-Java wrapper no longer delegates through the shared wrapper owner"
grep -Fq 'gradle-wrapper-support.ps1' "${launcher_wrapper_common_ps1}" || die \
    "PowerShell launcher shared helper no longer sources the shared Gradle wrapper helper"
grep -Fq 'source-checkout-cli-entrypoint.sh' "${launcher_wrapper}" || die \
    "POSIX source-checkout launcher wrapper no longer delegates through the shared wrapper entrypoint owner"
grep -Fq 'source-checkout-cli-entrypoint.sh' "${raw_java_wrapper}" || die \
    "POSIX direct-Java wrapper no longer delegates through the shared wrapper entrypoint owner"
grep -Fq 'source-checkout-cli-common.sh' "${launcher_wrapper_entrypoint}" || die \
    "POSIX launcher entrypoint helper no longer delegates through the runtime helper owner"
grep -Fq 'fg_gradle_source_checkout_runtime_manifest_path' "${launcher_wrapper_common}" || die \
    "POSIX launcher shared helper no longer resolves the source-checkout runtime manifest"
grep -Fq 'Invoke-FinGrindEnsureCliWrapperRuntime' "${launcher_wrapper_common_ps1}" || die \
    "PowerShell launcher shared helper no longer verifies wrapper runtime freshness"
grep -Fq 'Read-FinGrindSourceCheckoutRuntimeManifest' "${launcher_wrapper_common_ps1}" || die \
    "PowerShell launcher shared helper no longer loads the source-checkout runtime manifest"

# shellcheck source=/dev/null
source "${gradle_wrapper_support}"

is_darwin=false
case "$(uname -s)" in
    Darwin) is_darwin=true ;;
esac

readonly cli_build_dir="$(fg_gradle_project_build_dir "${repo_root}" 'cli' "${is_darwin}")"
readonly raw_jar="${cli_build_dir}/libs/fingrind.jar"
readonly source_checkout_runtime_manifest="$(
    fg_gradle_source_checkout_runtime_manifest_path "${repo_root}" 'cli' "${is_darwin}"
)"

mkdir -p "${repo_tmp_dir}"
tmp_dir="$(mktemp -d "${repo_tmp_dir}/source-checkout-launcher.XXXXXX")"
cleanup() {
    chmod -R u+rwx "${tmp_dir}" 2>/dev/null || true
    rm -rf "${tmp_dir}" 2>/dev/null || true
}
trap cleanup EXIT

"${repo_root}/gradlew" \
    :cli:prepareSourceCheckoutCliRuntime \
    --no-daemon \
    --console=plain >/dev/null

[[ -f "${source_checkout_runtime_manifest}" ]] || die \
    "missing source-checkout runtime manifest"

readonly expected_runtime_distribution="$(
    python3 "${contract_values_reader}" \
        | python3 "${launcher_contract_test_support}" contract-value sourceCheckoutRuntimeDistribution
)"
readonly expected_direct_java_runtime_distribution="$(
    python3 "${contract_values_reader}" \
        | python3 "${launcher_contract_test_support}" contract-value directJavaRuntimeDistribution
)"

help_stdout="${tmp_dir}/help.out"
help_stderr="${tmp_dir}/help.err"
environment_stdout="${tmp_dir}/environment.out"
environment_stderr="${tmp_dir}/environment.err"
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
raw_environment_stdout="${tmp_dir}/raw-environment.out"
raw_environment_stderr="${tmp_dir}/raw-environment.err"
raw_open_stdout="${tmp_dir}/raw-open.out"
raw_open_stderr="${tmp_dir}/raw-open.err"
template_request_stdout="${tmp_dir}/template-request.out"
template_request_stderr="${tmp_dir}/template-request.err"
template_plan_stdout="${tmp_dir}/template-plan.out"
template_plan_stderr="${tmp_dir}/template-plan.err"
healed_template_request_stdout="${tmp_dir}/healed-template-request.out"
healed_template_request_stderr="${tmp_dir}/healed-template-request.err"
raw_template_request_stdout="${tmp_dir}/raw-template-request.out"
raw_template_request_stderr="${tmp_dir}/raw-template-request.err"
raw_jar_help_stdout="${tmp_dir}/raw-jar-help.out"
raw_jar_help_stderr="${tmp_dir}/raw-jar-help.err"
raw_jar_environment_stdout="${tmp_dir}/raw-jar-environment.out"
raw_jar_environment_stderr="${tmp_dir}/raw-jar-environment.err"
raw_jar_open_stdout="${tmp_dir}/raw-jar-open.out"
raw_jar_open_stderr="${tmp_dir}/raw-jar-open.err"

readonly book_file="${tmp_dir}/Nested Dir/Books/ledger launcher.db"
readonly key_file="${tmp_dir}/Keys/book key.txt"
readonly entity_name='Launcher Smoke Co'
readonly functional_currency='EUR'
readonly fiscal_year_start='01-01'
[[ ! -e "$(dirname "${book_file}")" ]] || die "source-checkout launcher book parent started pre-created"
[[ ! -e "$(dirname "${key_file}")" ]] || die "source-checkout launcher key parent started pre-created"

progress 'source-checkout help surface'
"${launcher_wrapper}" help --output text >"${help_stdout}" 2>"${help_stderr}" ||
    die "source-checkout launcher help failed"

[[ ! -s "${help_stderr}" ]] || die "source-checkout launcher help wrote diagnostics"
grep -Fq 'Quick Start' "${help_stdout}" ||
    die "source-checkout launcher help did not render the front-door guidance section"
grep -Fq 'Command Catalog' "${help_stdout}" ||
    die "source-checkout launcher help did not render the grouped command catalog"
if grep -Fq 'Unsupported runtime distribution: null' "${help_stdout}"; then
    die "source-checkout launcher baked a null runtime distribution into help output"
fi
if grep -Fq 'A restricted method in java.lang.foreign.SymbolLookup has been called' "${help_stdout}"; then
    die "source-checkout launcher help leaked the Java native-access warning"
fi

progress 'source-checkout environment surface'
"${launcher_wrapper}" environment --output json >"${environment_stdout}" 2>"${environment_stderr}" ||
    die "source-checkout launcher environment failed"

[[ ! -s "${environment_stderr}" ]] || die "source-checkout launcher environment wrote diagnostics"
python3 "${launcher_contract_test_support}" \
    assert-runtime-environment \
    --document "${environment_stdout}" \
    --expected-distribution "${expected_runtime_distribution}" \
    --expected-status ready \
    --expected-provenance source-checkout-managed \
    --label 'source-checkout launcher'

progress 'source-checkout capabilities surface'
"${launcher_wrapper}" capabilities --output json --detail full >"${capabilities_stdout}" \
    2>"${capabilities_stderr}" || die "source-checkout launcher capabilities failed"

[[ ! -s "${capabilities_stderr}" ]] || die "source-checkout launcher capabilities wrote diagnostics"
readonly expected_managed_runtime_failure_exit="$(
    python3 "${launcher_contract_test_support}" \
        managed-runtime-failure-exit "${capabilities_stdout}"
)"

progress 'source-checkout request template'
"${launcher_wrapper}" print-request-template >"${template_request_stdout}" \
    2>"${template_request_stderr}" || die "source-checkout launcher print-request-template failed"

[[ ! -s "${template_request_stderr}" ]] || die \
    "source-checkout launcher print-request-template wrote diagnostics"
python3 "${launcher_contract_test_support}" \
    assert-request-template \
    --document "${template_request_stdout}" \
    --label 'source-checkout launcher' \
    --forbid-lines \
    --require-evidence-fields

progress 'source-checkout plan template'
"${launcher_wrapper}" print-plan-template >"${template_plan_stdout}" \
    2>"${template_plan_stderr}" || die "source-checkout launcher print-plan-template failed"

[[ ! -s "${template_plan_stderr}" ]] || die \
    "source-checkout launcher print-plan-template wrote diagnostics"
python3 "${launcher_contract_test_support}" assert-plan-template "${template_plan_stdout}"

progress 'direct-java request template'
"${raw_java_wrapper}" print-request-template >"${raw_template_request_stdout}" \
    2>"${raw_template_request_stderr}" || die "developer direct-Java print-request-template failed"

[[ ! -s "${raw_template_request_stderr}" ]] || die \
    "developer direct-Java print-request-template wrote diagnostics"
python3 "${launcher_contract_test_support}" \
    assert-request-template \
    --document "${raw_template_request_stdout}" \
    --label 'developer direct-Java'

python3 "${launcher_contract_test_support}" \
    corrupt-runtime-manifest "${source_checkout_runtime_manifest}"

progress 'source-checkout self-refresh'
"${launcher_wrapper}" print-request-template >"${healed_template_request_stdout}" \
    2>"${healed_template_request_stderr}" || die \
    "source-checkout launcher did not self-refresh after manifest corruption"

[[ ! -s "${healed_template_request_stderr}" ]] || die \
    "source-checkout launcher self-refresh wrote diagnostics"
python3 "${launcher_contract_test_support}" \
    assert-request-template \
    --document "${healed_template_request_stdout}" \
    --label 'source-checkout launcher self-refresh'

progress 'source-checkout key generation'
"${launcher_wrapper}" generate-book-key-file --book-key-file "${key_file}" --output json >"${key_stdout}" 2>"${key_stderr}" ||
    die "source-checkout launcher key generation failed"

[[ ! -s "${key_stderr}" ]] || die "source-checkout launcher key generation wrote diagnostics"
python3 "${launcher_contract_test_support}" \
    assert-status-ok \
    --document "${key_stdout}" \
    --label 'source-checkout launcher key generation'
python3 "${launcher_contract_test_support}" \
    assert-owner-only-parent \
    --path "${key_file}" \
    --label 'source-checkout launcher key generation'

progress 'source-checkout open-book'
"${launcher_wrapper}" \
    open-book \
    --book-file "${book_file}" \
    --book-key-file "${key_file}" \
    --entity-name "${entity_name}" \
    --functional-currency "${functional_currency}" \
    --fiscal-year-start "${fiscal_year_start}" \
    --output json >"${open_stdout}" 2>"${open_stderr}" ||
    die "source-checkout launcher open-book failed"

[[ ! -s "${open_stderr}" ]] || die "source-checkout launcher open-book wrote diagnostics"
python3 "${launcher_contract_test_support}" \
    assert-owner-only-parent \
    --path "${book_file}" \
    --label 'source-checkout launcher open-book'
python3 "${launcher_contract_test_support}" \
    assert-open-book \
    --document "${open_stdout}" \
    --label 'source-checkout launcher' \
    --entity-name "${entity_name}" \
    --functional-currency "${functional_currency}" \
    --fiscal-year-start "${fiscal_year_start}"

[[ -f "${raw_jar}" ]] || die "missing developer application JAR"

progress 'direct-java help surface'
"${raw_java_wrapper}" help --output text >"${raw_help_stdout}" 2>"${raw_help_stderr}" ||
    die "developer direct-Java help failed"

[[ ! -s "${raw_help_stderr}" ]] || die "developer direct-Java help wrote diagnostics"
grep -Fq 'Quick Start' "${raw_help_stdout}" ||
    die "developer direct-Java help did not render the front-door guidance section"
if grep -Fq 'Developer Raw JAR' "${raw_help_stdout}"; then
    die "developer direct-Java help regressed back to the retired runtime-specific quick-start block"
fi
if grep -Fq 'A restricted method in java.lang.foreign.SymbolLookup has been called' "${raw_help_stderr}"; then
    die "developer direct-Java help leaked the Java native-access warning"
fi

progress 'direct-java command help surface'
"${raw_java_wrapper}" help open-book --output text >"${raw_command_help_stdout}" \
    2>"${raw_command_help_stderr}" || die "developer direct-Java command help failed"

[[ ! -s "${raw_command_help_stderr}" ]] || die "developer direct-Java command help wrote diagnostics"
grep -Fq './scripts/direct-java-cli.sh open-book' "${raw_command_help_stdout}" ||
    die "developer direct-Java command help did not publish the direct Java launcher command"
if grep -Fq 'fingrind open-book' "${raw_command_help_stdout}"; then
    die "developer direct-Java command help leaked the generic launcher token"
fi

progress 'direct-java environment surface'
"${raw_java_wrapper}" environment --output json >"${raw_environment_stdout}" 2>"${raw_environment_stderr}" ||
    die "developer direct-Java environment failed"

[[ ! -s "${raw_environment_stderr}" ]] || die "developer direct-Java environment wrote diagnostics"
python3 "${launcher_contract_test_support}" \
    assert-runtime-environment \
    --document "${raw_environment_stdout}" \
    --expected-distribution "${expected_direct_java_runtime_distribution}" \
    --expected-status ready \
    --expected-provenance source-checkout-managed \
    --label 'developer direct-Java'

progress 'direct-java open-book'
"${raw_java_wrapper}" \
    open-book \
    --book-file "${tmp_dir}/raw-jar.sqlite" \
    --book-key-file "${key_file}" \
    --entity-name "${entity_name}" \
    --functional-currency "${functional_currency}" \
    --fiscal-year-start "${fiscal_year_start}" \
    --output json >"${raw_open_stdout}" 2>"${raw_open_stderr}" ||
    die "developer direct-Java open-book failed"

[[ ! -s "${raw_open_stderr}" ]] || die "developer direct-Java open-book wrote diagnostics"
python3 "${launcher_contract_test_support}" \
    assert-open-book \
    --document "${raw_open_stdout}" \
    --label 'developer direct-Java' \
    --entity-name "${entity_name}" \
    --functional-currency "${functional_currency}" \
    --fiscal-year-start "${fiscal_year_start}"

progress 'raw java -jar help surface'
java -jar "${raw_jar}" help --output text >"${raw_jar_help_stdout}" 2>"${raw_jar_help_stderr}" ||
    die "raw java -jar help failed"

[[ ! -s "${raw_jar_help_stderr}" ]] || die "raw java -jar help wrote diagnostics"
normalized_file_contains 'java --enable-native-access=dev.erst.fingrind.cli --module-path fingrind.jar --module' \
    "${raw_jar_help_stdout}" || die \
    "raw java -jar help did not publish the modular launcher prefix"
normalized_file_contains 'dev.erst.fingrind.cli/dev.erst.fingrind.cli.App help <command>' "${raw_jar_help_stdout}" || die \
    "raw java -jar help did not publish the modular launcher command token"
if grep -Fq './scripts/direct-java-cli.sh help' "${raw_jar_help_stdout}"; then
    die "raw java -jar help leaked the source-checkout direct-Java wrapper"
fi

progress 'raw java -jar environment surface'
java -jar "${raw_jar}" environment --output json >"${raw_jar_environment_stdout}" \
    2>"${raw_jar_environment_stderr}" || die "raw java -jar environment failed"

[[ ! -s "${raw_jar_environment_stderr}" ]] || die "raw java -jar environment wrote diagnostics"
python3 "${launcher_contract_test_support}" \
    assert-runtime-environment \
    --document "${raw_jar_environment_stdout}" \
    --expected-distribution "${expected_direct_java_runtime_distribution}" \
    --expected-status unavailable \
    --label 'raw java -jar' \
    --issue-substring 'supported FinGrind bundle launcher' \
    --issue-substring 'supported FinGrind launcher surface' \
    --issue-substring ':cli:prepareSourceCheckoutCliRuntime'

progress 'raw java -jar runtime failure envelope'
set +e
java -jar "${raw_jar}" \
    open-book \
    --book-file "${tmp_dir}/raw-jar-direct.sqlite" \
    --book-key-file "${key_file}" \
    --entity-name "${entity_name}" \
    --functional-currency "${functional_currency}" \
    --fiscal-year-start "${fiscal_year_start}" \
    --output json >"${raw_jar_open_stdout}" 2>"${raw_jar_open_stderr}"
raw_jar_open_exit=$?
set -e

[[ "${raw_jar_open_exit}" -eq "${expected_managed_runtime_failure_exit}" ]] || die \
    "raw java -jar open-book returned ${raw_jar_open_exit}; expected published managed-runtime-failure exit ${expected_managed_runtime_failure_exit}"
[[ ! -s "${raw_jar_open_stdout}" ]] || die "raw java -jar open-book wrote primary output on runtime failure"
[[ ! -f "${tmp_dir}/raw-jar-direct.sqlite" ]] || die \
    "raw java -jar open-book created a book despite missing native access"
python3 "${launcher_contract_test_support}" \
    assert-runtime-failure-envelope \
    --document "${raw_jar_open_stderr}" \
    --label 'raw java -jar open-book' \
    --stream diagnostics

printf 'source-checkout launcher regression: success\n'
