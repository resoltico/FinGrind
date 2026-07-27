from __future__ import annotations

import json
import pathlib
import tempfile
from dataclasses import replace

from .attestation_arguments import signing_credential_arguments
from .bridge_contract_support import base_bridge_config, smoke_path, write_bridge_script
from .open_book_support import open_book


def assert_attested_open_book_arguments(repo_root: pathlib.Path) -> None:
    with tempfile.TemporaryDirectory() as temporary_directory:
        temporary_path = pathlib.Path(temporary_directory)
        bridge_script = write_bridge_script(temporary_path)
        dummy = smoke_path(temporary_path, pathlib.Path("fixture"))
        config = base_bridge_config(
            repo_root,
            temporary_path,
            bridge_script,
            dummy,
            runtime_distribution_key="bundleRuntimeDistribution",
            reported_work_root=None,
            book_key_output_permissions="0600",
            pdf_path=dummy,
            pdf_argument_override=None,
            stderr_path=temporary_path / "stderr.txt",
            label="Attested open-book arguments",
        )
        payload = json.loads(open_book(config, {"openBook": "open-book"}))
        arguments = payload["arguments"]
        assert "--inventory-costing" not in arguments
        book_start_index = arguments.index("--book-start-effective-date")
        assert arguments[book_start_index - 2 : book_start_index + 2] == [
            "--fiscal-year-start",
            "01-01",
            "--book-start-effective-date",
            "2026-01-01",
        ]
        assert arguments[-10:] == [
            "--attestation-custodian",
            "file-pkcs8",
            "--attestation-founder-principal-id",
            "4bc17dd7-145f-4ea7-bb55-167ca2f6ac11",
            "--attestation-founder-key-file",
            str(temporary_path / "fixture"),
            "--attestation-founder-passphrase-file",
            str(temporary_path / "fixture"),
            "--output",
            "json",
        ]
        trading_config = replace(
            config,
            book_template_id="OWNER_MANAGED_TRADING",
            inventory_costing_doctrine="WEIGHTED_AVERAGE",
            accounting_basis="ACCRUAL",
        )
        trading_payload = json.loads(open_book(trading_config, {"openBook": "open-book"}))
        trading_arguments = trading_payload["arguments"]
        inventory_costing_index = trading_arguments.index("--inventory-costing")
        assert trading_arguments[inventory_costing_index - 4 : inventory_costing_index + 2] == [
            "--book-template-id",
            "OWNER_MANAGED_TRADING",
            "--accounting-basis",
            "ACCRUAL",
            "--inventory-costing",
            "WEIGHTED_AVERAGE",
        ]
        historical_config = replace(config, book_start_effective_date="2025-01-01")
        historical_payload = json.loads(open_book(historical_config, {"openBook": "open-book"}))
        historical_arguments = historical_payload["arguments"]
        historical_book_start_index = historical_arguments.index("--book-start-effective-date")
        assert historical_arguments[historical_book_start_index + 1] == "2025-01-01"
        assert signing_credential_arguments(config) == [
            "--attestation-custodian",
            "file-pkcs8",
            "--attestation-principal-id",
            "4bc17dd7-145f-4ea7-bb55-167ca2f6ac11",
            "--attestation-key-file",
            str(temporary_path / "fixture"),
            "--attestation-passphrase-file",
            str(temporary_path / "fixture"),
        ]
