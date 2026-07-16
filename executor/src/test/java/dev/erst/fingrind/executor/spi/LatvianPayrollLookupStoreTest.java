package dev.erst.fingrind.executor.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.PostingId;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** Locks the explicit failure mode for every payroll lookup a store has not implemented. */
class LatvianPayrollLookupStoreTest {
  private final LatvianPayrollLookupStore store = new LatvianPayrollLookupStore() {};

  @Test
  void defaultMethods_nameTheMissingOwnedStoreOperation() {
    assertUnsupported(
        () -> store.findLatvianPayrollRun(new LatvianPayrollRunId("run-1")),
        "findLatvianPayrollRun");
    assertUnsupported(
        () ->
            store.findActiveLatvianPayrollRun(
                new LatvianPayrollEmployeeReference("employee-1"),
                new LatvianPayrollMonth(YearMonth.of(2026, 7))),
        "findActiveLatvianPayrollRun");
    assertUnsupported(store::latvianPayrollRuns, "latvianPayrollRuns");
    assertUnsupported(
        () ->
            store.findActiveLatvianPayrollSettlement(
                new LatvianPayrollRunId("run-1"), LatvianPayrollSettlementKind.NET_WAGES),
        "findActiveLatvianPayrollSettlement");
    assertUnsupported(
        () -> store.findLatvianPayrollSettlementByPosting(new PostingId("posting-1")),
        "findLatvianPayrollSettlementByPosting");
    assertUnsupported(
        () -> store.findLatvianPayrollRunByOriginPosting(new PostingId("posting-1")),
        "findLatvianPayrollRunByOriginPosting");
    assertUnsupported(store::latvianPayrollSettlements, "latvianPayrollSettlements");
  }

  private static void assertUnsupported(Runnable action, String operation) {
    UnsupportedOperationException failure =
        assertThrows(UnsupportedOperationException.class, action::run);
    assertEquals(
        "Latvian payroll lookup operation '"
            + operation
            + "' requires an owned store implementation.",
        failure.getMessage());
  }
}
