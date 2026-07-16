package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import java.util.Objects;

/** Exact durable financing balances and lifecycle horizon for one arrangement. */
public record FinancingRegisterRow(
    FinancingArrangementId financingArrangementId,
    LocalDate originatedOn,
    LocalDate lifecycleHorizon,
    AccountCode principalLiabilityAccountCode,
    AccountCode interestPayableAccountCode,
    MonetaryAmount originalPrincipal,
    MonetaryAmount principalRepaid,
    MonetaryAmount principalOutstanding,
    MonetaryAmount interestAccrued,
    MonetaryAmount interestPaid,
    MonetaryAmount interestOutstanding) {
  /** Validates one complete financing register row. */
  public FinancingRegisterRow {
    Objects.requireNonNull(financingArrangementId, "financingArrangementId");
    Objects.requireNonNull(originatedOn, "originatedOn");
    Objects.requireNonNull(lifecycleHorizon, "lifecycleHorizon");
    Objects.requireNonNull(principalLiabilityAccountCode, "principalLiabilityAccountCode");
    Objects.requireNonNull(interestPayableAccountCode, "interestPayableAccountCode");
    Objects.requireNonNull(originalPrincipal, "originalPrincipal");
    Objects.requireNonNull(principalRepaid, "principalRepaid");
    Objects.requireNonNull(principalOutstanding, "principalOutstanding");
    Objects.requireNonNull(interestAccrued, "interestAccrued");
    Objects.requireNonNull(interestPaid, "interestPaid");
    Objects.requireNonNull(interestOutstanding, "interestOutstanding");
    if (lifecycleHorizon.isBefore(originatedOn)) {
      throw new IllegalArgumentException("lifecycleHorizon must not precede originatedOn.");
    }
    if (!sameCurrency(
        originalPrincipal,
        principalRepaid,
        principalOutstanding,
        interestAccrued,
        interestPaid,
        interestOutstanding)) {
      throw new IllegalArgumentException("Financing register amounts must share one currency.");
    }
    if (!originalPrincipal
        .toMoney()
        .equals(principalRepaid.toMoney().plus(principalOutstanding.toMoney()))) {
      throw new IllegalArgumentException(
          "originalPrincipal must equal principalRepaid plus principalOutstanding.");
    }
    if (!interestAccrued
        .toMoney()
        .equals(interestPaid.toMoney().plus(interestOutstanding.toMoney()))) {
      throw new IllegalArgumentException(
          "interestAccrued must equal interestPaid plus interestOutstanding.");
    }
  }

  private static boolean sameCurrency(MonetaryAmount first, MonetaryAmount... remaining) {
    for (MonetaryAmount candidate : remaining) {
      if (!first.currencyCode().equals(candidate.currencyCode())) {
        return false;
      }
    }
    return true;
  }
}
