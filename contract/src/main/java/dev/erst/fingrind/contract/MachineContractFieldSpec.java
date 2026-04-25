package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Canonical field contract shared by executable request schemas and request-shape descriptors. */
record MachineContractFieldSpec(
    String name,
    RequestFieldPresence presence,
    String description,
    @Nullable Map<String, Object> acceptedSchema) {
  MachineContractFieldSpec {
    name = ContractDescriptorValidation.requireText(name, "name");
    presence = ContractDescriptorValidation.requireValue(presence, "presence");
    description = ContractDescriptorValidation.requireText(description, "description");
    acceptedSchema =
        acceptedSchema == null
            ? null
            : ContractDescriptorValidation.copyMap(acceptedSchema, "acceptedSchema");
    if (presence == RequestFieldPresence.FORBIDDEN && acceptedSchema != null) {
      throw new IllegalArgumentException(
          "Forbidden field specs must not publish an accepted schema.");
    }
    if (presence != RequestFieldPresence.FORBIDDEN && acceptedSchema == null) {
      throw new IllegalArgumentException("Accepted field specs must publish a schema.");
    }
  }

  static MachineContractFieldSpec required(
      String name, String description, Map<String, Object> acceptedSchema) {
    return new MachineContractFieldSpec(
        name, RequestFieldPresence.REQUIRED, description, acceptedSchema);
  }

  static MachineContractFieldSpec optional(
      String name, String description, Map<String, Object> acceptedSchema) {
    return new MachineContractFieldSpec(
        name, RequestFieldPresence.OPTIONAL, description, acceptedSchema);
  }

  static MachineContractFieldSpec conditional(
      String name, String description, Map<String, Object> acceptedSchema) {
    return new MachineContractFieldSpec(
        name, RequestFieldPresence.CONDITIONAL, description, acceptedSchema);
  }

  static MachineContractFieldSpec forbidden(String name, String description) {
    return new MachineContractFieldSpec(name, RequestFieldPresence.FORBIDDEN, description, null);
  }

  boolean acceptsInput() {
    return acceptedSchema != null;
  }

  boolean requiredInSchema() {
    return presence == RequestFieldPresence.REQUIRED;
  }

  ContractRequestShapes.RequestFieldDescriptor descriptor() {
    return new ContractRequestShapes.RequestFieldDescriptor(name, presence, description);
  }
}
