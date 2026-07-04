package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers stable evidence-class wire values and round-trip parsing. */
class EvidenceClassTest {
  @Test
  void wireValues_roundTripInDeclarationOrder() {
    assertEquals(List.of("CASH_SETTLEMENT", "INVOICE", "OTHER"), EvidenceClass.wireValues());
    for (EvidenceClass evidenceClass : EvidenceClass.values()) {
      assertEquals(
          evidenceClass,
          EvidenceClass.fromWireValue(evidenceClass.wireValue()),
          evidenceClass.name());
    }

    IllegalArgumentException unsupported =
        assertThrows(
            IllegalArgumentException.class,
            () -> EvidenceClass.fromWireValue("UNSUPPORTED_EVIDENCE_CLASS"));
    assertEquals("Unsupported evidenceClass: UNSUPPORTED_EVIDENCE_CLASS", unsupported.getMessage());
  }
}
