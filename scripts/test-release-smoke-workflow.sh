#!/usr/bin/env bash
# Guard the shared release-smoke workflow wiring so Bash and PowerShell stay delegated.

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
readonly workflow_py="${repo_root}/scripts/release-smoke-workflow.py"
readonly workflow_package_dir="${repo_root}/scripts/release_smoke_workflow"
readonly python_runtime_support="${repo_root}/scripts/python-runtime-support.sh"
readonly common_support_sh="${repo_root}/scripts/release-smoke-common.sh"
readonly workflow_support_sh="${repo_root}/scripts/release-smoke-workflow-support.sh"
readonly bundle_support_sh="${repo_root}/scripts/release-smoke-support.sh"
readonly bundle_office_worker_ps1="${repo_root}/scripts/bundle-smoke-office-worker.ps1"
readonly bundle_command_bridge_ps1="${repo_root}/scripts/bundle-smoke-command-bridge.ps1"
readonly bundle_smoke_sh="${repo_root}/scripts/bundle-smoke.sh"
readonly docker_smoke_sh="${repo_root}/scripts/docker-smoke.sh"

[[ -f "${workflow_py}" ]] || die "missing shared release smoke workflow runner at ${workflow_py}"
[[ -d "${workflow_package_dir}" ]] || die "missing release smoke workflow package at ${workflow_package_dir}"
[[ -f "${python_runtime_support}" ]] || die "missing Python runtime support helper at ${python_runtime_support}"
[[ -f "${common_support_sh}" ]] || die "missing Bash release smoke common helper at ${common_support_sh}"
[[ -f "${workflow_support_sh}" ]] || die "missing Bash release smoke workflow support helper at ${workflow_support_sh}"
[[ -f "${bundle_support_sh}" ]] || die "missing Bash release smoke support wrapper at ${bundle_support_sh}"
[[ -f "${bundle_office_worker_ps1}" ]] || die "missing PowerShell office-worker wrapper at ${bundle_office_worker_ps1}"
[[ -f "${bundle_command_bridge_ps1}" ]] || die "missing PowerShell command bridge at ${bundle_command_bridge_ps1}"
[[ -f "${bundle_smoke_sh}" ]] || die "missing Bash bundle smoke entrypoint at ${bundle_smoke_sh}"
[[ -f "${docker_smoke_sh}" ]] || die "missing Bash Docker smoke entrypoint at ${docker_smoke_sh}"
grep -Fq 'release-smoke-workflow-support.sh' "${bundle_support_sh}" || die \
    "release-smoke-support.sh no longer sources the shared workflow support helper"
grep -Fq 'release-smoke-workflow.py' "${workflow_support_sh}" || die \
    "release-smoke-workflow-support.sh no longer delegates to the shared Python workflow owner"
grep -Fq 'release_smoke_workflow.runner import main' "${workflow_py}" || die \
    "release-smoke-workflow.py no longer delegates into the release_smoke_workflow package"
grep -Fq 'release-smoke-workflow.py' "${bundle_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer delegates to the shared Python workflow owner"
grep -Fq 'bundle-smoke-command-bridge.ps1' "${bundle_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer delegates Windows command execution through the bridge owner"
grep -Fq 'FINGRIND_RELEASE_SMOKE_COMMAND_BRIDGE_PREFIX_JSON' "${bundle_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer publishes the Windows bridge command contract"
grep -Fq 'release-smoke-common.sh' "${bundle_support_sh}" || die \
    "release-smoke-support.sh no longer sources the shared common helper owner"
if grep -Fq 'release-smoke-fixtures.sh' "${bundle_support_sh}"; then
    die "release-smoke-support.sh still sources the deleted Bash fixture owner"
fi
if grep -Fq 'release-smoke-assertions.sh' "${bundle_support_sh}"; then
    die "release-smoke-support.sh still sources the deleted Bash assertion owner"
fi

