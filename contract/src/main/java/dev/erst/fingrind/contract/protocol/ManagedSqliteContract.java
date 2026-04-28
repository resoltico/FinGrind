package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.LinkedHashSet;
import java.util.List;

/** Protocol-owned managed-SQLite version pins shared across runtime, build, and operator seams. */
record ManagedSqliteContract(
    String requiredMinimumSqliteVersion,
    String requiredSqlite3mcVersion,
    String requiredSqliteSourceId,
    List<String> requiredCompileOptions) {
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
  }

  private static List<String> validateCompileOptions(List<String> requiredCompileOptions) {
    List<String> normalized =
        ContractDescriptorValidation.copyList(requiredCompileOptions, "requiredCompileOptions")
            .stream()
            .map(
                option ->
                    ContractDescriptorValidation.requireText(option, "requiredCompileOptions"))
            .toList();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("requiredCompileOptions must not be empty.");
    }
    if (new LinkedHashSet<>(normalized).size() != normalized.size()) {
      throw new IllegalArgumentException("requiredCompileOptions must not contain duplicates.");
    }
    return normalized;
  }
}
