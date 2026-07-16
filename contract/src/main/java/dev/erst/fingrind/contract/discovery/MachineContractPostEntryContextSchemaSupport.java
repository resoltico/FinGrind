package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds the shared envelope and scalar fields for typed bounded-context entry schemas. */
final class MachineContractPostEntryContextSchemaSupport {
  private MachineContractPostEntryContextSchemaSupport() {}

  static Map<String, Object> typedEventSchema(
      BookkeepingEntryKind entryKind,
      String description,
      String entryKindDescription,
      List<MachineContractFieldSpec> contextFields) {
    RequestSurfaceFacts.BookkeepingEntryKindFacts facts =
        MachineContractPostEntryTypedVariantSchemaBuilders.entryKindFacts(entryKind);
    var fields = new ArrayList<MachineContractFieldSpec>();
    fields.add(
        MachineContractPostEntryComponentSchemas.requiredEntryKindField(
            entryKind, entryKindDescription));
    fields.add(MachineContractPostEntryRequiredFieldSpecs.requiredEffectiveDateField());
    fields.addAll(contextFields);
    fields.add(MachineContractPostEntryRequiredFieldSpecs.requiredEvidenceField(facts));
    fields.add(MachineContractPostEntryRequiredFieldSpecs.requiredProvenanceField());
    return MachineContractSchemaSupport.objectSchema(description, List.copyOf(fields));
  }

  static MachineContractFieldSpec requiredAccount(String fieldName, String description) {
    return MachineContractPostEntryTypedVariantSchemaBuilders.requiredAccountField(
        fieldName, description);
  }

  static MachineContractFieldSpec requiredPositiveMoney(String fieldName, String description) {
    return MachineContractFieldSpec.required(
        fieldName, description, MachineContractScalarSchemas.moneyObjectSchema(description, true));
  }
}
