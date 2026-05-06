package dev.erst.fingrind.core;

/** Cross-context request and workflow limits shared by protocol and local execution models. */
public final class InteractionLimits {
  /** Smallest accepted page size for paginated query operations. */
  public static final int PAGE_LIMIT_MIN = 1;

  /** Largest accepted page size for paginated query operations. */
  public static final int PAGE_LIMIT_MAX = 200;

  /** Default page size used when a paginated query omits an explicit limit. */
  public static final int DEFAULT_PAGE_LIMIT = 50;

  /** Largest accepted number of steps in one atomic ledger plan. */
  public static final int LEDGER_PLAN_STEP_MAX = 100;

  private InteractionLimits() {}
}
