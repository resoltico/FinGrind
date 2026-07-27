"""Regression checks for release-smoke scenario paths and work-root validation."""

from __future__ import annotations

import pathlib
import tempfile

from .config import require_existing_absolute_work_root
from .models import ReleaseSmokeFailure


def assert_release_smoke_scenarios(
    build_release_smoke_scenario,
    absolute_mode: str,
    relative_mode: str,
) -> None:
    bundle_work_root = pathlib.Path("/tmp/workspace odd/Rīga büro/2026 Q2 close")
    bundle = build_release_smoke_scenario(
        bundle_work_root,
        absolute_mode,
        "bundle-acceptance",
    )
    assert bundle.work_root == bundle_work_root
    assert "Rīga büro" in str(bundle.book.local_path)
    assert bundle.book.argument == str(bundle.book.local_path)
    assert bundle.backup_book.argument == str(bundle.backup_book.local_path)
    assert bundle.backup_id == "96ace780-ce14-5177-9c49-3917db69edae"
    assert bundle.attestation_receipt.argument == str(bundle.attestation_receipt.local_path)
    assert bundle.attestation_founder_principal_id == "4bc17dd7-145f-4ea7-bb55-167ca2f6ac11"
    assert bundle.attestation_founder_key.argument == str(bundle.attestation_founder_key.local_path)
    assert bundle.attestation_founder_passphrase.argument == str(
        bundle.attestation_founder_passphrase.local_path
    )
    assert bundle.accounting_basis == "CASH"

    docker_work_root = pathlib.Path("/workdir")
    docker = build_release_smoke_scenario(docker_work_root, relative_mode, "docker-acceptance")
    assert docker.work_root == docker_work_root
    assert docker.book.argument == "books odd/Rīga büro/nested/-entity [docker-acceptance].sqlite"
    assert (
        docker.backup_book.argument
        == "backup odd/Rīga büro/nested/-entity backup [docker-acceptance].sqlite"
    )
    assert (
        docker.replacement_book_key.argument
        == "keys odd/Rīga büro/nested/--entity [docker-acceptance]-replacement.key"
    )
    assert (
        docker.restored_book_key.argument
        == "restored odd/Rīga büro/nested/--entity restored [docker-acceptance].key"
    )
    assert docker.request_prefix == "docker-acceptance"
    assert docker.backup_id == "39f7a204-3096-54e3-ac73-c3a745350411"
    assert (
        docker.attestation_receipt.argument
        == "receipts odd/Rīga büro/retained/-receipt [docker-acceptance].fgar"
    )
    assert (
        docker.attestation_founder_key.argument
        == "attestation credentials/Rīga büro/founder/docker-acceptance.fgatk"
    )
    assert (
        docker.attestation_founder_passphrase.argument
        == "attestation credentials/Rīga büro/founder/docker-acceptance.passphrase"
    )
    assert docker.accounting_basis == "CASH"


def assert_release_smoke_work_root_contract() -> None:
    """Keep the environment path contract fail-closed before fixtures can be written."""
    with tempfile.TemporaryDirectory() as temporary_directory:
        work_root = pathlib.Path(temporary_directory)
        physical_work_root = work_root.resolve(strict=True)
        assert require_existing_absolute_work_root(str(work_root)) == physical_work_root
        _require_work_root_rejection("relative-release-smoke-root", "absolute directory")
        _require_work_root_rejection(str(work_root / "missing"), "existing directory")
        regular_file = work_root / "not-a-directory"
        regular_file.write_text("not a directory\n", encoding="utf-8")
        _require_work_root_rejection(str(regular_file), "existing directory")
        physical_child = work_root / "physical-child"
        physical_child.mkdir()
        symlink_root = work_root / "linked-release-smoke-root"
        try:
            symlink_root.symlink_to(physical_child, target_is_directory=True)
        except OSError:
            return
        assert require_existing_absolute_work_root(str(symlink_root)) == physical_child.resolve(
            strict=True
        )
        linked_parent = work_root / "linked-parent"
        linked_parent.symlink_to(work_root, target_is_directory=True)
        assert require_existing_absolute_work_root(
            str(linked_parent / physical_child.name)
        ) == physical_child.resolve(
            strict=True,
        )


def _require_work_root_rejection(value: str, expected_message: str) -> None:
    try:
        require_existing_absolute_work_root(value)
    except ReleaseSmokeFailure as exc:
        assert expected_message in str(exc)
        return
    raise AssertionError(f"release-smoke work-root validation accepted {value!r}")
