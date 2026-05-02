package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SqliteBestEffort}. */
class SqliteBestEffortTest {
  @Test
  void reportCleanupFailure_preservesPrimaryOutcomeWhenReporterFails() throws Exception {
    assertDoesNotThrow(
        () ->
            SqliteBestEffort.reportCleanupFailure(
                "closing one SQLite database",
                new IOException("cleanup"),
                (action, exception) -> {
                  throw new IllegalStateException("boom");
                }));
  }
}
