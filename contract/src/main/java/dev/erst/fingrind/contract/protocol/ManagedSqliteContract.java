package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.LinkedHashSet;
import java.util.List;

/** Protocol-owned managed-SQLite version pins shared across runtime, build, and operator seams. */
record ManagedSqliteContract(
    String requiredMinimumSqliteVersion,
    String requiredSqlite3mcVersion,
    String requiredSqliteSourceId,
    List<String> requiredCompileOptions,
    List<String> forbiddenCompileOptions,
    boolean requiresSecureMemorySupport) {
  ManagedSqliteContract {
    requiredMinimumSqliteVersion =
        ContractDescriptorValidation.requireText(
            requiredMinimumSqliteVersion, "requiredMinimumSqliteVersion");
    requiredSqlite3mcVersion =
        ContractDescriptorValidation.requireText(
            requiredSqlite3mcVersion, "requiredSqlite3mcVersion");
    requiredSqliteSourceId =
        ContractDescriptorValidation.requireText(requiredSqliteSourceId, "requiredSqliteSourceId");
    requiredCompileOptions = validateCompileOptions(requiredCompileOptions);
    forbiddenCompileOptions =
        validateOptionalCompileOptions(forbiddenCompileOptions, "forbiddenCompileOptions");
    if (requiredCompileOptions.stream().anyMatch(forbiddenCompileOptions::contains)) {
      throw new IllegalArgumentException(
          "requiredCompileOptions and forbiddenCompileOptions must not overlap.");
    }
  }

  private static List<String> validateCompileOptions(List<String> requiredCompileOptions) {
    List<String> normalized =
        validateOptionalCompileOptions(requiredCompileOptions, "requiredCompileOptions");
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("requiredCompileOptions must not be empty.");
    }
    return normalized;
  }

  private static List<String> validateOptionalCompileOptions(
      List<String> compileOptions, String fieldName) {
    List<String> normalized =
        ContractDescriptorValidation.copyList(compileOptions, fieldName).stream()
            .map(option -> ContractDescriptorValidation.requireText(option, fieldName))
            .toList();
    if (new LinkedHashSet<>(normalized).size() != normalized.size()) {
      throw new IllegalArgumentException(fieldName + " must not contain duplicates.");
    }
    return normalized;
  }
}
