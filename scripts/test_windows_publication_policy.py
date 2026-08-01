"""Unit and protocol fixtures for the cross-platform Windows publication policy owner."""

from __future__ import annotations

import json
import subprocess
import sys
import unittest
from pathlib import Path

from windows_publication_manifest_policy import validate_manifest_artifacts
from windows_publication_plan_policy import build_publication_plan
from windows_publication_policy_boundary import PublicationPolicyError
from windows_publication_policy_protocol import process_request, serialize_workflow_output

WINDOWS_ROOT = r"C:\runner\fingrind"
WINDOWS_CLASSIFIER = "windows-x86_64"


def bundle_layout(*, archive_format: str = "zip") -> str:
    return json.dumps(
        {
            "bundleTargets": {
                WINDOWS_CLASSIFIER: {
                    "operatingSystemId": "windows",
                    "architectureId": "x86_64",
                    "archiveFormat": archive_format,
                }
            }
        }
    )


def publication_plan(*, repository_root: str = WINDOWS_ROOT) -> dict[str, str]:
    return build_publication_plan(
        repository_root=repository_root,
        gradle_properties="version=0.62.0\n",
        bundle_layout_contract=bundle_layout(),
        expected_operating_system_id="windows",
        expected_architecture_id="x86_64",
        bundle_classifier=WINDOWS_CLASSIFIER,
    )


