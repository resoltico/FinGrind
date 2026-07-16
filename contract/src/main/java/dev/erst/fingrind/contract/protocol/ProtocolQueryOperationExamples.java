package dev.erst.fingrind.contract.protocol;

/** Example command lines for published query operations. */
final class ProtocolQueryOperationExamples {
  private ProtocolQueryOperationExamples() {}

  static String accountBalanceExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s cash %s text"
        .formatted(
            OperationId.ACCOUNT_BALANCE.wireName(),
            ProtocolBookAccessOptions.BOOK_FILE,
            ProtocolBookAccessOptions.BOOK_KEY_FILE,
            ProtocolOptions.Request.ACCOUNT_CODE,
            ProtocolOptions.Presentation.OUTPUT);
  }

  static String trialBalanceExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s text"
        .formatted(
            OperationId.TRIAL_BALANCE.wireName(),
            ProtocolBookAccessOptions.BOOK_FILE,
            ProtocolBookAccessOptions.BOOK_KEY_FILE,
            ProtocolOptions.Presentation.OUTPUT);
  }

  static String accountLedgerExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s cash %s 2026-04-01 %s 2026-04-30 %s text"
        .formatted(
            OperationId.ACCOUNT_LEDGER.wireName(),
            ProtocolBookAccessOptions.BOOK_FILE,
            ProtocolBookAccessOptions.BOOK_KEY_FILE,
            ProtocolOptions.Request.ACCOUNT_CODE,
            ProtocolOptions.DateRange.EFFECTIVE_DATE_FROM,
            ProtocolOptions.DateRange.EFFECTIVE_DATE_TO,
            ProtocolOptions.Presentation.OUTPUT);
  }

  static String periodSummaryExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-01 %s 2026-04-30 %s text"
        .formatted(
            OperationId.PERIOD_SUMMARY.wireName(),
            ProtocolBookAccessOptions.BOOK_FILE,
            ProtocolBookAccessOptions.BOOK_KEY_FILE,
            ProtocolOptions.DateRange.PERIOD_START,
            ProtocolOptions.DateRange.PERIOD_END,
            ProtocolOptions.Presentation.OUTPUT);
  }

  static String financialPositionExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-30 %s text"
        .formatted(
            OperationId.FINANCIAL_POSITION.wireName(),
            ProtocolBookAccessOptions.BOOK_FILE,
            ProtocolBookAccessOptions.BOOK_KEY_FILE,
            ProtocolOptions.DateRange.EFFECTIVE_DATE_AS_OF,
            ProtocolOptions.Presentation.OUTPUT);
  }

  static String inventoryValuationExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-30 %s %s text"
        .formatted(
            OperationId.INVENTORY_VALUATION.wireName(),
            ProtocolBookAccessOptions.BOOK_FILE,
            ProtocolBookAccessOptions.BOOK_KEY_FILE,
            ProtocolOptions.DateRange.AS_OF,
            ProtocolOptions.DateRange.MOVEMENTS,
            ProtocolOptions.Presentation.OUTPUT);
  }

  static String accrualCutoffScheduleExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-30 %s text"
        .formatted(
            OperationId.ACCRUAL_CUTOFF_SCHEDULE.wireName(),
            ProtocolBookAccessOptions.BOOK_FILE,
            ProtocolBookAccessOptions.BOOK_KEY_FILE,
            ProtocolOptions.DateRange.AS_OF,
            ProtocolOptions.Presentation.OUTPUT);
  }

  static String fixedAssetRegisterExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s text"
        .formatted(
            OperationId.FIXED_ASSET_REGISTER.wireName(),
            ProtocolBookAccessOptions.BOOK_FILE,
            ProtocolBookAccessOptions.BOOK_KEY_FILE,
            ProtocolOptions.Presentation.OUTPUT);
  }

  static String financingRegisterExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s text"
        .formatted(
            OperationId.FINANCING_REGISTER.wireName(),
            ProtocolBookAccessOptions.BOOK_FILE,
            ProtocolBookAccessOptions.BOOK_KEY_FILE,
            ProtocolOptions.Presentation.OUTPUT);
  }

  static String realizedForeignExchangeRegisterExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s text"
        .formatted(
            OperationId.REALIZED_FOREIGN_EXCHANGE_REGISTER.wireName(),
            ProtocolBookAccessOptions.BOOK_FILE,
            ProtocolBookAccessOptions.BOOK_KEY_FILE,
            ProtocolOptions.Presentation.OUTPUT);
  }

  static String latvianPayrollRegisterExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s text"
        .formatted(
            OperationId.LATVIAN_PAYROLL_REGISTER.wireName(),
            ProtocolBookAccessOptions.BOOK_FILE,
            ProtocolBookAccessOptions.BOOK_KEY_FILE,
            ProtocolOptions.Presentation.OUTPUT);
  }

  static String incomeStatementExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-01 %s 2026-04-30 %s text"
        .formatted(
            OperationId.INCOME_STATEMENT.wireName(),
            ProtocolBookAccessOptions.BOOK_FILE,
            ProtocolBookAccessOptions.BOOK_KEY_FILE,
            ProtocolOptions.DateRange.PERIOD_START,
            ProtocolOptions.DateRange.PERIOD_END,
            ProtocolOptions.Presentation.OUTPUT);
  }

  static String cashFlowStatementExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-01 %s 2026-04-30 %s text"
        .formatted(
            OperationId.CASH_FLOW_STATEMENT.wireName(),
            ProtocolBookAccessOptions.BOOK_FILE,
            ProtocolBookAccessOptions.BOOK_KEY_FILE,
            ProtocolOptions.DateRange.PERIOD_START,
            ProtocolOptions.DateRange.PERIOD_END,
            ProtocolOptions.Presentation.OUTPUT);
  }

  static String changesInEquityExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-01 %s 2026-04-30 %s text"
        .formatted(
            OperationId.CHANGES_IN_EQUITY.wireName(),
            ProtocolBookAccessOptions.BOOK_FILE,
            ProtocolBookAccessOptions.BOOK_KEY_FILE,
            ProtocolOptions.DateRange.PERIOD_START,
            ProtocolOptions.DateRange.PERIOD_END,
            ProtocolOptions.Presentation.OUTPUT);
  }

  static String taxObligationExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s tax-registration-main %s 2026-04-01 %s 2026-04-30 %s text"
        .formatted(
            OperationId.TAX_OBLIGATION.wireName(),
            ProtocolBookAccessOptions.BOOK_FILE,
            ProtocolBookAccessOptions.BOOK_KEY_FILE,
            ProtocolOptions.Request.TAX_REGISTRATION_ID,
            ProtocolOptions.DateRange.PERIOD_START,
            ProtocolOptions.DateRange.PERIOD_END,
            ProtocolOptions.Presentation.OUTPUT);
  }
}
