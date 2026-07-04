#!/usr/bin/env bash
# Shared fixture builders for the structural-governance shell regressions.

set -euo pipefail

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

json_fixture() {
    local fixture_root=$1
    mkdir -p \
        "${fixture_root}/contract/src/main/resources/dev/erst/fingrind/contract/protocol" \
        "${fixture_root}/docs/examples"
    cat > "${fixture_root}/contract/src/main/resources/dev/erst/fingrind/contract/protocol/runtime-surface-contract.json" <<'EOF'
{
  "contractVersion": "1",
  "runtimeSurface": {
    "helpOperation": "help",
    "capabilitiesOperation": "capabilities"
  }
}
EOF
    cp \
        "${structural_governance_common_repo_root}/docs/examples/account-ledger-response.json" \
        "${fixture_root}/docs/examples/account-ledger-response.json"
    cp \
        "${structural_governance_common_repo_root}/docs/examples/period-summary-response.json" \
        "${fixture_root}/docs/examples/period-summary-response.json"
    cp \
        "${structural_governance_common_repo_root}/docs/examples/trial-balance-response.json" \
        "${fixture_root}/docs/examples/trial-balance-response.json"
}

sql_fixture() {
    local fixture_root=$1
    mkdir -p "${fixture_root}/sqlite/src/main/resources/dev/erst/fingrind/sqlite"
    cp \
        "${structural_governance_common_repo_root}/sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql" \
        "${fixture_root}/sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql"
}

markdown_fixture() {
    local fixture_root=$1
    mkdir -p "${fixture_root}/docs" "${fixture_root}/.codex"
    cat > "${fixture_root}/README.md" <<'EOF'
# FinGrind

Short landing page.
EOF
    cat > "${fixture_root}/docs/USER_GUIDE.md" <<'EOF'
# User Guide

## Start

Run the app.
EOF
    cat > "${fixture_root}/.codex/AGENTS_EXTRA.md" <<'EOF'
# Extra Agents

Focused protocol note.
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