class WindowsPublicationPolicyTest(unittest.TestCase):
    def test_builds_the_exact_windows_publication_plan(self) -> None:
        plan = publication_plan()

        self.assertEqual(
            {
                "repositoryRoot": WINDOWS_ROOT,
                "cliBuildDirectory": WINDOWS_ROOT + r"\cli\build",
                "manifestPath": (
                    WINDOWS_ROOT + r"\cli\build\generated\bundle\bundle-archive-manifest.json"
                ),
                "archivePath": (
                    WINDOWS_ROOT + r"\cli\build\distributions\fingrind-0.62.0-windows-x86_64.zip"
                ),
                "checksumPath": (
                    WINDOWS_ROOT
                    + r"\cli\build\distributions\fingrind-0.62.0-windows-x86_64.zip.sha256"
                ),
                "projectVersion": "0.62.0",
                "bundleClassifier": WINDOWS_CLASSIFIER,
            },
            plan,
        )

    def test_plan_policy_is_lexically_cross_platform(self) -> None:
        plan = publication_plan(repository_root="/tmp/fingrind")

        self.assertEqual("/tmp/fingrind/cli/build", plan["cliBuildDirectory"])
        self.assertEqual(
            "/tmp/fingrind/cli/build/distributions/fingrind-0.62.0-windows-x86_64.zip",
            plan["archivePath"],
        )

    def test_rejects_ambiguous_gradle_versions_and_wrong_target_contract(self) -> None:
        with self.assertRaisesRegex(PublicationPolicyError, "exactly one release version"):
            build_publication_plan(
                repository_root=WINDOWS_ROOT,
                gradle_properties="version=0.62.0\nversion=0.62.1\n",
                bundle_layout_contract=bundle_layout(),
                expected_operating_system_id="windows",
                expected_architecture_id="x86_64",
                bundle_classifier=WINDOWS_CLASSIFIER,
            )

        with self.assertRaisesRegex(
            PublicationPolicyError, "does not describe the requested Windows publication target"
        ):
            build_publication_plan(
                repository_root=WINDOWS_ROOT,
                gradle_properties="version=0.62.0\n",
                bundle_layout_contract=bundle_layout(archive_format="tar.gz"),
                expected_operating_system_id="windows",
                expected_architecture_id="x86_64",
                bundle_classifier=WINDOWS_CLASSIFIER,
            )

    def test_rejects_path_unsafe_release_metadata(self) -> None:
        for forbidden_character in (
            "/",
            "\\",
            "<",
            ">",
            ":",
            '"',
            "|",
            "?",
            "*",
            "\x00",
            "\x1f",
        ):
            unsafe_version = f"0.62.0{forbidden_character}escape"
            with (
                self.subTest(unsafe_version=unsafe_version),
                self.assertRaisesRegex(PublicationPolicyError, "path-safe file-name component"),
            ):
                build_publication_plan(
                    repository_root=WINDOWS_ROOT,
                    gradle_properties=f"version={unsafe_version}\n",
                    bundle_layout_contract=bundle_layout(),
                    expected_operating_system_id="windows",
                    expected_architecture_id="x86_64",
                    bundle_classifier=WINDOWS_CLASSIFIER,
                )

        for unsafe_version in (" 0.62.0", "0.62.0 "):
            with (
                self.subTest(unsafe_version=unsafe_version),
                self.assertRaisesRegex(PublicationPolicyError, "path-safe file-name component"),
            ):
                build_publication_plan(
                    repository_root=WINDOWS_ROOT,
                    gradle_properties=f"version={unsafe_version}\n",
                    bundle_layout_contract=bundle_layout(),
                    expected_operating_system_id="windows",
                    expected_architecture_id="x86_64",
                    bundle_classifier=WINDOWS_CLASSIFIER,
                )

        for unsafe_classifier in ("windows:x86_64", " windows-x86_64", "windows-x86_64 "):
            with (
                self.subTest(unsafe_classifier=unsafe_classifier),
                self.assertRaisesRegex(PublicationPolicyError, "path-safe file-name component"),
            ):
                build_publication_plan(
                    repository_root=WINDOWS_ROOT,
                    gradle_properties="version=0.62.0\n",
                    bundle_layout_contract=bundle_layout(),
                    expected_operating_system_id="windows",
                    expected_architecture_id="x86_64",
                    bundle_classifier=unsafe_classifier,
                )

    def test_accepts_case_independent_canonical_manifest_paths(self) -> None:
        plan = publication_plan()
        manifest = json.dumps(
            {
                "archivePath": plan["archivePath"].upper(),
                "checksumPath": plan["checksumPath"].upper(),
            }
        )

        self.assertEqual(
            {
                "archivePath": plan["archivePath"],
                "checksumPath": plan["checksumPath"],
            },
            validate_manifest_artifacts(plan=plan, bundle_archive_manifest=manifest),
        )

    def test_rejects_noncanonical_relative_and_ambiguous_manifest_paths(self) -> None:
        plan = publication_plan()

        with self.assertRaisesRegex(
            PublicationPolicyError, "does not match the canonical Windows publication path"
        ):
            validate_manifest_artifacts(
                plan=plan,
                bundle_archive_manifest=json.dumps(
                    {"archivePath": r"C:\outside.zip", "checksumPath": plan["checksumPath"]}
                ),
            )
        with self.assertRaisesRegex(PublicationPolicyError, "must be an absolute path"):
            validate_manifest_artifacts(
                plan=plan,
                bundle_archive_manifest=json.dumps(
                    {"archivePath": "relative.zip", "checksumPath": plan["checksumPath"]}
                ),
            )
        with self.assertRaisesRegex(PublicationPolicyError, "duplicate property"):
            process_request(
                {
                    "operation": "manifest-artifacts",
                    "plan": plan,
                    "bundleArchiveManifest": (
                        '{"archivePath":'
                        + json.dumps(plan["archivePath"])
                        + ',"archivePath":'
                        + json.dumps(plan["archivePath"])
                        + ',"checksumPath":'
                        + json.dumps(plan["checksumPath"])
                        + "}"
                    ),
                }
            )

    def test_rejects_tampered_plan_and_extra_manifest_schema(self) -> None:
        plan = publication_plan()
        tampered_plan = dict(plan)
        tampered_plan["cliBuildDirectory"] = WINDOWS_ROOT + r"\elsewhere"
        with self.assertRaisesRegex(
            PublicationPolicyError, "is not the canonical publication path"
        ):
            validate_manifest_artifacts(
                plan=tampered_plan,
                bundle_archive_manifest=json.dumps(
                    {"archivePath": plan["archivePath"], "checksumPath": plan["checksumPath"]}
                ),
            )
        with self.assertRaisesRegex(PublicationPolicyError, "unrecognized properties"):
            validate_manifest_artifacts(
                plan=plan,
                bundle_archive_manifest=json.dumps(
                    {
                        "archivePath": plan["archivePath"],
                        "checksumPath": plan["checksumPath"],
                        "unexpected": "value",
                    }
                ),
            )

    def test_serializes_exactly_one_safe_workflow_output_record(self) -> None:
        self.assertEqual(
            "archive-path=\n", serialize_workflow_output(name="archive-path", value="")
        )
        self.assertEqual(
            "archive-path=C:\\archive.zip\n",
            serialize_workflow_output(name="archive-path", value=r"C:\archive.zip"),
        )
        with self.assertRaisesRegex(PublicationPolicyError, "name is invalid"):
            serialize_workflow_output(name="archive path", value="value")
        with self.assertRaisesRegex(PublicationPolicyError, "must be one line"):
            serialize_workflow_output(name="archive-path", value="one\ntwo")

    def test_cli_protocol_round_trip_and_strict_request_schema(self) -> None:
        policy_script = Path(__file__).with_name("windows_publication_policy.py")
        request = {
            "operation": "workflow-output-line",
            "name": "archive-path",
            "value": "",
        }
        completed = subprocess.run(
            [sys.executable, "-I", "-B", "-X", "utf8", str(policy_script)],
            input=json.dumps(request),
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertEqual({"line": "archive-path=\n"}, json.loads(completed.stdout))
        rejected = subprocess.run(
            [sys.executable, "-I", "-B", "-X", "utf8", str(policy_script)],
            input=json.dumps({**request, "unexpected": "value"}),
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(2, rejected.returncode)
        self.assertEqual("", rejected.stdout)
        self.assertIn("unrecognized properties", rejected.stderr)


if __name__ == "__main__":
    unittest.main()
