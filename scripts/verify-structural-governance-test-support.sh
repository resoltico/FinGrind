#!/usr/bin/env bash
# Shared fixture builders and assertions for the structural-governance shell regression.

set -euo pipefail

# shellcheck source=verify-structural-governance-common.sh
source "$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/verify-structural-governance-common.sh"
# shellcheck source=verify-structural-governance-fixture-builder-support.sh
source "${structural_governance_common_script_dir}/verify-structural-governance-fixture-builder-support.sh"
# shellcheck source=verify-structural-governance-fixture-case-support.sh
source "${structural_governance_common_script_dir}/verify-structural-governance-fixture-case-support.sh"

run_structural_governance_regressions() {
    run_in_temp_fixture fixture_root_success
    run_in_temp_fixture fixture_root_build_logic_budget_failure
    run_in_temp_fixture fixture_root_build_logic_duplicate_failure
    run_in_temp_fixture fixture_root_build_logic_function_count_failure
    run_in_temp_fixture fixture_root_shell_budget_failure
    run_in_temp_fixture fixture_root_shell_duplicate_failure
    run_in_temp_fixture fixture_root_python_budget_failure
    run_in_temp_fixture fixture_root_json_budget_failure
    run_in_temp_fixture fixture_root_sql_reviewed_surface_growth_failure
    run_in_temp_fixture fixture_root_sql_reviewed_surface_orphan_failure
    run_in_temp_fixture fixture_root_sql_budget_failure
    run_in_temp_fixture fixture_root_markdown_budget_failure
    run_in_temp_fixture fixture_root_markdown_protocol_budget_failure
    run_in_temp_fixture fixture_root_gradle_budget_failure
    PYTHONPATH="${structural_governance_common_repo_root}/scripts" python3 -m structural_governance.reviewed_surface_policy_contract_test
    PYTHONPATH="${structural_governance_common_repo_root}/scripts" python3 -m structural_governance.registry_contract_test
    assert_verifier_usage_mentions_all_supported_surfaces
}

assert_verifier_usage_mentions_all_supported_surfaces() {
    local output
    output="$("${structural_governance_common_repo_root}/scripts/verify-structural-governance.sh" --help)"
    [[ "${output}" == *"gradle-kts"* ]] || {
        printf 'expected verify-structural-governance help to mention gradle-kts\n' >&2
        exit 1
    }
    [[ "${output}" == *"json-resource"* ]] || {
        printf 'expected verify-structural-governance help to mention json-resource\n' >&2
        exit 1
    }
    [[ "${output}" == *"markdown-docs"* ]] || {
        printf 'expected verify-structural-governance help to mention markdown-docs\n' >&2
        exit 1
    }
}
