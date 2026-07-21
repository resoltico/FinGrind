package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.BookFormatContract;

/** Canonical SQLite book-format facts shared across SQLite-backed session adapters. */
final class SqliteBookContract {
  static final int APPLICATION_ID = BookFormatContract.APPLICATION_ID;
  static final int FORMAT_VERSION = BookFormatContract.FORMAT_VERSION;
  static final String NOT_INITIALIZED_BOOK_MESSAGE =
      "The selected SQLite file is not initialized as a FinGrind book.";

  static final String ACCOUNT_TABLE = "account";
  static final String ACCRUAL_CUTOFF_APPLICATION_TABLE = "accrual_cutoff_application";
  static final String ACCRUAL_CUTOFF_TABLE = "accrual_cutoff";
  static final String AUDIT_EVENT_TABLE = "audit_event";
  static final String ATTESTATION_OPERATION_TABLE = "attestation_operation";
  static final String BOOK_IDENTITY_TABLE = "book_identity";
  static final String BOOK_META_TABLE = "book_meta";
  static final String JOURNAL_LINE_TABLE = "journal_line";
  static final String LATVIAN_PAYROLL_RUN_TABLE = "latvian_payroll_run";
  static final String LATVIAN_PAYROLL_RUN_REVERSAL_TABLE = "latvian_payroll_run_reversal";
  static final String FISCAL_YEAR_CLOSE_POSTING_TABLE = "fiscal_year_close_posting";
  static final String FISCAL_YEAR_CLOSE_TABLE = "fiscal_year_close";
  static final String FIXED_ASSET_TABLE = "fixed_asset";
  static final String FIXED_ASSET_APPLICATION_TABLE = "fixed_asset_application";
  static final String FIXED_ASSET_REVERSAL_TABLE = "fixed_asset_reversal";
  static final String FIXED_ASSET_APPLICATION_REVERSAL_TABLE = "fixed_asset_application_reversal";
  static final String FINANCING_ARRANGEMENT_TABLE = "financing_arrangement";
  static final String FINANCING_APPLICATION_TABLE = "financing_application";
  static final String FOREIGN_CURRENCY_OBLIGATION_TABLE = "foreign_currency_obligation";
  static final String FOREIGN_CURRENCY_OBLIGATION_SETTLEMENT_TABLE =
      "foreign_currency_obligation_settlement";
  static final String INVENTORY_MOVEMENT_TABLE = "inventory_movement";
  static final String INVENTORY_ON_HAND_TABLE = "inventory_on_hand";
  static final String INTERIM_RESULT_SWEEP_POSTING_TABLE = "interim_result_sweep_posting";
  static final String INTERIM_RESULT_SWEEP_TABLE = "interim_result_sweep";
  static final String INTERIM_RESULT_SWEEP_TOTAL_TABLE = "interim_result_sweep_total";
  static final String POSTING_FACT_TABLE = "posting_fact";
  static final String POSTING_APPLIED_TAX_TABLE = "posting_applied_tax";
  static final String POSTING_FOREIGN_EXCHANGE_TABLE = "posting_foreign_exchange";
  static final String TAX_REGISTRATION_TABLE = "tax_registration";
  static final String TAX_REGISTRATION_CODE_TABLE = "tax_registration_code";

  static final SqliteBookStateReader BOOK_STATE_READER =
      new SqliteBookStateReader(
          APPLICATION_ID,
          FORMAT_VERSION,
          java.util.List.of(
              BOOK_META_TABLE,
              BOOK_IDENTITY_TABLE,
              ATTESTATION_OPERATION_TABLE,
              ACCOUNT_TABLE,
              ACCRUAL_CUTOFF_TABLE,
              ACCRUAL_CUTOFF_APPLICATION_TABLE,
              FIXED_ASSET_TABLE,
              FIXED_ASSET_APPLICATION_TABLE,
              FIXED_ASSET_REVERSAL_TABLE,
              FIXED_ASSET_APPLICATION_REVERSAL_TABLE,
              FINANCING_ARRANGEMENT_TABLE,
              FINANCING_APPLICATION_TABLE,
              FOREIGN_CURRENCY_OBLIGATION_TABLE,
              FOREIGN_CURRENCY_OBLIGATION_SETTLEMENT_TABLE,
              INVENTORY_MOVEMENT_TABLE,
              INVENTORY_ON_HAND_TABLE,
              TAX_REGISTRATION_TABLE,
              TAX_REGISTRATION_CODE_TABLE,
              LATVIAN_PAYROLL_RUN_TABLE,
              LATVIAN_PAYROLL_RUN_REVERSAL_TABLE,
              POSTING_FACT_TABLE,
              POSTING_APPLIED_TAX_TABLE,
              POSTING_FOREIGN_EXCHANGE_TABLE,
              JOURNAL_LINE_TABLE,
              INTERIM_RESULT_SWEEP_TABLE,
              INTERIM_RESULT_SWEEP_TOTAL_TABLE,
              INTERIM_RESULT_SWEEP_POSTING_TABLE,
              FISCAL_YEAR_CLOSE_TABLE,
              FISCAL_YEAR_CLOSE_POSTING_TABLE,
              AUDIT_EVENT_TABLE));

  private SqliteBookContract() {}
}
