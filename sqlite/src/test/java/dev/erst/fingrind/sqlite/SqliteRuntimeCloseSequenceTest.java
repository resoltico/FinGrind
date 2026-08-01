package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Behavioural tests for ordered release and failure preservation across retained SQLite resources.
 */
class SqliteRuntimeCloseSequenceTest {
  @Test
  void closeAllKeepsClosingAfterFailuresAndReportsThemInOrder() {
    List<String> calls = new ArrayList<>();
    IllegalStateException first = new IllegalStateException("first close failure");
    IllegalStateException second = new IllegalStateException("second close failure");

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRuntimeCloseSequence.closeAll(
                    List.of(
                        () -> calls.add("first"),
                        () -> {
                          calls.add("second");
                          throw first;
                        },
                        () -> {
                          calls.add("third");
                          throw second;
                        })));

    assertSame(first, failure);
    assertEquals(List.of("first", "second", "third"), calls);
    assertEquals(List.of(second), List.of(failure.getSuppressed()));
  }

  @Test
  void reverseAndPrimaryFailureClosingKeepTheirDeclaredReleaseOrder() {
    List<String> calls = new ArrayList<>();
    SqliteRuntimeCloseSequence.closeAllReverse(
        List.of(() -> calls.add("first"), () -> calls.add("second")));
    assertEquals(List.of("second", "first"), calls);

    IOException primary = new IOException("primary failure");
    IllegalStateException cleanupFailure = new IllegalStateException("cleanup failure");
    SqliteRuntimeCloseSequence.closeAllReversePreservingFailure(
        List.of(
            () -> calls.add("third"),
            () -> {
              calls.add("fourth");
              throw cleanupFailure;
            }),
        primary);

    assertEquals(List.of("second", "first", "fourth", "third"), calls);
    assertEquals(List.of(cleanupFailure), List.of(primary.getSuppressed()));
  }

  @Test
  void primaryFailureClosingSuppressesCleanupErrorsWithoutReplacingThePrimaryFailure() {
    IOException primary = new IOException("primary failure");
    AssertionError cleanupFailure = new AssertionError("cleanup error");

    SqliteRuntimeCloseSequence.closeAllPreservingFailure(
        List.of(
            () -> {
              throw cleanupFailure;
            }),
        primary);

    assertEquals(List.of(cleanupFailure), List.of(primary.getSuppressed()));
  }

  @Test
  void controlCloseActionPreservesNativeCloseFailuresAsRuntimeCleanupFailures() {
    AtomicInteger closeCalls = new AtomicInteger();
    SqliteRuntimeCloseSequence.coordinationControlCloseAction(
            SqliteCoordinationControlFiles.lockedControlFile(
                Path.of("/test-control"), closeCalls::incrementAndGet))
        .close();

    assertEquals(1, closeCalls.get());

    IOException nativeFailure = new IOException("native close failure");
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRuntimeCloseSequence.coordinationControlCloseAction(
                        SqliteCoordinationControlFiles.lockedControlFile(
                            Path.of("/failing-test-control"),
                            () -> {
                              throw nativeFailure;
                            }))
                    .close());

    IOException wrappedNativeFailure = assertInstanceOf(IOException.class, failure.getCause());
    assertSame(nativeFailure, wrappedNativeFailure.getCause());
  }
}
