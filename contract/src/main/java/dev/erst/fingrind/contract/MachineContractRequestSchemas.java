package dev.erst.fingrind.contract;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Facade that exposes the canonical executable request schemas. */
final class MachineContractRequestSchemas {
  static final String JSON_SCHEMA_DIALECT = MachineContractSchemaSupport.JSON_SCHEMA_DIALECT;

  private MachineContractRequestSchemas() {}

  static Map<String, Object> postEntrySchema() {
    return MachineContractPostEntrySchemas.postEntrySchema();
  }

  static Map<String, Object> declareAccountSchema() {
    return MachineContractDeclareAccountSchemas.declareAccountSchema();
  }

  static Map<String, Object> ledgerPlanSchema() {
    return MachineContractLedgerPlanSchemas.ledgerPlanSchema();
  }

  static Map<String, Object> arraySchema(
      String description, Map<String, Object> items, int minItems, @Nullable Integer maxItems) {
    return MachineContractSchemaSupport.arraySchema(description, items, minItems, maxItems);
  }

  static Map<String, Object> orderedMapFromEntries(List<Map.Entry<String, Object>> entries) {
    return MachineContractSchemaSupport.orderedMapFromEntries(entries);
  }
}
