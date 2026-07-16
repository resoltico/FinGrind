package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractResponseCatalog;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Structured CLI failure payload used for deterministic error envelopes. */
record CliFailure(
    String code,
    String message,
    @Nullable String hint,
    @Nullable String argument,
    CliErrorJsonModels.@Nullable ErrorDetails details,
    @Nullable Path path,
    List<Path> relatedPaths) {
  CliFailure(String code, String message, @Nullable String hint, @Nullable String argument) {
    this(code, message, hint, argument, null, null, List.of());
  }

  CliFailure(
      String code,
      String message,
      @Nullable String hint,
      @Nullable String argument,
      CliErrorJsonModels.@Nullable ErrorDetails details) {
    this(code, message, hint, argument, details, null, List.of());
  }

  CliFailure(
      String code,
      String message,
      @Nullable String hint,
      @Nullable String argument,
      @Nullable Path path) {
    this(code, message, hint, argument, null, path, List.of());
  }

  CliFailure(
      String code,
      String message,
      @Nullable String hint,
      @Nullable String argument,
      Path path,
      List<Path> relatedPaths) {
    this(code, message, hint, argument, null, path, relatedPaths);
  }

  CliFailure {
    code = requireText(code, "code");
    ContractResponseCatalog.failureCategoryFor(code);
    message = requireText(message, "message");
    hint = requireOptionalText(hint);
    argument = requireOptionalText(argument);
    details = requireSupportedDetails(details);
    relatedPaths = List.copyOf(Objects.requireNonNull(relatedPaths, "relatedPaths"));
    if (path == null && !relatedPaths.isEmpty()) {
      throw new IllegalArgumentException("relatedPaths require one primary path.");
    }
  }

  static CliFailure fromContractFailure(ContractFailure failure) {
    var paths = failure.paths();
    if (paths != null) {
      return new CliFailure(
          failure.code(),
          failure.message(),
          failure.hint(),
          failure.argument(),
          paths.path(),
          paths.relatedPaths());
    }
    return new CliFailure(
        failure.code(),
        failure.message(),
        failure.hint(),
        failure.argument(),
        null,
        null,
        List.of());
  }

  private static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null.");
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return normalized;
  }

  private static @Nullable String requireOptionalText(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      return null;
    }
    return normalized;
  }

  private static CliErrorJsonModels.@Nullable ErrorDetails requireSupportedDetails(
      CliErrorJsonModels.@Nullable ErrorDetails value) {
    if (value == null) {
      return null;
    }
    return value;
  }
}
