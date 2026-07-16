package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Scalar and shape validation shared by caller-authored bookkeeping entries. */
final class BookkeepingEntryScalarValidationSupport {
  private BookkeepingEntryScalarValidationSupport() {}

  static LocalDate requireEffectiveDate(LocalDate effectiveDate) {
    return Objects.requireNonNull(effectiveDate, "effectiveDate");
  }

  static AccountCode requireAccountCode(AccountCode accountCode, String fieldName) {
    return Objects.requireNonNull(accountCode, fieldName);
  }

  static MonetaryAmount requirePositiveAmount(MonetaryAmount amount, String fieldName) {
    Objects.requireNonNull(amount, fieldName);
    if (!amount.toMoney().isPositive()) {
      throw new IllegalArgumentException(fieldName + " must carry one positive amount.");
    }
    return amount;
  }

  static MonetaryAmount requireNonNegativeAmount(MonetaryAmount amount, String fieldName) {
    return Objects.requireNonNull(amount, fieldName);
  }

  static QuantityText requirePositiveQuantityText(QuantityText quantity, String fieldName) {
    Objects.requireNonNull(quantity, fieldName);
    if (quantity.isZero()) {
      throw new IllegalArgumentException(fieldName + " must carry one positive quantity.");
    }
    return quantity;
  }

  static @Nullable SettlementAdjunct requireOptionalSettlementAdjunct(
      @Nullable SettlementAdjunct settlementAdjunct,
      MonetaryAmount settlementAmount,
      String fieldName) {
    Objects.requireNonNull(settlementAmount, "settlementAmount");
    if (settlementAdjunct == null) {
      return null;
    }
    if (!settlementAmount.currencyCode().equals(settlementAdjunct.amount().currencyCode())) {
      throw new IllegalArgumentException(
          fieldName + ".amount currencyCode must match the entry amount currencyCode.");
    }
    if (settlementAdjunct.amount().toMoney().minorUnits()
        >= settlementAmount.toMoney().minorUnits()) {
      throw new IllegalArgumentException(
          fieldName
              + ".amount must be smaller than the settlement amount so one cash line remains.");
    }
    return settlementAdjunct;
  }

  static List<BookkeepingEntry.OpeningPosition.OpeningAccountBalance> requireOpeningBalances(
      List<BookkeepingEntry.OpeningPosition.OpeningAccountBalance> balances) {
    List<BookkeepingEntry.OpeningPosition.OpeningAccountBalance> requiredBalances =
        List.copyOf(Objects.requireNonNull(balances, "balances"));
    if (requiredBalances.isEmpty()) {
      throw new IllegalArgumentException("Opening position requires at least one opening balance.");
    }
    return requiredBalances;
  }

  static void requireOpeningAccountBalance(
      AccountCode accountCode, JournalLine.EntrySide side, MonetaryAmount amount) {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(side, "side");
    requirePositiveAmount(amount, "amount");
  }
}
