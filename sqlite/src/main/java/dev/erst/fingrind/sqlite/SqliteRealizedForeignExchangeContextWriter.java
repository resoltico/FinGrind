package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;

/** Persists foreign-currency obligations, settlements, and reversal compensation. */
final class SqliteRealizedForeignExchangeContextWriter {
  private SqliteRealizedForeignExchangeContextWriter() {}

  static void persist(
      SqliteNativeDatabase database, CommittedPosting posting, BookkeepingEntry resolvedEntry) {
    switch (resolvedEntry) {
      case RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable value ->
          insertObligation(database, posting, value);
      case RealizedForeignExchangeBookkeepingEntryVariants.Settlement value ->
          insertSettlement(database, posting, value);
      default -> {}
    }
  }

  private static void insertObligation(
      SqliteNativeDatabase database,
      CommittedPosting posting,
      RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable entry) {
    var foreignExchangeDetails = entry.foreignExchangeDetails();
    try (var statement =
        database.prepare(
            "insert into foreign_currency_obligation (foreign_currency_obligation_id, origin_posting_id, originated_on, receivable_account_code, realized_gain_account_code, realized_loss_account_code, transaction_currency_code, transaction_amount_minor, functional_currency_code, functional_carrying_amount_minor) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
      statement.bindText(1, entry.foreignCurrencyObligationId().value());
      statement.bindText(2, posting.postingId().value());
      statement.bindText(3, CanonicalTemporalText.formatLocalDate(entry.effectiveDate()));
      statement.bindText(4, entry.receivableAccountCode().value());
      statement.bindText(5, entry.realizedGainAccountCode().value());
      statement.bindText(6, entry.realizedLossAccountCode().value());
      statement.bindText(7, foreignExchangeDetails.transactionAmount().currencyCode());
      statement.bindLong(8, foreignExchangeDetails.transactionAmount().toMoney().minorUnits());
      statement.bindText(9, foreignExchangeDetails.functionalAmount().currencyCode());
      statement.bindLong(10, foreignExchangeDetails.functionalAmount().toMoney().minorUnits());
      statement.step();
    }
  }

  private static void insertSettlement(
      SqliteNativeDatabase database,
      CommittedPosting posting,
      RealizedForeignExchangeBookkeepingEntryVariants.Settlement entry) {
    var foreignExchangeDetails = entry.foreignExchangeDetails();
    try (var statement =
        database.prepare(
            "insert into foreign_currency_obligation_settlement (settlement_posting_id, foreign_currency_obligation_id, effective_date, functional_currency_code, functional_settlement_amount_minor) values (?, ?, ?, ?, ?)")) {
      statement.bindText(1, posting.postingId().value());
      statement.bindText(2, entry.foreignCurrencyObligationId().value());
      statement.bindText(3, CanonicalTemporalText.formatLocalDate(entry.effectiveDate()));
      statement.bindText(4, foreignExchangeDetails.functionalAmount().currencyCode());
      statement.bindLong(5, foreignExchangeDetails.functionalAmount().toMoney().minorUnits());
      statement.step();
    }
  }
}
