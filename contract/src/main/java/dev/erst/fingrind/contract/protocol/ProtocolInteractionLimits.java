package dev.erst.fingrind.contract.protocol;

/** Canonical public interaction and request-shape limits for FinGrind CLI and protocol surfaces. */
public final class ProtocolInteractionLimits {
  /** Largest accepted UTF-8 byte payload for one book passphrase source. */
  public static final int BOOK_PASSPHRASE_MAX_UTF8_BYTES = 4096;

  /**
   * Smallest Unicode character count accepted when a caller creates a protected-book passphrase.
   */
  public static final int BOOK_PASSPHRASE_NEW_SECRET_MINIMUM_UNICODE_CODE_POINTS = 16;

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

  private ProtocolInteractionLimits() {}
}
