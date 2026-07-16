package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.util.Objects;

/** Persists fixed-asset aggregate origins, lifecycle applications, and reversal compensation. */
final class SqliteFixedAssetContextWriter {
  private SqliteFixedAssetContextWriter() {}

  static void persist(
      SqliteNativeDatabase database, CommittedPosting posting, BookkeepingEntry resolvedEntry) {
    switch (resolvedEntry) {
      case FixedAssetBookkeepingEntryVariants.Capitalization value ->
          insertAsset(database, posting, value);
      case FixedAssetBookkeepingEntryVariants.Depreciation value ->
          insertDepreciation(database, posting, value);
      case FixedAssetBookkeepingEntryVariants.Disposal value ->
          insertDisposal(database, posting, value);
      default -> {}
    }
  }

  private static void insertAsset(
      SqliteNativeDatabase database,
      CommittedPosting posting,
      FixedAssetBookkeepingEntryVariants.Capitalization entry) {
    try (var statement =
        database.prepare(
            "insert into fixed_asset (fixed_asset_id, origin_posting_id, capitalized_on, asset_account_code, accumulated_depreciation_account_code, depreciation_expense_account_code, disposal_gain_account_code, disposal_loss_account_code, currency_code, cost_minor, residual_value_minor, in_service_date, useful_life_months) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
      statement.bindText(1, entry.fixedAssetId().value());
      statement.bindText(2, posting.postingId().value());
      statement.bindText(3, CanonicalTemporalText.formatLocalDate(entry.effectiveDate()));
      statement.bindText(4, entry.assetAccountCode().value());
      statement.bindText(5, entry.accumulatedDepreciationAccountCode().value());
      statement.bindText(6, entry.depreciationExpenseAccountCode().value());
      statement.bindText(7, entry.disposalGainAccountCode().value());
      statement.bindText(8, entry.disposalLossAccountCode().value());
      statement.bindText(9, entry.cost().currencyCode());
      statement.bindLong(10, entry.cost().toMoney().minorUnits());
      statement.bindLong(11, entry.depreciationSchedule().residualValue().toMoney().minorUnits());
      statement.bindText(
          12, CanonicalTemporalText.formatLocalDate(entry.depreciationSchedule().inServiceDate()));
      statement.bindLong(13, entry.depreciationSchedule().usefulLifeMonths());
      statement.step();
    }
  }

  private static void insertDepreciation(
      SqliteNativeDatabase database,
      CommittedPosting posting,
      FixedAssetBookkeepingEntryVariants.Depreciation entry) {
    var resolved =
        Objects.requireNonNull(
            entry.resolvedDepreciation(), "fixed-asset depreciation requires executor resolution");
    insertApplication(
        database,
        posting,
        entry.fixedAssetId().value(),
        "DEPRECIATION",
        resolved.amount().toMoney().minorUnits(),
        resolved.amount().currencyCode());
  }

  private static void insertDisposal(
      SqliteNativeDatabase database,
      CommittedPosting posting,
      FixedAssetBookkeepingEntryVariants.Disposal entry) {
    var resolved =
        Objects.requireNonNull(
            entry.resolvedDisposal(), "fixed-asset disposal requires executor resolution");
    insertApplication(
        database,
        posting,
        entry.fixedAssetId().value(),
        "DISPOSAL",
        resolved.carryingAmount().toMoney().minorUnits(),
        resolved.carryingAmount().currencyCode());
  }

  private static void insertApplication(
      SqliteNativeDatabase database,
      CommittedPosting posting,
      String fixedAssetId,
      String applicationKind,
      long amountMinor,
      String currencyCode) {
    try (var statement =
        database.prepare(
            "insert into fixed_asset_application (application_posting_id, fixed_asset_id, application_kind, effective_date, currency_code, amount_minor) values (?, ?, ?, ?, ?, ?)")) {
      statement.bindText(1, posting.postingId().value());
      statement.bindText(2, fixedAssetId);
      statement.bindText(3, applicationKind);
      statement.bindText(
          4, CanonicalTemporalText.formatLocalDate(posting.journalEntry().effectiveDate()));
      statement.bindText(5, currencyCode);
      statement.bindLong(6, amountMinor);
      statement.step();
    }
  }
}
