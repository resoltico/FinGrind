"""Regression coverage for secure PowerShell archive publication."""

from __future__ import annotations

import os
import shutil
import stat
import tarfile
import zipfile

from powershell_runtime import ProvisioningError, provision_runtime
from test_powershell_runtime_support import PowerShellRuntimeTestCase


class PowerShellRuntimeProvisioningTest(PowerShellRuntimeTestCase):
    """Exercise successful publication and every archive admission boundary."""

    def test_provisions_a_verified_linux_runtime_atomically(self) -> None:
        archive = self.write_tar("pwsh", self.fake_powershell("7.6.4"))
        executable = self.provision(archive, operating_system="Linux", architecture="x86_64")
        self.assertTrue(executable.is_file())
        self.assertEqual(executable.parent.name, "linux-x64")
        self.assertEqual(executable.parent.parent.name, "7.6.4")
        self.assertEqual(executable.parent.stat().st_mode & 0o777, 0o755)
        self.assertEqual(executable.parent.parent.stat().st_mode & 0o777, 0o755)

    def test_provisions_a_verified_macos_runtime_atomically(self) -> None:
        archive = self.write_tar("pwsh", self.fake_powershell("7.6.4"))
        executable = self.provision(archive, operating_system="Darwin", architecture="arm64")
        self.assertTrue(executable.is_file())
        self.assertEqual(executable.parent.name, "macos-arm64")
        self.assertEqual(executable.parent.parent.name, "7.6.4")
        self.assertEqual(executable.parent.stat().st_mode & 0o777, 0o755)
        self.assertEqual(executable.parent.parent.stat().st_mode & 0o777, 0o755)

    def test_rejects_checksum_mismatch_before_extraction(self) -> None:
        archive = self.write_tar("pwsh", self.fake_powershell("7.6.4"))
        metadata = self.metadata_for(archive, checksum="0" * 64)
        with self.assertRaisesRegex(ProvisioningError, "SHA-256 mismatch"):
            self.provision_with_metadata(metadata, archive)
        self.assertFalse((self.root / "install" / "7.6.4" / "linux-x64").exists())

    def test_rejects_version_mismatch_before_publication(self) -> None:
        archive = self.write_tar("pwsh", self.fake_powershell("7.6.3"))
        with self.assertRaisesRegex(ProvisioningError, "version mismatch"):
            self.provision(archive)
        self.assertFalse((self.root / "install" / "7.6.4" / "linux-x64").exists())

    def test_rejects_parent_traversal_member(self) -> None:
        archive = self.root / "traversal.tar.gz"
        with tarfile.open(archive, mode="w:gz") as package:
            self.add_tar_member(package, "../outside", b"unsafe")
        with self.assertRaisesRegex(ProvisioningError, "parent traversal"):
            self.provision(archive)
        self.assertFalse((self.root / "outside").exists())

    def test_rejects_duplicate_normalized_members(self) -> None:
        archive = self.root / "duplicate.tar.gz"
        with tarfile.open(archive, mode="w:gz") as package:
            self.add_tar_member(package, "pwsh", self.fake_powershell("7.6.4"))
            self.add_tar_member(package, "./pwsh", self.fake_powershell("7.6.4"))
        with self.assertRaisesRegex(ProvisioningError, "duplicate normalized destination"):
            self.provision(archive)

    def test_rejects_zip_symlink_member(self) -> None:
        archive = self.root / "symlink.zip"
        with zipfile.ZipFile(archive, mode="w") as package:
            member = zipfile.ZipInfo("pwsh.exe")
            member.create_system = 3
            member.external_attr = (stat.S_IFLNK | 0o777) << 16
            package.writestr(member, "elsewhere")
        with self.assertRaisesRegex(ProvisioningError, "non-regular or link"):
            self.provision(archive, operating_system="Windows", architecture="AMD64")

    def test_rejects_tar_hardlink_and_device_members(self) -> None:
        for name, member_type in (("hardlink", tarfile.LNKTYPE), ("device", tarfile.CHRTYPE)):
            with self.subTest(member_type=member_type):
                archive = self.root / f"{name}.tar.gz"
                with tarfile.open(archive, mode="w:gz") as package:
                    member = tarfile.TarInfo(name)
                    member.type = member_type
                    member.linkname = "pwsh"
                    member.devmajor = 1
                    member.devminor = 3
                    package.addfile(member)
                with self.assertRaisesRegex(ProvisioningError, "non-regular or link"):
                    self.provision(archive)

    def test_rejects_an_install_root_below_a_symlink_ancestor(self) -> None:
        archive = self.write_tar("pwsh", self.fake_powershell("7.6.4"))
        real_root = self.root / "real-root"
        real_root.mkdir()
        linked_root = self.root / "linked-root"
        linked_root.symlink_to(real_root, target_is_directory=True)
        with self.assertRaisesRegex(ProvisioningError, "symlink or reparse-point ancestor"):
            provision_runtime(
                self.metadata_for(archive),
                linked_root / "install",
                operating_system="Linux",
                architecture="x86_64",
                downloader=lambda _url, destination: shutil.copyfile(archive, destination),
            )

    def test_rejects_an_existing_child_below_a_symlink_ancestor(self) -> None:
        archive = self.write_tar("pwsh", self.fake_powershell("7.6.4"))
        real_root = self.root / "real-root"
        existing_child = real_root / "existing-child"
        existing_child.mkdir(parents=True)
        linked_root = self.root / "linked-root"
        linked_root.symlink_to(real_root, target_is_directory=True)
        with self.assertRaisesRegex(ProvisioningError, "symlink or reparse-point ancestor"):
            provision_runtime(
                self.metadata_for(archive),
                linked_root / "existing-child" / "install",
                operating_system="Linux",
                architecture="x86_64",
                downloader=lambda _url, destination: shutil.copyfile(archive, destination),
            )

    def test_rejects_a_reparse_point_at_the_runtime_target(self) -> None:
        archive = self.write_tar("pwsh", self.fake_powershell("7.6.4"))
        target_parent = self.root / "install" / "7.6.4"
        target_parent.mkdir(parents=True)
        target_directory = target_parent / "linux-x64"
        target_directory.symlink_to(self.root / "outside", target_is_directory=True)

        with self.assertRaisesRegex(ProvisioningError, "symlink or reparse point"):
            self.provision(archive)

    def test_reuses_the_verified_archive_cache_without_redownloading(self) -> None:
        archive = self.write_tar("pwsh", self.fake_powershell("7.6.4"))
        metadata = self.metadata_for(archive)
        executable = self.provision_with_metadata(metadata, archive)
        archive.unlink()

        reprovisioned = self.provision_with_metadata(metadata, archive)

        self.assertEqual(executable, reprovisioned)
        self.assertTrue(reprovisioned.is_file())

    def test_reprovisions_a_version_correct_but_byte_altered_cached_executable(self) -> None:
        archive = self.write_tar_members(
            {
                "pwsh": self.fake_powershell("7.6.4"),
                "modules/payload.txt": b"trusted payload\n",
            }
        )
        executable = self.provision(archive)
        expected_executable = executable.read_bytes()

        executable.write_bytes(expected_executable + b"# altered but version-correct\n")
        reprovisioned = self.provision(archive)

        self.assertEqual(executable, reprovisioned)
        self.assertEqual(expected_executable, reprovisioned.read_bytes())

    def test_reprovisions_regular_cached_tree_changes(self) -> None:
        for change in ("changed", "missing", "extra"):
            with self.subTest(change=change):
                archive = self.write_tar_members(
                    {
                        "pwsh": self.fake_powershell("7.6.4"),
                        "modules/payload.txt": b"trusted payload\n",
                    }
                )
                install_root = self.root / f"install-{change}"
                executable = provision_runtime(
                    self.metadata_for(archive),
                    install_root,
                    operating_system="Linux",
                    architecture="x86_64",
                    downloader=lambda _url, destination, source_archive=archive: shutil.copyfile(
                        source_archive, destination
                    ),
                )
                runtime_directory = executable.parent
                payload = runtime_directory / "modules" / "payload.txt"
                if change == "changed":
                    payload.write_bytes(b"altered payload\n")
                elif change == "missing":
                    payload.unlink()
                else:
                    (runtime_directory / "unexpected.txt").write_bytes(b"unexpected\n")

                reprovisioned = provision_runtime(
                    self.metadata_for(archive),
                    install_root,
                    operating_system="Linux",
                    architecture="x86_64",
                    downloader=lambda _url, destination, source_archive=archive: shutil.copyfile(
                        source_archive, destination
                    ),
                )

                self.assertEqual(executable, reprovisioned)
                self.assertEqual(b"trusted payload\n", payload.read_bytes())
                self.assertFalse((runtime_directory / "unexpected.txt").exists())

    def test_reprovisions_a_corrupted_regular_cached_archive(self) -> None:
        archive = self.write_tar("pwsh", self.fake_powershell("7.6.4"))
        executable = self.provision(archive)
        cached_archive = executable.parent.parent / "powershell-7.6.4-linux-x64.tar.gz"
        expected_archive = archive.read_bytes()

        cached_archive.write_bytes(b"corrupted archive")
        reprovisioned = self.provision(archive)

        self.assertEqual(executable, reprovisioned)
        self.assertEqual(expected_archive, cached_archive.read_bytes())

    def test_refuses_cached_runtime_with_a_nested_symlink(self) -> None:
        archive = self.write_tar("pwsh", self.fake_powershell("7.6.4"))
        executable = self.provision(archive)
        (executable.parent / "unsafe-link").symlink_to(self.root / "outside")

        with self.assertRaisesRegex(ProvisioningError, "symlink or reparse point"):
            self.provision(archive)

    def test_refuses_cached_runtime_with_a_hard_linked_regular_file(self) -> None:
        archive = self.write_tar("pwsh", self.fake_powershell("7.6.4"))
        executable = self.provision(archive)
        os.link(executable, executable.parent / "hard-linked-pwsh")

        with self.assertRaisesRegex(ProvisioningError, "hard-linked file"):
            self.provision(archive)
