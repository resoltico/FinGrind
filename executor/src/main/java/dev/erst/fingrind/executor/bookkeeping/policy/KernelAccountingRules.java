package dev.erst.fingrind.executor.bookkeeping.policy;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/** Named seam for executable bookkeeping policy inside the current kernel. */
@NullMarked
public interface KernelAccountingRules {
  /** Returns the policy that derives comparative reporting windows from one book identity. */
  StatementComparativePolicy statementComparativePolicy();

  /** Returns the policy that governs chart hierarchy and taxonomy support. */
  ChartPolicy chartPolicy();

  /** Returns the policy that governs reporting-period-close account-type behavior. */
  ClosePostingPolicy closePostingPolicy();

  /** Returns the policy that governs statement presentation classification. */
  StatementPresentationPolicy statementPresentationPolicy();

  /** Validates one accounting-rules reference before use. */
  static KernelAccountingRules requireAccountingRules(KernelAccountingRules accountingRules) {
    return Objects.requireNonNull(accountingRules, "accountingRules");
  }
}
