package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
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

  @Test
  void reportersReceiveTheOriginalCleanupAndRetainedEvidenceFailureFacts() {
    IOException cleanupFailure = new IOException("cleanup");
    AtomicReference<String> cleanupAction = new AtomicReference<>();
    AtomicReference<Exception> reportedCleanupFailure = new AtomicReference<>();

    SqliteBestEffort.reportCleanupFailure(
        "release database handle",
        cleanupFailure,
        (action, exception) -> {
          cleanupAction.set(action);
          reportedCleanupFailure.set(exception);
        });

    assertEquals("release database handle", cleanupAction.get());
    assertEquals(cleanupFailure, reportedCleanupFailure.get());

    IOException retainedEvidenceFailure = new IOException("retain evidence");
    AtomicReference<String> retainedAction = new AtomicReference<>();
    AtomicReference<Exception> reportedRetainedFailure = new AtomicReference<>();
    SqliteBestEffort.reportRetainedEvidenceReleaseFailure(
        "release retained stage",
        retainedEvidenceFailure,
        (action, exception) -> {
          retainedAction.set(action);
          reportedRetainedFailure.set(exception);
        });

    assertEquals("release retained stage", retainedAction.get());
    assertEquals(retainedEvidenceFailure, reportedRetainedFailure.get());
  }

  @Test
  void retainedEvidenceReleaseReportingDoesNotReplaceThePrimaryOutcomeWhenReporterFails() {
    assertDoesNotThrow(
        () ->
            SqliteBestEffort.reportRetainedEvidenceReleaseFailure(
                "release retained stage",
                new IOException("retain evidence"),
                (action, exception) -> {
                  throw new IllegalStateException("reporter failure");
                }));
  }

  @Test
  void retainedEvidenceReleaseUsesItsStandardReporterWithoutEscalatingTheFailure() {
    assertDoesNotThrow(
        () ->
            SqliteBestEffort.reportRetainedEvidenceReleaseFailure(
                "release retained stage", new IOException("retain evidence")));
  }
}
