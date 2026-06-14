#!/usr/bin/env bash
# Scenario fixtures for the structural-governance shell regressions.

set -euo pipefail

fixture_root_success() {
    build_logic_fixture "$1"
    gradle_kts_fixture "$1"
    json_fixture "$1"
    markdown_fixture "$1"
    shell_fixture "$1"
    python_fixture "$1"
    sql_fixture "$1"
    run_expect_success "$1" --surface build-logic-kotlin
    run_expect_success "$1" --surface gradle-kts
    run_expect_success "$1" --surface json-resource
    run_expect_success "$1" --surface markdown-docs
    run_expect_success "$1" --surface shell-release
    run_expect_success "$1" --surface python-support
    run_expect_success "$1" --surface sqlite-sql
}

fixture_root_build_logic_budget_failure() {
    build_logic_fixture "$1"
    python3 - "$1" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1]) / "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/OversizedPlugin.kt"
body = ["package dev.erst.fingrind.buildlogic", ""]
for index in range(0, 550):
    body.append(f"internal fun step{index}(): String = \"{index}\"")
path.write_text("\n".join(body) + "\n", encoding="utf-8")
PY
    run_expect_failure "OversizedPlugin.kt" "$1" --surface build-logic-kotlin
}

fixture_root_build_logic_duplicate_failure() {
    build_logic_fixture "$1"
    python3 - "$1" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1]) / "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic"
shared_lines = [f"internal fun shared_{index}(): String = \"value {index}\"" for index in range(0, 30)]
for name in ("OneSupport.kt", "TwoSupport.kt"):
    path = root / name
    path.write_text(
        "package dev.erst.fingrind.buildlogic\n\n" + "\n".join(shared_lines) + "\n",
        encoding="utf-8",
    )
PY
    run_expect_failure "duplicate normalized" "$1" --surface build-logic-kotlin
}

fixture_root_build_logic_function_count_failure() {
    build_logic_fixture "$1"
    python3 - "$1" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1]) / "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/TooManyReceivers.kt"
lines = ["package dev.erst.fingrind.buildlogic", ""]
for index in range(0, 21):
    lines.append(f"internal fun Path.helper{index}(): Path = this")
path.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
    run_expect_failure "functions exceeds 20" "$1" --surface build-logic-kotlin
}

fixture_root_shell_budget_failure() {
    build_logic_fixture "$1"
    shell_fixture "$1"
    python3 - "$1" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1]) / "scripts/test-shell-shape.sh"
lines = ["#!/usr/bin/env bash", "set -euo pipefail", ""]
for index in range(0, 790):
    lines.append(f"printf 'line {index}\\n'")
path.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
    run_expect_failure "test-shell-shape.sh" "$1" --surface shell-release
}

fixture_root_shell_duplicate_failure() {
    build_logic_fixture "$1"
    shell_fixture "$1"
    python3 - "$1" <<'PY'
from pathlib import Path
import sys

scripts = Path(sys.argv[1]) / "scripts"
shared = ["printf 'shared line {}\\n'".format(index) for index in range(0, 32)]
for name in ("release-one.sh", "release-two.sh"):
    path = scripts / name
    path.write_text(
        "#!/usr/bin/env bash\nset -euo pipefail\n\nrun_release() {\n"
        + "\n".join(f"    {line}" for line in shared)
        + "\n}\n",
        encoding="utf-8",
    )
PY
    run_expect_failure "duplicate normalized" "$1" --surface shell-release
}

fixture_root_python_budget_failure() {
    python_fixture "$1"
    python3 - "$1" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1]) / "scripts/oversized_support.py"
lines = []
for index in range(0, 330):
    lines.append(f"def helper_{index}():\n    return {index}\n")
path.write_text("\n".join(lines), encoding="utf-8")
PY
    run_expect_failure "oversized_support.py" "$1" --surface python-support
}

fixture_root_json_budget_failure() {
    json_fixture "$1"
    python3 - "$1" <<'PY'
from pathlib import Path
import json
import sys

path = Path(sys.argv[1]) / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/runtime-surface-contract.json"
payload = {
    "entries": [
        {
            "name": f"entry-{index}",
            "description": f"description-{index}",
            "detail": {"state": "active", "owner": "contract"},
        }
        for index in range(0, 220)
    ]
}
path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
PY
    run_expect_failure "runtime-surface-contract.json" "$1" --surface json-resource
}

fixture_root_sql_reviewed_surface_growth_failure() {
    mkdir -p "$1/sqlite/src/main/resources/dev/erst/fingrind/sqlite"
    python3 - "$1" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1]) / "sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql"
lines = []
for index in range(0, 1180):
    lines.append(f"create table table_{index} (id integer primary key);")
path.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
    run_expect_failure "reviewed structural approval" "$1" --surface sqlite-sql
}

fixture_root_sql_reviewed_surface_orphan_failure() {
    sql_fixture "$1"
    rm -f "$1/sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql"
    run_expect_failure "orphaned waiver" "$1" --surface sqlite-sql
}

fixture_root_sql_budget_failure() {
    sql_fixture "$1"
    python3 - "$1" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1]) / "sqlite/src/main/resources/dev/erst/fingrind/sqlite/oversized.sql"
lines = []
for index in range(0, 260):
    lines.append(f"create table t{index} (id integer primary key);")
path.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
    run_expect_failure "oversized.sql" "$1" --surface sqlite-sql
}

fixture_root_markdown_budget_failure() {
    markdown_fixture "$1"
    python3 - "$1" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1]) / "docs/oversized.md"
lines = ["# Oversized guide", ""]
for index in range(0, 260):
    lines.append(f"Paragraph {index}.")
path.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
    run_expect_failure "oversized.md" "$1" --surface markdown-docs
}

fixture_root_markdown_protocol_budget_failure() {
    markdown_fixture "$1"
    python3 - "$1" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1]) / ".codex/oversized-protocol.md"
lines = ["# Oversized Protocol", ""]
for index in range(0, 1150):
    lines.append(f"Instruction {index}.")
path.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
    run_expect_failure "oversized-protocol.md" "$1" --surface markdown-docs
}

fixture_root_gradle_budget_failure() {
    gradle_kts_fixture "$1"
    python3 - "$1" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1]) / "module/build.gradle.kts"
lines = ["plugins {", "    java", "}", ""]
for index in range(0, 120):
    lines.append(f'tasks.register("task{index}") {{ println("{index}") }}')
path.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
    run_expect_failure "module/build.gradle.kts" "$1" --surface gradle-kts
}
