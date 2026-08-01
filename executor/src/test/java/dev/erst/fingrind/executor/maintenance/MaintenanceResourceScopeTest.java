package dev.erst.fingrind.executor.maintenance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies deterministic maintenance-handle ownership and primary-failure preservation. */
class MaintenanceResourceScopeTest {
  @Test
  void releasesTheHandleAfterReturningTheOperationResult() {
    List<String> events = new ArrayList<>();

    String result =
        MaintenanceResourceScope.closeAfter(
            () -> events.add("close"),
            () -> {
              events.add("operation");
              return "completed";
            });

    assertEquals("completed", result);
    assertEquals(List.of("operation", "close"), events);
  }

  @Test
  void preservesPrimaryRuntimeAndErrorFailuresWhenReleaseAlsoFails() {
    IllegalStateException primaryRuntime = new IllegalStateException("primary-runtime");
    IllegalArgumentException closeRuntime = new IllegalArgumentException("close-runtime");
    IllegalStateException observedRuntime =
        assertThrows(
            IllegalStateException.class,
            () ->
                MaintenanceResourceScope.closeAfter(
                    () -> {
                      throw closeRuntime;
                    },
                    () -> {
                      throw primaryRuntime;
                    }));
    assertSame(primaryRuntime, observedRuntime);
    assertArrayEquals(new Throwable[] {closeRuntime}, observedRuntime.getSuppressed());

    AssertionError primaryError = new AssertionError("primary-error");
    LinkageError closeError = new LinkageError("close-error");
    AssertionError observedError =
        assertThrows(
            AssertionError.class,
            () ->
                MaintenanceResourceScope.closeAfter(
                    () -> {
                      throw closeError;
                    },
                    () -> {
                      throw primaryError;
                    }));
    assertSame(primaryError, observedError);
    assertArrayEquals(new Throwable[] {closeError}, observedError.getSuppressed());
  }

  @Test
  void propagatesAReleaseFailureAfterACompletedOperation() {
    IllegalStateException closeFailure = new IllegalStateException("close");

    IllegalStateException observed =
        assertThrows(
            IllegalStateException.class,
            () ->
                MaintenanceResourceScope.closeAfter(
                    () -> {
                      throw closeFailure;
                    },
                    () -> "completed"));

    assertSame(closeFailure, observed);
  }
}
