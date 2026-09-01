#!/usr/bin/env bash
# Guard the bundle-archive verifier against drifting back to host-target assumptions.

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
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly python_runtime_support="${repo_root}/scripts/python-runtime-support.sh"
readonly verifier_support="${repo_root}/scripts/bundle_archive_contract_support.py"
readonly verifier_root="${repo_root}/scripts/bundle_archive_root_verification.py"
readonly verifier_manifest="${repo_root}/scripts/bundle_archive_manifest_verification.py"
readonly verifier_runtime="${repo_root}/scripts/bundle_archive_runtime_verification.py"
readonly verifier_entrypoint="${repo_root}/scripts/verify-bundle-archive-contract.py"

[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${python_runtime_support}" ]] || die \
    "missing Python runtime support at ${python_runtime_support}"
[[ -f "${verifier_support}" ]] || die "missing bundle-archive verifier support at ${verifier_support}"
[[ -f "${verifier_root}" ]] || die "missing bundle-root verifier at ${verifier_root}"
[[ -f "${verifier_manifest}" ]] || die "missing bundle-manifest verifier at ${verifier_manifest}"
[[ -f "${verifier_runtime}" ]] || die "missing bundle-runtime verifier at ${verifier_runtime}"
[[ -f "${verifier_entrypoint}" ]] || die "missing bundle-archive verifier entrypoint at ${verifier_entrypoint}"
grep -Fq 'scripts/test-verify-bundle-archive-contract.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the bundle-archive verifier regression"
grep -Fq 'resolve_bundle_target' "${verifier_root}" || die \
    "bundle-root verifier no longer resolves bundle targets from the extracted bundle manifest"
grep -Fq 'resolve_bundle_target' "${verifier_manifest}" || die \
    "bundle-manifest verifier no longer resolves bundle targets from the extracted bundle manifest"
grep -Fq 'resolve_bundle_target' "${verifier_runtime}" || die \
    "bundle-runtime verifier no longer resolves bundle targets from the extracted bundle manifest"
if grep -Fq 'hostBundleTarget' "${verifier_root}"; then
    die "bundle-root verifier drifted back to host bundle targeting"
fi
if grep -Fq 'hostBundleTarget' "${verifier_manifest}"; then
    die "bundle-manifest verifier drifted back to host bundle targeting"
fi
if grep -Fq 'hostBundleTarget' "${verifier_runtime}"; then
    die "bundle-runtime verifier drifted back to host bundle targeting"
fi
grep -Fq -- '--structural-only' "${verifier_entrypoint}" || die \
    "bundle-archive verifier no longer exposes its non-distributable structural mode"
grep -Fq 'require_host_executability' "${verifier_root}" || die \
    "bundle-root verifier no longer distinguishes structural fixtures from executable bundles"

# shellcheck source=/dev/null
source "${python_runtime_support}"
prepare_python_runtime_env || die \
    "bundle-archive verifier regression could not prepare the repository-owned exact Python runtime"
readonly python_executable="${FINGRIND_PYTHON_EXECUTABLE}"

PYTHONPATH="${repo_root}/scripts${PYTHONPATH:+:${PYTHONPATH}}" "${python_executable}" - <<'PY' \
    "${verifier_entrypoint}" \
    "${repo_root}"
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
from unittest.mock import patch

entrypoint = Path(sys.argv[1])
repo_root = Path(sys.argv[2])
specification = importlib.util.spec_from_file_location("bundle_archive_contract_entrypoint", entrypoint)
assert specification is not None and specification.loader is not None
module = importlib.util.module_from_spec(specification)
specification.loader.exec_module(module)


def verify_mode(*, structural_only: bool) -> list[tuple[str, object]]:
    calls: list[tuple[str, object]] = []
    module.load_contract_values = lambda root: {"root": root}
    module.verify_bundle_root_files = lambda root, contract, require_host_executability, **_: calls.append(
        ("root", require_host_executability)
    )
    module.verify_bundle_manifest = lambda root, contract: calls.append(("manifest", root))
    module.verify_bundled_runtime = lambda root, contract: calls.append(("runtime", root))
    module.verify_distributed_module_identity = lambda root, contract: calls.append(("module", root))
    arguments = [
        str(entrypoint),
        "--repo-root",
        str(repo_root),
        "--bundle-root",
        str(repo_root / "tmp" / "synthetic-bundle"),
    ]
    if structural_only:
        arguments.append("--structural-only")
    with patch.object(sys, "argv", arguments):
        assert module.main() == 0
    return calls


structural_calls = verify_mode(structural_only=True)
assert structural_calls[0] == ("root", False)
assert [name for name, _ in structural_calls] == ["root", "manifest", "module"]
normal_calls = verify_mode(structural_only=False)
assert normal_calls[0] == ("root", True)
assert [name for name, _ in normal_calls] == ["root", "manifest", "runtime", "module"]
PY

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-bundle-archive-contract.XXXXXX")"
cleanup() {
    rm -rf "${fixture_root}"
}
trap cleanup EXIT

mkdir -p \
    "${fixture_root}/bin" \
    "${fixture_root}/lib/app" \
    "${fixture_root}/lib/native" \
    "${fixture_root}/lib/release-smoke" \
    "${fixture_root}/runtime/bin"
"${python_executable}" - <<'PY' \
    "${fixture_root}/lib/app/fingrind.jar" \
    "${fixture_root}/test-runtime-legal.lock.tsv"
from pathlib import Path
import hashlib
import sys
import zipfile

application_jar = Path(sys.argv[1])
lock_file = Path(sys.argv[2])
entries = {
    "META-INF/LICENSE": "MIT License",
    "META-INF/NOTICE": "FinGrind notice",
    "META-INF/NOTICE-ZULU-26.32.203": "Zulu26.32+203-CA",
    "META-INF/LICENSE-APACHE-2.0": "Apache License",
    "META-INF/LICENSE-CC0-1.0": "CC0",
    "META-INF/LICENSE-SIL-OFL-1.1": "SIL OFL",
    "META-INF/LICENSE-SQLITE3MULTIPLECIPHERS": "SQLite3MC",
    "META-INF/LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY": "Olivier Gay",
    "META-INF/SOURCE_OFFER.md": "corresponding source",
}
legal_resources = [
    ("commons-logging-1.4.0.jar", "META-INF/LICENSE.txt", "Commons license"),
    ("commons-logging-1.4.0.jar", "META-INF/NOTICE.txt", "Commons Logging"),
    ("fontbox-3.0.8.jar", "META-INF/LICENSE", "FontBox license"),
    ("fontbox-3.0.8.jar", "META-INF/NOTICE", "FontBox"),
    ("jackson-annotations-2.22.jar", "META-INF/LICENSE", "Jackson annotations license"),
    ("jackson-annotations-2.22.jar", "META-INF/NOTICE", "Jackson annotations"),
    ("jackson-core-3.2.2.jar", "META-INF/LICENSE", "Jackson core license"),
    ("jackson-core-3.2.2.jar", "META-INF/NOTICE", "Jackson"),
    ("jackson-core-3.2.2.jar", "META-INF/Schubfach-LICENSE", "Schubfach"),
    ("jackson-databind-3.2.2.jar", "META-INF/LICENSE", "Jackson databind license"),
    ("jackson-databind-3.2.2.jar", "META-INF/NOTICE", "Jackson databind"),
    ("pdfbox-3.0.8.jar", "META-INF/LICENSE", "EXTERNAL COMPONENTS"),
    ("pdfbox-3.0.8.jar", "META-INF/NOTICE", "PDFBox"),
    ("pdfbox-io-3.0.8.jar", "META-INF/LICENSE", "PDFBox IO license"),
    ("pdfbox-io-3.0.8.jar", "META-INF/NOTICE", "PDFBox IO"),
]
index_rows = ["artifact\tartifact-sha256\tresource\tresource-sha256"]
for artifact, resource, text in legal_resources:
    relative_resource = resource.removeprefix("META-INF/")
    staged_path = f"META-INF/third-party/{artifact.removesuffix('.jar')}/{relative_resource}"
    entries[staged_path] = text
    resource_digest = hashlib.sha256(text.encode("utf-8")).hexdigest()
    index_rows.append(f"{artifact}\t{'0' * 64}\t{resource}\t{resource_digest}")
entries["META-INF/third-party/INDEX.tsv"] = "\n".join(index_rows) + "\n"
lock_file.write_text(entries["META-INF/third-party/INDEX.tsv"], encoding="utf-8")
with zipfile.ZipFile(application_jar, "w") as archive:
    for name, text in entries.items():
        archive.writestr(name, text)
PY
"${python_executable}" - <<'PY' "${fixture_root}/lib/release-smoke/native-sqlite-format-boundary-probe.jar"
from __future__ import annotations

from pathlib import Path
import sys
import zipfile

probe = Path(sys.argv[1])
with zipfile.ZipFile(probe, "w") as archive:
    for entry in (
        "NativeSqliteFormatBoundaryProbe.class",
        "NativeSqliteFormatBoundaryProbe$Arguments.class",
        "NativeSqliteFormatBoundaryProbe$ProbeFailure.class",
        "NativeSqliteFormatBoundaryProbe$Sqlite.class",
    ):
        archive.writestr(entry, b"fixture")
PY
touch "${fixture_root}/lib/native/libsqlite3.so.0"
touch "${fixture_root}/lib/native/libsqlite3.so.0.sha256"
touch "${fixture_root}/lib/native/toolchain-fingerprint.json"
touch "${fixture_root}/lib/native/build-contract.json"
"${python_executable}" - <<'PY' \
    "${fixture_root}/lib/native/libsqlite3.so.0" \
    "${fixture_root}/lib/native/libsqlite3.so.0.sha256"
from __future__ import annotations

import hashlib
import os
from pathlib import Path
import sys

native_library = Path(sys.argv[1])
checksum_file = Path(sys.argv[2])
checksum_file.write_text(
    hashlib.sha256(native_library.read_bytes()).hexdigest() + "  " + native_library.name + "\n",
    encoding="utf-8",
)
normalized_epoch_seconds = 1781455388
os.utime(checksum_file, (normalized_epoch_seconds, normalized_epoch_seconds))
PY
touch "${fixture_root}/quick-start-request.json"
cp "${repo_root}/LICENSE" "${fixture_root}/LICENSE"
cp "${repo_root}/LICENSE-APACHE-2.0" "${fixture_root}/LICENSE-APACHE-2.0"
cp "${repo_root}/LICENSE-CC0-1.0" "${fixture_root}/LICENSE-CC0-1.0"
cp "${repo_root}/LICENSE-SIL-OFL-1.1" "${fixture_root}/LICENSE-SIL-OFL-1.1"
cp "${repo_root}/LICENSE-SQLITE3MULTIPLECIPHERS" "${fixture_root}/LICENSE-SQLITE3MULTIPLECIPHERS"
cp "${repo_root}/LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY" "${fixture_root}/LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY"
cp "${repo_root}/NOTICE" "${fixture_root}/NOTICE"
cp "${repo_root}/NOTICE-ZULU-26.32.203" "${fixture_root}/NOTICE-ZULU-26.32.203"
cp "${repo_root}/PATENTS.md" "${fixture_root}/PATENTS.md"
cp "${repo_root}/SOURCE_OFFER.md" "${fixture_root}/SOURCE_OFFER.md"
touch "${fixture_root}/README.md"
touch "${fixture_root}/bin/fingrind"
touch "${fixture_root}/runtime/bin/java"
mkdir -p "${fixture_root}/runtime/legal/java.base"
mkdir -p "${fixture_root}/runtime/provenance"
printf 'GNU General Public License\n' > "${fixture_root}/runtime/legal/java.base/LICENSE"
printf 'Classpath Exception\n' > "${fixture_root}/runtime/legal/java.base/ADDITIONAL_LICENSE_INFO"
printf 'OPENJDK ASSEMBLY EXCEPTION\n' > "${fixture_root}/runtime/legal/java.base/ASSEMBLY_EXCEPTION"
printf 'JAVA_VERSION="26.0.2.1"\nMODULES="java.base"\n' > "${fixture_root}/runtime/release"
printf 'IMPLEMENTOR="Azul Systems, Inc."\nIMPLEMENTOR_VERSION="Zulu26.32+203-CA"\nJAVA_RUNTIME_VERSION="26.0.2.1+1"\nJAVA_VERSION="26.0.2.1"\nOS_ARCH="aarch64"\nOS_NAME="Linux"\nSOURCE=".:git:fixture"\n' > "${fixture_root}/runtime/provenance/source-jdk-release"
printf 'java.base\n' > "${fixture_root}/runtime/provenance/requested-modules.txt"
"${python_executable}" - <<'PY' "${fixture_root}/runtime/legal"
from pathlib import Path
import hashlib
import sys

legal_root = Path(sys.argv[1])
rows = []
for legal_file in sorted(path for path in legal_root.rglob("*") if path.is_file()):
    relative_name = legal_file.relative_to(legal_root).as_posix()
    rows.append(f"{hashlib.sha256(legal_file.read_bytes()).hexdigest()}  {relative_name}")
legal_root.joinpath("INDEX.sha256").write_text("\n".join(rows) + "\n", encoding="utf-8")
PY
chmod +x "${fixture_root}/bin/fingrind" "${fixture_root}/runtime/bin/java"
cat > "${fixture_root}/bundle-manifest.json" <<'EOF'
{"normalizedArtifactTimestampUtc":"2026-06-14T16:43:08Z","bundleTarget":{"classifier":"linux-aarch64"}}
EOF

"${python_executable}" - <<'PY' "${fixture_root}/bundle-manifest.json"
from __future__ import annotations

import os
from pathlib import Path
import sys

manifest_path = Path(sys.argv[1])
normalized_epoch_seconds = 1781455388
os.utime(manifest_path, (normalized_epoch_seconds, normalized_epoch_seconds))
PY

"${python_executable}" - <<'PY' "${fixture_root}"
from __future__ import annotations

import os
from pathlib import Path
import sys

fixture_root = Path(sys.argv[1])
normalized_epoch_seconds = 1781455388
for path in [fixture_root, *fixture_root.rglob("*")]:
    os.utime(path, (normalized_epoch_seconds, normalized_epoch_seconds))
PY

PYTHONPATH="${repo_root}/scripts${PYTHONPATH:+:${PYTHONPATH}}" "${python_executable}" - <<'PY' \
    "${repo_root}" \
    "${fixture_root}"
from pathlib import Path
import sys

from bundle_archive_root_verification import verify_bundle_root_files
from contract_values import load_contract_values

repo_root = Path(sys.argv[1])
bundle_root = Path(sys.argv[2])
contract = load_contract_values(repo_root)
verify_bundle_root_files(
    bundle_root,
    contract,
    runtime_legal_lock_file=bundle_root / "test-runtime-legal.lock.tsv",
)
PY

printf '%s\n' '0  libsqlite3.so.0' > "${fixture_root}/lib/native/libsqlite3.so.0.sha256"
if PYTHONPATH="${repo_root}/scripts${PYTHONPATH:+:${PYTHONPATH}}" "${python_executable}" - <<'PY' \
    "${repo_root}" \
    "${fixture_root}"
from pathlib import Path
import sys

from bundle_archive_root_verification import verify_bundle_root_files
from contract_values import load_contract_values

repo_root = Path(sys.argv[1])
bundle_root = Path(sys.argv[2])
contract = load_contract_values(repo_root)
verify_bundle_root_files(
    bundle_root,
    contract,
    runtime_legal_lock_file=bundle_root / "test-runtime-legal.lock.tsv",
)
PY
then
    die "bundle archive verifier accepted one mismatched native SQLite checksum"
fi

"${python_executable}" - <<'PY' \
    "${fixture_root}/lib/native/libsqlite3.so.0" \
    "${fixture_root}/lib/native/libsqlite3.so.0.sha256"
from __future__ import annotations

import hashlib
import os
from pathlib import Path
import sys

native_library = Path(sys.argv[1])
checksum_file = Path(sys.argv[2])
checksum_file.write_text(
    hashlib.sha256(native_library.read_bytes()).hexdigest() + "  " + native_library.name + "\n",
    encoding="utf-8",
)
normalized_epoch_seconds = 1781455388
os.utime(checksum_file, (normalized_epoch_seconds, normalized_epoch_seconds))
PY

cat > "${fixture_root}/bundle-manifest.json" <<'EOF'
{"normalizedArtifactTimestampUtc":"2026-06-14T16:43:09Z","bundleTarget":{"classifier":"linux-aarch64"}}
EOF

if PYTHONPATH="${repo_root}/scripts${PYTHONPATH:+:${PYTHONPATH}}" "${python_executable}" - <<'PY' \
    "${repo_root}" \
    "${fixture_root}"
from pathlib import Path
import sys

from bundle_archive_root_verification import verify_bundle_root_files
from contract_values import load_contract_values

repo_root = Path(sys.argv[1])
bundle_root = Path(sys.argv[2])
contract = load_contract_values(repo_root)
verify_bundle_root_files(
    bundle_root,
    contract,
    runtime_legal_lock_file=bundle_root / "test-runtime-legal.lock.tsv",
)
PY
then
    die "bundle archive verifier accepted one timestamp that ZIP extraction cannot preserve"
fi

cat > "${fixture_root}/bundle-manifest.json" <<'EOF'
{"normalizedArtifactTimestampUtc":"2026-06-14T16:43:08Z","bundleTarget":{"classifier":"linux-aarch64"}}
EOF

"${python_executable}" - <<'PY' "${fixture_root}/bundle-manifest.json"
from __future__ import annotations

import os
from pathlib import Path
import sys

manifest_path = Path(sys.argv[1])
normalized_epoch_seconds = 1781455388
os.utime(manifest_path, (normalized_epoch_seconds, normalized_epoch_seconds))
PY

"${python_executable}" - <<'PY' "${fixture_root}"
from __future__ import annotations

import os
from pathlib import Path
import sys

fixture_root = Path(sys.argv[1])
shifted_epoch_seconds = 1781456399
for directory_path in [fixture_root, *fixture_root.rglob("*")]:
    if directory_path.is_dir():
        os.utime(directory_path, (shifted_epoch_seconds, shifted_epoch_seconds))
PY

PYTHONPATH="${repo_root}/scripts${PYTHONPATH:+:${PYTHONPATH}}" "${python_executable}" - <<'PY' \
    "${repo_root}" \
    "${fixture_root}"
from pathlib import Path
import sys

from bundle_archive_root_verification import verify_bundle_root_files
from contract_values import load_contract_values

repo_root = Path(sys.argv[1])
bundle_root = Path(sys.argv[2])
contract = load_contract_values(repo_root)
verify_bundle_root_files(
    bundle_root,
    contract,
    runtime_legal_lock_file=bundle_root / "test-runtime-legal.lock.tsv",
)
PY

printf 'bundle archive verifier regression: success\n'
