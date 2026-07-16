package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the stable durable vocabulary for accrual cut-off lifecycle applications. */
class AccrualCutoffApplicationKindTest {
  @Test
  void wireValues_roundTripInDeclarationOrder() {
    assertEquals(List.of("RECOGNITION", "SETTLEMENT"), AccrualCutoffApplicationKind.wireValues());
    for (AccrualCutoffApplicationKind kind : AccrualCutoffApplicationKind.values()) {
      assertEquals(kind, AccrualCutoffApplicationKind.fromWireValue(kind.wireValue()));
    }
  }

  @Test
  void fromWireValue_rejectsNullAndUnknownValues() {
    assertThrows(
        NullPointerException.class, () -> AccrualCutoffApplicationKind.fromWireValue(nullOf()));
    assertThrows(
        IllegalArgumentException.class,
        () -> AccrualCutoffApplicationKind.fromWireValue("UNSUPPORTED"));
  }
}
