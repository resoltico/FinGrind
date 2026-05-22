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
grep -Fq 'fg_gradle_source_checkout_artifact_needs_refresh' "${launcher_wrapper}" || die \
    "POSIX source-checkout launcher wrapper no longer verifies raw-JAR freshness"
grep -Fq 'fg_gradle_source_checkout_artifact_needs_refresh' "${raw_java_wrapper}" || die \
    "POSIX direct-Java wrapper no longer verifies raw-JAR freshness"
grep -Fq 'Invoke-FinGrindEnsureSourceCheckoutArtifact' "${launcher_wrapper_ps1}" || die \
    "PowerShell source-checkout launcher wrapper no longer verifies raw-JAR freshness"
grep -Fq 'Invoke-FinGrindEnsureSourceCheckoutArtifact' "${raw_java_wrapper_ps1}" || die \
    "PowerShell direct-Java wrapper no longer verifies raw-JAR freshness"

# shellcheck source=/dev/null
source "${gradle_wrapper_support}"

is_darwin=false
case "$(uname -s)" in
    Darwin) is_darwin=true ;;
esac

readonly cli_build_dir="$(fg_gradle_project_build_dir "${repo_root}" 'cli' "${is_darwin}")"
readonly launcher="${cli_build_dir}/install/cli-shadow/bin/cli"
readonly raw_jar="${cli_build_dir}/libs/fingrind.jar"
readonly source_checkout_artifact_manifest="$(
    fg_gradle_source_checkout_artifact_manifest_path "${repo_root}" 'cli' "${is_darwin}"
)"

mkdir -p "${repo_tmp_dir}"
tmp_dir="$(mktemp -d "${repo_tmp_dir}/source-checkout-launcher.XXXXXX")"
cleanup() {
    chmod -R u+rwx "${tmp_dir}" 2>/dev/null || true
    rm -rf "${tmp_dir}" 2>/dev/null || true
}
trap cleanup EXIT

"${repo_root}/gradlew" :cli:installShadowDist prepareManagedSqlite --no-daemon --console=plain >/dev/null

[[ -x "${launcher}" ]] || die "missing generated source-checkout launcher"
[[ -f "${source_checkout_artifact_manifest}" ]] || die \
    "missing source-checkout artifact manifest"

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
readonly entity_form='COMPANY'
readonly owner_model='MULTI_OWNER'
readonly business_activity_tag='translation-services'
readonly functional_currency='EUR'
readonly fiscal_year_start='01-01'
readonly policy_profile='INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1'
[[ ! -e "$(dirname "${book_file}")" ]] || die "source-checkout launcher book parent started pre-created"
[[ ! -e "$(dirname "${key_file}")" ]] || die "source-checkout launcher key parent started pre-created"

progress 'source-checkout help surface'
"${launcher_wrapper}" help --output human >"${help_stdout}" 2>"${help_stderr}" ||
    die "source-checkout launcher help failed"

[[ ! -s "${help_stderr}" ]] || die "source-checkout launcher help wrote diagnostics"
grep -Fq 'Start Here' "${help_stdout}" ||
    die "source-checkout launcher help did not render the front-door guidance section"
grep -Fq 'Command Groups' "${help_stdout}" ||
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
python3 - "${environment_stdout}" "${expected_runtime_distribution}" <<'PY'
import json
import pathlib
import sys

document = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
expected_runtime_distribution = sys.argv[2]
sqlite = document["payload"]["sqlite"]
runtime = sqlite["runtime"]
actual_runtime_distribution = document["payload"]["distribution"]["runtimeDistribution"]
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

progress 'source-checkout capabilities surface'
"${launcher_wrapper}" capabilities --output json --detail full >"${capabilities_stdout}" \
    2>"${capabilities_stderr}" || die "source-checkout launcher capabilities failed"

