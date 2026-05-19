package dev.erst.fingrind.executor.bookkeeping.policy;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/** Named seam for executable bookkeeping policy inside the current kernel. */
@NullMarked
public interface BookkeepingPolicyPack {

  /** Returns the policy that derives comparative reporting windows from one book identity. */
  StatementComparativePolicy statementComparativePolicy();

  /** Returns the policy that governs explicit accounting-basis support. */
  AccountingBasisPolicy accountingBasisPolicy();

  /** Returns the policy that governs chart hierarchy and taxonomy support. */
  ChartPolicy chartPolicy();

  /** Returns the policy that governs period-close account-type behavior. */
  ClosePolicy closePolicy();

  /** Returns the policy that governs statement presentation classification. */
  StatementPresentationPolicy statementPresentationPolicy();

  /** Validates one policy-pack reference before use. */
  static BookkeepingPolicyPack requirePolicyPack(BookkeepingPolicyPack policyPack) {
    return Objects.requireNonNull(policyPack, "policyPack");
  }
}
