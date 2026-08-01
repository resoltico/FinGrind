package dev.erst.fingrind.core;

import java.time.Clock;

/**
 * Owns the process wall-clock source used only where a caller cannot receive a clock explicitly.
 */
public final class SystemUtcClock {
  private static final Clock INSTANCE = Clock.systemUTC();

  private SystemUtcClock() {}

  /** Returns the sole UTC wall-clock source for process-boundary defaults. */
  public static Clock instance() {
    return INSTANCE;
  }
}
