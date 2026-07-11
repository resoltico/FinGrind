#!/usr/bin/env bash
# Guard the repo-owned structural-governance verifier surface.

set -euo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
# shellcheck source=verify-structural-governance-test-support.sh
source "${script_dir}/verify-structural-governance-test-support.sh"

run_structural_governance_regressions
cd "${repo_root}"
PYTHONPATH="${script_dir}" python3 - <<'PY'
from pathlib import Path

from structural_governance.docs_budgets import markdown_budget_for
from structural_governance.verification import verify_markdown_docs

budget = markdown_budget_for(Path("docs/sqlite/SCHEMA_CORE_04_POSTING_FACT.md"))
assert budget.role_name == "docs-schema-reference-companion", budget
assert budget.max_duplicate_window_lines == 64, budget
assert not [
    violation
    for violation in verify_markdown_docs(Path(".").resolve())
    if "docs/sqlite/SCHEMA_CORE_04_POSTING_FACT.md" in violation
]
PY

printf 'structural governance verifier regression: success\n'
