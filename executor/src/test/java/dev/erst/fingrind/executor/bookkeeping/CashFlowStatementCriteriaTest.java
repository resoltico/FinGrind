package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.ComparativeSelection;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Direct coverage for bounded cash-flow statement criteria validation. */
class CashFlowStatementCriteriaTest {
  @Test
  void constructor_rejectsPeriodsWhoseStartFallsAfterTheirEnd() {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CashFlowStatementCriteria(
                    LocalDate.parse("2026-04-08"),
                    LocalDate.parse("2026-04-07"),
                    ComparativeSelection.none()));

    assertEquals("effectiveDateFrom must be on or before effectiveDateTo.", failure.getMessage());
  }
}
