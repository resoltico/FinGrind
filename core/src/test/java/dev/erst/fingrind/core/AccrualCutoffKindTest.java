package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the stable durable vocabulary for accrual cut-off aggregates. */
class AccrualCutoffKindTest {
  @Test
  void wireValues_roundTripInDeclarationOrder() {
    assertEquals(
        List.of("PREPAYMENT", "DEFERRED_REVENUE", "ACCRUED_EXPENSE"),
        AccrualCutoffKind.wireValues());
    for (AccrualCutoffKind kind : AccrualCutoffKind.values()) {
      assertEquals(kind, AccrualCutoffKind.fromWireValue(kind.wireValue()));
    }
  }

  @Test
  void fromWireValue_rejectsNullAndUnknownValues() {
    assertThrows(NullPointerException.class, () -> AccrualCutoffKind.fromWireValue(nullOf()));
    assertThrows(
        IllegalArgumentException.class, () -> AccrualCutoffKind.fromWireValue("UNSUPPORTED"));
  }
}
