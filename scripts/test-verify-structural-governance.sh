#!/usr/bin/env bash
# Guard the repo-owned structural-governance verifier for build-logic Kotlin and release shell surfaces.

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
readonly verifier_py="${repo_root}/scripts/verify-structural-governance.py"

[[ -f "${verifier_py}" ]] || die "missing structural governance verifier at ${verifier_py}"

run_expect_success() {
    local fixture_root=$1
    shift
    python3 "${verifier_py}" --repo-root "${fixture_root}" "$@" >/dev/null
}

run_expect_failure() {
    local expected_fragment=$1
    local fixture_root=$2
    shift 2
    local output
    local status
    set +e
    output="$(python3 "${verifier_py}" --repo-root "${fixture_root}" "$@" 2>&1)"
    status=$?
    set -e
    [[ ${status} -ne 0 ]] || die "verifier unexpectedly succeeded for ${expected_fragment}"
    [[ "${output}" == *"${expected_fragment}"* ]] || die \
        "verifier output did not contain expected fragment: ${expected_fragment}"
}

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

run_in_temp_fixture() {
    local callback=$1
    local temp_dir
    temp_dir="$(mktemp -d)"
    "${callback}" "${temp_dir}"
    rm -rf "${temp_dir}"
}

fixture_root_success() {
    build_logic_fixture "$1"
    shell_fixture "$1"
    run_expect_success "$1" --surface build-logic-kotlin
    run_expect_success "$1" --surface shell-release
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

run_in_temp_fixture fixture_root_success
run_in_temp_fixture fixture_root_build_logic_budget_failure
run_in_temp_fixture fixture_root_build_logic_duplicate_failure
run_in_temp_fixture fixture_root_shell_budget_failure
run_in_temp_fixture fixture_root_shell_duplicate_failure

printf 'structural governance verifier regression: success\n'
