package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.initializedLifecycleInspection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Coverage for the resolved execution context used by derived close workflows. */
class ReportingPeriodCloseExecutionSupportTest {
  private static final Instant EXECUTED_AT = Instant.parse("2026-04-07T10:15:30Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(EXECUTED_AT, ZoneOffset.UTC);

  @Test
  void preparedExecution_passesTheBoundIdentityAndNoSeparateBookStartDate() {
    BookLifecycleInspection.Initialized inspection =
        initializedLifecycleInspection(1001, 1, 1, EXECUTED_AT);
    PostingIdGenerator postingIdGenerator =
        () -> new PostingId("f69a68be-269e-3c0f-96ac-2e3f7d806a8b");
    ReportingPeriodCloseExecutionSupport support =
        new ReportingPeriodCloseExecutionSupport(() -> inspection, postingIdGenerator, FIXED_CLOCK);

    PreparedCloseCapture capture =
        support.execute(
            () -> {
              throw new AssertionError("Initialized close execution must not use the fallback.");
            },
            ignored -> "planner",
            (resolvedBookIdentity,
                planner,
                currentUtcDate,
                executedAt,
                resolvedPostingIdGenerator) ->
                new PreparedCloseCapture(
                    resolvedBookIdentity,
                    planner,
                    currentUtcDate,
                    executedAt,
                    resolvedPostingIdGenerator));

    assertEquals(bookIdentity(), capture.bookIdentity());
    assertEquals("planner", capture.planner());
    assertEquals(LocalDate.parse("2026-04-07"), capture.currentUtcDate());
    assertEquals(EXECUTED_AT, capture.executedAt());
    assertSame(postingIdGenerator, capture.postingIdGenerator());
  }

  @Test
  void preparedExecution_returnsTheNotInitializedOutcomeWithoutConstructingThePlanner() {
    ReportingPeriodCloseExecutionSupport support =
        new ReportingPeriodCloseExecutionSupport(
            () -> new BookLifecycleInspection.Missing(1),
            () -> new PostingId("f69a68be-269e-3c0f-96ac-2e3f7d806a8b"),
            FIXED_CLOCK);

    String outcome =
        support.execute(
            () -> "not initialized",
            ignored -> {
              throw new AssertionError(
                  "Uninitialized close execution must not construct a planner.");
            },
            (resolvedBookIdentity, planner, currentUtcDate, executedAt, postingIdGenerator) -> {
              throw new AssertionError("Uninitialized close execution must not invoke the store.");
            });

    assertEquals("not initialized", outcome);
  }

  private record PreparedCloseCapture(
      BookIdentity bookIdentity,
      String planner,
      LocalDate currentUtcDate,
      Instant executedAt,
      PostingIdGenerator postingIdGenerator) {}
}
