package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.stream.Stream;

/** Canonical query-operation registry for the public FinGrind protocol catalog. */
final class ProtocolQueryOperations {
  private ProtocolQueryOperations() {}

  static List<ProtocolOperation> operations() {
    List<ProtocolOperation> reports = reportOperations();
    return Stream.of(
            List.of(
                inspectBookOperation(), listAccountsOperation(), listTaxRegistrationsOperation()),
            reports.subList(0, 1),
            List.of(getPostingOperation(), listPostingsOperation()),
            reports.subList(1, reports.size()))
        .flatMap(List::stream)
        .toList();
  }

  private static ProtocolOperation inspectBookOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.INSPECT_BOOK,
        OperationCategory.QUERY,
        "Inspect Book",
        List.of(),
        List.of(
            ProtocolBookAccessOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
        "Inspect the selected book for lifecycle state, format version, and compatibility.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key"
                    .formatted(
                        OperationId.INSPECT_BOOK.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolBookAccessOptions.BOOK_KEY_FILE))));
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
    return ProtocolOperationDefinitions.operation(
        OperationId.GET_POSTING,
        OperationCategory.QUERY,
        "Get Posting",
        List.of(),
        List.of(
            ProtocolBookAccessOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.Request.POSTING_ID + " <posting-id>",
            "[" + ProtocolOptions.Presentation.WITH_CONTEXT + "]",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
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

  private static List<ProtocolOperation> reportOperations() {
    return List.of(
        reportOperation(
            OperationId.TAX_OBLIGATION,
            "Tax Obligation",
            ProtocolQueryReportOperations.ReportShape.TAX_REGISTRATION_PERIOD,
            "Compute a bounded tax-obligation report for the selected declared tax registration.",
            ProtocolQueryOperationExamples.taxObligationExample()),
        reportOperation(
            OperationId.ACCOUNT_BALANCE,
            "Account Balance",
            ProtocolQueryReportOperations.ReportShape.ACCOUNT_WINDOW,
            "Compute grouped per-currency balances for a declared account.",
            ProtocolQueryOperationExamples.accountBalanceExample()),
        reportOperation(
            OperationId.TRIAL_BALANCE,
            "Trial Balance",
            ProtocolQueryReportOperations.ReportShape.AS_OF,
            "Compute a book-wide trial balance as of the selected effective date or the latest effective date in the selected book when no date filter is supplied.",
            ProtocolQueryOperationExamples.trialBalanceExample()),
        reportOperation(
            OperationId.ACCOUNT_LEDGER,
            "Account Ledger",
            ProtocolQueryReportOperations.ReportShape.ACCOUNT_LEDGER,
            "Compute a stable ascending keyset page of an account's running ledger, including opening balances, per-posting movement, and whole-range closing balances.",
            ProtocolQueryOperationExamples.accountLedgerExample()),
        reportOperation(
            OperationId.PERIOD_SUMMARY,
            "Period Summary",
            ProtocolQueryReportOperations.ReportShape.PERIOD_WITH_POSTING_COVERAGE,
            "Compute a bounded accounting-period summary with posting totals, currency totals, and per-account activity.",
            ProtocolQueryOperationExamples.periodSummaryExample()),
        reportOperation(
            OperationId.FINANCIAL_POSITION,
            "Financial Position",
            ProtocolQueryReportOperations.ReportShape.AS_OF,
            "Compute a statement of financial position as of the selected effective date or the latest effective date in the selected book when no date filter is supplied.",
            ProtocolQueryOperationExamples.financialPositionExample()),
        reportOperation(
            OperationId.INVENTORY_VALUATION,
            "Inventory Valuation",
            ProtocolQueryReportOperations.ReportShape.INVENTORY_VALUATION,
            "Compute exact per-account inventory quantity and carrying value from the canonical inventory movement replay order. The rounded moving-average unit-cost projection is informational only.",
            ProtocolQueryOperationExamples.inventoryValuationExample()),
        reportOperation(
            OperationId.ACCRUAL_CUTOFF_SCHEDULE,
            "Accrual Cut-Off Schedule",
            ProtocolQueryReportOperations.ReportShape.ACCRUAL_CUTOFF_SCHEDULE,
            "Compute durable prepayment, deferred-revenue, and accrued-expense lifecycle balances from the append-only cut-off aggregate facts.",
            ProtocolQueryOperationExamples.accrualCutoffScheduleExample()),
        reportOperation(
            OperationId.FIXED_ASSET_REGISTER,
            "Fixed Asset Register",
            ProtocolQueryReportOperations.ReportShape.FIXED_ASSET_REGISTER,
            "Compute durable fixed-asset cost, depreciation, carrying value, and disposal state from immutable lifecycle facts.",
            ProtocolQueryOperationExamples.fixedAssetRegisterExample()),
        reportOperation(
            OperationId.FINANCING_REGISTER,
            "Financing Register",
            ProtocolQueryReportOperations.ReportShape.BOOK_WIDE,
            "Compute durable financing principal, accrued interest, paid interest, and outstanding balances from immutable lifecycle facts.",
            ProtocolQueryOperationExamples.financingRegisterExample()),
        reportOperation(
            OperationId.REALIZED_FOREIGN_EXCHANGE_REGISTER,
            "Realized Foreign Exchange Register",
            ProtocolQueryReportOperations.ReportShape.BOOK_WIDE,
            "Compute durable foreign-currency receivable carrying amounts, settlements, and realized gain or loss from immutable lifecycle facts.",
            ProtocolQueryOperationExamples.realizedForeignExchangeRegisterExample()),
        reportOperation(
            OperationId.LATVIAN_PAYROLL_REGISTER,
            "Latvian Payroll Register",
            ProtocolQueryReportOperations.ReportShape.BOOK_WIDE,
            "Compute immutable Latvian payroll calculations and complete settlement posting lineage from the protected book's durable payroll facts.",
            ProtocolQueryOperationExamples.latvianPayrollRegisterExample()),
        reportOperation(
            OperationId.INCOME_STATEMENT,
            "Income Statement",
            ProtocolQueryReportOperations.ReportShape.PERIOD_WITH_COMPARATIVE,
            "Compute a bounded income statement for the selected reporting period.",
            ProtocolQueryOperationExamples.incomeStatementExample()),
        reportOperation(
            OperationId.CASH_FLOW_STATEMENT,
            "Cash Receipts And Payments",
            ProtocolQueryReportOperations.ReportShape.PERIOD_WITH_COMPARATIVE,
            "Compute a bounded statement of cash receipts and payments for the selected reporting period.",
            ProtocolQueryOperationExamples.cashFlowStatementExample()),
        reportOperation(
            OperationId.CHANGES_IN_EQUITY,
            "Changes In Equity",
            ProtocolQueryReportOperations.ReportShape.PERIOD_WITH_COMPARATIVE,
            "Compute a bounded statement of changes in equity for the selected reporting period.",
            ProtocolQueryOperationExamples.changesInEquityExample()));
  }

  private static ProtocolOperation reportOperation(
      OperationId operationId,
      String title,
      ProtocolQueryReportOperations.ReportShape reportShape,
      String description,
      String example) {
    return ProtocolQueryReportOperations.reportOperation(
        operationId, title, reportShape, description, example);
  }
}
