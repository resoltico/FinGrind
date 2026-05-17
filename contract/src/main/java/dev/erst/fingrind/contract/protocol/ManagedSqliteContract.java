package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Protocol-owned managed-SQLite version pins shared across runtime, build, and operator seams. */
record ManagedSqliteContract(
    String requiredMinimumSqliteVersion,
    String requiredSqlite3mcVersion,
    String requiredSqliteSourceId,
    String requiredSourcePackageId,
    Map<String, String> vendoredReleaseFiles,
    NativeHardeningContract nativeHardening,
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
    requiredSourcePackageId =
        ContractDescriptorValidation.requireText(
            requiredSourcePackageId, "requiredSourcePackageId");
    vendoredReleaseFiles = validateVendoredReleaseFiles(vendoredReleaseFiles);
    NativeHardeningContract validatedNativeHardening =
        Objects.requireNonNull(nativeHardening, "nativeHardening");
    nativeHardening = validatedNativeHardening;
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

  private static Map<String, String> validateVendoredReleaseFiles(Map<String, String> fileDigests) {
    Objects.requireNonNull(fileDigests, "fileDigests");
    if (fileDigests.isEmpty()) {
      throw new IllegalArgumentException("vendoredReleaseFiles must not be empty.");
    }
    Map<String, String> normalized = new ConcurrentHashMap<>();
    for (Map.Entry<String, String> entry : fileDigests.entrySet()) {
      String relativePath =
          ContractDescriptorValidation.requireText(entry.getKey(), "vendoredReleaseFiles key");
      String digest =
          ContractDescriptorValidation.requireText(entry.getValue(), "vendoredReleaseFiles value");
      if (relativePath.startsWith("/") || relativePath.contains("..")) {
        throw new IllegalArgumentException(
            "vendoredReleaseFiles keys must be normalized relative paths.");
      }
      if (normalized.put(relativePath, digest) != null) {
        throw new IllegalArgumentException("vendoredReleaseFiles must not contain duplicates.");
      }
    }
    return Map.copyOf(normalized);
  }

  /** Contract-owned native-binary hardening policy for managed SQLite builds. */
  record NativeHardeningContract(
      List<String> unixCompilerFlags,
      List<String> linuxLinkerFlags,
      List<String> macosLinkerFlags,
      List<String> windowsCompilerFlags,
      List<String> windowsLinkerFlags) {
    NativeHardeningContract {
      unixCompilerFlags = validateFlagList(unixCompilerFlags, "unixCompilerFlags");
      linuxLinkerFlags = validateFlagList(linuxLinkerFlags, "linuxLinkerFlags");
      macosLinkerFlags = validateFlagList(macosLinkerFlags, "macosLinkerFlags");
      windowsCompilerFlags = validateFlagList(windowsCompilerFlags, "windowsCompilerFlags");
      windowsLinkerFlags = validateFlagList(windowsLinkerFlags, "windowsLinkerFlags");
    }

    private static List<String> validateFlagList(List<String> flags, String fieldName) {
      List<String> normalized =
          ContractDescriptorValidation.copyList(flags, fieldName).stream()
              .map(flag -> ContractDescriptorValidation.requireText(flag, fieldName))
              .toList();
      if (new LinkedHashSet<>(normalized).size() != normalized.size()) {
        throw new IllegalArgumentException(fieldName + " must not contain duplicates.");
      }
      return normalized;
    }
  }
}
