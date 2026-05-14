package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.InteractionLimits;
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
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.HUMAN))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
            "Inspect one selected book for lifecycle state, format version, and compatibility.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key"
                        .formatted(
                            OperationId.INSPECT_BOOK.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE)))),
        ProtocolOperationDefinitions.operation(
            OperationId.LIST_ACCOUNTS,
            OperationCategory.QUERY,
            "List Accounts",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.optionalLimitSyntax(),
                ProtocolOptions.optionalCursorSyntax(),
                ProtocolOptions.optionalOutputSyntax(
                    List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV),
            "List one stable page of declared accounts in the selected book using keyset pagination.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s %d"
                        .formatted(
                            OperationId.LIST_ACCOUNTS.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.LIMIT,
                            InteractionLimits.DEFAULT_PAGE_LIMIT)))),
        ProtocolOperationDefinitions.operation(
            OperationId.GET_POSTING,
            OperationCategory.QUERY,
            "Get Posting",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.POSTING_ID + " <posting-id>",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.HUMAN))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
            "Return one committed posting by durable posting identifier.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 018f0e6d-7f7e-7b04-b93f-bc0b69f19d5b"
                        .formatted(
                            OperationId.GET_POSTING.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.POSTING_ID)))),
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
                ProtocolOptions.optionalOutputSyntax(
                    List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV),
            "List one filtered page of committed postings in stable reverse-chronological order using keyset pagination.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 1000 %s 25"
                        .formatted(
                            OperationId.LIST_POSTINGS.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.ACCOUNT_CODE,
                            ProtocolOptions.LIMIT)))),
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
                    ProtocolOptions.optionalPostingCoverageSyntax(),
                    ProtocolOptions.optionalPdfOutSyntax(),
                    ProtocolOptions.optionalOutputSyntax(
                        List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV))),
                ExecutionMode.JSON_ENVELOPE,
                List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV),
                List.of(ProtocolArtifactOutput.pdf()),
                "Compute grouped per-currency balances for one declared account.",
                List.of(
                    ProtocolExampleStep.command(
                        "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 1000 %s ./reports/cash-balance.pdf"
                            .formatted(
                                OperationId.ACCOUNT_BALANCE.wireName(),
                                ProtocolOptions.BOOK_FILE,
                                ProtocolOptions.BOOK_KEY_FILE,
                                ProtocolOptions.ACCOUNT_CODE,
                                ProtocolOptions.PDF_OUT))))),
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
                    ProtocolOptions.optionalOutputSyntax(
                        List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV))),
                ExecutionMode.JSON_ENVELOPE,
                List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV),
                List.of(ProtocolArtifactOutput.pdf()),
                "Compute one book-wide trial balance as of the selected effective date or the current durable posting horizon when no date filter is supplied.",
                List.of(
                    ProtocolExampleStep.command(
                        "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-30 %s ./reports/trial-balance.pdf %s human"
                            .formatted(
                                OperationId.TRIAL_BALANCE.wireName(),
                                ProtocolOptions.BOOK_FILE,
                                ProtocolOptions.BOOK_KEY_FILE,
                                ProtocolOptions.EFFECTIVE_DATE_TO,
                                ProtocolOptions.PDF_OUT,
                                ProtocolOptions.OUTPUT))))),
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
                    ProtocolOptions.optionalPostingCoverageSyntax(),
                    ProtocolOptions.optionalPdfOutSyntax(),
                    ProtocolOptions.optionalOutputSyntax(
                        List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV))),
                ExecutionMode.JSON_ENVELOPE,
                List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV),
                List.of(ProtocolArtifactOutput.pdf()),
                "Compute the running ledger for one account, including opening balances, per-posting movement, and closing balances.",
                List.of(
                    ProtocolExampleStep.command(
                        "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 1000 %s 2026-04-01 %s 2026-04-30 %s ./reports/cash-ledger.pdf %s human"
                            .formatted(
                                OperationId.ACCOUNT_LEDGER.wireName(),
                                ProtocolOptions.BOOK_FILE,
                                ProtocolOptions.BOOK_KEY_FILE,
                                ProtocolOptions.ACCOUNT_CODE,
                                ProtocolOptions.EFFECTIVE_DATE_FROM,
                                ProtocolOptions.EFFECTIVE_DATE_TO,
                                ProtocolOptions.PDF_OUT,
                                ProtocolOptions.OUTPUT))))),
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
                    ProtocolOptions.optionalPostingCoverageSyntax(),
                    ProtocolOptions.optionalPdfOutSyntax(),
                    ProtocolOptions.optionalOutputSyntax(
                        List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV))),
                ExecutionMode.JSON_ENVELOPE,
                List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV),
                List.of(ProtocolArtifactOutput.pdf()),
                "Compute one bounded accounting-period summary with posting totals, currency totals, and per-account activity.",
                List.of(
                    ProtocolExampleStep.command(
                        "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-01 %s 2026-04-30 %s ./reports/april-summary.pdf %s human"
                            .formatted(
                                OperationId.PERIOD_SUMMARY.wireName(),
                                ProtocolOptions.BOOK_FILE,
                                ProtocolOptions.BOOK_KEY_FILE,
                                ProtocolOptions.EFFECTIVE_DATE_FROM,
                                ProtocolOptions.EFFECTIVE_DATE_TO,
                                ProtocolOptions.PDF_OUT,
                                ProtocolOptions.OUTPUT))))),
        ProtocolOperationDefinitions.operation(
            new ProtocolOperationDefinitions.OperationDefinition(
                OperationId.FINANCIAL_POSITION,
                OperationCategory.QUERY,
                "Financial Position",
                List.of(),
                List.of(
                    ProtocolOptions.BOOK_FILE + " <path>",
                    ProtocolOptions.currentPassphraseSourceSyntax(),
                    "[" + ProtocolOptions.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>]",
                    ProtocolOptions.optionalPdfOutSyntax(),
                    ProtocolOptions.optionalOutputSyntax(
                        List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV))),
                ExecutionMode.JSON_ENVELOPE,
                List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV),
                List.of(ProtocolArtifactOutput.pdf()),
                "Compute one statement of financial position as of the selected effective date or the current durable posting horizon when no date filter is supplied.",
                List.of(
                    ProtocolExampleStep.command(
                        "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-30 %s ./reports/financial-position.pdf %s human"
                            .formatted(
                                OperationId.FINANCIAL_POSITION.wireName(),
                                ProtocolOptions.BOOK_FILE,
                                ProtocolOptions.BOOK_KEY_FILE,
                                ProtocolOptions.EFFECTIVE_DATE_TO,
                                ProtocolOptions.PDF_OUT,
                                ProtocolOptions.OUTPUT))))),
        ProtocolOperationDefinitions.operation(
            new ProtocolOperationDefinitions.OperationDefinition(
                OperationId.INCOME_STATEMENT,
                OperationCategory.QUERY,
                "Income Statement",
                List.of(),
                List.of(
                    ProtocolOptions.BOOK_FILE + " <path>",
                    ProtocolOptions.currentPassphraseSourceSyntax(),
                    ProtocolOptions.EFFECTIVE_DATE_FROM + " <YYYY-MM-DD>",
                    ProtocolOptions.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>",
                    ProtocolOptions.optionalPdfOutSyntax(),
                    ProtocolOptions.optionalOutputSyntax(
                        List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV))),
                ExecutionMode.JSON_ENVELOPE,
                List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV),
                List.of(ProtocolArtifactOutput.pdf()),
                "Compute one bounded income statement for the selected reporting period.",
                List.of(
                    ProtocolExampleStep.command(
                        "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-01 %s 2026-04-30 %s ./reports/income-statement.pdf %s human"
                            .formatted(
                                OperationId.INCOME_STATEMENT.wireName(),
                                ProtocolOptions.BOOK_FILE,
                                ProtocolOptions.BOOK_KEY_FILE,
                                ProtocolOptions.EFFECTIVE_DATE_FROM,
                                ProtocolOptions.EFFECTIVE_DATE_TO,
                                ProtocolOptions.PDF_OUT,
                                ProtocolOptions.OUTPUT))))),
        ProtocolOperationDefinitions.operation(
            new ProtocolOperationDefinitions.OperationDefinition(
                OperationId.CHANGES_IN_EQUITY,
                OperationCategory.QUERY,
                "Changes In Equity",
                List.of(),
                List.of(
                    ProtocolOptions.BOOK_FILE + " <path>",
                    ProtocolOptions.currentPassphraseSourceSyntax(),
                    ProtocolOptions.EFFECTIVE_DATE_FROM + " <YYYY-MM-DD>",
                    ProtocolOptions.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>",
                    ProtocolOptions.optionalPdfOutSyntax(),
                    ProtocolOptions.optionalOutputSyntax(
                        List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV))),
                ExecutionMode.JSON_ENVELOPE,
                List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV),
                List.of(ProtocolArtifactOutput.pdf()),
                "Compute one bounded statement of changes in equity for the selected reporting period.",
                List.of(
                    ProtocolExampleStep.command(
                        "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-01 %s 2026-04-30 %s ./reports/changes-in-equity.pdf %s human"
                            .formatted(
                                OperationId.CHANGES_IN_EQUITY.wireName(),
                                ProtocolOptions.BOOK_FILE,
                                ProtocolOptions.BOOK_KEY_FILE,
                                ProtocolOptions.EFFECTIVE_DATE_FROM,
                                ProtocolOptions.EFFECTIVE_DATE_TO,
                                ProtocolOptions.PDF_OUT,
                                ProtocolOptions.OUTPUT))))));
  }
}
