from __future__ import annotations

import pathlib

from .attestation_review_window_contract import assert_review_window_error_contract
from .capability_baseline_contract import assert_capability_baseline_contract
from .capability_baseline_loading_contract import assert_capability_baseline_loading_contract
from .cli_transport_encoding_contract import assert_cli_transport_encoding_contract
from .contract_bridge_test_suite import run_bridge_and_report_contracts
from .contract_open_book_test import assert_attested_open_book_arguments
from .discovery_runtime_assertions_contract import assert_discovery_runtime_assertions_contract
from .field_matrix.artifact_assertions_contract import assert_pdf_artifact_contract
from .field_matrix.contract_assertions import assert_field_matrix_contracts
from .field_matrix.pair_publication_transaction_contract import (
    assert_pair_publication_transaction_contract,
)
from .field_matrix.receipt_artifact_assertions_contract import assert_receipt_artifact_contract
from .native_probe_path_transport_contract import assert_native_probe_path_transport_contract


def assert_external_surface_contracts(repo_root: pathlib.Path) -> None:
    assert_capability_baseline_contract()
    assert_capability_baseline_loading_contract()
    assert_cli_transport_encoding_contract()
    assert_native_probe_path_transport_contract()
    assert_field_matrix_contracts()
    assert_pdf_artifact_contract(repo_root)
    assert_pair_publication_transaction_contract()
    assert_receipt_artifact_contract(repo_root)
    run_bridge_and_report_contracts(repo_root)
    assert_attested_open_book_arguments(repo_root)
    assert_discovery_runtime_assertions_contract(repo_root)
    assert_review_window_error_contract()
