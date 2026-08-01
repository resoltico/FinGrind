package dev.erst.fingrind.executor.maintenance;

import java.util.Objects;
import java.util.function.Supplier;

/** Owns deterministic release of non-null maintenance handles while preserving primary failures. */
final class MaintenanceResourceScope {
  private MaintenanceResourceScope() {}

  /**
   * Runs one operation, releases its handle, and attaches any release failure to a primary failure.
   */
  static <T> T closeAfter(Runnable close, Supplier<? extends T> operation) {
    Runnable checkedClose = Objects.requireNonNull(close, "close");
    Supplier<? extends T> checkedOperation = Objects.requireNonNull(operation, "operation");
    T result;
    try {
      result = checkedOperation.get();
    } catch (RuntimeException | Error primaryFailure) {
      suppressCloseFailure(checkedClose, primaryFailure);
      throw primaryFailure;
    }
    checkedClose.run();
    return result;
  }

  private static void suppressCloseFailure(Runnable close, Throwable primaryFailure) {
    try {
      close.run();
    } catch (RuntimeException | Error closeFailure) {
      primaryFailure.addSuppressed(closeFailure);
    }
  }
}
