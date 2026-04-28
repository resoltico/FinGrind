package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit tests for isolated recovery helpers inside {@link SqliteStoreMutationOperations}. */
@NullUnmarked
class SqliteStoreMutationOperationsTest {
  @Test
  void captureBestEffortRuntimeFailure_returnsNullOnSuccessAndReturnsRuntimeFailure() {
    assertNull(SqliteStoreMutationOperations.captureBestEffortRuntimeFailure(() -> {}));

    RuntimeException failure = new IllegalStateException("close failed");
    assertSame(
        failure,
        SqliteStoreMutationOperations.captureBestEffortRuntimeFailure(
            () -> {
              throw failure;
            }));
  }

  @Test
  void catastrophicRekeyRestoreFailure_preservesVerificationAndSuppressedFailures() {
    RuntimeException verificationFailure = new IllegalStateException("verify failed");
    RuntimeException restoreFailure = new IllegalStateException("restore failed");
    RuntimeException closeFailure = new IllegalStateException("close failed");

    IllegalStateException failure =
        SqliteStoreMutationOperations.catastrophicRekeyRestoreFailure(
            verificationFailure, restoreFailure, closeFailure);

    assertSame(verificationFailure, failure.getCause());
    assertEquals(2, failure.getSuppressed().length);
    assertSame(restoreFailure, failure.getSuppressed()[0]);
    assertSame(closeFailure, failure.getSuppressed()[1]);

    IllegalStateException withoutCloseFailure =
        SqliteStoreMutationOperations.catastrophicRekeyRestoreFailure(
            verificationFailure, restoreFailure, null);
    assertEquals(1, withoutCloseFailure.getSuppressed().length);
    assertSame(restoreFailure, withoutCloseFailure.getSuppressed()[0]);
  }

  @Test
  void restoredOriginalBookFailure_preservesVerificationCauseAndOptionalCloseFailure() {
    RuntimeException verificationFailure = new IllegalStateException("verify failed");
    RuntimeException closeFailure = new IllegalStateException("close failed");

    IllegalStateException withCloseFailure =
        SqliteStoreMutationOperations.restoredOriginalBookFailure(
            verificationFailure, closeFailure);
    assertSame(verificationFailure, withCloseFailure.getCause());
    assertEquals(1, verificationFailure.getSuppressed().length);
    assertSame(closeFailure, verificationFailure.getSuppressed()[0]);

    RuntimeException cleanVerificationFailure = new IllegalStateException("verify failed cleanly");
    IllegalStateException withoutCloseFailure =
        SqliteStoreMutationOperations.restoredOriginalBookFailure(cleanVerificationFailure, null);
    assertSame(cleanVerificationFailure, withoutCloseFailure.getCause());
    assertEquals(0, cleanVerificationFailure.getSuppressed().length);
  }

  @Test
  void finalizeFailedRekey_selectsTheCorrectFailureShapeAndDeletesOnlyAfterSuccessfulRestore() {
    RuntimeException verificationFailure = new IllegalStateException("verify failed");
    RuntimeException restoreFailure = new IllegalStateException("restore failed");
    RuntimeException closeFailure = new IllegalStateException("close failed");
    AtomicInteger deleteCalls = new AtomicInteger();

    IllegalStateException catastrophic =
        SqliteStoreMutationOperations.finalizeFailedRekey(
            verificationFailure, restoreFailure, closeFailure, deleteCalls::incrementAndGet);
    assertSame(verificationFailure, catastrophic.getCause());
    assertEquals(2, catastrophic.getSuppressed().length);
    assertEquals(0, deleteCalls.get());

    RuntimeException restoredVerificationFailure =
        new IllegalStateException("verify failed cleanly");
    IllegalStateException restored =
        SqliteStoreMutationOperations.finalizeFailedRekey(
            restoredVerificationFailure, null, closeFailure, deleteCalls::incrementAndGet);
    assertSame(restoredVerificationFailure, restored.getCause());
    assertEquals(1, restoredVerificationFailure.getSuppressed().length);
    assertEquals(1, deleteCalls.get());
  }
}
