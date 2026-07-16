package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;

/** Persists financing aggregate origins, applications, and reversal compensation. */
final class SqliteFinancingContextWriter {
  private SqliteFinancingContextWriter() {}

  static void persist(
      SqliteNativeDatabase database, CommittedPosting posting, BookkeepingEntry resolvedEntry) {
    switch (resolvedEntry) {
      case FinancingBookkeepingEntryVariants.Borrowing value ->
          insertArrangement(database, posting, value);
      case FinancingBookkeepingEntryVariants.PrincipalRepayment value ->
          insertApplication(
              database,
              posting,
              value.financingArrangementId().value(),
              "PRINCIPAL_REPAYMENT",
              value.principalAmount());
      case FinancingBookkeepingEntryVariants.InterestAccrual value ->
          insertApplication(
              database,
              posting,
              value.financingArrangementId().value(),
              "INTEREST_ACCRUAL",
              value.interestAmount());
      case FinancingBookkeepingEntryVariants.InterestPayment value ->
          insertApplication(
              database,
              posting,
              value.financingArrangementId().value(),
              "INTEREST_PAYMENT",
              value.interestAmount());
      default -> {}
    }
  }

  private static void insertArrangement(
      SqliteNativeDatabase database,
      CommittedPosting posting,
      FinancingBookkeepingEntryVariants.Borrowing entry) {
    try (var statement =
        database.prepare(
            "insert into financing_arrangement (financing_arrangement_id, origin_posting_id, originated_on, principal_liability_account_code, interest_payable_account_code, currency_code, original_principal_minor) values (?, ?, ?, ?, ?, ?, ?)")) {
      statement.bindText(1, entry.financingArrangementId().value());
      statement.bindText(2, posting.postingId().value());
      statement.bindText(3, CanonicalTemporalText.formatLocalDate(entry.effectiveDate()));
      statement.bindText(4, entry.principalLiabilityAccountCode().value());
      statement.bindText(5, entry.interestPayableAccountCode().value());
      statement.bindText(6, entry.principalAmount().currencyCode());
      statement.bindLong(7, entry.principalAmount().toMoney().minorUnits());
      statement.step();
    }
  }

  private static void insertApplication(
      SqliteNativeDatabase database,
      CommittedPosting posting,
      String financingArrangementId,
      String applicationKind,
      MonetaryAmount amount) {
    try (var statement =
        database.prepare(
            "insert into financing_application (application_posting_id, financing_arrangement_id, application_kind, effective_date, currency_code, amount_minor) values (?, ?, ?, ?, ?, ?)")) {
      statement.bindText(1, posting.postingId().value());
      statement.bindText(2, financingArrangementId);
      statement.bindText(3, applicationKind);
      statement.bindText(
          4, CanonicalTemporalText.formatLocalDate(posting.journalEntry().effectiveDate()));
      statement.bindText(5, amount.currencyCode());
      statement.bindLong(6, amount.toMoney().minorUnits());
      statement.step();
    }
  }
}
