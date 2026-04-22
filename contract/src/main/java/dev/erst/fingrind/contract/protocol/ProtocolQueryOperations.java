package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical query-operation registry for the public FinGrind protocol catalog. */
final class ProtocolQueryOperations {
  private ProtocolQueryOperations() {}

  static List<ProtocolOperation> operations() {
    return List.of(
        ProtocolOperationDefinitions.operation(
            OperationId.INSPECT_BOOK,
            OperationCategory.QUERY,
            "Inspect Book",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.optionalOutputSyntax(
                    List.of(OutputMode.JSON.wireValue(), OutputMode.HUMAN.wireValue()))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON.wireValue(), OutputMode.HUMAN.wireValue()),
            "Inspect one selected book for lifecycle state, format version, and compatibility.",
            List.of(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key"
                    .formatted(
                        OperationId.INSPECT_BOOK.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE))),
        ProtocolOperationDefinitions.operation(
            OperationId.LIST_ACCOUNTS,
            OperationCategory.QUERY,
            "List Accounts",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.optionalLimitSyntax(),
                ProtocolOptions.optionalOffsetSyntax(),
                ProtocolOptions.optionalOutputSyntax(OutputMode.wireValues())),
            ExecutionMode.JSON_ENVELOPE,
            OutputMode.wireValues(),
            "List one stable page of declared accounts in the selected book.",
            List.of(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s %d"
                    .formatted(
                        OperationId.LIST_ACCOUNTS.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.LIMIT,
                        ProtocolLimits.DEFAULT_PAGE_LIMIT))),
        ProtocolOperationDefinitions.operation(
            OperationId.GET_POSTING,
            OperationCategory.QUERY,
            "Get Posting",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.POSTING_ID + " <posting-id>",
                ProtocolOptions.optionalOutputSyntax(
                    List.of(OutputMode.JSON.wireValue(), OutputMode.HUMAN.wireValue()))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON.wireValue(), OutputMode.HUMAN.wireValue()),
            "Return one committed posting by durable posting identifier.",
            List.of(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 018f0e6d-7f7e-7b04-b93f-bc0b69f19d5b"
                    .formatted(
                        OperationId.GET_POSTING.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.POSTING_ID))),
        ProtocolOperationDefinitions.operation(
            OperationId.LIST_POSTINGS,
            OperationCategory.QUERY,
            "List Postings",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                "[" + ProtocolOptions.ACCOUNT_CODE + " <account-code>]",
                "[" + ProtocolOptions.EFFECTIVE_DATE_FROM + " <YYYY-MM-DD>]",
                "[" + ProtocolOptions.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>]",
                ProtocolOptions.optionalLimitSyntax(),
                ProtocolOptions.optionalCursorSyntax(),
                ProtocolOptions.optionalOutputSyntax(OutputMode.wireValues())),
            ExecutionMode.JSON_ENVELOPE,
            OutputMode.wireValues(),
            "List one filtered page of committed postings in stable reverse-chronological order using keyset pagination.",
            List.of(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 1000 %s 25"
                    .formatted(
                        OperationId.LIST_POSTINGS.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.ACCOUNT_CODE,
                        ProtocolOptions.LIMIT))),
        ProtocolOperationDefinitions.operation(
            new ProtocolOperationDefinitions.OperationDefinition(
                OperationId.ACCOUNT_BALANCE,
                OperationCategory.QUERY,
                "Account Balance",
                List.of(),
                List.of(
                    ProtocolOptions.BOOK_FILE + " <path>",
                    ProtocolOptions.currentPassphraseSourceSyntax(),
                    ProtocolOptions.ACCOUNT_CODE + " <account-code>",
                    "[" + ProtocolOptions.EFFECTIVE_DATE_FROM + " <YYYY-MM-DD>]",
                    "[" + ProtocolOptions.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>]",
                    ProtocolOptions.optionalPdfOutSyntax(),
                    ProtocolOptions.optionalOutputSyntax(OutputMode.wireValues())),
                ExecutionMode.JSON_ENVELOPE,
                OutputMode.wireValues(),
                List.of(ProtocolArtifactOutput.pdf()),
                "Compute grouped per-currency balances for one declared account.",
                List.of(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 1000 %s ./reports/cash-balance.pdf"
                        .formatted(
                            OperationId.ACCOUNT_BALANCE.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.ACCOUNT_CODE,
                            ProtocolOptions.PDF_OUT)))),
        ProtocolOperationDefinitions.operation(
            new ProtocolOperationDefinitions.OperationDefinition(
                OperationId.TRIAL_BALANCE,
                OperationCategory.QUERY,
                "Trial Balance",
                List.of(),
                List.of(
                    ProtocolOptions.BOOK_FILE + " <path>",
                    ProtocolOptions.currentPassphraseSourceSyntax(),
                    "[" + ProtocolOptions.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>]",
                    ProtocolOptions.optionalPdfOutSyntax(),
                    ProtocolOptions.optionalOutputSyntax(OutputMode.wireValues())),
                ExecutionMode.JSON_ENVELOPE,
                OutputMode.wireValues(),
                List.of(ProtocolArtifactOutput.pdf()),
                "Compute one book-wide trial balance as of the selected effective date or the current durable posting horizon when no date filter is supplied.",
                List.of(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-30 %s ./reports/trial-balance.pdf %s human"
                        .formatted(
                            OperationId.TRIAL_BALANCE.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.EFFECTIVE_DATE_TO,
                            ProtocolOptions.PDF_OUT,
                            ProtocolOptions.OUTPUT)))),
        ProtocolOperationDefinitions.operation(
            new ProtocolOperationDefinitions.OperationDefinition(
                OperationId.ACCOUNT_LEDGER,
                OperationCategory.QUERY,
                "Account Ledger",
                List.of(),
                List.of(
                    ProtocolOptions.BOOK_FILE + " <path>",
                    ProtocolOptions.currentPassphraseSourceSyntax(),
                    ProtocolOptions.ACCOUNT_CODE + " <account-code>",
                    "[" + ProtocolOptions.EFFECTIVE_DATE_FROM + " <YYYY-MM-DD>]",
                    "[" + ProtocolOptions.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>]",
                    ProtocolOptions.optionalPdfOutSyntax(),
                    ProtocolOptions.optionalOutputSyntax(OutputMode.wireValues())),
                ExecutionMode.JSON_ENVELOPE,
                OutputMode.wireValues(),
                List.of(ProtocolArtifactOutput.pdf()),
                "Compute the running ledger for one account, including opening balances, per-posting movement, and closing balances.",
                List.of(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 1000 %s 2026-04-01 %s 2026-04-30 %s ./reports/cash-ledger.pdf %s human"
                        .formatted(
                            OperationId.ACCOUNT_LEDGER.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.ACCOUNT_CODE,
                            ProtocolOptions.EFFECTIVE_DATE_FROM,
                            ProtocolOptions.EFFECTIVE_DATE_TO,
                            ProtocolOptions.PDF_OUT,
                            ProtocolOptions.OUTPUT)))),
        ProtocolOperationDefinitions.operation(
            new ProtocolOperationDefinitions.OperationDefinition(
                OperationId.PERIOD_SUMMARY,
                OperationCategory.QUERY,
                "Period Summary",
                List.of(),
                List.of(
                    ProtocolOptions.BOOK_FILE + " <path>",
                    ProtocolOptions.currentPassphraseSourceSyntax(),
                    ProtocolOptions.EFFECTIVE_DATE_FROM + " <YYYY-MM-DD>",
                    ProtocolOptions.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>",
                    ProtocolOptions.optionalPdfOutSyntax(),
                    ProtocolOptions.optionalOutputSyntax(OutputMode.wireValues())),
                ExecutionMode.JSON_ENVELOPE,
                OutputMode.wireValues(),
                List.of(ProtocolArtifactOutput.pdf()),
                "Compute one bounded office-work period summary with posting totals, currency totals, and per-account activity.",
                List.of(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-01 %s 2026-04-30 %s ./reports/april-summary.pdf %s human"
                        .formatted(
                            OperationId.PERIOD_SUMMARY.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.EFFECTIVE_DATE_FROM,
                            ProtocolOptions.EFFECTIVE_DATE_TO,
                            ProtocolOptions.PDF_OUT,
                            ProtocolOptions.OUTPUT)))));
  }
}
