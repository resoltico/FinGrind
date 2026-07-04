package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.initializedLifecycleInspection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Coverage for the initialized-inspection guard used by derived close workflows. */
class ReportingPeriodCloseExecutionSupportTest {
  @Test
  void requireInitializedInspection_returnsInitializedInspection() {
    BookLifecycleInspection.Initialized inspection =
        initializedLifecycleInspection(1001, 1, 1, Instant.parse("2026-04-07T10:15:30Z"));

    assertSame(
        inspection, ReportingPeriodCloseExecutionSupport.requireInitializedInspection(inspection));
  }

  @Test
  void initializedInspection_derivesBookStartFromTheFiscalYearAnchor() {
    BookLifecycleInspection.Initialized inspection =
        initializedLifecycleInspection(1001, 1, 1, Instant.parse("2026-07-02T10:15:30Z"));

    assertEquals(LocalDate.parse("2026-01-01"), inspection.bookStartDate());
  }

  @Test
  void requireInitializedInspection_rejectsMissingInspection() {
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                ReportingPeriodCloseExecutionSupport.requireInitializedInspection(
                    new BookLifecycleInspection.Missing(1)));

    assertEquals(
        "Prepared close operations require one initialized book inspection.", failure.getMessage());
  }

  @Test
  void requireInitializedInspection_rejectsExistingInspection() {
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                ReportingPeriodCloseExecutionSupport.requireInitializedInspection(
                    new BookLifecycleInspection.Existing(
                        BookLifecycleInspection.Status.BLANK_SQLITE, 1001, 0, 1)));

    assertEquals(
        "Prepared close operations require one initialized book inspection.", failure.getMessage());
  }
}
