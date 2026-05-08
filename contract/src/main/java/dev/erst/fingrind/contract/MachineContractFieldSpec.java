package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.Map;

/** Canonical field contract shared by executable request schemas and request-shape descriptors. */
record MachineContractFieldSpec(
    String name, RequestFieldPresence presence, String description, FieldContract contract) {
  MachineContractFieldSpec {
    name = ContractDescriptorValidation.requireText(name, "name");
    presence = ContractDescriptorValidation.requireValue(presence, "presence");
    description = ContractDescriptorValidation.requireText(description, "description");
    contract = ContractDescriptorValidation.requireValue(contract, "contract");
    if (presence == RequestFieldPresence.FORBIDDEN) {
      if (!(contract instanceof ForbiddenFieldContract)) {
        throw new IllegalArgumentException(
            "Forbidden field specs must not publish an accepted schema.");
      }
    } else if (!(contract instanceof AcceptedFieldContract)) {
      throw new IllegalArgumentException("Accepted field specs must publish a schema.");
    }
  }

  static MachineContractFieldSpec required(
      String name, String description, Map<String, Object> acceptedSchema) {
    return new MachineContractFieldSpec(
        name,
        RequestFieldPresence.REQUIRED,
        description,
        new AcceptedFieldContract(acceptedSchema));
  }

  static MachineContractFieldSpec optional(
      String name, String description, Map<String, Object> acceptedSchema) {
    return new MachineContractFieldSpec(
        name,
        RequestFieldPresence.OPTIONAL,
        description,
        new AcceptedFieldContract(acceptedSchema));
  }

  static MachineContractFieldSpec conditional(
      String name, String description, Map<String, Object> acceptedSchema) {
    return new MachineContractFieldSpec(
        name,
        RequestFieldPresence.CONDITIONAL,
        description,
        new AcceptedFieldContract(acceptedSchema));
  }

  static MachineContractFieldSpec forbidden(String name, String description) {
    return new MachineContractFieldSpec(
        name, RequestFieldPresence.FORBIDDEN, description, ForbiddenFieldContract.INSTANCE);
  }

  boolean acceptsInput() {
    return contract instanceof AcceptedFieldContract;
  }

  boolean requiredInSchema() {
    return presence == RequestFieldPresence.REQUIRED;
  }

  Map<String, Object> inputSchema() {
    return switch (contract) {
      case AcceptedFieldContract acceptedFieldContract -> acceptedFieldContract.schema();
      case ForbiddenFieldContract ignored ->
          throw new IllegalStateException("Forbidden field " + name + " does not accept input.");
    };
  }

  ContractRequestShapes.RequestFieldDescriptor descriptor() {
    return new ContractRequestShapes.RequestFieldDescriptor(name, presence, description);
  }

  /** Closed field-contract family for accepted versus forbidden request fields. */
  sealed interface FieldContract permits AcceptedFieldContract, ForbiddenFieldContract {}

  record AcceptedFieldContract(Map<String, Object> schema) implements FieldContract {
    AcceptedFieldContract {
      schema = ContractDescriptorValidation.copyMap(schema, "acceptedSchema");
    }
  }

  /** Marker variant for fields that must not accept caller input. */
  enum ForbiddenFieldContract implements FieldContract {
    INSTANCE
  }
}