assert_source_only_guard() {
    local script_path=$1
    local expected_fragment=$2
    local output
    local status

    set +e
    output="$(bash "${script_path}" 2>&1)"
    status=$?
    set -e

    [[ ${status} -ne 0 ]] || die "${script_path} unexpectedly succeeded when executed directly"
    [[ "${output}" == *"${expected_fragment}"* ]] || die \
        "${script_path} did not explain that it must be sourced"
}

assert_source_only_guard \
    "${common_support_sh}" \
    "release-smoke-common.sh is a library and must be sourced by a release-smoke support script."
assert_source_only_guard \
    "${bundle_support_sh}" \
    "release-smoke-support.sh is a library and must be sourced by a release-smoke entrypoint."
assert_source_only_guard \
    "${workflow_support_sh}" \
    "release-smoke-workflow-support.sh is a library and must be sourced by release-smoke-support.sh."
grep -Fq 'FINGRIND_RELEASE_SMOKE_WORK_ROOT' "${bundle_smoke_sh}" || die \
    "bundle-smoke.sh no longer publishes the compact shared work-root contract"
grep -Fq 'Bundle acceptance: using archive' "${bundle_smoke_sh}" || die \
    "bundle-smoke.sh no longer reports which archive the acceptance run selected"
grep -Fq 'FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE' "${bundle_smoke_sh}" || die \
    "bundle-smoke.sh no longer publishes the shared argument-path-mode contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_SCENARIO_ID' "${bundle_smoke_sh}" || die \
    "bundle-smoke.sh no longer publishes the shared scenario-id contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_WORK_ROOT' "${docker_smoke_sh}" || die \
    "docker-smoke.sh no longer publishes the compact shared work-root contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_REPORTED_WORK_ROOT' "${docker_smoke_sh}" || die \
    "docker-smoke.sh no longer publishes the shared reported-work-root contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE' "${docker_smoke_sh}" || die \
    "docker-smoke.sh no longer publishes the shared argument-path-mode contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_SCENARIO_ID' "${docker_smoke_sh}" || die \
    "docker-smoke.sh no longer publishes the shared scenario-id contract"
if grep -Fq 'FINGRIND_RELEASE_SMOKE_REQUEST_SALE_ARG' "${bundle_smoke_sh}"; then
    die "bundle-smoke.sh still exports legacy per-path release-smoke arguments"
fi
if grep -Fq 'FINGRIND_RELEASE_SMOKE_REQUEST_SALE_ARG' "${docker_smoke_sh}"; then
    die "docker-smoke.sh still exports legacy per-path release-smoke arguments"
fi

# shellcheck source=/dev/null
source "${python_runtime_support}"

prepare_python_runtime_env

