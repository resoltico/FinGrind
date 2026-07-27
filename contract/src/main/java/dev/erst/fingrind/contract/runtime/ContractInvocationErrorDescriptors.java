package dev.erst.fingrind.contract.runtime;

import java.util.Map;

/** Owns descriptor metadata for invocation syntax and output-selection failures. */
final class ContractInvocationErrorDescriptors {
  static final ContractErrorDescriptorDefinition UNKNOWN_COMMAND =
      ContractErrorDescriptorDefinitions.structuralInvalid(
          "unknown-command",
          "Invocation refused because the selected command name is not among the public FinGrind operations.");
  static final ContractErrorDescriptorDefinition INVALID_REQUEST =
      ContractErrorDescriptorDefinitions.structuralInvalid(
          "invalid-request",
          "Invocation or request document refused because it does not match the accepted FinGrind command or request contract.");
  static final ContractErrorDescriptorDefinition INVALID_PAGE_CURSOR =
      ContractErrorDescriptorDefinitions.structuralInvalid(
          "invalid-page-cursor",
          "Paginated query refused because the supplied cursor is not a valid FinGrind page cursor.");
  static final ContractErrorDescriptorDefinition UNSUPPORTED_OUTPUT_SELECTION =
      ContractErrorDescriptorDefinitions.unsupportedSelection(
          "unsupported-output-selection",
          "Invocation refused because the selected output mode does not fit the understood command and runtime policy.");

  private ContractInvocationErrorDescriptors() {}

  static void addTo(Map<ContractErrors.Descriptor, ContractErrorDescriptorDefinition> definitions) {
    definitions.put(ContractErrors.Descriptor.UNKNOWN_COMMAND, UNKNOWN_COMMAND);
    definitions.put(ContractErrors.Descriptor.INVALID_REQUEST, INVALID_REQUEST);
    definitions.put(ContractErrors.Descriptor.INVALID_PAGE_CURSOR, INVALID_PAGE_CURSOR);
    definitions.put(
        ContractErrors.Descriptor.UNSUPPORTED_OUTPUT_SELECTION, UNSUPPORTED_OUTPUT_SELECTION);
  }
}
