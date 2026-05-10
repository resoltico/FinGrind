package dev.erst.fingrind.core;

/** Cross-context request and workflow limits shared by protocol and local execution models. */
public final class InteractionLimits {
  /** Largest accepted UTF-8 byte payload for one request JSON document. */
  public static final int REQUEST_PAYLOAD_MAX_BYTES = 1_048_576;

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
