package dev.erst.fingrind.contract.runtime;

/** Creates internal descriptor metadata with the published category and exit-code policy. */
final class ContractErrorDescriptorDefinitions {
  private ContractErrorDescriptorDefinitions() {}

  static ContractErrorDescriptorDefinition structuralInvalid(String code, String description) {
    return definition(code, description, 1, FailureCategory.STRUCTURAL_INVALID);
  }

  static ContractErrorDescriptorDefinition internal(String code, String description) {
    return definition(code, description, 70, FailureCategory.INTERNAL);
  }

  static ContractErrorDescriptorDefinition precondition(
      String code, String description, int exitCode) {
    return definition(code, description, exitCode, FailureCategory.PRECONDITION);
  }

  static ContractErrorDescriptorDefinition domainSemantic(String code, String description) {
    return definition(code, description, 1, FailureCategory.DOMAIN_SEMANTIC);
  }

  static ContractErrorDescriptorDefinition unsupportedSelection(String code, String description) {
    return definition(code, description, 2, FailureCategory.UNSUPPORTED_SELECTION);
  }

  private static ContractErrorDescriptorDefinition definition(
      String code, String description, int exitCode, FailureCategory category) {
    return new ContractErrorDescriptorDefinition(code, description, exitCode, category);
  }
}
