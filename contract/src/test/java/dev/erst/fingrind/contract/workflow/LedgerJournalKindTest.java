package dev.erst.fingrind.contract.workflow;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contract coverage for canonical plan-journal kind wire values. */
class LedgerJournalKindTest {
  @Test
  void standardJournalKindsReuseCanonicalStepKindsAndReserveOneBoundaryKind() {
    List<String> expectedWireValues = new ArrayList<>(LedgerStepKind.wireValues());
    expectedWireValues.add("plan-boundary");
    assertEquals(expectedWireValues, LedgerJournalKind.wireValues());
    for (LedgerStepKind stepKind : LedgerStepKind.supportedPlanStepKinds()) {
      assertSame(stepKind, LedgerJournalKind.fromWireValue(stepKind.wireValue()));
    }
    assertSame(
        LedgerJournalKind.BoundaryKind.PLAN_BOUNDARY,
        LedgerJournalKind.fromWireValue("plan-boundary"));
    assertThrows(IllegalArgumentException.class, () -> LedgerJournalKind.fromWireValue("unknown"));
    assertThrows(NullPointerException.class, () -> LedgerJournalKind.fromWireValue(nullOf()));
  }
}
