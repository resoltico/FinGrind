from __future__ import annotations

import json
import pathlib
import tempfile

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
        assert arguments[-8:] == [
            "--attestation-founder-principal-id",
            "4bc17dd7-145f-4ea7-bb55-167ca2f6ac11",
            "--attestation-founder-key-file",
            str(temporary_path / "fixture"),
            "--attestation-founder-passphrase-file",
            str(temporary_path / "fixture"),
            "--output",
            "json",
        ]
