#!/usr/bin/env bash
# Build-time and contract-level validation for the committed contributor devcontainer surface.

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

readonly repo_root="$(cd "$(resolve_script_dir)/.." && pwd)"
readonly dockerfile_path="${repo_root}/.devcontainer/Dockerfile"
readonly config_path="${repo_root}/.devcontainer/devcontainer.json"

command -v docker >/dev/null 2>&1 || die "docker is required to validate the contributor devcontainer"
command -v python3 >/dev/null 2>&1 || die "python3 is required to validate devcontainer.json"
[[ -f "${dockerfile_path}" ]] || die "missing ${dockerfile_path}"
[[ -f "${config_path}" ]] || die "missing ${config_path}"

python3 - <<'PY' "${config_path}"
import json
import sys
from pathlib import Path

config = json.loads(Path(sys.argv[1]).read_text())

expected_feature = "ghcr.io/devcontainers/features/docker-outside-of-docker:1"
features = config.get("features", {})
if expected_feature not in features:
    raise SystemExit(f"missing {expected_feature} feature")

if config.get("remoteUser") != "vscode":
    raise SystemExit("remoteUser must stay 'vscode'")

if config.get("workspaceFolder") != "/workspaces/fingrind":
    raise SystemExit("workspaceFolder must stay /workspaces/fingrind")

mounts = config.get("mounts", [])
if not any("target=/home/vscode/.gradle" in mount for mount in mounts):
    raise SystemExit("devcontainer must keep a named Gradle cache volume")
if not any("target=/home/vscode/.cache" in mount for mount in mounts):
    raise SystemExit("devcontainer must keep a named general cache volume")

settings = config.get("customizations", {}).get("vscode", {}).get("settings", {})
runtimes = settings.get("java.configuration.runtimes", [])
if not any(runtime.get("path") == "/usr/lib/jvm/zulu-26" and runtime.get("default") for runtime in runtimes):
    raise SystemExit("devcontainer must register /usr/lib/jvm/zulu-26 as the default Java runtime")

extension_kind = settings.get("remote.extensionKind", {})
for extension_id in ("redhat.java", "vscjava.vscode-gradle", "vscjava.vscode-java-test"):
    if extension_kind.get(extension_id) != ["workspace"]:
        raise SystemExit(f"{extension_id} must stay forced into the workspace/container extension host")

extensions = config.get("customizations", {}).get("vscode", {}).get("extensions", [])
for extension_id in ("redhat.java", "vscjava.vscode-gradle", "vscjava.vscode-java-test"):
    if extension_id not in extensions:
        raise SystemExit(f"{extension_id} must remain installed in the devcontainer")
PY

readonly image_tag="fingrind-devcontainer-validate:local"

docker build \
    --file "${dockerfile_path}" \
    --tag "${image_tag}" \
    "${repo_root}/.devcontainer" >/dev/null

docker run --rm "${image_tag}" bash -lc '
    set -euo pipefail
    . /etc/os-release
    case "${ID}" in
        ubuntu|debian) ;;
        *) echo "unsupported base distribution: ${ID}" >&2; exit 1 ;;
    esac
    java -XshowSettings:properties -version 2>&1 | grep -F "java.vendor = Azul Systems, Inc." >/dev/null
    java --version | head -1 | grep -E "26(\\.|\\+| )" >/dev/null
    javac --version | grep -E "^javac 26(\\.|$)" >/dev/null
    git --version >/dev/null
    gh --version >/dev/null
    jq --version >/dev/null
    python3 --version >/dev/null
    shellcheck --version >/dev/null
    fc-match "DejaVu Sans" >/dev/null
'

printf 'devcontainer validation: success\n'
