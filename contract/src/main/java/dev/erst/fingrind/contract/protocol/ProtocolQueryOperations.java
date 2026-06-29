package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical query-operation registry for the public FinGrind protocol catalog. */
final class ProtocolQueryOperations {
  private ProtocolQueryOperations() {}

  static List<ProtocolOperation> operations() {
    return List.of(
        inspectBookOperation(),
        listAccountsOperation(),
        listTaxRegistrationsOperation(),
        taxObligationOperation(),
        getPostingOperation(),
        listPostingsOperation(),
        accountBalanceOperation(),
        trialBalanceOperation(),
        accountLedgerOperation(),
        periodSummaryOperation(),
        financialPositionOperation(),
        incomeStatementOperation(),
        cashFlowStatementOperation(),
        changesInEquityOperation());
  }

  private static ProtocolOperation inspectBookOperation() {
    return ProtocolOperationDefinitions.operation(
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
                        ProtocolOptions.BOOK_KEY_FILE))));
  }

  private static ProtocolOperation listAccountsOperation() {
    return ProtocolOperationDefinitions.operation(
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
                        ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT))));
  }

  private static ProtocolOperation listTaxRegistrationsOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.LIST_TAX_REGISTRATIONS,
        OperationCategory.QUERY,
        "List Tax Registrations",
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
        "List one stable page of declared tax registrations in the selected book using keyset pagination.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s %d"
                    .formatted(
                        OperationId.LIST_TAX_REGISTRATIONS.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.LIMIT,
                        ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT))));
  }

  private static ProtocolOperation getPostingOperation() {
    return ProtocolOperationDefinitions.operation(
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
                        ProtocolOptions.POSTING_ID))));
  }

  private static ProtocolOperation listPostingsOperation() {
    return ProtocolOperationDefinitions.operation(
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
                        ProtocolOptions.LIMIT))));
  }

  private static ProtocolOperation taxObligationOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.TAX_OBLIGATION,
        OperationCategory.QUERY,
        "Tax Obligation",
        List.of(),
        List.of(
            ProtocolOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.TAX_REGISTRATION_ID + " <tax-registration-id>",
            ProtocolOptions.PERIOD_START + " <YYYY-MM-DD>",
            ProtocolOptions.PERIOD_END + " <YYYY-MM-DD>",
            ProtocolOptions.optionalOutputSyntax(
                List.of(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV),
        "Compute one bounded tax-obligation report for the selected declared tax registration.",
        List.of(
            ProtocolExampleStep.command(ProtocolQueryOperationExamples.taxObligationExample())));
  }

  private static ProtocolOperation accountBalanceOperation() {
    return ProtocolQueryReportOperations.accountWindowReportOperation(
        OperationId.ACCOUNT_BALANCE,
        "Account Balance",
        "Compute grouped per-currency balances for one declared account.",
        ProtocolQueryOperationExamples.accountBalanceExample());
  }

  private static ProtocolOperation trialBalanceOperation() {
    return ProtocolQueryReportOperations.asOfReportOperation(
        OperationId.TRIAL_BALANCE,
        "Trial Balance",
        "Compute one book-wide trial balance as of the selected effective date or the latest effective date in the selected book when no date filter is supplied.",
        ProtocolQueryOperationExamples.trialBalanceExample());
  }

  private static ProtocolOperation accountLedgerOperation() {
    return ProtocolQueryReportOperations.accountWindowReportOperation(
        OperationId.ACCOUNT_LEDGER,
        "Account Ledger",
        "Compute the running ledger for one account, including opening balances, per-posting movement, and closing balances.",
        ProtocolQueryOperationExamples.accountLedgerExample());
  }

  private static ProtocolOperation periodSummaryOperation() {
    return ProtocolQueryReportOperations.periodReportOperation(
        OperationId.PERIOD_SUMMARY,
        "Period Summary",
        false,
        true,
        "Compute one bounded accounting-period summary with posting totals, currency totals, and per-account activity.",
        ProtocolQueryOperationExamples.periodSummaryExample());
  }

  private static ProtocolOperation financialPositionOperation() {
    return ProtocolQueryReportOperations.asOfReportOperation(
        OperationId.FINANCIAL_POSITION,
        "Financial Position",
        "Compute one statement of financial position as of the selected effective date or the latest effective date in the selected book when no date filter is supplied.",
        ProtocolQueryOperationExamples.financialPositionExample());
  }

  private static ProtocolOperation incomeStatementOperation() {
    return ProtocolQueryReportOperations.periodReportOperation(
        OperationId.INCOME_STATEMENT,
        "Income Statement",
        true,
        false,
        "Compute one bounded income statement for the selected reporting period.",
        ProtocolQueryOperationExamples.incomeStatementExample());
  }

  private static ProtocolOperation cashFlowStatementOperation() {
    return ProtocolQueryReportOperations.periodReportOperation(
        OperationId.CASH_FLOW_STATEMENT,
        "Cash Flow Statement",
        true,
        false,
        "Compute one bounded statement of cash receipts and payments for the selected reporting period.",
        ProtocolQueryOperationExamples.cashFlowStatementExample());
  }

  private static ProtocolOperation changesInEquityOperation() {
    return ProtocolQueryReportOperations.periodReportOperation(
        OperationId.CHANGES_IN_EQUITY,
        "Changes In Equity",
        true,
        false,
        "Compute one bounded statement of changes in equity for the selected reporting period.",
        ProtocolQueryOperationExamples.changesInEquityExample());
  }
}
