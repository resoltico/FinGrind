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
afad: "4.0"
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

python3 - <<'PY' "${fixture_root}/sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql" "${fixture_root}/docs/sqlite/SCHEMA_CORE.md"
from pathlib import Path
import sys

schema_path = Path(sys.argv[1])
document_path = Path(sys.argv[2])
schema_text = schema_path.read_text(encoding="utf-8").strip()
document_text = document_path.read_text(encoding="utf-8")
if schema_text not in document_text:
    raise SystemExit("error: generated schema doc did not embed the canonical SQL body")
PY
grep -Fq '`book_meta.schema_fingerprint_sha256`' "${fixture_root}/docs/sqlite/SCHEMA_CORE.md" || die \
    "generated schema doc did not describe runtime integrity semantics"

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
