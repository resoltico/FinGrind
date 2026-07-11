package dev.erst.fingrind.contract.protocol;

/** Example command lines for published query operations. */
final class ProtocolQueryOperationExamples {
  private ProtocolQueryOperationExamples() {}

  static String accountBalanceExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s cash %s text"
        .formatted(
            OperationId.ACCOUNT_BALANCE.wireName(),
            ProtocolOptions.BOOK_FILE,
            ProtocolOptions.BOOK_KEY_FILE,
            ProtocolOptions.ACCOUNT_CODE,
            ProtocolOptions.OUTPUT);
  }

  static String trialBalanceExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s text"
        .formatted(
            OperationId.TRIAL_BALANCE.wireName(),
            ProtocolOptions.BOOK_FILE,
            ProtocolOptions.BOOK_KEY_FILE,
            ProtocolOptions.OUTPUT);
  }

  static String accountLedgerExample() {
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

  static String periodSummaryExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-01 %s 2026-04-30 %s text"
        .formatted(
            OperationId.PERIOD_SUMMARY.wireName(),
            ProtocolOptions.BOOK_FILE,
            ProtocolOptions.BOOK_KEY_FILE,
            ProtocolOptions.PERIOD_START,
            ProtocolOptions.PERIOD_END,
            ProtocolOptions.OUTPUT);
  }

  static String financialPositionExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-30 %s text"
        .formatted(
            OperationId.FINANCIAL_POSITION.wireName(),
            ProtocolOptions.BOOK_FILE,
            ProtocolOptions.BOOK_KEY_FILE,
            ProtocolOptions.EFFECTIVE_DATE_AS_OF,
            ProtocolOptions.OUTPUT);
  }

  static String inventoryValuationExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-30 %s %s text"
        .formatted(
            OperationId.INVENTORY_VALUATION.wireName(),
            ProtocolOptions.BOOK_FILE,
            ProtocolOptions.BOOK_KEY_FILE,
            ProtocolOptions.AS_OF,
            ProtocolOptions.MOVEMENTS,
            ProtocolOptions.OUTPUT);
  }

  static String incomeStatementExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-01 %s 2026-04-30 %s text"
        .formatted(
            OperationId.INCOME_STATEMENT.wireName(),
            ProtocolOptions.BOOK_FILE,
            ProtocolOptions.BOOK_KEY_FILE,
            ProtocolOptions.PERIOD_START,
            ProtocolOptions.PERIOD_END,
            ProtocolOptions.OUTPUT);
  }

  static String cashFlowStatementExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-01 %s 2026-04-30 %s text"
        .formatted(
            OperationId.CASH_FLOW_STATEMENT.wireName(),
            ProtocolOptions.BOOK_FILE,
            ProtocolOptions.BOOK_KEY_FILE,
            ProtocolOptions.PERIOD_START,
            ProtocolOptions.PERIOD_END,
            ProtocolOptions.OUTPUT);
  }

  static String changesInEquityExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-01 %s 2026-04-30 %s text"
        .formatted(
            OperationId.CHANGES_IN_EQUITY.wireName(),
            ProtocolOptions.BOOK_FILE,
            ProtocolOptions.BOOK_KEY_FILE,
            ProtocolOptions.PERIOD_START,
            ProtocolOptions.PERIOD_END,
            ProtocolOptions.OUTPUT);
  }

  static String taxObligationExample() {
    return "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s tax-registration-main %s 2026-04-01 %s 2026-04-30 %s text"
        .formatted(
            OperationId.TAX_OBLIGATION.wireName(),
            ProtocolOptions.BOOK_FILE,
            ProtocolOptions.BOOK_KEY_FILE,
            ProtocolOptions.TAX_REGISTRATION_ID,
            ProtocolOptions.PERIOD_START,
            ProtocolOptions.PERIOD_END,
            ProtocolOptions.OUTPUT);
  }
}
