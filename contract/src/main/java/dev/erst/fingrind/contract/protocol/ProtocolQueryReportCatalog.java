package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical report-query entries for the public FinGrind protocol catalog. */
final class ProtocolQueryReportCatalog {
  private ProtocolQueryReportCatalog() {}

  static List<ProtocolOperation> operations() {
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
