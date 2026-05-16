package dev.erst.fingrind.executor.bookkeeping.policy;

import dev.erst.fingrind.core.AccountingBasis;
import java.util.List;

/** Operational policy seam that declares which accounting-basis premises one pack supports. */
@FunctionalInterface
public interface AccountingBasisPolicy {
  /** Returns the stable supported accounting-basis vocabulary for this pack. */
  List<AccountingBasis> supportedBases();

  /** Returns whether this pack accepts one explicit accounting basis. */
  default boolean supports(AccountingBasis accountingBasis) {
    return supportedBases().contains(accountingBasis);
  }
}
