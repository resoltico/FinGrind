#!/usr/bin/env bash
# Verify that the generated SQLite schema reference stays in sync with the canonical schema file.

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
readonly render_script="${repo_root}/scripts/render-sqlite-schema-doc.py"
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"

[[ -f "${render_script}" ]] || die "missing schema renderer at ${render_script}"
[[ -f "${stage_contract_script}" ]] || die \
    "missing check stage contract helper at ${stage_contract_script}"
grep -Fq 'scripts/test-render-sqlite-schema-doc.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the SQLite schema-doc generator"

readonly temp_parent="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-render-sqlite-schema-doc.XXXXXX")"
readonly fixture_root="${temp_parent}/fixture"

cleanup() {
    chmod -R u+rwx "${temp_parent}" 2>/dev/null || true
    rm -rf "${temp_parent}" 2>/dev/null || true
}
trap cleanup EXIT

mkdir -p \
    "${fixture_root}/docs/sqlite" \
    "${fixture_root}/sqlite/src/main/resources/dev/erst/fingrind/sqlite"

cp "${repo_root}/sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql" \
    "${fixture_root}/sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql"

cat > "${fixture_root}/docs/sqlite/SCHEMA_CORE.md" <<'EOF'
---
afad: "5.0.1"
version: "9.9.9"
domain: SQLITE_SCHEMA_CORE
updated: "2026-05-09"
route:
  keywords: [fingrind, sqlite, schema]
  questions: ["what is the current sqlite schema"]
---

placeholder
EOF

python3 "${render_script}" --repo-root "${fixture_root}" --write

python3 - <<'PY' "${fixture_root}/sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql" "${fixture_root}/docs/sqlite"
from pathlib import Path
import re
import sys

schema_path = Path(sys.argv[1])
docs_root = Path(sys.argv[2])
schema_text = schema_path.read_text(encoding="utf-8").strip()
section_docs = sorted(docs_root.glob("SCHEMA_CORE_*.md"))
if not section_docs:
    raise SystemExit("error: schema renderer did not generate any schema section documents")
sql_block_pattern = re.compile(r"```sql\n(.*?)\n```", re.DOTALL)
sql_fragments = []
for document_path in section_docs:
    document_text = document_path.read_text(encoding="utf-8")
    match = sql_block_pattern.search(document_text)
    if match is None:
        raise SystemExit(f"error: generated schema section {document_path.name} is missing one SQL block")
    sql_fragments.append(match.group(1).strip())
reconstructed_schema = "\n\n".join(sql_fragments)
if reconstructed_schema != schema_text:
    raise SystemExit("error: generated schema sections did not reconstruct the canonical SQL body")
PY
[[ -f "${fixture_root}/docs/sqlite/SCHEMA_CORE_01_FOUNDATION.md" ]] || die \
    "generated schema doc set is missing the foundation page"
[[ -f "${fixture_root}/docs/sqlite/SCHEMA_CORE_03_ACCOUNT_DECLARATION_RULES.md" ]] || die \
    "generated schema doc set is missing the account-declaration rules page"
[[ -f "${fixture_root}/docs/sqlite/SCHEMA_CORE_03a_ACCOUNT_LIFECYCLE_RULES.md" ]] || die \
    "generated schema doc set is missing the account-lifecycle rules page"
[[ ! -f "${fixture_root}/docs/sqlite/SCHEMA_CORE_03_ACCOUNT_RULES.md" ]] || die \
    "generated schema doc set retained the superseded combined account-rules page"
[[ -f "${fixture_root}/docs/sqlite/SCHEMA_CORE_13ze_LATVIAN_PAYROLL_RUNS.md" ]] || die \
    "generated schema doc set is missing the Latvian payroll-runs page"
[[ -f "${fixture_root}/docs/sqlite/SCHEMA_CORE_13zea_LATVIAN_PAYROLL_RUN_IMMUTABILITY.md" ]] || die \
    "generated schema doc set is missing the Latvian payroll-run immutability page"
[[ -f "${fixture_root}/docs/sqlite/SCHEMA_CORE_13zf_LATVIAN_PAYROLL_SETTLEMENTS.md" ]] || die \
    "generated schema doc set is missing the Latvian payroll-settlements page"
grep -Fq 'latvian_payroll_run_reject_update' \
    "${fixture_root}/docs/sqlite/SCHEMA_CORE_13zea_LATVIAN_PAYROLL_RUN_IMMUTABILITY.md" || die \
    "generated payroll-run immutability page is missing append-only triggers"
if grep -Fq 'latvian_payroll_run_reject_update' \
    "${fixture_root}/docs/sqlite/SCHEMA_CORE_13ze_LATVIAN_PAYROLL_RUNS.md"; then
    die "generated Latvian payroll-runs page incorrectly owns append-only triggers"
fi
grep -Fq 'version: "9.9.9"' "${fixture_root}/docs/sqlite/SCHEMA_CORE_01_FOUNDATION.md" || die \
    "generated schema foundation page did not inherit the overview version frontmatter"
grep -Fq 'afad: "5.0.1"' "${fixture_root}/docs/sqlite/SCHEMA_CORE_01_FOUNDATION.md" || die \
    "generated schema foundation page did not inherit the overview AFAD frontmatter"
grep -Fq 'updated: "2026-05-09"' "${fixture_root}/docs/sqlite/SCHEMA_CORE_01_FOUNDATION.md" || die \
    "generated schema foundation page did not inherit the overview updated frontmatter"
grep -Fq '`book_meta.schema_fingerprint_sha256`' "${fixture_root}/docs/sqlite/SCHEMA_CORE.md" || die \
    "generated schema overview did not describe runtime integrity semantics"

python3 "${render_script}" --repo-root "${fixture_root}" --check

python3 - <<'PY' "${fixture_root}/sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql"
from pathlib import Path
import re
import sys

schema_path = Path(sys.argv[1])
text = schema_path.read_text(encoding="utf-8")
match = re.search(r"pragma user_version = (\d+);", text)
if match is None:
    raise SystemExit("error: canonical schema file is missing pragma user_version")
schema_path.write_text(
    text.replace(
        f"pragma user_version = {match.group(1)};",
        f"pragma user_version = {int(match.group(1)) + 2};",
    ),
    encoding="utf-8",
)
PY

if python3 "${render_script}" --repo-root "${fixture_root}" --check >/dev/null 2>&1; then
    die "schema-doc drift check passed after the canonical schema changed"
fi

printf 'render-sqlite-schema-doc regression: success\n'
