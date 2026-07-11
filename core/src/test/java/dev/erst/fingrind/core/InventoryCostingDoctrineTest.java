package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link InventoryCostingDoctrine}. */
class InventoryCostingDoctrineTest {
  @Test
  void wireVocabulary_isStable() {
    assertEquals(List.of("WEIGHTED_AVERAGE"), InventoryCostingDoctrine.wireValues());
    assertEquals(
        InventoryCostingDoctrine.WEIGHTED_AVERAGE,
        InventoryCostingDoctrine.fromWireValue(
            InventoryCostingDoctrine.WEIGHTED_AVERAGE.wireValue()));
    assertThrows(
        IllegalArgumentException.class,
        () -> InventoryCostingDoctrine.fromWireValue("weighted-average"));
  }
}
