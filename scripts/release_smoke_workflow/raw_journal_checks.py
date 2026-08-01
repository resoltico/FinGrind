from __future__ import annotations

from .attestation_arguments import signing_credential_arguments
from .cli import run_cli
from .models import ReleaseSmokeConfig
from .support import parse_json_output, payload_field, require


def verify_raw_journal_commit_and_readback(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
) -> None:
    print(f"{config.label}: verifying direct journal commit and read-back")
    commit_payload = parse_json_output(
        run_cli(
            config,
            operation_ids["postEntry"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--request-file",
            config.request_raw_journal.argument,
            *signing_credential_arguments(config),
            "--output",
            "json",
        ),
        f"{config.label} raw journal commit output was not valid JSON",
    )
    require(
        commit_payload.get("status") == "ok",
        f"{config.label} raw journal commit did not report ok status",
    )
    posting_id = payload_field(commit_payload, "payload", "postingId")
    require(
        isinstance(posting_id, str) and posting_id,
        f"{config.label} raw journal commit did not report payload.postingId",
    )
    readback_payload = parse_json_output(
        run_cli(
            config,
            operation_ids["getPosting"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--posting-id",
            posting_id,
            "--output",
            "json",
        ),
        f"{config.label} raw journal read-back output was not valid JSON",
    )
    posting = payload_field(readback_payload, "payload", "posting")
    require(
        isinstance(posting, dict),
        f"{config.label} raw journal read-back did not expose payload.posting",
    )
    require(
        posting.get("postingOriginKind") == "DIRECT_JOURNAL",
        f"{config.label} raw journal read-back did not preserve postingOriginKind DIRECT_JOURNAL",
    )
    require(
        posting.get("postingKind") == "STANDARD",
        f"{config.label} raw journal read-back did not preserve postingKind STANDARD",
    )
    lines = posting.get("lines")
    require(
        isinstance(lines, list) and len(lines) == 2,
        f"{config.label} raw journal read-back did not expose exactly two journal lines",
    )
    account_codes = {
        line.get("accountCode")
        for line in lines
        if isinstance(line, dict) and "accountCode" in line
    }
    require(
        account_codes == {config.starter_cash_account_code, config.bank_account_code},
        f"{config.label} raw journal read-back did not preserve the expected bank-transfer account codes",
    )
    source_documents = (
        posting.get("evidence", {}).get("sourceDocuments", [])
        if isinstance(posting.get("evidence"), dict)
        else []
    )
    require(
        isinstance(source_documents, list)
        and len(source_documents) == 1
        and isinstance(source_documents[0], dict)
        and source_documents[0].get("sourceDocumentType") == "bank-deposit",
        f"{config.label} raw journal read-back did not preserve the bank-deposit evidence contract",
    )