python3 -m py_compile "${workflow_py}" "${workflow_package_dir}"/*.py >/dev/null
python3 - <<'PY' "${repo_root}"
import csv
import json
import pathlib
import sys
import tempfile
from io import StringIO

sys.path.insert(0, str(pathlib.Path(sys.argv[1]) / "scripts"))
from release_smoke_workflow.cli import (  # noqa: E402
    run_cli_allow_failure,
    run_cli_with_split_streams,
)
from release_smoke_workflow.assertions import assert_operator_queries_and_reports  # noqa: E402
from release_smoke_workflow.fixtures import (  # noqa: E402
    prepare_fixture_directories,
    write_acceptance_fixtures,
)
from release_smoke_workflow.models import ReleaseSmokeConfig, SmokePath  # noqa: E402
from release_smoke_workflow.scenario import (  # noqa: E402
    ARGUMENT_PATH_MODE_ABSOLUTE,
    ARGUMENT_PATH_MODE_WORK_ROOT_RELATIVE,
    build_release_smoke_scenario,
)
from release_smoke_workflow.support import (  # noqa: E402
    extract_pdf_exported_path,
    normalize_reported_path,
)


def structured_account_ledger_csv(actor_prefix: str) -> str:
    header = [
        "recordKind",
        "currencyCode",
        "bucketDebitTotal",
        "bucketCreditTotal",
        "bucketNetAmount",
        "bucketBalanceSide",
        "postingId",
        "postingKind",
        "reversalState",
        "reversalTarget",
        "effectiveDate",
        "recordedAt",
        "debitAmount",
        "creditAmount",
        "runningNetAmount",
        "runningBalanceSide",
        "counterpartAccounts",
        "sourceDocuments",
        "approvals",
    ]
    rows = [
        {
            "recordKind": "opening-balance",
            "currencyCode": "EUR",
            "bucketDebitTotal": "0.00",
            "bucketCreditTotal": "0.00",
            "bucketNetAmount": "0.00",
            "bucketBalanceSide": "ZERO",
            "postingId": "",
            "postingKind": "",
            "reversalState": "",
            "reversalTarget": "",
            "effectiveDate": "",
            "recordedAt": "",
            "debitAmount": "",
            "creditAmount": "",
            "runningNetAmount": "",
            "runningBalanceSide": "",
            "counterpartAccounts": "",
            "sourceDocuments": "",
            "approvals": "",
        },
        {
            "recordKind": "ledger-entry",
            "currencyCode": "EUR",
            "bucketDebitTotal": "",
            "bucketCreditTotal": "",
            "bucketNetAmount": "",
            "bucketBalanceSide": "",
            "postingId": "019e2ae5-5f56-7025-8449-984160a327f3",
            "postingKind": "STANDARD",
            "reversalState": "direct",
            "reversalTarget": "",
            "effectiveDate": "2026-04-07",
            "recordedAt": "2026-04-07T10:00:00Z",
            "debitAmount": "10.00",
            "creditAmount": "0.00",
            "runningNetAmount": "10.00",
            "runningBalanceSide": "DEBIT",
            "counterpartAccounts": "2000",
            "sourceDocuments": json.dumps(
                [
                    {
                        "sourceDocumentId": f"{actor_prefix}-sale-document-1",
                        "sourceDocumentType": "invoice",
                    }
                ],
                separators=(",", ":"),
            ),
            "approvals": "[]",
        },
        {
            "recordKind": "ledger-entry",
            "currencyCode": "EUR",
            "bucketDebitTotal": "",
            "bucketCreditTotal": "",
            "bucketNetAmount": "",
            "bucketBalanceSide": "",
            "postingId": "019e2ae5-6557-7410-8611-f55876f12ca5",
            "postingKind": "STANDARD",
            "reversalState": "direct",
            "reversalTarget": "",
            "effectiveDate": "2026-04-08",
            "recordedAt": "2026-04-08T10:00:00Z",
            "debitAmount": "0.00",
            "creditAmount": "4.00",
            "runningNetAmount": "6.00",
            "runningBalanceSide": "DEBIT",
            "counterpartAccounts": "2000",
            "sourceDocuments": json.dumps(
                [
                    {
                        "sourceDocumentId": f"{actor_prefix}-adjustment-document-1",
                        "sourceDocumentType": "invoice",
                    }
                ],
                separators=(",", ":"),
            ),
            "approvals": "[]",
        },
        {
            "recordKind": "closing-balance",
            "currencyCode": "EUR",
            "bucketDebitTotal": "10.00",
            "bucketCreditTotal": "4.00",
            "bucketNetAmount": "6.00",
            "bucketBalanceSide": "DEBIT",
            "postingId": "",
            "postingKind": "",
            "reversalState": "",
            "reversalTarget": "",
            "effectiveDate": "",
            "recordedAt": "",
            "debitAmount": "",
            "creditAmount": "",
            "runningNetAmount": "",
            "runningBalanceSide": "",
            "counterpartAccounts": "",
            "sourceDocuments": "",
            "approvals": "",
        },
    ]
    buffer = StringIO()
    writer = csv.DictWriter(buffer, fieldnames=header, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return buffer.getvalue()

bundle = build_release_smoke_scenario(
    pathlib.Path("/tmp/workspace odd/Rīga büro/2026 Q2 close"),
    ARGUMENT_PATH_MODE_ABSOLUTE,
    "bundle-acceptance",
)
assert "Rīga büro" in str(bundle.book.local_path)
assert bundle.book.argument == str(bundle.book.local_path)
assert bundle.second_page_command_id == "bundle-acceptance-sale"

docker = build_release_smoke_scenario(
    pathlib.Path("/workdir"),
    ARGUMENT_PATH_MODE_WORK_ROOT_RELATIVE,
    "docker-acceptance",
)
assert docker.book.argument == "books odd/Rīga büro/nested/-entity [docker-acceptance].sqlite"
assert (
    docker.replacement_book_key.argument
    == "keys odd/Rīga büro/nested/--entity [docker-acceptance]-replacement.key"
)
assert docker.actor_prefix == "docker-acceptance"

with tempfile.TemporaryDirectory() as fixture_dir:
    fixture_workspace = pathlib.Path(fixture_dir)
    fixture_scenario = build_release_smoke_scenario(
        fixture_workspace,
        ARGUMENT_PATH_MODE_ABSOLUTE,
        "fixture-regression",
    )
    prepare_fixture_directories(fixture_scenario)
    write_acceptance_fixtures(fixture_scenario)
    sale_request = json.loads(
        fixture_scenario.request_sale.local_path.read_text(encoding="utf-8")
    )
    adjustment_request = json.loads(
        fixture_scenario.request_adjustment.local_path.read_text(encoding="utf-8")
    )
    for request_payload, expected_command_id, expected_document_id in [
        (
            sale_request,
            "fixture-regression-sale",
            "fixture-regression-sale-document-1",
        ),
        (
            adjustment_request,
            "fixture-regression-adjustment",
            "fixture-regression-adjustment-document-1",
        ),
    ]:
        assert request_payload["evidence"] == {
            "sourceDocuments": [
                {
                    "sourceDocumentId": expected_document_id,
                    "sourceDocumentType": "invoice",
                }
            ],
            "approvals": [],
        }
        assert request_payload["provenance"]["commandId"] == expected_command_id

dummy = SmokePath(
    relative_path=pathlib.Path("dummy"),
    local_path=pathlib.Path("/tmp/dummy"),
    argument="dummy",
)
with tempfile.TemporaryDirectory() as temp_dir:
    temp_path = pathlib.Path(temp_dir)
    bridge_script = temp_path / "bridge.py"
    bridge_script.write_text(
        "\n".join(
            [
                "import json",
                "import pathlib",
                "import sys",
                "request = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding='utf-8'))",
                "json.dump(request, sys.stdout, ensure_ascii=False)",
                "sys.stdout.write('\\n')",
            ]
        ),
        encoding="utf-8",
    )
    config = ReleaseSmokeConfig(
        label="Bridge regression",
        repo_root=pathlib.Path(sys.argv[1]),
        command_prefix=["unused-direct-command"],
        command_bridge_prefix=[sys.executable, str(bridge_script)],
        command_cwd=None,
        reported_work_root=None,
        command_env_drop=[],
        command_env_set={},
        runtime_distribution_key="bundleRuntimeDistribution",
        expect_loaded_sqlite_details=True,
        expect_bundle_home_property=True,
        book_key_output_permissions="owner-only-acl",
        request_sale=dummy,
        request_adjustment=dummy,
        invalid_request=dummy,
        declare_cash=dummy,
        declare_revenue=dummy,
        book=dummy,
        book_key=dummy,
        replacement_book_key=dummy,
        prompt_failure_book=dummy,
        trial_balance_pdf=dummy,
        trial_balance_pdf_stderr_path=temp_path / "stderr.txt",
        second_page_command_id="bridge-sale",
        actor_prefix="bridge",
        open_book_mode="book-key-file",
        entity_name="Acme Studio",
        entity_form="COMPANY",
        owner_model="MULTI_OWNER",
        reporting_obligation_status="INTERNAL_MANAGEMENT_ONLY",
        business_activity_tags=["consulting-services"],
        functional_currency="EUR",
        fiscal_year_start="01-01",
        accounting_basis="ACCRUAL",
    )
    unicode_argument = str(temp_path / "workspace odd" / "Rīga büro" / "key.key")
    output, exit_code = run_cli_allow_failure(
        config,
        "generate-book-key-file",
        "--book-key-file",
        unicode_argument,
    )
    assert exit_code == 0
    payload = json.loads(output)
    assert payload["arguments"][2] == unicode_argument

    stdout, stderr = run_cli_with_split_streams(
        config,
        "trial-balance",
        "--book-file",
        unicode_argument,
    )
    payload = json.loads(stdout)
    assert payload["arguments"][2] == unicode_argument
    assert stderr == ""

    pdf_path = temp_path / "reports odd" / "trial balance [bridge].pdf"
    pdf_path.parent.mkdir(parents=True, exist_ok=True)
    pdf_path.write_bytes(b"%PDF-1.7\nbridge")
    config = ReleaseSmokeConfig(
        label="Bridge regression",
        repo_root=pathlib.Path(sys.argv[1]),
        command_prefix=["unused-direct-command"],
        command_bridge_prefix=[sys.executable, str(bridge_script)],
        command_cwd=None,
        reported_work_root=None,
        command_env_drop=[],
        command_env_set={},
        runtime_distribution_key="bundleRuntimeDistribution",
        expect_loaded_sqlite_details=True,
        expect_bundle_home_property=True,
        book_key_output_permissions="owner-only-acl",
        request_sale=dummy,
        request_adjustment=dummy,
        invalid_request=dummy,
        declare_cash=dummy,
        declare_revenue=dummy,
        book=dummy,
        book_key=dummy,
        replacement_book_key=dummy,
        prompt_failure_book=dummy,
        trial_balance_pdf=SmokePath(
            relative_path=pathlib.Path("reports odd") / "trial balance [bridge].pdf",
            local_path=pdf_path,
            argument=str(pdf_path),
        ),
        trial_balance_pdf_stderr_path=temp_path / "stderr.txt",
        second_page_command_id="bridge-sale",
        actor_prefix="bridge",
        open_book_mode="book-key-file",
        entity_name="Acme Studio",
        entity_form="COMPANY",
        owner_model="MULTI_OWNER",
        reporting_obligation_status="INTERNAL_MANAGEMENT_ONLY",
        business_activity_tags=["consulting-services"],
        functional_currency="EUR",
        fiscal_year_start="01-01",
        accounting_basis="ACCRUAL",
    )
    report_stdout = "Trial Balance\nAccount  : 1000\nNet      : 6.00\n"
    report_stderr = (
        "Info\n"
        "====\n\n"
        "Code     : pdf-exported\n"
        f"Message  : Wrote the requested PDF report artifact to {pdf_path}\n"
        "Argument : --pdf-out\n"
    )
    assert_operator_queries_and_reports(
        config,
        list_postings_second_page_output='{"commandId":"bridge-sale"}\n',
        list_postings_human_output=(
            "Postings\n"
            "========\n\n"
            "Returned postings : 2\n\n"
            "2026-04-08 | Direct | posting-2\n"
            "Recorded at      : 2026-04-08 10:00:00 UTC\n"
            "Debit total      : 4.00\n\n"
            "2026-04-07 | Direct | posting-1\n"
            "Recorded at      : 2026-04-07 10:00:00 UTC\n"
            "Debit total      : 10.00\n"
        ),
        account_balance_human_output="Account Balance\nAccount : 1000\nNet     : 6.00\n",
        trial_balance_human_output="Trial Balance\nEffective date to : 2026-04-08\n1000 | 6.00\n",
        pdf_stdout="Trial Balance\nEffective date to : 2026-04-08\n1000 | 6.00\n",
        pdf_stderr=report_stderr,
        account_ledger_csv_output=structured_account_ledger_csv("bridge"),
        period_summary_human_output="Period Summary\nPosting count : 2\n",
    )

    windows_report_stderr = (
        "Info\n"
        "====\n\n"
        "Code     : pdf-exported\n"
        r"Message  : Wrote the requested PDF report artifact to D:\a\FinGrind\workspace odd\Rīga büro\reports odd\trial balance [bundle-acceptance].pdf"
        "\n"
        "Argument : --pdf-out\n"
    )
    assert normalize_reported_path(extract_pdf_exported_path(windows_report_stderr)) == (
        normalize_reported_path(
            "d:/a/FinGrind/workspace odd/Rīga büro/reports odd/trial balance [bundle-acceptance].pdf"
        )
    )

    docker_report_stderr = (
        "Info\n"
        "====\n\n"
        "Code     : pdf-exported\n"
        "Message  : Wrote the requested PDF report artifact to /workdir/reports odd/trial balance [bridge].pdf\n"
        "Argument : --pdf-out\n"
    )
    docker_config = ReleaseSmokeConfig(
        label="Docker regression",
        repo_root=pathlib.Path(sys.argv[1]),
        command_prefix=["unused-direct-command"],
        command_bridge_prefix=[sys.executable, str(bridge_script)],
        command_cwd=None,
        reported_work_root=pathlib.Path("/workdir"),
        command_env_drop=[],
        command_env_set={},
        runtime_distribution_key="containerRuntimeDistribution",
        expect_loaded_sqlite_details=True,
        expect_bundle_home_property=True,
        book_key_output_permissions="0600",
        request_sale=dummy,
        request_adjustment=dummy,
        invalid_request=dummy,
        declare_cash=dummy,
        declare_revenue=dummy,
        book=dummy,
        book_key=dummy,
        replacement_book_key=dummy,
        prompt_failure_book=dummy,
        trial_balance_pdf=SmokePath(
            relative_path=pathlib.Path("reports odd") / "trial balance [bridge].pdf",
            local_path=pdf_path,
            argument="reports odd/trial balance [bridge].pdf",
        ),
        trial_balance_pdf_stderr_path=temp_path / "docker-stderr.txt",
        second_page_command_id="bridge-sale",
        actor_prefix="bridge",
        open_book_mode="book-key-file",
        entity_name="Acme Studio",
        entity_form="COMPANY",
        owner_model="MULTI_OWNER",
        reporting_obligation_status="INTERNAL_MANAGEMENT_ONLY",
        business_activity_tags=["consulting-services"],
        functional_currency="EUR",
        fiscal_year_start="01-01",
        accounting_basis="ACCRUAL",
    )
    assert_operator_queries_and_reports(
        docker_config,
        list_postings_second_page_output='{"commandId":"bridge-sale"}\n',
        list_postings_human_output=(
            "Postings\n"
            "========\n\n"
            "Returned postings : 2\n\n"
            "2026-04-08 | Direct | posting-2\n"
            "Recorded at      : 2026-04-08 10:00:00 UTC\n"
            "Debit total      : 4.00\n\n"
            "2026-04-07 | Direct | posting-1\n"
            "Recorded at      : 2026-04-07 10:00:00 UTC\n"
            "Debit total      : 10.00\n"
        ),
        account_balance_human_output="Account Balance\nAccount : 1000\nNet     : 6.00\n",
        trial_balance_human_output="Trial Balance\nEffective date to : 2026-04-08\n1000 | 6.00\n",
        pdf_stdout="Trial Balance\nEffective date to : 2026-04-08\n1000 | 6.00\n",
        pdf_stderr=docker_report_stderr,
        account_ledger_csv_output=structured_account_ledger_csv("bridge"),
        period_summary_human_output="Period Summary\nPosting count : 2\n",
    )
PY

set +e
missing_env_output="$(python3 "${workflow_py}" 2>&1)"
missing_env_status=$?
set -e
[[ "${missing_env_status}" -ne 0 ]] || die \
    "shared release smoke workflow unexpectedly succeeded without required environment wiring"
printf '%s\n' "${missing_env_output}" | grep -Fq 'FINGRIND_RELEASE_SMOKE_COMMAND_PREFIX_JSON' || die \
    "shared release smoke workflow did not fail through its required-environment guard"

printf 'release smoke workflow wiring regression: success\n'
