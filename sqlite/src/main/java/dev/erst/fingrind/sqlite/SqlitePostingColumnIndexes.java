package dev.erst.fingrind.sqlite;

/** Column indexes for SQLite posting, account, source-document, approval, and report rows. */
final class SqlitePostingColumnIndexes {
  static final int COL_POSTING_ID = 0;
  static final int COL_POSTING_KIND = 1;
  static final int COL_POSTING_ORIGIN_KIND = 2;
  static final int COL_ENTRY_PRIMARY_DEBIT_ACCOUNT_CODE = 3;
  static final int COL_ENTRY_PRIMARY_CREDIT_ACCOUNT_CODE = 4;
  static final int COL_ENTRY_ADJUNCT_ACCOUNT_CODE = 5;
  static final int COL_ENTRY_AMOUNT_CURRENCY_CODE = 6;
  static final int COL_ENTRY_AMOUNT_MINOR = 7;
  static final int COL_ENTRY_ADJUNCT_AMOUNT_MINOR = 8;
  static final int COL_ENTRY_QUANTITY = 9;
  static final int COL_ENTRY_UNIT_COST_CURRENCY_CODE = 10;
  static final int COL_ENTRY_UNIT_COST_MINOR = 11;
  static final int COL_EFFECTIVE_DATE = 12;
  static final int COL_RECORDED_AT = 13;
  static final int COL_ACTOR_ID = 14;
  static final int COL_ACTOR_TYPE = 15;
  static final int COL_COMMAND_ID = 16;
  static final int COL_IDEMPOTENCY_KEY = 17;
  static final int COL_CAUSATION_ID = 18;
  static final int COL_CORRELATION_ID = 19;
  static final int COL_REASON = 20;
  static final int COL_SOURCE_CHANNEL = 21;
  static final int COL_PRIOR_POSTING_ID = 22;
  static final int COL_REQUEST_FINGERPRINT_VERSION = 23;
  static final int COL_REQUEST_FINGERPRINT_SHA256 = 24;

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
  static final int COL_ACCOUNT_UNIT_OF_MEASURE = 8;
  static final int COL_ACCOUNT_QUANTITY_SCALE = 9;
  static final int COL_ACCOUNT_ACTIVE = 10;
  static final int COL_ACCOUNT_DECLARED_AT = 11;
  static final int COL_REPORT_POSTING_ID = 12;
  static final int COL_REPORT_ENTRY_SIDE = 13;
  static final int COL_REPORT_CURRENCY_CODE = 14;
  static final int COL_REPORT_AMOUNT_MINOR = 15;
  static final int COL_TOTAL_CURRENCY_CODE = 12;
  static final int COL_TOTAL_DEBIT_MINOR = 13;
  static final int COL_TOTAL_CREDIT_MINOR = 14;

  private SqlitePostingColumnIndexes() {}
}
