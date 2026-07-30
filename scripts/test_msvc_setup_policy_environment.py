"""Regression tests for the captured MSVC environment policy owner."""

from __future__ import annotations

import re
import unittest

import msvc_setup_policy_environment as environment
from msvc_setup_policy_models import EnvironmentEntry, MsvcSetupPolicyError


class MsvcSetupPolicyEnvironmentTest(unittest.TestCase):
    def test_environment_parse_preserves_order_first_key_spelling_and_first_equals_boundary(
        self,
    ) -> None:
        entries = environment.parse_vsdevcmd_environment_dump(
            (
                "VSCMD_VER=17.12.3",
                "PATH=C:\\MSVC\\bin",
                "path=second=equals=preserved",
                "=C:=C:\\work",
                "not-an-environment-entry",
            )
        )
        self.assertEqual(
            (
                EnvironmentEntry("VSCMD_VER", "17.12.3"),
                EnvironmentEntry("PATH", "second=equals=preserved"),
            ),
            entries,
        )
        environment.validate_vsdevcmd_environment(entries)
        self.assertEqual(
            (EnvironmentEntry("VSCMD_VER", "17.12.3"),),
            environment.parse_vsdevcmd_environment_dump(("\ufeffVSCMD_VER=17.12.3",)),
        )

    def test_environment_validation_requires_non_blank_vscmd_version_case_insensitively(
        self,
    ) -> None:
        environment.validate_vsdevcmd_environment((EnvironmentEntry("vscmd_ver", "17.12.3"),))
        for entries in (
            (),
            (EnvironmentEntry("VSCMD_VER", "\t"),),
            (EnvironmentEntry("PATH", "C:\\MSVC\\bin"),),
        ):
            with (
                self.subTest(entries=entries),
                self.assertRaisesRegex(
                    MsvcSetupPolicyError,
                    re.escape(environment.VSCMD_ENVIRONMENT_FAILURE),
                ),
            ):
                environment.validate_vsdevcmd_environment(entries)

    def test_github_environment_serialization_matches_the_existing_normal_contract(self) -> None:
        entries = (
            EnvironmentEntry("VSCMD_VER", "17.12.3"),
            EnvironmentEntry("PATH", r"C:\MSVC\bin;C:\Windows\System32"),
            EnvironmentEntry("FINGRIND_VALUE", "left=right"),
        )
        self.assertEqual(
            "VSCMD_VER<<__FINGRIND_ENV__\n17.12.3\n__FINGRIND_ENV__\n"
            "PATH<<__FINGRIND_ENV__\nC:\\MSVC\\bin;C:\\Windows\\System32\n__FINGRIND_ENV__\n"
            "FINGRIND_VALUE<<__FINGRIND_ENV__\nleft=right\n__FINGRIND_ENV__\n",
            environment.serialize_github_environment(entries),
        )

    def test_github_environment_serialization_rotates_a_colliding_delimiter(self) -> None:
        serialized = environment.serialize_github_environment(
            (EnvironmentEntry("FINGRIND_VALUE", "above\n__FINGRIND_ENV__\nbelow"),)
        )
        self.assertEqual(
            "FINGRIND_VALUE<<__FINGRIND_ENV___1\n"
            "above\n__FINGRIND_ENV__\nbelow\n__FINGRIND_ENV___1\n",
            serialized,
        )

    def test_github_environment_serialization_rejects_unsafe_name_and_value(self) -> None:
        with self.assertRaisesRegex(MsvcSetupPolicyError, "unsafe for GITHUB_ENV"):
            environment.serialize_github_environment((EnvironmentEntry("BAD<<NAME", "value"),))
        with self.assertRaisesRegex(MsvcSetupPolicyError, "unsafe for GITHUB_ENV"):
            environment.serialize_github_environment((EnvironmentEntry("GOOD", "value\x00"),))


if __name__ == "__main__":
    unittest.main()
