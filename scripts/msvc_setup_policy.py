"""JSON transport boundary for the pure MSVC developer-command policy owners."""

from __future__ import annotations

import json
import sys
from collections.abc import Mapping, Sequence
from pathlib import Path

# Isolated Python excludes a script directory; add only this resolved, trusted owner directory.
_POLICY_OWNER_DIRECTORY = str(Path(__file__).resolve().parent)
if _POLICY_OWNER_DIRECTORY not in sys.path:
    sys.path.insert(0, _POLICY_OWNER_DIRECTORY)

from msvc_setup_policy_discovery import (
    VSDEV_CMD_NOT_FOUND_FAILURE,
    plan_vsdevcmd_candidates,
    render_vsdevcmd_command_line,
    select_vswhere_installation,
    vswhere_arguments,
)
from msvc_setup_policy_environment import (
    parse_vsdevcmd_environment_dump,
    serialize_github_environment,
    validate_vsdevcmd_environment,
)
from msvc_setup_policy_models import MsvcSetupPolicyError


def execute_request(request: object) -> dict[str, object]:
    """Evaluate one JSON adapter request without reading process or filesystem state."""

    if not isinstance(request, Mapping):
        raise MsvcSetupPolicyError("MSVC setup policy request must be a JSON object")
    operation = _required_text(request, "operation")
    payload_value = request.get("payload")
    if not isinstance(payload_value, Mapping):
        raise MsvcSetupPolicyError("MSVC setup policy request payload must be a JSON object")

    if operation == "vswhere-arguments":
        return {"arguments": list(vswhere_arguments())}
    if operation == "select-vswhere-installation":
        return {
            "installationPath": select_vswhere_installation(
                payload_value.get("exitCode"), payload_value.get("output")
            )
        }
    if operation == "vsdevcmd-candidates":
        candidate_paths = plan_vsdevcmd_candidates(
            _optional_text(payload_value, "installationPath"),
            _optional_text(payload_value, "programFiles"),
        )
        return {
            "candidatePaths": list(candidate_paths),
            "notFoundMessage": VSDEV_CMD_NOT_FOUND_FAILURE,
        }
    if operation == "command-line":
        return {
            "commandLine": render_vsdevcmd_command_line(
                _required_text(payload_value, "vsdevcmdPath"),
                _required_text(payload_value, "arch"),
                _required_text(payload_value, "hostArch"),
            )
        }
    if operation == "github-environment":
        environment_dump = _required_text_sequence(payload_value, "environmentDump")
        entries = parse_vsdevcmd_environment_dump(environment_dump)
        validate_vsdevcmd_environment(entries)
        return {"githubEnvironment": serialize_github_environment(entries)}
    raise MsvcSetupPolicyError(f"unknown MSVC setup policy operation: {operation}")


def main() -> int:
    """Read exactly one request from standard input and write exactly one JSON response."""

    try:
        request = json.load(sys.stdin)
        response = execute_request(request)
    except (json.JSONDecodeError, MsvcSetupPolicyError) as error:
        sys.stderr.write(f"{error}\n")
        return 1
    sys.stdout.write(json.dumps(response, separators=(",", ":"), ensure_ascii=False))
    return 0


def _required_text(payload: Mapping[str, object], name: str) -> str:
    value = payload.get(name)
    if not isinstance(value, str):
        raise MsvcSetupPolicyError(f"MSVC setup policy request field {name} must be text")
    return value


def _optional_text(payload: Mapping[str, object], name: str) -> str | None:
    value = payload.get(name)
    if value is None:
        return None
    if not isinstance(value, str):
        raise MsvcSetupPolicyError(f"MSVC setup policy request field {name} must be text or null")
    return value


def _required_text_sequence(payload: Mapping[str, object], name: str) -> tuple[str, ...]:
    value = payload.get(name)
    if isinstance(value, (bytes, str)) or not isinstance(value, Sequence):
        raise MsvcSetupPolicyError(f"MSVC setup policy request field {name} must be a text array")
    if any(not isinstance(item, str) for item in value):
        raise MsvcSetupPolicyError(f"MSVC setup policy request field {name} must be a text array")
    return tuple(value)


if __name__ == "__main__":
    raise SystemExit(main())
