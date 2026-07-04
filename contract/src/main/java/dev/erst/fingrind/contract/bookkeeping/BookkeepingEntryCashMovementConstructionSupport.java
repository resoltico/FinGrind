package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Package-private constructor normalization for settlement and owner-cash entry variants. */
final class BookkeepingEntryCashMovementConstructionSupport {
  record ReceiptState(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode receivableAccountCode,
      MonetaryAmount amount) {}

  record PaymentState(
      LocalDate effectiveDate,
      AccountCode payableAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount) {}

  record OwnerContributionState(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode equityAccountCode,
      MonetaryAmount amount) {}

  record OwnerWithdrawalState(
      LocalDate effectiveDate,
      AccountCode equityAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount) {}

  private BookkeepingEntryCashMovementConstructionSupport() {}

  static ReceiptState receipt(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode receivableAccountCode,
      MonetaryAmount amount,
      @Nullable SettlementAdjunct settlementAdjunct) {
    LocalDate requiredEffectiveDate =
        BookkeepingEntryValidationSupport.requireEffectiveDate(effectiveDate);
    AccountCode requiredCashAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(cashAccountCode, "cashAccountCode");
    AccountCode requiredReceivableAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(
            receivableAccountCode, "receivableAccountCode");
    MonetaryAmount requiredAmount =
        BookkeepingEntryValidationSupport.requirePositiveAmount(amount, "amount");
    BookkeepingEntryValidationSupport.requireOptionalSettlementAdjunct(
        settlementAdjunct, requiredAmount, "settlementAdjunct");
    return new ReceiptState(
        requiredEffectiveDate,
        requiredCashAccountCode,
        requiredReceivableAccountCode,
        requiredAmount);
  }

  static PaymentState payment(
      LocalDate effectiveDate,
      AccountCode payableAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount,
      @Nullable SettlementAdjunct settlementAdjunct) {
    LocalDate requiredEffectiveDate =
        BookkeepingEntryValidationSupport.requireEffectiveDate(effectiveDate);
    AccountCode requiredPayableAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(
            payableAccountCode, "payableAccountCode");
    AccountCode requiredCashAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(cashAccountCode, "cashAccountCode");
    MonetaryAmount requiredAmount =
        BookkeepingEntryValidationSupport.requirePositiveAmount(amount, "amount");
    BookkeepingEntryValidationSupport.requireOptionalSettlementAdjunct(
        settlementAdjunct, requiredAmount, "settlementAdjunct");
    return new PaymentState(
        requiredEffectiveDate, requiredPayableAccountCode, requiredCashAccountCode, requiredAmount);
  }

  static OwnerContributionState ownerContribution(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode equityAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    LocalDate requiredEffectiveDate =
        BookkeepingEntryValidationSupport.requireEffectiveDate(effectiveDate);
    AccountCode requiredCashAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(cashAccountCode, "cashAccountCode");
    AccountCode requiredEquityAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(
            equityAccountCode, "equityAccountCode");
    MonetaryAmount requiredAmount =
        BookkeepingEntryValidationSupport.requirePositiveAmount(amount, "amount");
    BookkeepingEntryValidationSupport.requireTypedEntryForeignExchange(
        requiredAmount,
        foreignExchangeDetails,
        BookkeepingEntryValidationSupport.ForeignExchangeAllowance.SPOT_SETTLEMENT_ONLY,
        "ownerContribution");
    return new OwnerContributionState(
        requiredEffectiveDate, requiredCashAccountCode, requiredEquityAccountCode, requiredAmount);
  }

  static OwnerWithdrawalState ownerWithdrawal(
      LocalDate effectiveDate,
      AccountCode equityAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    LocalDate requiredEffectiveDate =
        BookkeepingEntryValidationSupport.requireEffectiveDate(effectiveDate);
    AccountCode requiredEquityAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(
            equityAccountCode, "equityAccountCode");
    AccountCode requiredCashAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(cashAccountCode, "cashAccountCode");
    MonetaryAmount requiredAmount =
        BookkeepingEntryValidationSupport.requirePositiveAmount(amount, "amount");
    BookkeepingEntryValidationSupport.requireTypedEntryForeignExchange(
        requiredAmount,
        foreignExchangeDetails,
        BookkeepingEntryValidationSupport.ForeignExchangeAllowance.SPOT_SETTLEMENT_ONLY,
        "ownerWithdrawal");
    return new OwnerWithdrawalState(
        requiredEffectiveDate, requiredEquityAccountCode, requiredCashAccountCode, requiredAmount);
  }
}
