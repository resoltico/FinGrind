package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.util.Deque;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** One injected I/O failure that becomes active after a fixed number of successful calls. */
record AclFixturePlannedIOException(int successfulCallsBeforeFailure, IOException exception) {
  AclFixturePlannedIOException {
    if (successfulCallsBeforeFailure < 0) {
      throw new IllegalArgumentException(
          "successfulCallsBeforeFailure must be greater than or equal to zero.");
    }
    Objects.requireNonNull(exception, "exception");
  }

  AclFixturePlannedIOException afterSuccessfulCall() {
    return new AclFixturePlannedIOException(successfulCallsBeforeFailure - 1, exception);
  }

  static @Nullable IOException nextFailure(Deque<AclFixturePlannedIOException> plannedFailures) {
    AclFixturePlannedIOException plannedFailure = plannedFailures.peekFirst();
    if (plannedFailure == null) {
      return null;
    }
    if (plannedFailure.successfulCallsBeforeFailure() > 0) {
      plannedFailures.removeFirst();
      plannedFailures.addFirst(plannedFailure.afterSuccessfulCall());
      return null;
    }
    plannedFailures.removeFirst();
    return plannedFailure.exception();
  }
}
