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
readonly workflow_sh="${repo_root}/scripts/release-smoke-workflow.sh"
readonly bundle_support_sh="${repo_root}/scripts/release-smoke-support.sh"
readonly bundle_office_worker_ps1="${repo_root}/scripts/bundle-smoke-office-worker.ps1"
readonly bundle_command_bridge_ps1="${repo_root}/scripts/bundle-smoke-command-bridge.ps1"
readonly bundle_smoke_sh="${repo_root}/scripts/bundle-smoke.sh"
readonly docker_smoke_sh="${repo_root}/scripts/docker-smoke.sh"

[[ -f "${workflow_py}" ]] || die "missing shared release smoke workflow runner at ${workflow_py}"
[[ -d "${workflow_package_dir}" ]] || die "missing release smoke workflow package at ${workflow_package_dir}"
[[ -f "${workflow_sh}" ]] || die "missing Bash release smoke workflow wrapper at ${workflow_sh}"
[[ -f "${bundle_support_sh}" ]] || die "missing Bash release smoke support wrapper at ${bundle_support_sh}"
[[ -f "${bundle_office_worker_ps1}" ]] || die "missing PowerShell office-worker wrapper at ${bundle_office_worker_ps1}"
[[ -f "${bundle_command_bridge_ps1}" ]] || die "missing PowerShell command bridge at ${bundle_command_bridge_ps1}"
[[ -f "${bundle_smoke_sh}" ]] || die "missing Bash bundle smoke entrypoint at ${bundle_smoke_sh}"
[[ -f "${docker_smoke_sh}" ]] || die "missing Bash Docker smoke entrypoint at ${docker_smoke_sh}"
grep -Fq 'release-smoke-workflow.py' "${workflow_sh}" || die \
    "release-smoke-workflow.sh no longer delegates to the shared Python workflow owner"
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
grep -Fq 'FINGRIND_RELEASE_SMOKE_WORK_ROOT' "${bundle_smoke_sh}" || die \
    "bundle-smoke.sh no longer publishes the compact shared work-root contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE' "${bundle_smoke_sh}" || die \
    "bundle-smoke.sh no longer publishes the shared argument-path-mode contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_SCENARIO_ID' "${bundle_smoke_sh}" || die \
    "bundle-smoke.sh no longer publishes the shared scenario-id contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_WORK_ROOT' "${docker_smoke_sh}" || die \
    "docker-smoke.sh no longer publishes the compact shared work-root contract"
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

python3 -m py_compile "${workflow_py}" "${workflow_package_dir}"/*.py >/dev/null
python3 - <<'PY' "${repo_root}"
import json
import pathlib
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(sys.argv[1]) / "scripts"))
from release_smoke_workflow.cli import run_cli_allow_failure  # noqa: E402
from release_smoke_workflow.models import ReleaseSmokeConfig, SmokePath  # noqa: E402
from release_smoke_workflow.scenario import (  # noqa: E402
    ARGUMENT_PATH_MODE_ABSOLUTE,
    ARGUMENT_PATH_MODE_WORK_ROOT_RELATIVE,
    build_release_smoke_scenario,
)

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

dummy = SmokePath(local_path=pathlib.Path("/tmp/dummy"), argument="dummy")
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
