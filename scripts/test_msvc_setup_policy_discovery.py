"""Regression tests for pure Visual Studio discovery and command planning."""

from __future__ import annotations

import re
import unittest

import msvc_setup_policy_discovery as discovery
from msvc_setup_policy_models import MsvcSetupPolicyError


class MsvcSetupPolicyDiscoveryTest(unittest.TestCase):
    def test_vswhere_arguments_are_exact_and_immutable(self) -> None:
        self.assertEqual(
            (
                "-latest",
                "-products",
                "*",
                "-requires",
                "Microsoft.VisualStudio.Component.VC.Tools.x86.x64",
                "-property",
                "installationPath",
                "-utf8",
            ),
            discovery.vswhere_arguments(),
        )

    def test_vswhere_selection_preserves_first_substantive_path(self) -> None:
        self.assertEqual(
            r" C:\Visual Studio\BuildTools ",
            discovery.select_vswhere_installation(0, ["", "\t", r" C:\Visual Studio\BuildTools "]),
        )
        self.assertEqual(
            r"C:\Visual Studio\BuildTools",
            discovery.select_vswhere_installation(0, ["\ufeffC:\\Visual Studio\\BuildTools"]),
        )
        self.assertIsNone(discovery.select_vswhere_installation(0, ["", "  "]))

    def test_vswhere_selection_rejects_failed_or_malformed_invocations(self) -> None:
        with self.assertRaisesRegex(
            MsvcSetupPolicyError,
            re.escape(discovery.VSWHERE_INVOCATION_FAILURE),
        ):
            discovery.select_vswhere_installation(17, [r"C:\Visual Studio\BuildTools"])
        with self.assertRaisesRegex(
            MsvcSetupPolicyError,
            re.escape(discovery.VSWHERE_INVOCATION_SHAPE_FAILURE),
        ):
            discovery.select_vswhere_installation("0", [r"C:\Visual Studio\BuildTools"])

    def test_candidate_plan_uses_vswhere_then_all_supported_2022_editions(self) -> None:
        self.assertEqual(
            (
                r"D:\VS\BuildTools\Common7\Tools\VsDevCmd.bat",
                r"C:\Program Files\Microsoft Visual Studio\2022\Enterprise\Common7\Tools\VsDevCmd.bat",
                r"C:\Program Files\Microsoft Visual Studio\2022\Professional\Common7\Tools\VsDevCmd.bat",
                r"C:\Program Files\Microsoft Visual Studio\2022\Community\Common7\Tools\VsDevCmd.bat",
                r"C:\Program Files\Microsoft Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat",
            ),
            discovery.plan_vsdevcmd_candidates(r"D:\VS\BuildTools", r"C:\Program Files"),
        )
        self.assertEqual((), discovery.plan_vsdevcmd_candidates(" ", "\t"))

    def test_command_line_accepts_safe_tokens_and_rejects_command_syntax(self) -> None:
        self.assertEqual(
            r'"C:\VS\VsDevCmd.bat" -arch=x64 -host_arch=arm64 >nul && set',
            discovery.render_vsdevcmd_command_line(r"C:\VS\VsDevCmd.bat", "x64", "arm64"),
        )
        self.assertEqual(
            r'"C:\Rīga\VsDevCmd.bat" -arch=x64 -host_arch=arm64 >nul && set',
            discovery.render_vsdevcmd_command_line(r"C:\Rīga\VsDevCmd.bat", "x64", "arm64"),
        )
        self.assertEqual(
            "x64.debug-1", discovery.validate_architecture_token("x64.debug-1", "Arch")
        )
        for unsafe_architecture in ("", " ", "x64 & whoami", "x64;whoami", "x64/x86", "x64\n"):
            with (
                self.subTest(unsafe_architecture=unsafe_architecture),
                self.assertRaisesRegex(MsvcSetupPolicyError, "without command syntax"),
            ):
                discovery.render_vsdevcmd_command_line(
                    r"C:\VS\VsDevCmd.bat",
                    unsafe_architecture,
                    "x64",
                )
        for forbidden_syntax in (
            '"',
            "%",
            "!",
            "^",
            "&",
            "|",
            "<",
            ">",
            "(",
            ")",
            "\x00",
            "\r",
            "\n",
        ):
            unsafe_path = f"C:\\VS\\before{forbidden_syntax}after\\VsDevCmd.bat"
            with (
                self.subTest(unsafe_path=unsafe_path),
                self.assertRaisesRegex(MsvcSetupPolicyError, "cmd expansion or control syntax"),
            ):
                discovery.render_vsdevcmd_command_line(unsafe_path, "x64", "x64")


if __name__ == "__main__":
    unittest.main()
