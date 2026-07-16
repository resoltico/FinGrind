package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetDepreciationSchedule;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFixedAssetDepreciation;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFixedAssetDisposal;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingOriginKind;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Rebuilds fixed-asset caller and executor facts from dedicated durable lifecycle tables. */
final class SqliteFixedAssetOriginatingEntryMapper {
  private SqliteFixedAssetOriginatingEntryMapper() {}

  static @Nullable BookkeepingEntry originatingEntry(
      SqliteNativeDatabase database,
      PostingId postingId,
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingOriginKind postingOriginKind) {
    return switch (postingOriginKind) {
      case FIXED_ASSET_CAPITALIZATION ->
          capitalization(database, postingId, postingRow, journalEntry);
      case FIXED_ASSET_DEPRECIATION -> depreciation(database, postingId, journalEntry);
      case FIXED_ASSET_DISPOSAL -> disposal(database, postingId, postingRow, journalEntry);
      default -> null;
    };
  }

  private static BookkeepingEntry capitalization(
      SqliteNativeDatabase database,
      PostingId postingId,
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry) {
    FixedAssetOrigin origin =
        findOrigin(database, postingId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Fixed-asset capitalization posting has no durable asset aggregate."));
    MonetaryAmount cost =
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryAmount(postingRow);
    return new FixedAssetBookkeepingEntryVariants.Capitalization(
        journalEntry.effectiveDate(),
        origin.fixedAssetId(),
        origin.assetAccountCode(),
        origin.accumulatedDepreciationAccountCode(),
        origin.depreciationExpenseAccountCode(),
        origin.disposalGainAccountCode(),
        origin.disposalLossAccountCode(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        cost,
        origin.depreciationSchedule());
  }

  private static BookkeepingEntry depreciation(
      SqliteNativeDatabase database, PostingId postingId, JournalEntry journalEntry) {
    FixedAssetApplication application =
        findApplication(database, postingId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Fixed-asset depreciation posting has no durable lifecycle application."));
    if (!"DEPRECIATION".equals(application.kind())) {
      throw new IllegalStateException(
          "Fixed-asset depreciation posting has the wrong application kind.");
    }
    return new FixedAssetBookkeepingEntryVariants.Depreciation(
        journalEntry.effectiveDate(),
        application.fixedAssetId(),
        new ResolvedFixedAssetDepreciation(
            application.depreciationExpenseAccountCode(),
            application.accumulatedDepreciationAccountCode(),
            MonetaryAmount.of(application.amount())));
  }

  private static BookkeepingEntry disposal(
      SqliteNativeDatabase database,
      PostingId postingId,
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry) {
    FixedAssetApplication application =
        findApplication(database, postingId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Fixed-asset disposal posting has no durable lifecycle application."));
    if (!"DISPOSAL".equals(application.kind())) {
      throw new IllegalStateException(
          "Fixed-asset disposal posting has the wrong application kind.");
    }
    MonetaryAmount proceeds =
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryAmount(postingRow);
    Money carryingAmount = application.amount();
    Money accumulatedDepreciation = application.cost().minus(carryingAmount);
    long difference = Math.abs(proceeds.toMoney().minorUnits() - carryingAmount.minorUnits());
    boolean gain = proceeds.toMoney().compareTo(carryingAmount) >= 0;
    Money gainOrLossAmount = Money.ofMinorUnits(application.cost().currencyUnit(), difference);
    return new FixedAssetBookkeepingEntryVariants.Disposal(
        journalEntry.effectiveDate(),
        application.fixedAssetId(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        proceeds,
        new ResolvedFixedAssetDisposal(
            application.assetAccountCode(),
            application.accumulatedDepreciationAccountCode(),
            gain ? application.disposalGainAccountCode() : application.disposalLossAccountCode(),
            MonetaryAmount.of(application.cost()),
            MonetaryAmount.of(accumulatedDepreciation),
            MonetaryAmount.of(carryingAmount),
            MonetaryAmount.of(gainOrLossAmount),
            gain));
  }

  private static Optional<FixedAssetOrigin> findOrigin(
      SqliteNativeDatabase database, PostingId postingId) {
    return SqliteStatementQueries.queryWithStatement(
        database,
        """
        select fixed_asset_id, asset_account_code, accumulated_depreciation_account_code,
               depreciation_expense_account_code, disposal_gain_account_code,
               disposal_loss_account_code, currency_code, cost_minor, residual_value_minor,
               in_service_date, useful_life_months
        from fixed_asset
        where origin_posting_id = ?
        """,
        statement -> {
          statement.bindText(1, postingId.value());
          if (statement.step() != SqliteNativeResultCode.code("ROW")) {
            return Optional.empty();
          }
          return Optional.of(origin(statement));
        });
  }

  private static Optional<FixedAssetApplication> findApplication(
      SqliteNativeDatabase database, PostingId postingId) {
    return SqliteStatementQueries.queryWithStatement(
        database,
        """
        select application.application_kind, application.fixed_asset_id, application.currency_code,
               application.amount_minor, asset.asset_account_code,
               asset.accumulated_depreciation_account_code, asset.depreciation_expense_account_code,
               asset.disposal_gain_account_code, asset.disposal_loss_account_code, asset.cost_minor
        from fixed_asset_application application
        inner join fixed_asset asset on asset.fixed_asset_id = application.fixed_asset_id
        where application.application_posting_id = ?
        """,
        statement -> {
          statement.bindText(1, postingId.value());
          if (statement.step() != SqliteNativeResultCode.code("ROW")) {
            return Optional.empty();
          }
          CurrencyUnit currency = CurrencyUnit.of(SqlitePostingMapper.requiredText(statement, 2));
          return Optional.of(
              new FixedAssetApplication(
                  SqlitePostingMapper.requiredText(statement, 0),
                  new FixedAssetId(SqlitePostingMapper.requiredText(statement, 1)),
                  Money.ofMinorUnits(currency, statement.columnLong(3)),
                  new AccountCode(SqlitePostingMapper.requiredText(statement, 4)),
                  new AccountCode(SqlitePostingMapper.requiredText(statement, 5)),
                  new AccountCode(SqlitePostingMapper.requiredText(statement, 6)),
                  new AccountCode(SqlitePostingMapper.requiredText(statement, 7)),
                  new AccountCode(SqlitePostingMapper.requiredText(statement, 8)),
                  Money.ofMinorUnits(currency, statement.columnLong(9))));
        });
  }

  private static FixedAssetOrigin origin(SqliteNativeStatement statement) {
    CurrencyUnit currency = CurrencyUnit.of(SqlitePostingMapper.requiredText(statement, 6));
    return new FixedAssetOrigin(
        new FixedAssetId(SqlitePostingMapper.requiredText(statement, 0)),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 1)),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 2)),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 3)),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 4)),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 5)),
        new FixedAssetDepreciationSchedule(
            CanonicalTemporalText.parseLocalDate(
                SqlitePostingMapper.requiredText(statement, 9), "fixedAsset.inServiceDate"),
            (int) statement.columnLong(10),
            MonetaryAmount.of(Money.ofMinorUnits(currency, statement.columnLong(8)))));
  }

  private record FixedAssetOrigin(
      FixedAssetId fixedAssetId,
      AccountCode assetAccountCode,
      AccountCode accumulatedDepreciationAccountCode,
      AccountCode depreciationExpenseAccountCode,
      AccountCode disposalGainAccountCode,
      AccountCode disposalLossAccountCode,
      FixedAssetDepreciationSchedule depreciationSchedule) {}

  private record FixedAssetApplication(
      String kind,
      FixedAssetId fixedAssetId,
      Money amount,
      AccountCode assetAccountCode,
      AccountCode accumulatedDepreciationAccountCode,
      AccountCode depreciationExpenseAccountCode,
      AccountCode disposalGainAccountCode,
      AccountCode disposalLossAccountCode,
      Money cost) {}
}
