package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.QuantityText;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import org.jspecify.annotations.Nullable;

/** Shared scalar reconstruction for persisted originating-entry facts. */
final class SqlitePostingOriginatingEntryMappingSupport {
  private SqlitePostingOriginatingEntryMappingSupport() {}

  static AccountCode requiredPrimaryDebitAccountCode(SqliteNativeStatement postingRow) {
    return new AccountCode(
        SqlitePostingMapper.requiredText(
            postingRow, SqlitePostingColumnIndexes.COL_ENTRY_PRIMARY_DEBIT_ACCOUNT_CODE));
  }

  static AccountCode requiredPrimaryCreditAccountCode(SqliteNativeStatement postingRow) {
    return new AccountCode(
        SqlitePostingMapper.requiredText(
            postingRow, SqlitePostingColumnIndexes.COL_ENTRY_PRIMARY_CREDIT_ACCOUNT_CODE));
  }

  static @Nullable TaxSelection taxSelection(@Nullable AppliedTax appliedTax) {
    return appliedTax == null
        ? null
        : new TaxSelection(appliedTax.taxRegistrationId(), appliedTax.taxCode());
  }

  static MonetaryAmount requiredEntryAmount(SqliteNativeStatement postingRow) {
    return MonetaryAmount.of(
        Money.ofMinorUnits(
            CurrencyUnit.of(
                SqlitePostingMapper.requiredText(
                    postingRow, SqlitePostingColumnIndexes.COL_ENTRY_AMOUNT_CURRENCY_CODE)),
            postingRow.columnLong(SqlitePostingColumnIndexes.COL_ENTRY_AMOUNT_MINOR)));
  }

  static QuantityText requiredEntryQuantity(SqliteNativeStatement postingRow) {
    return new QuantityText(
        SqlitePostingMapper.requiredText(
            postingRow, SqlitePostingColumnIndexes.COL_ENTRY_QUANTITY));
  }

  static MonetaryAmount requiredEntryUnitCost(SqliteNativeStatement postingRow) {
    return MonetaryAmount.of(
        Money.ofMinorUnits(
            CurrencyUnit.of(
                SqlitePostingMapper.requiredText(
                    postingRow, SqlitePostingColumnIndexes.COL_ENTRY_UNIT_COST_CURRENCY_CODE)),
            postingRow.columnLong(SqlitePostingColumnIndexes.COL_ENTRY_UNIT_COST_MINOR)));
  }

  static @Nullable SettlementAdjunct settlementAdjunct(SqliteNativeStatement postingRow) {
    String accountCode =
        postingRow.columnText(SqlitePostingColumnIndexes.COL_ENTRY_ADJUNCT_ACCOUNT_CODE);
    if (accountCode == null) {
      return null;
    }
    return new SettlementAdjunct(
        new AccountCode(accountCode),
        MonetaryAmount.of(
            Money.ofMinorUnits(
                CurrencyUnit.of(
                    SqlitePostingMapper.requiredText(
                        postingRow, SqlitePostingColumnIndexes.COL_ENTRY_AMOUNT_CURRENCY_CODE)),
                postingRow.columnLong(SqlitePostingColumnIndexes.COL_ENTRY_ADJUNCT_AMOUNT_MINOR))));
  }
}
