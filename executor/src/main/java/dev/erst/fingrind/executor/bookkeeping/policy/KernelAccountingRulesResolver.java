package dev.erst.fingrind.executor.bookkeeping.policy;

import dev.erst.fingrind.core.AccountingKernelProfiles;
import dev.erst.fingrind.core.BookIdentity;
import java.util.Objects;

/** Resolves built-in policy-pack implementations from persisted book policy profiles. */
public final class KernelAccountingRulesResolver {
  private KernelAccountingRulesResolver() {}

  /** Returns the executable built-in policy pack selected by one initialized book identity. */
  public static KernelAccountingRules forBookIdentity(BookIdentity bookIdentity) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    if (AccountingKernelProfiles.INTERNAL_MANAGEMENT_CASH_BOOKKEEPING_KERNEL.equals(
        bookIdentity.bookDoctrine().accountingKernelProfileId())) {
      return InternalManagementKernelAccountingRules.current();
    }
    throw new IllegalArgumentException(
        "Unsupported accounting kernel profile: "
            + bookIdentity.bookDoctrine().accountingKernelProfileId().value()
            + ".");
  }
}
