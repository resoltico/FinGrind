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
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
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
                    List.of(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV),
            "List one stable page of declared accounts in the selected book using keyset pagination.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s %d"
                        .formatted(
                            OperationId.LIST_ACCOUNTS.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.LIMIT,
                            ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT)))),
        ProtocolOperationDefinitions.operation(
            OperationId.GET_POSTING,
            OperationCategory.QUERY,
            "Get Posting",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.POSTING_ID + " <posting-id>",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
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
                    List.of(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV),
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
        ProtocolQueryReportOperations.accountWindowReportOperation(
            OperationId.ACCOUNT_BALANCE,
            "Account Balance",
            "Compute grouped per-currency balances for one declared account.",
            ProtocolQueryOperationExamples.accountBalanceExample()),
        ProtocolQueryReportOperations.asOfReportOperation(
            OperationId.TRIAL_BALANCE,
            "Trial Balance",
            "Compute one book-wide trial balance as of the selected effective date or the current book horizon when no date filter is supplied.",
            ProtocolQueryOperationExamples.trialBalanceExample()),
        ProtocolQueryReportOperations.accountWindowReportOperation(
            OperationId.ACCOUNT_LEDGER,
            "Account Ledger",
            "Compute the running ledger for one account, including opening balances, per-posting movement, and closing balances.",
            ProtocolQueryOperationExamples.accountLedgerExample()),
        ProtocolQueryReportOperations.periodReportOperation(
            OperationId.PERIOD_SUMMARY,
            "Period Summary",
            true,
            "Compute one bounded accounting-period summary with posting totals, currency totals, and per-account activity.",
            ProtocolQueryOperationExamples.periodSummaryExample()),
        ProtocolQueryReportOperations.asOfReportOperation(
            OperationId.FINANCIAL_POSITION,
            "Financial Position",
            "Compute one statement of financial position as of the selected effective date or the current book horizon when no date filter is supplied.",
            ProtocolQueryOperationExamples.financialPositionExample()),
        ProtocolQueryReportOperations.periodReportOperation(
            OperationId.INCOME_STATEMENT,
            "Income Statement",
            false,
            "Compute one bounded income statement for the selected reporting period.",
            ProtocolQueryOperationExamples.incomeStatementExample()),
        ProtocolQueryReportOperations.periodReportOperation(
            OperationId.CHANGES_IN_EQUITY,
            "Changes In Equity",
            false,
            "Compute one bounded statement of changes in equity for the selected reporting period.",
            ProtocolQueryOperationExamples.changesInEquityExample()));
  }
}
