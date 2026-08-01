package dev.erst.fingrind.contract.runtime;

import java.util.Map;

/** Owns descriptor metadata for internal and managed runtime failures. */
final class ContractRuntimeErrorDescriptors {
  static final ContractErrorDescriptorDefinition INTERNAL_ERROR =
      ContractErrorDescriptorDefinitions.internal(
          "internal-error",
          "Command failed because FinGrind encountered an internal software defect rather than a deterministic caller or environment problem.");
  static final ContractErrorDescriptorDefinition INTERNAL_DEFECT =
      ContractErrorDescriptorDefinitions.internal(
          "internal-defect",
          "Command failed because FinGrind detected a deterministic internal contract defect in its typed bookkeeping write semantics.");
  static final ContractErrorDescriptorDefinition MANAGED_RUNTIME_FAILURE =
      ContractErrorDescriptorDefinitions.precondition(
          "managed-runtime-failure",
          "Command failed because the managed FinGrind runtime dependency surface is unavailable, incompatible, or misconfigured.",
          5);
  static final ContractErrorDescriptorDefinition STORAGE_RUNTIME_FAILURE =
      ContractErrorDescriptorDefinitions.precondition(
          "storage-runtime-failure",
          "Command failed because SQLite storage or book-handle execution encountered a runtime problem outside the deterministic caller contract.",
          4);
  static final ContractErrorDescriptorDefinition PDF_EXPORT_FAILURE =
      ContractErrorDescriptorDefinitions.precondition(
          "pdf-export-failure", "Command failed while exporting the requested PDF artifact.", 4);

  private ContractRuntimeErrorDescriptors() {}

  static void addTo(Map<ContractErrors.Descriptor, ContractErrorDescriptorDefinition> definitions) {
    definitions.put(ContractErrors.Descriptor.INTERNAL_ERROR, INTERNAL_ERROR);
    definitions.put(ContractErrors.Descriptor.INTERNAL_DEFECT, INTERNAL_DEFECT);
    definitions.put(ContractErrors.Descriptor.MANAGED_RUNTIME_FAILURE, MANAGED_RUNTIME_FAILURE);
    definitions.put(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, STORAGE_RUNTIME_FAILURE);
    definitions.put(ContractErrors.Descriptor.PDF_EXPORT_FAILURE, PDF_EXPORT_FAILURE);
  }
}
