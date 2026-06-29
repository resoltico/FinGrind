package dev.erst.fingrind.sqlite;

/** Column indexes for SQLite posting, account, source-document, approval, and report rows. */
final class SqlitePostingColumnIndexes {
  static final int COL_POSTING_ID = 0;
  static final int COL_POSTING_KIND = 1;
  static final int COL_POSTING_ORIGIN_KIND = 2;
  static final int COL_ENTRY_CASH_ACCOUNT_CODE = 3;
  static final int COL_ENTRY_REVENUE_ACCOUNT_CODE = 4;
  static final int COL_ENTRY_EXPENSE_ACCOUNT_CODE = 5;
  static final int COL_ENTRY_EQUITY_ACCOUNT_CODE = 6;
  static final int COL_ENTRY_AMOUNT_CURRENCY_CODE = 7;
  static final int COL_ENTRY_AMOUNT_MINOR = 8;
  static final int COL_EFFECTIVE_DATE = 9;
  static final int COL_RECORDED_AT = 10;
  static final int COL_ACTOR_ID = 11;
  static final int COL_ACTOR_TYPE = 12;
  static final int COL_COMMAND_ID = 13;
  static final int COL_IDEMPOTENCY_KEY = 14;
  static final int COL_CAUSATION_ID = 15;
  static final int COL_CORRELATION_ID = 16;
  static final int COL_REASON = 17;
  static final int COL_SOURCE_CHANNEL = 18;
  static final int COL_PRIOR_POSTING_ID = 19;
  static final int COL_REQUEST_FINGERPRINT_VERSION = 20;
  static final int COL_REQUEST_FINGERPRINT_SHA256 = 21;

  static final int COL_LINE_ACCOUNT_CODE = 0;
  static final int COL_LINE_ENTRY_SIDE = 1;
  static final int COL_LINE_CURRENCY_CODE = 2;
  static final int COL_LINE_AMOUNT_MINOR = 3;

  static final int COL_SOURCE_DOCUMENT_ID = 0;
  static final int COL_SOURCE_DOCUMENT_TYPE = 1;
  static final int COL_SOURCE_DOCUMENT_DATE = 2;

  static final int COL_APPROVAL_ID = 0;
  static final int COL_APPROVAL_TYPE = 1;
  static final int COL_APPROVER_ID = 2;
  static final int COL_APPROVER_TYPE = 3;
  static final int COL_APPROVAL_DECISION = 4;
  static final int COL_APPROVED_AT = 5;

  static final int COL_ACCOUNT_CODE = 0;
  static final int COL_ACCOUNT_NAME = 1;
  static final int COL_ACCOUNT_TYPE = 2;
  static final int COL_ACCOUNT_NODE_KIND = 3;
  static final int COL_ACCOUNT_PARENT_ACCOUNT_CODE = 4;
  static final int COL_ACCOUNT_FINANCIAL_POSITION_LINE_CLASSIFICATION = 5;
  static final int COL_ACCOUNT_CASH_FLOW_ASSET_CLASSIFICATION = 6;
  static final int COL_ACCOUNT_PROFIT_AND_LOSS_LINE_CLASSIFICATION = 7;
  static final int COL_ACCOUNT_ACTIVE = 8;
  static final int COL_ACCOUNT_DECLARED_AT = 9;
  static final int COL_REPORT_POSTING_ID = 10;
  static final int COL_REPORT_ENTRY_SIDE = 11;
  static final int COL_REPORT_CURRENCY_CODE = 12;
  static final int COL_REPORT_AMOUNT_MINOR = 13;
  static final int COL_TOTAL_CURRENCY_CODE = 10;
  static final int COL_TOTAL_DEBIT_MINOR = 11;
  static final int COL_TOTAL_CREDIT_MINOR = 12;

  private SqlitePostingColumnIndexes() {}
}
