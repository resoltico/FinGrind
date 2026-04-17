package dev.erst.fingrind.contract.protocol;

/** Canonical protocol-level limits shared by query operations and rendered command help. */
public final class ProtocolLimits {
  /** Smallest accepted page size for paginated query operations. */
  public static final int PAGE_LIMIT_MIN = 1;

  /** Largest accepted page size for paginated query operations. */
  public static final int PAGE_LIMIT_MAX = 200;

  /** Default page size used when a paginated query omits an explicit limit. */
  public static final int DEFAULT_PAGE_LIMIT = 50;

  /** Smallest accepted page offset for paginated query operations. */
  public static final int PAGE_OFFSET_MIN = 0;

  /** Default page offset used when a paginated query omits an explicit offset. */
  public static final int DEFAULT_PAGE_OFFSET = 0;

  private ProtocolLimits() {}
}