[[ ! -s "${capabilities_stderr}" ]] || die "source-checkout launcher capabilities wrote diagnostics"
readonly expected_managed_runtime_failure_exit="$(
    python3 - "${capabilities_stdout}" <<'PY'
import json
import pathlib
import sys

document = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
payload = document["payload"]
if payload["detail"] != "full":
    raise SystemExit("source-checkout launcher capabilities did not expose the full contract")
response_model = payload["fullContract"]["responseModel"]
for descriptor in response_model["errorDescriptors"]:
    if descriptor["code"] != "managed-runtime-failure":
        continue
    exit_code = descriptor.get("exitCode")
    if not isinstance(exit_code, int) or isinstance(exit_code, bool) or exit_code < 0:
        raise SystemExit(
            "source-checkout launcher capabilities published an invalid managed-runtime-failure exitCode"
        )
    print(exit_code)
    break
else:
    raise SystemExit(
        "source-checkout launcher capabilities omitted the managed-runtime-failure error descriptor"
    )
PY
)"

progress 'source-checkout request template'
"${launcher_wrapper}" print-request-template >"${template_request_stdout}" \
    2>"${template_request_stderr}" || die "source-checkout launcher print-request-template failed"

[[ ! -s "${template_request_stderr}" ]] || die \
    "source-checkout launcher print-request-template wrote diagnostics"
python3 - "${template_request_stdout}" <<'PY'
import json
import pathlib
import sys

document = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
if document["entryKind"] != "CASH_REVENUE":
    raise SystemExit("source-checkout launcher request template did not expose CASH_REVENUE")
if "postingKind" in document:
    raise SystemExit("source-checkout launcher request template leaked retired postingKind")
if "lines" in document:
    raise SystemExit("source-checkout launcher request template leaked retired journal lines")
evidence = document["evidence"]["sourceDocuments"][0]
for required_field in ("documentDate", "capturedAt", "storageLocator", "contentSha256"):
    if required_field not in evidence:
        raise SystemExit(
            "source-checkout launcher request template omitted retained evidence field "
            + required_field
        )
PY

progress 'source-checkout plan template'
"${launcher_wrapper}" print-plan-template >"${template_plan_stdout}" \
    2>"${template_plan_stderr}" || die "source-checkout launcher print-plan-template failed"

[[ ! -s "${template_plan_stderr}" ]] || die \
    "source-checkout launcher print-plan-template wrote diagnostics"
python3 - "${template_plan_stdout}" <<'PY'
import json
import pathlib
import sys

document = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
open_book = document["steps"][0]["openBook"]
if "reportingObligationStatus" in open_book:
    raise SystemExit("source-checkout launcher plan template leaked retired reporting status")
if "accountingBasis" in open_book:
    raise SystemExit("source-checkout launcher plan template leaked retired accounting basis")
if open_book["policyProfile"] != "INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1":
    raise SystemExit("source-checkout launcher plan template omitted policyProfile")
declare_account = document["steps"][1]["declareAccount"]
if declare_account["accountNodeKind"] != "POSTABLE":
    raise SystemExit("source-checkout launcher plan template omitted accountNodeKind")
post_entry = document["steps"][3]["posting"]
if post_entry["entryKind"] != "CASH_REVENUE":
    raise SystemExit("source-checkout launcher plan template did not expose typed post-entry")
if "postingKind" in post_entry:
    raise SystemExit("source-checkout launcher plan template leaked retired postingKind")
PY

progress 'direct-java request template'
"${raw_java_wrapper}" print-request-template >"${raw_template_request_stdout}" \
    2>"${raw_template_request_stderr}" || die "developer direct-Java print-request-template failed"

[[ ! -s "${raw_template_request_stderr}" ]] || die \
    "developer direct-Java print-request-template wrote diagnostics"
python3 - "${raw_template_request_stdout}" <<'PY'
import json
import pathlib
import sys

document = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
if document["entryKind"] != "CASH_REVENUE":
    raise SystemExit("developer direct-Java request template did not expose CASH_REVENUE")
