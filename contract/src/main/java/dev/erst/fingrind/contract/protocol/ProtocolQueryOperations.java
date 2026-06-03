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
        accountWindowReportOperation(
            OperationId.ACCOUNT_BALANCE,
            "Account Balance",
            "Compute grouped per-currency balances for one declared account.",
            accountBalanceExample()),
        asOfReportOperation(
            OperationId.TRIAL_BALANCE,
            "Trial Balance",
            "Compute one book-wide trial balance as of the selected effective date or the current book horizon when no date filter is supplied.",
            trialBalanceExample()),
        accountWindowReportOperation(
            OperationId.ACCOUNT_LEDGER,
            "Account Ledger",
            "Compute the running ledger for one account, including opening balances, per-posting movement, and closing balances.",
            accountLedgerExample()),
        periodReportOperation(
            OperationId.PERIOD_SUMMARY,
            "Period Summary",
            true,
            "Compute one bounded accounting-period summary with posting totals, currency totals, and per-account activity.",
            periodSummaryExample()),
        asOfReportOperation(
            OperationId.FINANCIAL_POSITION,
            "Financial Position",
            "Compute one statement of financial position as of the selected effective date or the current book horizon when no date filter is supplied.",
            financialPositionExample()),
        periodReportOperation(
            OperationId.INCOME_STATEMENT,
            "Income Statement",
            false,
            "Compute one bounded income statement for the selected reporting period.",
            incomeStatementExample()),
        periodReportOperation(
            OperationId.CHANGES_IN_EQUITY,
            "Changes In Equity",
            false,
            "Compute one bounded statement of changes in equity for the selected reporting period.",
            changesInEquityExample()));
  }

  private static ProtocolOperation accountWindowReportOperation(
      OperationId operationId, String title, String description, String example) {
    return pdfQueryOperation(
        operationId, title, accountWindowInvocationSyntax(), description, example);
  }

  private static ProtocolOperation asOfReportOperation(
      OperationId operationId, String title, String description, String example) {
    return pdfQueryOperation(operationId, title, asOfInvocationSyntax(), description, example);
  }

  private static ProtocolOperation periodReportOperation(
      OperationId operationId,
      String title,
      boolean includePostingCoverage,
      String description,
      String example) {
    return pdfQueryOperation(
        operationId, title, periodInvocationSyntax(includePostingCoverage), description, example);
  }

  private static ProtocolOperation pdfQueryOperation(
      OperationId operationId,
      String title,
      List<String> invocationSyntax,
      String description,
      String example) {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            operationId,
            OperationCategory.QUERY,
            title,
            List.of(),
            invocationSyntax,
            ExecutionMode.JSON_ENVELOPE,
            pdfOutputModes(),
            pdfArtifactOutputs(),
            description,
            List.of(ProtocolExampleStep.command(example))));
  }

  private static List<String> accountWindowInvocationSyntax() {
    return List.of(
        ProtocolOptions.BOOK_FILE + " <path>",
        ProtocolOptions.currentPassphraseSourceSyntax(),
        ProtocolOptions.ACCOUNT_CODE + " <account-code>",
        "[" + ProtocolOptions.EFFECTIVE_DATE_FROM + " <YYYY-MM-DD>]",
        "[" + ProtocolOptions.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>]",
        ProtocolOptions.optionalPostingCoverageSyntax(),
        ProtocolOptions.optionalPdfOutSyntax(),
        ProtocolOptions.optionalOutputSyntax(pdfOutputModes()));
  }

  private static List<String> asOfInvocationSyntax() {
    return List.of(
        ProtocolOptions.BOOK_FILE + " <path>",
        ProtocolOptions.currentPassphraseSourceSyntax(),
        "[" + ProtocolOptions.EFFECTIVE_DATE_AS_OF + " <YYYY-MM-DD>]",
        ProtocolOptions.optionalPdfOutSyntax(),
        ProtocolOptions.optionalOutputSyntax(pdfOutputModes()));
  }

  private static List<String> periodInvocationSyntax(boolean includePostingCoverage) {
    List<String> invocationSyntax =
        new java.util.ArrayList<>(
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.EFFECTIVE_DATE_FROM + " <YYYY-MM-DD>",
                ProtocolOptions.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>"));
    if (includePostingCoverage) {
      invocationSyntax.add(ProtocolOptions.optionalPostingCoverageSyntax());
    }
    invocationSyntax.add(ProtocolOptions.optionalPdfOutSyntax());
    invocationSyntax.add(ProtocolOptions.optionalOutputSyntax(pdfOutputModes()));
    return List.copyOf(invocationSyntax);
  }

  private static List<OutputMode> pdfOutputModes() {
    return List.of(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV);
  }

  private static List<ProtocolArtifactOutput> pdfArtifactOutputs() {
    return List.of(ProtocolArtifactOutput.pdf());
  }

  private static String accountBalanceExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s cash %s text"
        .formatted(
            OperationId.ACCOUNT_BALANCE.wireName(),
            ProtocolOptions.BOOK_FILE,
            ProtocolOptions.BOOK_KEY_FILE,
            ProtocolOptions.ACCOUNT_CODE,
            ProtocolOptions.OUTPUT);
  }

  private static String trialBalanceExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-30 %s text"
        .formatted(
            OperationId.TRIAL_BALANCE.wireName(),
            ProtocolOptions.BOOK_FILE,
            ProtocolOptions.BOOK_KEY_FILE,
            ProtocolOptions.EFFECTIVE_DATE_AS_OF,
            ProtocolOptions.OUTPUT);
  }

  private static String accountLedgerExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s cash %s 2026-04-01 %s 2026-04-30 %s text"
        .formatted(
            OperationId.ACCOUNT_LEDGER.wireName(),
            ProtocolOptions.BOOK_FILE,
            ProtocolOptions.BOOK_KEY_FILE,
            ProtocolOptions.ACCOUNT_CODE,
            ProtocolOptions.EFFECTIVE_DATE_FROM,
            ProtocolOptions.EFFECTIVE_DATE_TO,
            ProtocolOptions.OUTPUT);
  }

  private static String periodSummaryExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-01 %s 2026-04-30 %s text"
        .formatted(
            OperationId.PERIOD_SUMMARY.wireName(),
            ProtocolOptions.BOOK_FILE,
            ProtocolOptions.BOOK_KEY_FILE,
            ProtocolOptions.EFFECTIVE_DATE_FROM,
            ProtocolOptions.EFFECTIVE_DATE_TO,
            ProtocolOptions.OUTPUT);
  }

  private static String financialPositionExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-30 %s text"
        .formatted(
            OperationId.FINANCIAL_POSITION.wireName(),
            ProtocolOptions.BOOK_FILE,
            ProtocolOptions.BOOK_KEY_FILE,
            ProtocolOptions.EFFECTIVE_DATE_AS_OF,
            ProtocolOptions.OUTPUT);
  }

  private static String incomeStatementExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-01 %s 2026-04-30 %s text"
        .formatted(
            OperationId.INCOME_STATEMENT.wireName(),
            ProtocolOptions.BOOK_FILE,
            ProtocolOptions.BOOK_KEY_FILE,
            ProtocolOptions.EFFECTIVE_DATE_FROM,
            ProtocolOptions.EFFECTIVE_DATE_TO,
            ProtocolOptions.OUTPUT);
  }

  private static String changesInEquityExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-01 %s 2026-04-30 %s text"
        .formatted(
            OperationId.CHANGES_IN_EQUITY.wireName(),
            ProtocolOptions.BOOK_FILE,
            ProtocolOptions.BOOK_KEY_FILE,
            ProtocolOptions.EFFECTIVE_DATE_FROM,
            ProtocolOptions.EFFECTIVE_DATE_TO,
            ProtocolOptions.OUTPUT);
  }
}
