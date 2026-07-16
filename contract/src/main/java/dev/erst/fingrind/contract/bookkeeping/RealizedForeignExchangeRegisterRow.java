package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Exact durable carrying, settlement, and realized result facts for one foreign-currency
 * obligation.
 */
public record RealizedForeignExchangeRegisterRow(
    ForeignCurrencyObligationId foreignCurrencyObligationId,
    LocalDate originatedOn,
    LocalDate lifecycleHorizon,
    AccountCode receivableAccountCode,
    MonetaryAmount transactionAmount,
    MonetaryAmount functionalCarryingAmount,
    Optional<LocalDate> settledOn,
    Optional<MonetaryAmount> functionalSettlementAmount,
    Optional<MonetaryAmount> realizedGainOrLossAmount,
    Optional<Boolean> realizedGain) {
  /** Validates one complete realized-FX register row. */
  public RealizedForeignExchangeRegisterRow {
    Objects.requireNonNull(foreignCurrencyObligationId, "foreignCurrencyObligationId");
    Objects.requireNonNull(originatedOn, "originatedOn");
    Objects.requireNonNull(lifecycleHorizon, "lifecycleHorizon");
    Objects.requireNonNull(receivableAccountCode, "receivableAccountCode");
    Objects.requireNonNull(transactionAmount, "transactionAmount");
    Objects.requireNonNull(functionalCarryingAmount, "functionalCarryingAmount");
    Objects.requireNonNull(settledOn, "settledOn");
    Objects.requireNonNull(functionalSettlementAmount, "functionalSettlementAmount");
    Objects.requireNonNull(realizedGainOrLossAmount, "realizedGainOrLossAmount");
    Objects.requireNonNull(realizedGain, "realizedGain");
    if (lifecycleHorizon.isBefore(originatedOn)) {
      throw new IllegalArgumentException("lifecycleHorizon must not precede originatedOn.");
    }
    if (transactionAmount.currencyCode().equals(functionalCarryingAmount.currencyCode())) {
      throw new IllegalArgumentException(
          "Transaction and functional carrying currencies must differ.");
    }
    if (settledOn.isPresent()
        != (functionalSettlementAmount.isPresent()
            && realizedGainOrLossAmount.isPresent()
            && realizedGain.isPresent())) {
      throw new IllegalArgumentException(
          "Settlement result fields must be present exactly when the obligation is settled.");
    }
    functionalSettlementAmount.ifPresent(
        amount -> {
          if (!amount.currencyCode().equals(functionalCarryingAmount.currencyCode())) {
            throw new IllegalArgumentException(
                "Functional settlement amount must use the carrying currency.");
          }
        });
    realizedGainOrLossAmount.ifPresent(
        amount -> {
          if (!amount.currencyCode().equals(functionalCarryingAmount.currencyCode())) {
            throw new IllegalArgumentException(
                "Realized gain or loss amount must use the carrying currency.");
          }
        });
  }
}
