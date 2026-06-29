#!/usr/bin/env bash
# Guard the repo-owned structural-governance verifier surface.

set -euo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=verify-structural-governance-test-support.sh
source "${script_dir}/verify-structural-governance-test-support.sh"

run_structural_governance_regressions
PYTHONPATH="${script_dir}" python3 - <<'PY'
from pathlib import Path

from structural_governance.docs_budgets import markdown_budget_for

budget = markdown_budget_for(Path("docs/sqlite/SCHEMA_CORE_04_POSTING_FACT.md"))
assert budget.role_name == "docs-schema-reference-companion", budget
assert budget.max_duplicate_window_lines == 64, budget
PY

printf 'structural governance verifier regression: success\n'
