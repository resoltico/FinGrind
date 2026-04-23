package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Focused coverage for executable request-schema helper branches. */
class MachineContractRequestSchemasTest {
  @Test
  void arraySchema_supportsOptionalMaxItems() {
    Map<String, Object> bounded =
        MachineContractRequestSchemas.arraySchema(
            "Bounded array.", Map.of("type", "string"), 1, Integer.valueOf(3));
    Map<String, Object> unbounded =
        MachineContractRequestSchemas.arraySchema(
            "Unbounded array.", Map.of("type", "string"), 1, null);

    assertEquals(3, bounded.get("maxItems"));
    assertFalse(unbounded.containsKey("maxItems"));
  }

  @Test
  void orderedMapFromEntries_preservesInsertionOrderAndUsesRightmostDuplicate() {
    Map<String, Object> ordered =
        MachineContractRequestSchemas.orderedMapFromEntries(
            List.of(
                Map.entry("type", "object"),
                Map.entry("description", "first"),
                Map.entry("type", "array")));

    assertEquals("array", ordered.get("type"));
    assertEquals(List.of("type", "description"), List.copyOf(ordered.keySet()));
    assertTrue(ordered.containsKey("description"));
  }
}
