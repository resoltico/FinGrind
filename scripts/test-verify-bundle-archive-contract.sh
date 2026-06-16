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
readonly verifier_support="${repo_root}/scripts/bundle_archive_contract_support.py"
readonly verifier_root="${repo_root}/scripts/bundle_archive_root_verification.py"
readonly verifier_manifest="${repo_root}/scripts/bundle_archive_manifest_verification.py"
readonly verifier_runtime="${repo_root}/scripts/bundle_archive_runtime_verification.py"

[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${verifier_support}" ]] || die "missing bundle-archive verifier support at ${verifier_support}"
[[ -f "${verifier_root}" ]] || die "missing bundle-root verifier at ${verifier_root}"
[[ -f "${verifier_manifest}" ]] || die "missing bundle-manifest verifier at ${verifier_manifest}"
[[ -f "${verifier_runtime}" ]] || die "missing bundle-runtime verifier at ${verifier_runtime}"
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

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-bundle-archive-contract.XXXXXX")"
cleanup() {
    rm -rf "${fixture_root}"
}
trap cleanup EXIT

mkdir -p \
    "${fixture_root}/bin" \
    "${fixture_root}/lib/app" \
    "${fixture_root}/lib/native" \
    "${fixture_root}/runtime/bin"
touch "${fixture_root}/lib/app/fingrind.jar"
touch "${fixture_root}/lib/native/libsqlite3.so.0"
touch "${fixture_root}/lib/native/libsqlite3.so.0.sha256"
touch "${fixture_root}/quick-start-request.json"
touch "${fixture_root}/LICENSE"
touch "${fixture_root}/LICENSE-APACHE-2.0"
touch "${fixture_root}/LICENSE-SIL-OFL-1.1"
touch "${fixture_root}/LICENSE-SQLITE3MULTIPLECIPHERS"
touch "${fixture_root}/NOTICE"
touch "${fixture_root}/PATENTS.md"
touch "${fixture_root}/README.md"
touch "${fixture_root}/bin/fingrind"
touch "${fixture_root}/runtime/bin/java"
chmod +x "${fixture_root}/bin/fingrind" "${fixture_root}/runtime/bin/java"
cat > "${fixture_root}/bundle-manifest.json" <<'EOF'
{"normalizedArtifactTimestampUtc":"2026-06-14T16:43:09Z","bundleTarget":{"classifier":"linux-aarch64"}}
EOF

python3 - <<'PY' "${fixture_root}"
from __future__ import annotations

import os
from pathlib import Path
import sys

fixture_root = Path(sys.argv[1])
normalized_epoch_seconds = 1781455389
for path in [fixture_root, *fixture_root.rglob("*")]:
    os.utime(path, (normalized_epoch_seconds, normalized_epoch_seconds))
PY

PYTHONPATH="${repo_root}/scripts${PYTHONPATH:+:${PYTHONPATH}}" python3 - <<'PY' \
    "${repo_root}" \
    "${fixture_root}"
from pathlib import Path
import sys

from bundle_archive_root_verification import verify_bundle_root_files
from contract_values import load_contract_values

repo_root = Path(sys.argv[1])
bundle_root = Path(sys.argv[2])
contract = load_contract_values(repo_root)
verify_bundle_root_files(bundle_root, contract)
PY

python3 - <<'PY' "${fixture_root}"
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

PYTHONPATH="${repo_root}/scripts${PYTHONPATH:+:${PYTHONPATH}}" python3 - <<'PY' \
    "${repo_root}" \
    "${fixture_root}"
from pathlib import Path
import sys

from bundle_archive_root_verification import verify_bundle_root_files
from contract_values import load_contract_values

repo_root = Path(sys.argv[1])
bundle_root = Path(sys.argv[2])
contract = load_contract_values(repo_root)
verify_bundle_root_files(bundle_root, contract)
PY

printf 'bundle archive verifier regression: success\n'
