package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;

/** Canonical CSV export-family tokens shared across CLI query and report surfaces. */
final class CliCsvExportFamilies {
  static final String ACCOUNTS = "accounts";
  static final String TAX_REGISTRATIONS = OperationId.LIST_TAX_REGISTRATIONS.wireName();
  static final String ACCOUNT_BALANCE = OperationId.ACCOUNT_BALANCE.wireName();
  static final String POSTINGS = "postings";
  static final String TAX_OBLIGATION = OperationId.TAX_OBLIGATION.wireName();
  static final String ACCOUNT_LEDGER = OperationId.ACCOUNT_LEDGER.wireName();
  static final String TRIAL_BALANCE = OperationId.TRIAL_BALANCE.wireName();
  static final String PERIOD_SUMMARY = OperationId.PERIOD_SUMMARY.wireName();
  static final String FINANCIAL_POSITION = OperationId.FINANCIAL_POSITION.wireName();
  static final String INCOME_STATEMENT = OperationId.INCOME_STATEMENT.wireName();
  static final String INVENTORY_VALUATION = OperationId.INVENTORY_VALUATION.wireName();
  static final String CASH_FLOW_STATEMENT = OperationId.CASH_FLOW_STATEMENT.wireName();
  static final String CHANGES_IN_EQUITY = OperationId.CHANGES_IN_EQUITY.wireName();

  private CliCsvExportFamilies() {}
}
