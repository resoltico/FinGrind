#!/usr/bin/env bash
# Shared fixture builders and assertions for the structural-governance shell regression.

set -euo pipefail

# shellcheck source=verify-structural-governance-common.sh
source "$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/verify-structural-governance-common.sh"

build_logic_fixture() {
    local fixture_root=$1
    mkdir -p "${fixture_root}/gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic"
    cat > "${fixture_root}/gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/SmallSupport.kt" <<'EOF'
package dev.erst.fingrind.buildlogic

internal fun smallSupport(): String = "ok"
EOF
}

shell_fixture() {
    local fixture_root=$1
    mkdir -p "${fixture_root}/scripts" "${fixture_root}/jazzer/bin"
    cat > "${fixture_root}/check.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'ok\n'
EOF
    cat > "${fixture_root}/scripts/release-support.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

release_support_message() {
    printf 'support\n'
}
EOF
}

python_fixture() {
    local fixture_root=$1
    mkdir -p "${fixture_root}/scripts/release_smoke_workflow"
    cat > "${fixture_root}/scripts/support.py" <<'EOF'
def ok() -> str:
    return "ok"
EOF
}

sql_fixture() {
    local fixture_root=$1
    mkdir -p "${fixture_root}/sqlite/src/main/resources/dev/erst/fingrind/sqlite"
    cat > "${fixture_root}/sqlite/src/main/resources/dev/erst/fingrind/sqlite/schema.sql" <<'EOF'
create table sample (id integer primary key);
EOF
}

markdown_fixture() {
    local fixture_root=$1
    mkdir -p "${fixture_root}/docs"
    cat > "${fixture_root}/README.md" <<'EOF'
# FinGrind

Short landing page.
EOF
    cat > "${fixture_root}/docs/USER_GUIDE.md" <<'EOF'
# User Guide

## Start

Run the app.
EOF
}

gradle_kts_fixture() {
    local fixture_root=$1
    mkdir -p "${fixture_root}/module"
    cat > "${fixture_root}/build.gradle.kts" <<'EOF'
plugins {}
EOF
    cat > "${fixture_root}/module/build.gradle.kts" <<'EOF'
plugins {
    java
}
EOF
    cat > "${fixture_root}/settings.gradle.kts" <<'EOF'
rootProject.name = "fixture"
EOF
}

fixture_root_success() {
    build_logic_fixture "$1"
    gradle_kts_fixture "$1"
    markdown_fixture "$1"
    shell_fixture "$1"
    python_fixture "$1"
    sql_fixture "$1"
    run_expect_success "$1" --surface build-logic-kotlin
    run_expect_success "$1" --surface gradle-kts
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
    run_expect_failure "reviewed structural surface" "$1" --surface sqlite-sql
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

run_structural_governance_regressions() {
    run_in_temp_fixture fixture_root_success
    run_in_temp_fixture fixture_root_build_logic_budget_failure
    run_in_temp_fixture fixture_root_build_logic_duplicate_failure
    run_in_temp_fixture fixture_root_shell_budget_failure
    run_in_temp_fixture fixture_root_shell_duplicate_failure
    run_in_temp_fixture fixture_root_python_budget_failure
    run_in_temp_fixture fixture_root_sql_reviewed_surface_growth_failure
    run_in_temp_fixture fixture_root_sql_budget_failure
    run_in_temp_fixture fixture_root_markdown_budget_failure
    run_in_temp_fixture fixture_root_gradle_budget_failure
    assert_verifier_usage_mentions_all_supported_surfaces
}

assert_verifier_usage_mentions_all_supported_surfaces() {
    local output
    output="$("${structural_governance_common_repo_root}/scripts/verify-structural-governance.sh" --help)"
    [[ "${output}" == *"gradle-kts"* ]] || {
        printf 'expected verify-structural-governance help to mention gradle-kts\n' >&2
        exit 1
    }
    [[ "${output}" == *"markdown-docs"* ]] || {
        printf 'expected verify-structural-governance help to mention markdown-docs\n' >&2
        exit 1
    }
}
