#!/usr/bin/env bash
# Exercise the release version-prep helper against a disposable repository fixture so the
# version-bearing surface stays scripted and repeatable.

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

require_file_contains() {
    local file_path=$1
    local expected_text=$2

    grep -Fq -- "${expected_text}" "${file_path}" || die \
        "expected ${file_path} to contain: ${expected_text}"
}

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly prepare_script="${repo_root}/scripts/prepare-release-version.sh"

[[ -x "${prepare_script}" ]] || die "missing executable helper script at ${prepare_script}"
grep -Fq 'scripts/test-prepare-release-version.sh' "${repo_root}/check.sh" || die \
    "root check no longer exercises the release version-prep regression"
grep -Fq './scripts/prepare-release-version.sh X.Y.Z YYYY-MM-DD' \
    "${repo_root}/docs/RELEASE_PROTOCOL.md" || die \
    "release protocol no longer requires the version-prep helper"

readonly temp_parent="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-prepare-release-version.XXXXXX")"
readonly fixture_root="${temp_parent}/fixture"

cleanup() {
    chmod -R u+rwx "${temp_parent}" 2>/dev/null || true
    rm -rf "${temp_parent}" 2>/dev/null || true
}
trap cleanup EXIT

mkdir -p \
    "${fixture_root}/docs" \
    "${fixture_root}/docs/sqlite" \
    "${fixture_root}/cli/src/test/java/dev/erst/fingrind/cli" \
    "${fixture_root}/contract/src/test/java/dev/erst/fingrind/contract" \
    "${fixture_root}/report-pdf/src/test/java/dev/erst/fingrind/report/pdf"

cat > "${fixture_root}/gradle.properties" <<'EOF'
group=dev.erst.fingrind
version=1.2.3
EOF

cat > "${fixture_root}/CHANGELOG.md" <<'EOF'
# Changelog

## [Unreleased]

### Fixed
- Example fix pending release.

## [1.2.3] - 2026-04-22

### Fixed
- Previous release note.

[Unreleased]: https://github.com/resoltico/FinGrind/compare/v1.2.3...HEAD
[1.2.3]: https://github.com/resoltico/FinGrind/releases/tag/v1.2.3
EOF

cat > "${fixture_root}/docs/RELEASE_PROTOCOL.md" <<'EOF'
---
afad: "3.5"
version: "1.2.3"
domain: RELEASE_PROTOCOL
updated: "2026-04-20"
---

Protocol text
EOF

cat > "${fixture_root}/docs/USER_CLI.md" <<'EOF'
---
afad: "3.5"
version: "1.2.3"
domain: USER_CLI
updated: "2026-04-20"
---

tar -xzf fingrind-1.2.3-macos-aarch64.tar.gz
Expand-Archive fingrind-1.2.3-windows-x86_64.zip -DestinationPath .
EOF

cat > "${fixture_root}/docs/USER_EXAMPLES.md" <<'EOF'
---
afad: "3.5"
version: "1.2.3"
domain: USER_EXAMPLES
updated: "2026-04-20"
---

./fingrind-1.2.3-macos-aarch64/bin/fingrind help
.\fingrind-1.2.3-windows-x86_64\bin\fingrind.ps1 help
EOF

cat > "${fixture_root}/docs/sqlite/SCHEMA_CORE.md" <<'EOF'
---
afad: "3.5"
version: "1.2.3"
domain: SCHEMA_CORE
updated: "2026-04-20"
---

Schema text
EOF

cat > "${fixture_root}/cli/src/test/java/dev/erst/fingrind/cli/ExampleTest.java" <<'EOF'
class ExampleTest {
  String version = "1.2.3";
}
EOF

cat > "${fixture_root}/contract/src/test/java/dev/erst/fingrind/contract/ExampleTest.java" <<'EOF'
class ExampleTest {
  String version = "1.2.3";
}
EOF

cat > "${fixture_root}/report-pdf/src/test/java/dev/erst/fingrind/report/pdf/ExampleTest.java" <<'EOF'
class ExampleTest {
  String creator = "FinGrind 1.2.3";
}
EOF

FINGRIND_RELEASE_REPO_ROOT="${fixture_root}" \
    "${prepare_script}" "1.2.4" "2026-04-23" >/dev/null

require_file_contains "${fixture_root}/gradle.properties" 'version=1.2.4'
require_file_contains "${fixture_root}/docs/RELEASE_PROTOCOL.md" 'version: "1.2.4"'
require_file_contains "${fixture_root}/docs/RELEASE_PROTOCOL.md" 'updated: "2026-04-23"'
require_file_contains "${fixture_root}/docs/USER_CLI.md" 'fingrind-1.2.4-macos-aarch64.tar.gz'
require_file_contains "${fixture_root}/docs/USER_CLI.md" 'fingrind-1.2.4-windows-x86_64.zip'
require_file_contains "${fixture_root}/docs/USER_EXAMPLES.md" './fingrind-1.2.4-macos-aarch64/bin/fingrind help'
require_file_contains "${fixture_root}/cli/src/test/java/dev/erst/fingrind/cli/ExampleTest.java" '"1.2.4"'
require_file_contains "${fixture_root}/report-pdf/src/test/java/dev/erst/fingrind/report/pdf/ExampleTest.java" \
    '"FinGrind 1.2.4"'
require_file_contains "${fixture_root}/CHANGELOG.md" '## [Unreleased]'
require_file_contains "${fixture_root}/CHANGELOG.md" '## [1.2.4] - 2026-04-23'
require_file_contains "${fixture_root}/CHANGELOG.md" '- Example fix pending release.'
require_file_contains "${fixture_root}/CHANGELOG.md" \
    '[Unreleased]: https://github.com/resoltico/FinGrind/compare/v1.2.4...HEAD'
require_file_contains "${fixture_root}/CHANGELOG.md" \
    '[1.2.4]: https://github.com/resoltico/FinGrind/releases/tag/v1.2.4'

FINGRIND_RELEASE_REPO_ROOT="${fixture_root}" \
    "${prepare_script}" "1.2.4" "2026-04-23" >/dev/null

if [[ "$(grep -c '^## \[1.2.4\] - 2026-04-23$' "${fixture_root}/CHANGELOG.md")" -ne 1 ]]; then
    die "release changelog section duplicated on repeat invocation"
fi

printf 'prepare-release-version regression: success\n'
