package dev.erst.fingrind.executor.bookkeeping.policy;

import dev.erst.fingrind.core.BookIdentity;
import java.util.Objects;

/** Operational seam for statement presentation taxonomy and classification behavior. */
public interface StatementPresentationPolicy {
  /** Returns whether the current pack supports rich current/non-current and line taxonomy. */
  boolean supportsRichClassification();

  /** Returns the policy-owned derived equity line for current-period result publication. */
  DerivedEquityLine currentPeriodResultLine(BookIdentity bookIdentity);

  /** Validates one policy reference before use. */
  static StatementPresentationPolicy requirePolicy(
      StatementPresentationPolicy statementPresentationPolicy) {
    return Objects.requireNonNull(statementPresentationPolicy, "statementPresentationPolicy");
  }
}
