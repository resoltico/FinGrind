from __future__ import annotations

import sys
import tempfile
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import contract_values
import release_publication_contract
from contract_values_fixture_publication_regression import (
    assert_fixture_publication_contract,
)
from contract_values_fixture_setup import create_fixture
from contract_values_fixture_surface_regression import assert_fixture_surface_contract
from contract_values_regression_support import assert_declared_fixture_formats_are_current


def assert_current_contract(repository_root: Path) -> None:
    repo_root = repository_root
    current_contract = contract_values.load_contract_values(repo_root)
    current_release_plan = release_publication_contract.load_release_publication_plan(
        repo_root,
        version="9.9.9",
    )
    assert {
        entry["classifier"]: (
            entry["archiveExtension"],
            entry["operatingSystemId"],
            entry["architectureId"],
        )
        for entry in current_release_plan["bundleTargets"]
    } == {
        "linux-aarch64": ("tar.gz", "linux", "aarch64"),
        "linux-x86_64": ("tar.gz", "linux", "x86_64"),
        "macos-aarch64": ("tar.gz", "macos", "aarch64"),
        "macos-x86_64": ("tar.gz", "macos", "x86_64"),
        "windows-x86_64": ("zip", "windows", "x86_64"),
    }
    assert all("runner" not in entry for entry in current_release_plan["bundleTargets"])
    assert all("runner" not in entry for entry in current_release_plan["containerTargets"])
    assert "containerRunnerLabel" not in current_release_plan
    assert_declared_fixture_formats_are_current(
        repo_root / "sqlite/src/test/resources/dev/erst/fingrind/sqlite/fixtures",
        current_contract["protectedBookFormat"]["formatVersion"],
    )


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="fingrind-contract-values-") as temporary_root:
        fixture_root, protocol_root = create_fixture(Path(temporary_root))
        assert_fixture_surface_contract(fixture_root, protocol_root)
        assert_fixture_publication_contract(fixture_root, protocol_root)
    assert_current_contract(SCRIPT_DIR.parent)
    print("contract values reader regression: success")


if __name__ == "__main__":
    main()
