package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Durable financing aggregate retaining principal and interest balances independently. */
public record FinancingArrangementRecord(
    FinancingArrangementId financingArrangementId,
    LocalDate originatedOn,
    AccountCode principalLiabilityAccountCode,
    AccountCode interestPayableAccountCode,
    Money originalPrincipal,
    Money principalRepaid,
    Money interestAccrued,
    Money interestPaid,
    Optional<LocalDate> latestLifecycleEffectiveDate) {
  /** Validates retained financing balances and their lifecycle horizon. */
  public FinancingArrangementRecord {
    Objects.requireNonNull(financingArrangementId, "financingArrangementId");
    Objects.requireNonNull(originatedOn, "originatedOn");
    Objects.requireNonNull(principalLiabilityAccountCode, "principalLiabilityAccountCode");
    Objects.requireNonNull(interestPayableAccountCode, "interestPayableAccountCode");
    Objects.requireNonNull(originalPrincipal, "originalPrincipal");
    Objects.requireNonNull(principalRepaid, "principalRepaid");
    Objects.requireNonNull(interestAccrued, "interestAccrued");
    Objects.requireNonNull(interestPaid, "interestPaid");
    Objects.requireNonNull(latestLifecycleEffectiveDate, "latestLifecycleEffectiveDate");
    if (!originalPrincipal.isPositive()) {
      throw new IllegalArgumentException("Financing originalPrincipal must be positive.");
    }
    if (!sameCurrency(originalPrincipal, principalRepaid, interestAccrued, interestPaid)) {
      throw new IllegalArgumentException("Financing balances must use one currency.");
    }
    if (principalRepaid.compareTo(originalPrincipal) > 0) {
      throw new IllegalArgumentException(
          "Financing principalRepaid must not exceed originalPrincipal.");
    }
    if (interestPaid.compareTo(interestAccrued) > 0) {
      throw new IllegalArgumentException("Financing interestPaid must not exceed interestAccrued.");
    }
  }

  /** Returns the current outstanding principal. */
  public Money outstandingPrincipal() {
    return originalPrincipal.minus(principalRepaid);
  }

  /** Returns the current unpaid accrued interest. */
  public Money outstandingInterest() {
    return interestAccrued.minus(interestPaid);
  }

  /** Returns the inclusive effective-date floor for the next lifecycle event. */
  public LocalDate lifecycleHorizon() {
    return latestLifecycleEffectiveDate.orElse(originatedOn);
  }

  private static boolean sameCurrency(Money first, Money... remaining) {
    for (Money candidate : remaining) {
      if (!first.currencyUnit().equals(candidate.currencyUnit())) {
        return false;
      }
    }
    return true;
  }
}
