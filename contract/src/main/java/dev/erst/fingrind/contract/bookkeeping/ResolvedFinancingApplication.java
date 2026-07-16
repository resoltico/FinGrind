package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.util.Objects;

/** Executor-owned liability account facts resolved from one admitted financing arrangement. */
public record ResolvedFinancingApplication(
    AccountCode principalLiabilityAccountCode, AccountCode interestPayableAccountCode) {
  /** Validates the resolved principal and accrued-interest liability accounts. */
  public ResolvedFinancingApplication {
    Objects.requireNonNull(principalLiabilityAccountCode, "principalLiabilityAccountCode");
    Objects.requireNonNull(interestPayableAccountCode, "interestPayableAccountCode");
  }
}
