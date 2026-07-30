"""Regression tests for the isolated JSON transport boundary of MSVC policy."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import msvc_setup_policy as policy
from msvc_setup_policy_models import MsvcSetupPolicyError

POLICY_PATH = Path(__file__).with_name("msvc_setup_policy.py")
POLICY_OWNER_FILENAMES = (
    "msvc_setup_policy.py",
    "msvc_setup_policy_models.py",
    "msvc_setup_policy_discovery.py",
    "msvc_setup_policy_environment.py",
)


class MsvcSetupPolicyTransportTest(unittest.TestCase):
    def test_dispatcher_routes_every_operation_to_its_named_policy_owner(self) -> None:
        self.assertEqual(
            {
                "arguments": [
                    "-latest",
                    "-products",
                    "*",
                    "-requires",
                    "Microsoft.VisualStudio.Component.VC.Tools.x86.x64",
                    "-property",
                    "installationPath",
                    "-utf8",
                ]
            },
            policy.execute_request({"operation": "vswhere-arguments", "payload": {}}),
        )
        self.assertEqual(
            {"installationPath": r"C:\Rīga Visual Studio"},
            policy.execute_request(
                {
                    "operation": "select-vswhere-installation",
                    "payload": {"exitCode": 0, "output": ["", r"C:\Rīga Visual Studio"]},
                }
            ),
        )
        self.assertEqual(
            {
                "candidatePaths": [r"C:\VS\Common7\Tools\VsDevCmd.bat"],
                "notFoundMessage": (
                    "unable to locate VsDevCmd.bat via vswhere or standard Visual Studio 2022 "
                    "installation paths"
                ),
            },
            policy.execute_request(
                {
                    "operation": "vsdevcmd-candidates",
                    "payload": {"installationPath": r"C:\VS", "programFiles": None},
                }
            ),
        )
        self.assertEqual(
            {"commandLine": r'"C:\Rīga\VsDevCmd.bat" -arch=x64 -host_arch=x64 >nul && set'},
            policy.execute_request(
                {
                    "operation": "command-line",
                    "payload": {
                        "vsdevcmdPath": r"C:\Rīga\VsDevCmd.bat",
                        "arch": "x64",
                        "hostArch": "x64",
                    },
                }
            ),
        )
        self.assertEqual(
            {"githubEnvironment": "VSCMD_VER<<__FINGRIND_ENV__\n17.12.3\n__FINGRIND_ENV__\n"},
            policy.execute_request(
                {
                    "operation": "github-environment",
                    "payload": {"environmentDump": ["VSCMD_VER=17.12.3"]},
                }
            ),
        )

    def test_dispatcher_rejects_unknown_or_malformed_requests(self) -> None:
        with self.assertRaisesRegex(MsvcSetupPolicyError, "unknown MSVC setup policy operation"):
            policy.execute_request({"operation": "unknown", "payload": {}})
        with self.assertRaisesRegex(MsvcSetupPolicyError, "request payload must be a JSON object"):
            policy.execute_request({"operation": "vswhere-arguments", "payload": []})

    def test_json_adapter_exercises_the_same_policy_without_powershell(self) -> None:
        completed = _run_policy(
            {
                "operation": "github-environment",
                "payload": {"environmentDump": ["VSCMD_VER=17.12.3", "FINGRIND_VALUE=left=right"]},
            }
        )
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertEqual(
            {
                "githubEnvironment": (
                    "VSCMD_VER<<__FINGRIND_ENV__\n17.12.3\n__FINGRIND_ENV__\n"
                    "FINGRIND_VALUE<<__FINGRIND_ENV__\nleft=right\n__FINGRIND_ENV__\n"
                )
            },
            json.loads(completed.stdout),
        )

    def test_json_adapter_returns_stable_policy_errors_without_tracebacks(self) -> None:
        completed = _run_policy(
            {
                "operation": "command-line",
                "payload": {
                    "vsdevcmdPath": r"C:\VS\VsDevCmd.bat",
                    "arch": "x64 & whoami",
                    "hostArch": "x64",
                },
            }
        )
        self.assertEqual(1, completed.returncode)
        self.assertEqual(
            "Arch must be one non-empty MSVC architecture token without command syntax\n",
            completed.stderr,
        )
        self.assertEqual("", completed.stdout)

    def test_isolated_json_adapter_uses_only_resolved_policy_siblings_with_utf8(self) -> None:
        with tempfile.TemporaryDirectory() as fixture_directory:
            fixture_root = Path(fixture_directory)
            trusted_owner_root = fixture_root / "trusted-policy"
            trusted_owner_root.mkdir()
            for filename in POLICY_OWNER_FILENAMES:
                shutil.copy2(POLICY_PATH.with_name(filename), trusted_owner_root / filename)
            ambient_module_root = fixture_root / "ambient-modules"
            ambient_module_root.mkdir()
            (ambient_module_root / "json.py").write_text(
                'raise RuntimeError("ambient json module was imported")\n', encoding="utf-8"
            )
            (ambient_module_root / "msvc_setup_policy_environment.py").write_text(
                'raise RuntimeError("ambient policy module was imported")\n', encoding="utf-8"
            )
            completed = _run_policy(
                {
                    "operation": "github-environment",
                    "payload": {"environmentDump": ["VSCMD_VER=Rīga"]},
                },
                isolated=True,
                policy_path=trusted_owner_root / POLICY_PATH.name,
                environment={
                    **os.environ,
                    "LC_ALL": "C",
                    "PYTHONPATH": str(ambient_module_root),
                    "PYTHONUTF8": "0",
                },
            )
            bytecode_directory = trusted_owner_root / "__pycache__"

        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertEqual(
            {"githubEnvironment": "VSCMD_VER<<__FINGRIND_ENV__\nRīga\n__FINGRIND_ENV__\n"},
            json.loads(completed.stdout),
        )
        self.assertFalse(bytecode_directory.exists())

    def test_json_adapter_rejects_invalid_json_without_a_traceback(self) -> None:
        completed = subprocess.run(
            [sys.executable, str(POLICY_PATH)],
            input="{",
            capture_output=True,
            check=False,
            encoding="utf-8",
        )

        self.assertEqual(1, completed.returncode)
        self.assertIn("Expecting property name", completed.stderr)
        self.assertNotIn("Traceback", completed.stderr)
        self.assertEqual("", completed.stdout)


def _run_policy(
    request: dict[str, object],
    *,
    isolated: bool = False,
    environment: dict[str, str] | None = None,
    policy_path: Path = POLICY_PATH,
) -> subprocess.CompletedProcess[str]:
    command = [sys.executable]
    if isolated:
        command.extend(("-B", "-I", "-X", "utf8"))
    command.append(str(policy_path))
    return subprocess.run(
        command,
        input=json.dumps(request, ensure_ascii=False),
        capture_output=True,
        check=False,
        encoding="utf-8",
        env=environment,
    )


if __name__ == "__main__":
    unittest.main()