if "postingKind" in document:
    raise SystemExit("developer direct-Java request template leaked retired postingKind")
PY

touch -t 200001010000 "${source_checkout_artifact_manifest}"

progress 'source-checkout self-refresh'
"${launcher_wrapper}" print-request-template >"${healed_template_request_stdout}" \
    2>"${healed_template_request_stderr}" || die \
    "source-checkout launcher did not self-refresh after manifest corruption"

[[ ! -s "${healed_template_request_stderr}" ]] || die \
    "source-checkout launcher self-refresh wrote diagnostics"
python3 - "${healed_template_request_stdout}" <<'PY'
import json
import pathlib
import sys

document = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
if document["entryKind"] != "CASH_REVENUE":
    raise SystemExit("source-checkout launcher self-refresh did not restore the typed template")
if "postingKind" in document:
    raise SystemExit("source-checkout launcher self-refresh restored a retired postingKind")
PY

progress 'source-checkout key generation'
"${launcher_wrapper}" generate-book-key-file --book-key-file "${key_file}" --output json >"${key_stdout}" 2>"${key_stderr}" ||
    die "source-checkout launcher key generation failed"

[[ ! -s "${key_stderr}" ]] || die "source-checkout launcher key generation wrote diagnostics"
python3 - "${key_stdout}" <<'PY'
import json
import pathlib
import sys

document = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
if document["status"] != "ok":
    raise SystemExit("source-checkout launcher key generation did not return ok")
PY
python3 - "${key_file}" <<'PY'
import os
import pathlib
import stat
import sys

key_path = pathlib.Path(sys.argv[1])
parent_mode = stat.S_IMODE(os.stat(key_path.parent).st_mode)
if parent_mode != 0o700:
    raise SystemExit(
        "source-checkout launcher key generation did not create an owner-only parent directory"
    )
PY

progress 'source-checkout open-book'
"${launcher_wrapper}" \
    open-book \
    --book-file "${book_file}" \
    --book-key-file "${key_file}" \
    --entity-name "${entity_name}" \
    --entity-form "${entity_form}" \
    --owner-model "${owner_model}" \
    --business-activity-tag "${business_activity_tag}" \
    --functional-currency "${functional_currency}" \
    --fiscal-year-start "${fiscal_year_start}" \
    --policy-profile "${policy_profile}" \
    --output json >"${open_stdout}" 2>"${open_stderr}" ||
    die "source-checkout launcher open-book failed"

[[ ! -s "${open_stderr}" ]] || die "source-checkout launcher open-book wrote diagnostics"
python3 - "${book_file}" <<'PY'
import os
import pathlib
import stat
import sys

book_path = pathlib.Path(sys.argv[1])
parent_mode = stat.S_IMODE(os.stat(book_path.parent).st_mode)
if parent_mode != 0o700:
    raise SystemExit(
        "source-checkout launcher open-book did not create an owner-only parent directory"
    )
PY
python3 - "${open_stdout}" "${entity_name}" "${entity_form}" "${owner_model}" "${business_activity_tag}" "${functional_currency}" "${fiscal_year_start}" "${policy_profile}" <<'PY'
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
if book_identity["entityForm"] != sys.argv[3]:
    raise SystemExit("source-checkout launcher open-book returned the wrong entity form")
if book_identity["ownerModel"] != sys.argv[4]:
    raise SystemExit("source-checkout launcher open-book returned the wrong owner model")
if book_identity["businessActivityTags"] != [sys.argv[5]]:
    raise SystemExit("source-checkout launcher open-book returned the wrong business activity tags")
if book_identity["functionalCurrency"] != sys.argv[6]:
    raise SystemExit("source-checkout launcher open-book returned the wrong functional currency")
if book_identity["fiscalYearStart"] != sys.argv[7]:
    raise SystemExit("source-checkout launcher open-book returned the wrong fiscal year start")
