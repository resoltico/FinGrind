package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
  void orderedMapFromEntries_preservesInsertionOrderAndRejectsDuplicateKeys() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                MachineContractRequestSchemas.orderedMapFromEntries(
                    List.of(
                        Map.entry("type", "object"),
                        Map.entry("description", "first"),
                        Map.entry("type", "array"))));

    assertEquals("Duplicate schema key: type", exception.getMessage());
  }

  @Test
  void schemaFacade_exposesCanonicalSchemasAndOrderedMapWrapper() {
    assertEquals(
        MachineContractSchemaSupport.JSON_SCHEMA_DIALECT,
        MachineContractRequestSchemas.JSON_SCHEMA_DIALECT);
    assertEquals("object", MachineContractRequestSchemas.postEntrySchema().get("type"));
    assertEquals("object", MachineContractRequestSchemas.declareAccountSchema().get("type"));
    assertEquals("object", MachineContractRequestSchemas.ledgerPlanSchema().get("type"));

    Map<String, Object> ordered =
        MachineContractRequestSchemas.orderedMapFromEntries(
            List.of(Map.entry("first", 1), Map.entry("second", 2)));

    assertEquals(List.of("first", "second"), ordered.keySet().stream().toList());
    assertEquals(2, ordered.get("second"));
  }

  @Test
  void orderedMap_rejectsOddArityAndNonStringKeys() {
    IllegalArgumentException oddArity =
        assertThrows(
            IllegalArgumentException.class, () -> MachineContractSchemaSupport.orderedMap("type"));
    IllegalArgumentException nonStringKey =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                MachineContractSchemaSupport.orderedMap(
                    "type", "object", Integer.valueOf(7), "value"));

    assertEquals(
        "orderedMap requires an even number of key/value arguments.", oddArity.getMessage());
    assertEquals(
        "orderedMap keys must be non-null Strings at argument index 2.", nonStringKey.getMessage());
  }
}
