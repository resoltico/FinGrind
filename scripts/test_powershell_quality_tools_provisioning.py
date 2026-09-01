"""Regression tests for PowerShell quality-tool archive and cache provisioning."""

from __future__ import annotations

import os
import stat
import unittest
import urllib.error
import zipfile
from dataclasses import replace
from pathlib import Path
from unittest.mock import patch

from powershell_quality_tools import (
    ProvisioningError,
    QualityToolsMetadata,
    download_artifact,
)
from powershell_quality_tools_test_support import PowerShellQualityToolsTestCase


class PowerShellQualityToolsProvisioningTest(PowerShellQualityToolsTestCase):
    """Exercise archive admission, cache rebuilding, and module-manifest ownership."""

    def test_provisions_exact_module_manifest_trees_from_verified_archives(self) -> None:
        source_metadata = self.canonical_metadata()
        archives = self.archives_for(source_metadata)
        metadata = self.metadata_for_archives(source_metadata, archives)

        installation = self.provision(metadata, archives)

        self.assertEqual(installation.pester_version, "6.1.0")
        self.assertEqual(installation.script_analyzer_version, "1.25.0")
        self.assertEqual(installation.pester_manifest.name, "Pester.psd1")
        self.assertEqual(installation.script_analyzer_manifest.name, "PSScriptAnalyzer.psd1")
        self.assertTrue(installation.pester_manifest.is_file())
        self.assertTrue(installation.script_analyzer_manifest.is_file())

    def test_rejects_checksum_mismatch_before_extracting_any_module(self) -> None:
        source_metadata = self.canonical_metadata()
        archives = self.archives_for(source_metadata)
        metadata = self.metadata_for_archives(source_metadata, archives)
        malformed = QualityToolsMetadata(
            artifacts=(replace(metadata.artifacts[0], sha256="0" * 64), metadata.artifacts[1])
        )

        with self.assertRaisesRegex(ProvisioningError, "SHA-256 mismatch"):
            self.provision(malformed, archives)
        self.assertFalse((self.root / "install" / "modules").exists())

    def test_rejects_archive_parent_traversal_and_symlink_members(self) -> None:
        source_metadata = self.canonical_metadata()
        archives = self.archives_for(
            source_metadata,
            override_members={"Pester": {"../outside": b"unsafe"}},
        )
        metadata = self.metadata_for_archives(source_metadata, archives)
        with self.assertRaisesRegex(ProvisioningError, "parent traversal"):
            self.provision(metadata, archives)

        symlink_archive = archives["Pester"]
        pester = source_metadata.artifacts[0]
        with zipfile.ZipFile(symlink_archive, mode="w") as package:
            package.writestr(pester.manifest_name, self.module_manifest(pester))
            package.writestr(pester.root_module_name, b"# module entrypoint\n")
            member = zipfile.ZipInfo("unsafe-link")
            member.create_system = 3
            member.external_attr = (stat.S_IFLNK | 0o777) << 16
            package.writestr(member, "elsewhere")
        metadata = self.metadata_for_archives(source_metadata, archives)
        with self.assertRaisesRegex(ProvisioningError, "non-regular or link"):
            self.provision(metadata, archives, install_root=self.root / "install-symlink")

    def test_rejects_ambiguous_or_wrong_module_manifests(self) -> None:
        source_metadata = self.canonical_metadata()
        pester = source_metadata.artifacts[0]
        archives = self.archives_for(
            source_metadata,
            override_members={
                "Pester": {f"nested/{pester.manifest_name}": self.module_manifest(pester).encode()}
            },
        )
        metadata = self.metadata_for_archives(source_metadata, archives)
        with self.assertRaisesRegex(ProvisioningError, "ambiguous manifest layout"):
            self.provision(metadata, archives)

        wrong_manifest = self.module_manifest(replace(pester, version="5.7.0")).encode()
        archives = self.archives_for(
            source_metadata,
            override_members={"Pester": {pester.manifest_name: wrong_manifest}},
        )
        metadata = self.metadata_for_archives(source_metadata, archives)
        with self.assertRaisesRegex(ProvisioningError, "manifest does not match"):
            self.provision(metadata, archives, install_root=self.root / "install-manifest")

    def test_rebuilds_regular_cached_archives_and_module_trees_from_verified_snapshots(
        self,
    ) -> None:
        source_metadata = self.canonical_metadata()
        archives = self.archives_for(source_metadata)
        metadata = self.metadata_for_archives(source_metadata, archives)
        installation = self.provision(metadata, archives)
        pester_manifest = installation.pester_manifest
        expected_manifest = pester_manifest.read_bytes()
        pester_manifest.write_bytes(b"altered")
        cached_archive = (
            self.root / "install" / "archive-cache" / metadata.artifacts[0].archive_name
        )
        expected_archive = archives["Pester"].read_bytes()
        cached_archive.write_bytes(b"corrupted")

        rebuilt = self.provision(metadata, archives)

        self.assertEqual(rebuilt.pester_manifest.read_bytes(), expected_manifest)
        self.assertEqual(cached_archive.read_bytes(), expected_archive)

    def test_retries_a_transient_quality_tool_download_with_a_fresh_destination(self) -> None:
        artifact = self.canonical_metadata().artifacts[0]
        destination = self.root / artifact.archive_name
        attempts = 0

        def download_once(_artifact: object, download_destination: Path) -> None:
            nonlocal attempts
            attempts += 1
            if attempts == 1:
                download_destination.write_bytes(b"partial")
                raise urllib.error.URLError(TimeoutError("The read operation timed out"))
            self.assertFalse(download_destination.exists())
            download_destination.write_bytes(b"complete")

        with (
            patch(
                "powershell_quality_tool_provisioning._download_artifact_once",
                side_effect=download_once,
            ),
            patch("powershell_quality_tool_provisioning.time.sleep") as sleep,
        ):
            download_artifact(artifact, destination)

        self.assertEqual(attempts, 2)
        self.assertEqual(destination.read_bytes(), b"complete")
        sleep.assert_called_once_with(1)

    def test_stops_after_bounded_transient_quality_tool_download_attempts(self) -> None:
        artifact = self.canonical_metadata().artifacts[0]
        destination = self.root / artifact.archive_name

        with (
            patch(
                "powershell_quality_tool_provisioning._download_artifact_once",
                side_effect=urllib.error.URLError(TimeoutError("The read operation timed out")),
            ) as download_once,
            patch("powershell_quality_tool_provisioning.time.sleep") as sleep,
            self.assertRaisesRegex(
                ProvisioningError, "could not download PowerShell quality-tool archive"
            ),
        ):
            download_artifact(artifact, destination)

        self.assertEqual(download_once.call_count, 3)
        self.assertFalse(destination.exists())
        self.assertEqual(sleep.call_args_list, [((1,), {}), ((2,), {})])

    def test_fails_closed_for_symlinked_install_roots_and_cached_module_trees(self) -> None:
        source_metadata = self.canonical_metadata()
        archives = self.archives_for(source_metadata)
        metadata = self.metadata_for_archives(source_metadata, archives)
        real_root = self.root / "real-root"
        real_root.mkdir()
        linked_root = self.root / "linked-root"
        linked_root.symlink_to(real_root, target_is_directory=True)
        with self.assertRaisesRegex(ProvisioningError, "symlink or reparse-point ancestor"):
            self.provision(metadata, archives, install_root=linked_root / "install")

        installation = self.provision(metadata, archives, install_root=self.root / "safe-install")
        unsafe_link = installation.pester_manifest.parent / "unsafe-link"
        unsafe_link.symlink_to(self.root / "outside")
        with self.assertRaisesRegex(ProvisioningError, "symlink or reparse point"):
            self.provision(metadata, archives, install_root=self.root / "safe-install")

    @unittest.skipUnless(
        os.name == "posix", "POSIX directory privacy has no Windows mode equivalent"
    )
    def test_rejects_a_nonprivate_install_root(self) -> None:
        source_metadata = self.canonical_metadata()
        archives = self.archives_for(source_metadata)
        metadata = self.metadata_for_archives(source_metadata, archives)
        install_root = self.root / "nonprivate-install"
        install_root.mkdir(mode=0o755)
        install_root.chmod(0o755)

        with self.assertRaisesRegex(ProvisioningError, "private to its owner"):
            self.provision(metadata, archives, install_root=install_root)

    def test_fails_closed_for_hard_linked_cached_module_content(self) -> None:
        source_metadata = self.canonical_metadata()
        archives = self.archives_for(source_metadata)
        metadata = self.metadata_for_archives(source_metadata, archives)
        installation = self.provision(metadata, archives)
        duplicate = installation.pester_manifest.parent / "hard-linked-manifest.psd1"
        os.link(installation.pester_manifest, duplicate)
        with self.assertRaisesRegex(ProvisioningError, "hard-linked file"):
            self.provision(metadata, archives)
