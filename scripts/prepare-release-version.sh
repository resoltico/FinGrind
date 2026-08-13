#!/usr/bin/env bash
# Apply the target release version across the canonical version-bearing surfaces so release prep
# is repeatable instead of relying on ad hoc manual edits.

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

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "required command '$1' is not available"
}

require_semver() {
    [[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "release version must match X.Y.Z"
}

require_iso_date() {
    [[ "$1" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || die "release date must match YYYY-MM-DD"
}

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="${FINGRIND_RELEASE_REPO_ROOT:-$(cd -P -- "${script_dir}/.." && pwd)}"
readonly target_version="${1:-}"
readonly release_date="${2:-}"

[[ -n "${target_version}" ]] || die \
    "usage: scripts/prepare-release-version.sh <target-version> <release-date>"
[[ -n "${release_date}" ]] || die \
    "usage: scripts/prepare-release-version.sh <target-version> <release-date>"
require_semver "${target_version}"
require_iso_date "${release_date}"
require_command python3

readonly gradle_properties_path="${repo_root}/gradle.properties"
[[ -f "${gradle_properties_path}" ]] || die "missing gradle.properties at ${gradle_properties_path}"

readonly current_version="$(awk -F= '/^version=/{print $2; exit}' "${gradle_properties_path}")"
[[ -n "${current_version}" ]] || die "could not determine current version from gradle.properties"

python3 - "${repo_root}" "${current_version}" "${target_version}" "${release_date}" <<'PY'
from __future__ import annotations

from pathlib import Path
import re
import sys

repo_root = Path(sys.argv[1])
source_version = sys.argv[2]
target_version = sys.argv[3]
release_date = sys.argv[4]

docs_root = repo_root / "docs"
if not docs_root.is_dir():
    raise SystemExit(f"error: missing docs directory at {docs_root}")


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write_if_changed(path: Path, updated_text: str) -> None:
    original_text = read_text(path)
    if original_text != updated_text:
        path.write_text(updated_text, encoding="utf-8")


def replace_required(
    text: str, pattern: str, replacement: str, *, count: int = 1, flags: int = re.MULTILINE
) -> str:
    updated_text, replacements = re.subn(pattern, replacement, text, count=count, flags=flags)
    if replacements != count:
        raise SystemExit(f"error: expected {count} replacement(s) for pattern {pattern!r}")
    return updated_text


gradle_properties = repo_root / "gradle.properties"
gradle_text = read_text(gradle_properties)
gradle_text = replace_required(
    gradle_text,
    r"^version=.*$",
    f"version={target_version}",
)
write_if_changed(gradle_properties, gradle_text)

for doc_path in sorted(docs_root.rglob("*.md")):
    doc_text = read_text(doc_path)
    updated_text = doc_text
    updated_text, version_replacements = re.subn(
        r'^version: ".*"$',
        f'version: "{target_version}"',
        updated_text,
        count=1,
        flags=re.MULTILINE,
    )
    updated_text, date_replacements = re.subn(
        r'^updated: ".*"$',
        f'updated: "{release_date}"',
        updated_text,
        count=1,
        flags=re.MULTILINE,
    )
    if version_replacements == 1 or date_replacements == 1:
        if version_replacements != 1 or date_replacements != 1:
            raise SystemExit(f"error: incomplete frontmatter in {doc_path}")
        updated_text = updated_text.replace(
            f"fingrind-{source_version}-", f"fingrind-{target_version}-"
        )
        write_if_changed(doc_path, updated_text)

versioned_test_roots = (
    repo_root / "cli" / "src" / "test",
    repo_root / "contract" / "src" / "test",
    repo_root / "report-pdf" / "src" / "test",
)
for test_root in versioned_test_roots:
    if not test_root.is_dir():
        continue
    for test_path in sorted(test_root.rglob("*.java")):
        test_text = read_text(test_path)
        if source_version not in test_text:
            continue
        updated_text = test_text.replace(source_version, target_version)
        write_if_changed(test_path, updated_text)

changelog_path = repo_root / "CHANGELOG.md"
changelog_text = read_text(changelog_path)

unreleased_match = re.search(r"^## \[Unreleased\]\n", changelog_text, flags=re.MULTILINE)
if unreleased_match is None:
    raise SystemExit("error: CHANGELOG.md is missing the [Unreleased] heading")

release_match = re.search(
    rf"^## \[{re.escape(target_version)}\] - {re.escape(release_date)}$",
    changelog_text,
    flags=re.MULTILINE,
)
if release_match is None:
    next_heading = re.search(
        r"^## \[",
        changelog_text[unreleased_match.end() :],
        flags=re.MULTILINE,
    )
    if next_heading is None:
        raise SystemExit("error: CHANGELOG.md is missing a release section after [Unreleased]")
    unreleased_body_start = unreleased_match.end()
    unreleased_body_end = unreleased_body_start + next_heading.start()
    unreleased_body = changelog_text[unreleased_body_start:unreleased_body_end].strip("\n")
    remainder = changelog_text[unreleased_body_end:].lstrip("\n")
    release_header = f"## [{target_version}] - {release_date}"
    new_changelog = changelog_text[: unreleased_match.start()]
    new_changelog += "## [Unreleased]\n\n"
    new_changelog += f"{release_header}\n"
    if unreleased_body:
        new_changelog += f"\n{unreleased_body}\n\n"
    else:
        new_changelog += "\n"
    new_changelog += remainder
    changelog_text = new_changelog

changelog_text = replace_required(
    changelog_text,
    r"^\[Unreleased\]: https://github\.com/resoltico/FinGrind/compare/v[0-9]+\.[0-9]+\.[0-9]+\.\.\.HEAD$",
    f"[Unreleased]: https://github.com/resoltico/FinGrind/compare/v{target_version}...HEAD",
)
release_link = (
    f"[{target_version}]: https://github.com/resoltico/FinGrind/releases/tag/v{target_version}"
)
if re.search(rf"^\[{re.escape(target_version)}\]: ", changelog_text, flags=re.MULTILINE) is None:
    previous_link_match = re.search(
        rf"^\[{re.escape(source_version)}\]: .*$",
        changelog_text,
        flags=re.MULTILINE,
    )
    if previous_link_match is None:
        if not changelog_text.endswith("\n"):
            changelog_text += "\n"
        changelog_text += f"{release_link}\n"
    else:
        changelog_text = (
            changelog_text[: previous_link_match.start()]
            + f"{release_link}\n"
            + changelog_text[previous_link_match.start() :]
        )

write_if_changed(changelog_path, changelog_text)
PY

if [[ -x "${repo_root}/gradlew" ]] && [[ -f "${repo_root}/contract/build.gradle.kts" ]]; then
    (
        cd "${repo_root}"
        ./gradlew --quiet --no-configuration-cache \
            :contract:syncUserInstallDocs \
            :contract:syncUserPdfCapabilityDocs
    )
fi

printf 'Prepared release version %s (%s)\n' "${target_version}" "${release_date}"
