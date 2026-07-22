package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.stream.Stream;

/** Canonical query-operation registry for the public FinGrind protocol catalog. */
final class ProtocolQueryOperations {
  private ProtocolQueryOperations() {}

  static List<ProtocolOperation> operations() {
    List<ProtocolOperation> reports = ProtocolQueryReportCatalog.operations();
    return Stream.of(
            List.of(
                ProtocolAttestationKeyFileOperations.inspectAttestationKeyFileOperation(),
                inspectBookOperation(),
                verifyBookOperation(),
                attestationReviewOperation(),
                exportAttestationReceiptOperation(),
                verifyReceiptOperation(),
                listAccountsOperation(),
                listTaxRegistrationsOperation()),
            reports.subList(0, 1),
            List.of(getPostingOperation(), listPostingsOperation()),
            reports.subList(1, reports.size()))
        .flatMap(List::stream)
        .toList();
  }

  private static ProtocolOperation inspectBookOperation() {
    return ProtocolOperationDefinitions.jsonEnvelopeOperation(
        OperationId.INSPECT_BOOK,
        OperationCategory.QUERY,
        "Inspect Book",
        List.of(
            ProtocolBookAccessOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        "Inspect the selected book for lifecycle state, format version, and compatibility.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key"
                    .formatted(
                        OperationId.INSPECT_BOOK.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolBookAccessOptions.BOOK_KEY_FILE))));
  }

  private static ProtocolOperation verifyBookOperation() {
    return ProtocolOperationDefinitions.jsonEnvelopeOperation(
        OperationId.VERIFY_BOOK,
        OperationCategory.QUERY,
        "Verify Book",
        List.of(
            ProtocolBookAccessOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            "[" + ProtocolOptions.Attestation.REVIEW_FILE + " <path>]",
            "[" + ProtocolOptions.Attestation.REQUIRE_CLEAN + "]",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        "Verify every immutable attestation structure from genesis and report the first exact structural break, if any.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./reviews/acme-compromise.json %s"
                    .formatted(
                        OperationId.VERIFY_BOOK.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolBookAccessOptions.BOOK_KEY_FILE,
                        ProtocolOptions.Attestation.REVIEW_FILE,
                        ProtocolOptions.Attestation.REQUIRE_CLEAN))));
  }

  private static ProtocolOperation attestationReviewOperation() {
    return ProtocolOperationDefinitions.jsonEnvelopeOperation(
        OperationId.ATTESTATION_REVIEW,
        OperationCategory.QUERY,
        "Attestation Review",
        List.of(
            ProtocolBookAccessOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            "[" + ProtocolOptions.Attestation.REVIEW_FILE + " <path>]",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        "Report non-persisted compromise-review findings from a structurally valid attestation chain.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./reviews/acme-compromise.json"
                    .formatted(
                        OperationId.ATTESTATION_REVIEW.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolBookAccessOptions.BOOK_KEY_FILE,
                        ProtocolOptions.Attestation.REVIEW_FILE))));
  }

  private static ProtocolOperation exportAttestationReceiptOperation() {
    return ProtocolOperationDefinitions.jsonEnvelopeOperation(
        OperationId.EXPORT_ATTESTATION_RECEIPT,
        OperationCategory.QUERY,
        "Export Attestation Receipt",
        List.of(
            ProtocolBookAccessOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.Attestation.RECEIPT_FILE + " <path>",
            ProtocolOptions.requiredAttestationCredentialSyntax(),
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        "Publish an independently retained quorum-signed receipt without changing the selected book.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./receipts/acme.fgar %s file-pkcs8 %s 123e4567-e89b-12d3-a456-426614174000 %s ./secrets/operator.fgatk %s ./secrets/operator.passphrase"
                    .formatted(
                        OperationId.EXPORT_ATTESTATION_RECEIPT.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolBookAccessOptions.BOOK_KEY_FILE,
                        ProtocolOptions.Attestation.RECEIPT_FILE,
                        ProtocolOptions.Attestation.CUSTODIAN,
                        ProtocolOptions.Attestation.PRINCIPAL_ID,
                        ProtocolOptions.Attestation.KEY_FILE,
                        ProtocolOptions.Attestation.PASSPHRASE_FILE))));
  }

  private static ProtocolOperation verifyReceiptOperation() {
    return ProtocolOperationDefinitions.jsonEnvelopeOperation(
        OperationId.VERIFY_RECEIPT,
        OperationCategory.QUERY,
        "Verify Receipt",
        List.of(
            ProtocolBookAccessOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.Attestation.RECEIPT_FILE + " <path>",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        "Verify an independently retained receipt against the selected book's complete immutable chain.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./receipts/acme.fgar"
                    .formatted(
                        OperationId.VERIFY_RECEIPT.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolBookAccessOptions.BOOK_KEY_FILE,
                        ProtocolOptions.Attestation.RECEIPT_FILE))));
  }

  private static ProtocolOperation listAccountsOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.LIST_ACCOUNTS,
        OperationCategory.QUERY,
        "List Accounts",
        List.of(),
        List.of(
            ProtocolBookAccessOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.optionalLimitSyntax(),
            ProtocolOptions.optionalCursorSyntax(),
            "[" + ProtocolOptions.Presentation.WITH_CONTEXT + "]",
            ProtocolOptions.optionalOutputSyntax(
                List.of(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV),
        "List a stable page of declared accounts in the selected book using keyset pagination.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s %d"
                    .formatted(
                        OperationId.LIST_ACCOUNTS.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolBookAccessOptions.BOOK_KEY_FILE,
                        ProtocolOptions.ReportQuery.LIMIT,
                        ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT))));
  }

  private static ProtocolOperation listTaxRegistrationsOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.LIST_TAX_REGISTRATIONS,
        OperationCategory.QUERY,
        "List Tax Registrations",
        List.of(),
        List.of(
            ProtocolBookAccessOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.optionalLimitSyntax(),
            ProtocolOptions.optionalCursorSyntax(),
            "[" + ProtocolOptions.Presentation.WITH_CONTEXT + "]",
            ProtocolOptions.optionalOutputSyntax(
                List.of(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV),
        "List a stable page of declared tax registrations in the selected book using keyset pagination.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s %d"
                    .formatted(
                        OperationId.LIST_TAX_REGISTRATIONS.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolBookAccessOptions.BOOK_KEY_FILE,
                        ProtocolOptions.ReportQuery.LIMIT,
                        ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT))));
  }

  private static ProtocolOperation getPostingOperation() {
    return ProtocolOperationDefinitions.jsonEnvelopeOperation(
        OperationId.GET_POSTING,
        OperationCategory.QUERY,
        "Get Posting",
        List.of(
            ProtocolBookAccessOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.Request.POSTING_ID + " <posting-id>",
            "[" + ProtocolOptions.Presentation.WITH_CONTEXT + "]",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        "Return a committed posting by durable posting identifier.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 018f0e6d-7f7e-7b04-b93f-bc0b69f19d5b"
                    .formatted(
                        OperationId.GET_POSTING.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolBookAccessOptions.BOOK_KEY_FILE,
                        ProtocolOptions.Request.POSTING_ID))));
  }

  private static ProtocolOperation listPostingsOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.LIST_POSTINGS,
        OperationCategory.QUERY,
        "List Postings",
        List.of(),
        List.of(
            ProtocolBookAccessOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            "[" + ProtocolOptions.Request.ACCOUNT_CODE + " <account-code>]",
            "[" + ProtocolOptions.DateRange.EFFECTIVE_DATE_FROM + " <YYYY-MM-DD>]",
            "[" + ProtocolOptions.DateRange.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>]",
            ProtocolOptions.optionalLimitSyntax(),
            ProtocolOptions.optionalCursorSyntax(),
            "[" + ProtocolOptions.Presentation.WITH_CONTEXT + "]",
            ProtocolOptions.optionalOutputSyntax(
                List.of(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV),
        "List a filtered page of committed postings in stable reverse-chronological order using keyset pagination.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 1000 %s 25"
                    .formatted(
                        OperationId.LIST_POSTINGS.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolBookAccessOptions.BOOK_KEY_FILE,
                        ProtocolOptions.Request.ACCOUNT_CODE,
                        ProtocolOptions.ReportQuery.LIMIT))));
  }
}
