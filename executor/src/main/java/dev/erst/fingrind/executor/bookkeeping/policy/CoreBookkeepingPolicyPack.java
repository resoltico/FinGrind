package dev.erst.fingrind.executor.bookkeeping.policy;

import org.jspecify.annotations.NullMarked;

/** Current FinGrind bookkeeping policy pack for the built-in country-agnostic kernel. */
@NullMarked
public final class CoreBookkeepingPolicyPack implements BookkeepingPolicyPack {
  private static final CoreBookkeepingPolicyPack CURRENT = new CoreBookkeepingPolicyPack();

  private final StatementComparativePolicy statementComparativePolicy =
      new FiscalYearAnchoredStatementComparativePolicy();

  private CoreBookkeepingPolicyPack() {}

  /** Returns the built-in bookkeeping policy pack. */
  public static CoreBookkeepingPolicyPack current() {
    return CURRENT;
  }

  @Override
  public StatementComparativePolicy statementComparativePolicy() {
    return statementComparativePolicy;
  }
}