if book_identity["policyProfile"] != sys.argv[8]:
    raise SystemExit("source-checkout launcher open-book returned the wrong accounting policy profile")
PY

[[ -f "${raw_jar}" ]] || die "missing developer application JAR"

progress 'direct-java help surface'
"${raw_java_wrapper}" help --output human >"${raw_help_stdout}" 2>"${raw_help_stderr}" ||
    die "developer direct-Java help failed"

[[ ! -s "${raw_help_stderr}" ]] || die "developer direct-Java help wrote diagnostics"
grep -Fq 'Start Here' "${raw_help_stdout}" ||
    die "developer direct-Java help did not render the front-door guidance section"
if grep -Fq 'Developer Raw JAR' "${raw_help_stdout}"; then
    die "developer direct-Java help regressed back to the retired runtime-specific quick-start block"
fi
if grep -Fq 'A restricted method in java.lang.foreign.SymbolLookup has been called' "${raw_help_stderr}"; then
    die "developer direct-Java help leaked the Java native-access warning"
fi

progress 'direct-java command help surface'
"${raw_java_wrapper}" help open-book --output human >"${raw_command_help_stdout}" \
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
python3 - "${raw_environment_stdout}" "${expected_direct_java_runtime_distribution}" <<'PY'
import json
import pathlib
import sys

document = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
expected_distribution = sys.argv[2]
distribution = document["payload"]["distribution"]["runtimeDistribution"]
provenance = document["payload"]["sqlite"]["runtime"]["runtimeProvenance"]
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

progress 'direct-java open-book'
"${raw_java_wrapper}" \
    open-book \
    --book-file "${tmp_dir}/raw-jar.sqlite" \
    --book-key-file "${key_file}" \
    --entity-name "${entity_name}" \
    --entity-form "${entity_form}" \
    --owner-model "${owner_model}" \
    --business-activity-tag "${business_activity_tag}" \
    --functional-currency "${functional_currency}" \
    --fiscal-year-start "${fiscal_year_start}" \
    --policy-profile "${policy_profile}" \
    --output json >"${raw_open_stdout}" 2>"${raw_open_stderr}" ||
    die "developer direct-Java open-book failed"

[[ ! -s "${raw_open_stderr}" ]] || die "developer direct-Java open-book wrote diagnostics"
python3 - "${raw_open_stdout}" "${entity_name}" "${entity_form}" "${owner_model}" "${business_activity_tag}" "${functional_currency}" "${fiscal_year_start}" "${policy_profile}" <<'PY'
import json
import pathlib
import sys

document = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
payload = document["payload"]
book_identity = payload["bookIdentity"]
if document["status"] != "ok":
    raise SystemExit("developer direct-Java open-book did not return ok")
if book_identity["entityName"] != sys.argv[2]:
    raise SystemExit("developer direct-Java open-book returned the wrong entity name")
if book_identity["entityForm"] != sys.argv[3]:
    raise SystemExit("developer direct-Java open-book returned the wrong entity form")
if book_identity["ownerModel"] != sys.argv[4]:
    raise SystemExit("developer direct-Java open-book returned the wrong owner model")
if book_identity["businessActivityTags"] != [sys.argv[5]]:
    raise SystemExit("developer direct-Java open-book returned the wrong business activity tags")
if book_identity["functionalCurrency"] != sys.argv[6]:
    raise SystemExit("developer direct-Java open-book returned the wrong functional currency")
if book_identity["fiscalYearStart"] != sys.argv[7]:
    raise SystemExit("developer direct-Java open-book returned the wrong fiscal year start")
if book_identity["policyProfile"] != sys.argv[8]:
    raise SystemExit("developer direct-Java open-book returned the wrong accounting policy profile")
PY

progress 'raw java -jar help surface'
java -jar "${raw_jar}" help --output human >"${raw_jar_help_stdout}" 2>"${raw_jar_help_stderr}" ||
    die "raw java -jar help failed"

