package dev.erst.fingrind.executor.bookkeeping.policy;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/** Named seam for bookkeeping policy that may vary by standards or jurisdictional layer. */
@NullMarked
@FunctionalInterface
public interface BookkeepingPolicyPack {
  /** Returns the policy that derives comparative reporting windows from one book identity. */
  StatementComparativePolicy statementComparativePolicy();

  /** Validates one policy-pack reference before use. */
  static BookkeepingPolicyPack requirePolicyPack(BookkeepingPolicyPack policyPack) {
    return Objects.requireNonNull(policyPack, "policyPack");
  }
}
