package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.ResolvedRealizedForeignExchangeSettlement;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingOriginKind;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Rebuilds realized-FX caller and executor facts from durable obligation lifecycle tables. */
final class SqliteRealizedForeignExchangeOriginatingEntryMapper {
  private SqliteRealizedForeignExchangeOriginatingEntryMapper() {}

  static @Nullable BookkeepingEntry originatingEntry(
      SqliteNativeDatabase database,
      PostingId postingId,
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingOriginKind postingOriginKind,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return switch (postingOriginKind) {
      case FOREIGN_CURRENCY_OBLIGATION ->
          receivable(database, postingId, postingRow, journalEntry, foreignExchangeDetails);
      case REALIZED_FOREIGN_EXCHANGE_SETTLEMENT ->
          settlement(database, postingId, postingRow, journalEntry, foreignExchangeDetails);
      default -> null;
    };
  }

  private static BookkeepingEntry receivable(
      SqliteNativeDatabase database,
      PostingId postingId,
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    Obligation obligation =
        findObligationByOrigin(database, postingId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Foreign-currency receivable posting has no durable obligation aggregate."));
    return new RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable(
        journalEntry.effectiveDate(),
        obligation.id(),
        obligation.receivableAccountCode(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        obligation.realizedGainAccountCode(),
        obligation.realizedLossAccountCode(),
        requireForeignExchangeDetails(foreignExchangeDetails));
  }

  private static BookkeepingEntry settlement(
      SqliteNativeDatabase database,
      PostingId postingId,
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    Settlement settlement =
        findSettlement(database, postingId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Realized foreign-exchange settlement posting has no durable settlement aggregate."));
    ForeignExchangeDetails requiredForeignExchangeDetails =
        requireForeignExchangeDetails(foreignExchangeDetails);
    Money functionalSettlement = requiredForeignExchangeDetails.functionalAmount().toMoney();
    long difference =
        Math.abs(functionalSettlement.minorUnits() - settlement.carryingAmount().minorUnits());
    boolean gain = functionalSettlement.compareTo(settlement.carryingAmount()) >= 0;
    return new RealizedForeignExchangeBookkeepingEntryVariants.Settlement(
        journalEntry.effectiveDate(),
        settlement.id(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        requiredForeignExchangeDetails,
        new ResolvedRealizedForeignExchangeSettlement(
            settlement.receivableAccountCode(),
            gain ? settlement.realizedGainAccountCode() : settlement.realizedLossAccountCode(),
            MonetaryAmount.of(settlement.carryingAmount()),
            MonetaryAmount.of(
                Money.ofMinorUnits(settlement.carryingAmount().currencyUnit(), difference)),
            gain));
  }

  private static ForeignExchangeDetails requireForeignExchangeDetails(
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return java.util.Objects.requireNonNull(
        foreignExchangeDetails,
        "realized foreign-exchange posting requires retained foreign-exchange facts");
  }

  private static Optional<Obligation> findObligationByOrigin(
      SqliteNativeDatabase database, PostingId postingId) {
    return SqliteStatementQueries.queryWithStatement(
        database,
        """
        select foreign_currency_obligation_id, receivable_account_code,
               realized_gain_account_code, realized_loss_account_code
        from foreign_currency_obligation
        where origin_posting_id = ?
        """,
        statement -> {
          statement.bindText(1, postingId.value());
          if (statement.step() != SqliteNativeResultCode.code("ROW")) {
            return Optional.empty();
          }
          return Optional.of(obligation(statement));
        });
  }

  private static Optional<Settlement> findSettlement(
      SqliteNativeDatabase database, PostingId postingId) {
    return SqliteStatementQueries.queryWithStatement(
        database,
        """
        select settlement.foreign_currency_obligation_id, obligation.receivable_account_code,
               obligation.realized_gain_account_code, obligation.realized_loss_account_code,
               obligation.functional_currency_code, obligation.functional_carrying_amount_minor
        from foreign_currency_obligation_settlement settlement
        inner join foreign_currency_obligation obligation
            on obligation.foreign_currency_obligation_id = settlement.foreign_currency_obligation_id
        where settlement.settlement_posting_id = ?
        """,
        statement -> {
          statement.bindText(1, postingId.value());
          if (statement.step() != SqliteNativeResultCode.code("ROW")) {
            return Optional.empty();
          }
          CurrencyUnit currency = CurrencyUnit.of(SqlitePostingMapper.requiredText(statement, 4));
          return Optional.of(
              new Settlement(
                  new ForeignCurrencyObligationId(SqlitePostingMapper.requiredText(statement, 0)),
                  new AccountCode(SqlitePostingMapper.requiredText(statement, 1)),
                  new AccountCode(SqlitePostingMapper.requiredText(statement, 2)),
                  new AccountCode(SqlitePostingMapper.requiredText(statement, 3)),
                  Money.ofMinorUnits(currency, statement.columnLong(5))));
        });
  }

  private static Obligation obligation(SqliteNativeStatement statement) {
    return new Obligation(
        new ForeignCurrencyObligationId(SqlitePostingMapper.requiredText(statement, 0)),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 1)),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 2)),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 3)));
  }

  private record Obligation(
      ForeignCurrencyObligationId id,
      AccountCode receivableAccountCode,
      AccountCode realizedGainAccountCode,
      AccountCode realizedLossAccountCode) {}

  private record Settlement(
      ForeignCurrencyObligationId id,
      AccountCode receivableAccountCode,
      AccountCode realizedGainAccountCode,
      AccountCode realizedLossAccountCode,
      Money carryingAmount) {}
}