[[ ! -s "${raw_jar_help_stderr}" ]] || die "raw java -jar help wrote diagnostics"
grep -Fq 'java --enable-native-access=fingrind --module-path fingrind.jar --module' \
    "${raw_jar_help_stdout}" || die \
    "raw java -jar help did not publish the modular launcher prefix"
grep -Fq 'fingrind/dev.erst.fingrind.cli.App help <command>' "${raw_jar_help_stdout}" || die \
    "raw java -jar help did not publish the modular launcher command token"
if grep -Fq './scripts/direct-java-cli.sh help' "${raw_jar_help_stdout}"; then
    die "raw java -jar help leaked the source-checkout direct-Java wrapper"
fi

progress 'raw java -jar environment surface'
java -jar "${raw_jar}" environment --output json >"${raw_jar_environment_stdout}" \
    2>"${raw_jar_environment_stderr}" || die "raw java -jar environment failed"

[[ ! -s "${raw_jar_environment_stderr}" ]] || die "raw java -jar environment wrote diagnostics"
python3 - "${raw_jar_environment_stdout}" "${expected_direct_java_runtime_distribution}" <<'PY'
import json
import pathlib
import sys

document = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
payload = document["payload"]
distribution = payload["distribution"]["runtimeDistribution"]
runtime = payload["sqlite"]["runtime"]
if distribution != sys.argv[2]:
    raise SystemExit(
        "unexpected raw java -jar runtime distribution: "
        + distribution
        + " != "
        + sys.argv[2]
    )
if runtime["status"] != "unavailable":
    raise SystemExit("raw java -jar environment did not report an unavailable SQLite runtime")
issue = runtime["runtimeIssue"]
if "--enable-native-access=ALL-UNNAMED" not in issue:
    raise SystemExit("raw java -jar environment did not surface the native-access repair flag")
PY

progress 'raw java -jar runtime failure envelope'
set +e
java -jar "${raw_jar}" \
    open-book \
    --book-file "${tmp_dir}/raw-jar-direct.sqlite" \
    --book-key-file "${key_file}" \
    --entity-name "${entity_name}" \
    --entity-form "${entity_form}" \
    --owner-model "${owner_model}" \
    --business-activity-tag "${business_activity_tag}" \
    --functional-currency "${functional_currency}" \
    --fiscal-year-start "${fiscal_year_start}" \
    --policy-profile "${policy_profile}" \
    --output json >"${raw_jar_open_stdout}" 2>"${raw_jar_open_stderr}"
raw_jar_open_exit=$?
set -e

[[ "${raw_jar_open_exit}" -eq "${expected_managed_runtime_failure_exit}" ]] || die \
    "raw java -jar open-book returned ${raw_jar_open_exit}; expected published managed-runtime-failure exit ${expected_managed_runtime_failure_exit}"
[[ ! -s "${raw_jar_open_stderr}" ]] || die "raw java -jar open-book wrote diagnostics"
[[ ! -f "${tmp_dir}/raw-jar-direct.sqlite" ]] || die \
    "raw java -jar open-book created a book despite missing native access"
python3 - "${raw_jar_open_stdout}" <<'PY'
import json
import pathlib
import sys

document = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
if document["status"] != "error":
    raise SystemExit("raw java -jar open-book did not fail with an error envelope")
if document["code"] != "managed-runtime-failure":
    raise SystemExit("raw java -jar open-book did not classify the failure as managed-runtime-failure")
message = document["message"]
hint = document.get("hint", "")
if "--enable-native-access=ALL-UNNAMED" not in message:
    raise SystemExit("raw java -jar open-book did not report the native-access repair flag")
if "supported launchers" not in message and "supported launchers" not in hint:
    raise SystemExit("raw java -jar open-book did not direct the operator toward supported launchers")
PY

printf 'source-checkout launcher regression: success\n'
