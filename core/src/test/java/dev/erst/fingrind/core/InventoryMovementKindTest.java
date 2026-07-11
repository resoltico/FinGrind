package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link InventoryMovementKind}. */
class InventoryMovementKindTest {
  @Test
  void wireVocabulary_isStable() {
    assertEquals(
        List.of(
            "ACQUISITION",
            "CAPITALIZATION",
            "COUNT_INCREASE",
            "OPENING",
            "DISPOSAL",
            "WRITE_DOWN",
            "SHRINKAGE",
            "REVERSAL_COMP"),
        InventoryMovementKind.wireValues());
    assertEquals(
        InventoryMovementKind.ACQUISITION,
        InventoryMovementKind.fromWireValue(InventoryMovementKind.ACQUISITION.wireValue()));
    assertEquals(
        InventoryMovementKind.REVERSAL_COMP,
        InventoryMovementKind.fromWireValue(InventoryMovementKind.REVERSAL_COMP.wireValue()));
    assertThrows(
        IllegalArgumentException.class, () -> InventoryMovementKind.fromWireValue("REVERSAL"));
  }
}
