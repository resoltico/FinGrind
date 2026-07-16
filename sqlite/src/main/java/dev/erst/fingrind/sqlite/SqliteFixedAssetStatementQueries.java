package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.FixedAssetDepreciationSchedule;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.executor.bookkeeping.FixedAssetRecord;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Shared SQLite read mapping for durable fixed-asset lifecycle state. */
final class SqliteFixedAssetStatementQueries {
  private static final String LIST_CURRENT =
      """
      select asset.fixed_asset_id, asset.capitalized_on, asset.asset_account_code,
             asset.accumulated_depreciation_account_code, asset.depreciation_expense_account_code,
             asset.disposal_gain_account_code, asset.disposal_loss_account_code, asset.currency_code,
             asset.cost_minor, asset.residual_value_minor, asset.in_service_date, asset.useful_life_months,
             coalesce(sum(case when application.application_kind = 'DEPRECIATION' then application.amount_minor else 0 end), 0),
             coalesce(sum(case when application.application_kind = 'DEPRECIATION' then 1 else 0 end), 0),
             max(application.effective_date),
             max(case when application.application_kind = 'DISPOSAL' then application.effective_date end)
      from fixed_asset asset
      left join fixed_asset_application application
          on application.fixed_asset_id = asset.fixed_asset_id
          and not exists (
              select 1
              from fixed_asset_application_reversal reversal
              where reversal.application_posting_id = application.application_posting_id
          )
      where not exists (select 1 from fixed_asset_reversal reversal where reversal.fixed_asset_id = asset.fixed_asset_id)
      group by asset.fixed_asset_id
      order by asset.capitalized_on, asset.fixed_asset_id
      """;
  private static final String LIST_AS_OF =
      """
      select asset.fixed_asset_id, asset.capitalized_on, asset.asset_account_code,
             asset.accumulated_depreciation_account_code, asset.depreciation_expense_account_code,
             asset.disposal_gain_account_code, asset.disposal_loss_account_code, asset.currency_code,
             asset.cost_minor, asset.residual_value_minor, asset.in_service_date, asset.useful_life_months,
             coalesce(sum(case when application.application_kind = 'DEPRECIATION' then application.amount_minor else 0 end), 0),
             coalesce(sum(case when application.application_kind = 'DEPRECIATION' then 1 else 0 end), 0),
             max(application.effective_date),
             max(case when application.application_kind = 'DISPOSAL' then application.effective_date end)
      from fixed_asset asset
      left join fixed_asset_application application
          on application.fixed_asset_id = asset.fixed_asset_id
          and application.effective_date <= ?
          and not exists (
              select 1
              from fixed_asset_application_reversal reversal
              inner join posting_fact reversal_posting
                  on reversal_posting.posting_id = reversal.reversal_posting_id
              where reversal.application_posting_id = application.application_posting_id
                and reversal_posting.effective_date <= ?
          )
      where asset.capitalized_on <= ?
        and not exists (
            select 1
            from fixed_asset_reversal reversal
            inner join posting_fact reversal_posting
                on reversal_posting.posting_id = reversal.reversal_posting_id
            where reversal.fixed_asset_id = asset.fixed_asset_id
              and reversal_posting.effective_date <= ?
        )
      group by asset.fixed_asset_id
      order by asset.capitalized_on, asset.fixed_asset_id
      """;

  private SqliteFixedAssetStatementQueries() {}

  static List<FixedAssetRecord> load(
      SqliteNativeDatabase database, Optional<LocalDate> effectiveDateAsOf) {
    String sql = effectiveDateAsOf.isPresent() ? LIST_AS_OF : LIST_CURRENT;
    return SqliteStatementQueries.queryWithStatement(
        database,
        sql,
        statement -> {
          if (effectiveDateAsOf.isPresent()) {
            String asOf = CanonicalTemporalText.formatLocalDate(effectiveDateAsOf.orElseThrow());
            statement.bindText(1, asOf);
            statement.bindText(2, asOf);
            statement.bindText(3, asOf);
            statement.bindText(4, asOf);
          }
          List<FixedAssetRecord> assets = new ArrayList<>();
          while (statement.step() == SqliteNativeResultCode.code("ROW")) {
            assets.add(map(statement));
          }
          return List.copyOf(assets);
        });
  }

  static boolean exists(SqliteNativeDatabase database, FixedAssetId fixedAssetId) {
    return SqliteStatementQueries.queryWithStatement(
        database,
        "select 1 from fixed_asset where fixed_asset_id = ?",
        statement -> {
          statement.bindText(1, fixedAssetId.value());
          return statement.step() == SqliteNativeResultCode.code("ROW");
        });
  }

  private static FixedAssetRecord map(SqliteNativeStatement s) {
    CurrencyUnit currency = CurrencyUnit.of(SqlitePostingMapper.requiredText(s, 7));
    return new FixedAssetRecord(
        new FixedAssetId(SqlitePostingMapper.requiredText(s, 0)),
        date(s, 1, "fixedAsset.capitalizedOn"),
        new AccountCode(SqlitePostingMapper.requiredText(s, 2)),
        new AccountCode(SqlitePostingMapper.requiredText(s, 3)),
        new AccountCode(SqlitePostingMapper.requiredText(s, 4)),
        new AccountCode(SqlitePostingMapper.requiredText(s, 5)),
        new AccountCode(SqlitePostingMapper.requiredText(s, 6)),
        Money.ofMinorUnits(currency, s.columnLong(8)),
        new FixedAssetDepreciationSchedule(
            date(s, 10, "fixedAsset.inServiceDate"),
            (int) s.columnLong(11),
            dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
                Money.ofMinorUnits(currency, s.columnLong(9)))),
        Money.ofMinorUnits(currency, s.columnLong(12)),
        (int) s.columnLong(13),
        Optional.ofNullable(s.columnText(14))
            .map(
                value ->
                    CanonicalTemporalText.parseLocalDate(
                        value, "fixedAsset.latestLifecycleEffectiveDate")),
        Optional.ofNullable(s.columnText(15))
            .map(value -> CanonicalTemporalText.parseLocalDate(value, "fixedAsset.disposedOn")));
  }

  private static LocalDate date(SqliteNativeStatement statement, int index, String field) {
    return CanonicalTemporalText.parseLocalDate(
        SqlitePostingMapper.requiredText(statement, index), field);
  }
}
