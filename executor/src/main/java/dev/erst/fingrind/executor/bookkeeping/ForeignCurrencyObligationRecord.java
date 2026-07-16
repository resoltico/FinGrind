package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Durable foreign-currency receivable carrying amount and one-to-one settlement state. */
public record ForeignCurrencyObligationRecord(
    ForeignCurrencyObligationId foreignCurrencyObligationId,
    LocalDate originatedOn,
    LocalDate lifecycleHorizon,
    AccountCode receivableAccountCode,
    AccountCode realizedGainAccountCode,
    AccountCode realizedLossAccountCode,
    Money transactionAmount,
    Money initialFunctionalCarryingAmount,
    Optional<LocalDate> settledOn,
    Optional<Money> functionalSettlementAmount,
    Optional<Money> realizedGainOrLossAmount,
    Optional<Boolean> realizedGain) {
  /** Validates a retained foreign-currency receivable lifecycle. */
  public ForeignCurrencyObligationRecord {
    Objects.requireNonNull(foreignCurrencyObligationId, "foreignCurrencyObligationId");
    Objects.requireNonNull(originatedOn, "originatedOn");
    Objects.requireNonNull(lifecycleHorizon, "lifecycleHorizon");
    Objects.requireNonNull(receivableAccountCode, "receivableAccountCode");
    Objects.requireNonNull(realizedGainAccountCode, "realizedGainAccountCode");
    Objects.requireNonNull(realizedLossAccountCode, "realizedLossAccountCode");
    Objects.requireNonNull(transactionAmount, "transactionAmount");
    Objects.requireNonNull(initialFunctionalCarryingAmount, "initialFunctionalCarryingAmount");
    Objects.requireNonNull(settledOn, "settledOn");
    Objects.requireNonNull(functionalSettlementAmount, "functionalSettlementAmount");
    Objects.requireNonNull(realizedGainOrLossAmount, "realizedGainOrLossAmount");
    Objects.requireNonNull(realizedGain, "realizedGain");
    if (!transactionAmount.isPositive() || !initialFunctionalCarryingAmount.isPositive()) {
      throw new IllegalArgumentException("Foreign-currency obligation amounts must be positive.");
    }
    if (transactionAmount.currencyUnit().equals(initialFunctionalCarryingAmount.currencyUnit())) {
      throw new IllegalArgumentException(
          "Foreign-currency obligation transaction and functional currencies must differ.");
    }
    if (lifecycleHorizon.isBefore(originatedOn)) {
      throw new IllegalArgumentException(
          "Foreign-currency obligation lifecycleHorizon must not precede originatedOn.");
    }
    settledOn.ifPresent(
        date -> {
          if (date.isBefore(originatedOn)) {
            throw new IllegalArgumentException(
                "Foreign-currency obligation settlement must not precede its origin.");
          }
        });
    if (settledOn.isPresent()
        != (functionalSettlementAmount.isPresent()
            && realizedGainOrLossAmount.isPresent()
            && realizedGain.isPresent())) {
      throw new IllegalArgumentException(
          "Foreign-currency settlement facts must be present exactly when the obligation is settled.");
    }
    functionalSettlementAmount.ifPresent(
        amount -> {
          if (!amount.isPositive()
              || !amount.currencyUnit().equals(initialFunctionalCarryingAmount.currencyUnit())) {
            throw new IllegalArgumentException(
                "Foreign-currency functional settlement amount must be positive in the carrying currency.");
          }
        });
    realizedGainOrLossAmount.ifPresent(
        amount -> {
          if (!amount.currencyUnit().equals(initialFunctionalCarryingAmount.currencyUnit())) {
            throw new IllegalArgumentException(
                "Foreign-currency realized gain or loss must be non-negative in the carrying currency.");
          }
        });
  }

  /** Returns whether settlement remains admissible. */
  public boolean unsettled() {
    return settledOn.isEmpty();
  }
}
