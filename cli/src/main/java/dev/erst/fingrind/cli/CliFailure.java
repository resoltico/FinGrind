package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Structured CLI failure payload used for deterministic error envelopes. */
record CliFailure(
    String code,
    String message,
    @Nullable String hint,
    @Nullable String argument,
    CliErrorJsonModels.@Nullable ErrorDetails details) {
  CliFailure(String code, String message, @Nullable String hint, @Nullable String argument) {
    this(code, message, hint, argument, null);
  }

  CliFailure {
    code = requireText(code, "code");
    message = requireText(message, "message");
    hint = requireOptionalText(hint);
    argument = requireOptionalText(argument);
    details = requireSupportedDetails(details);
  }

  static CliFailure fromContractFailure(ContractFailure failure) {
    return new CliFailure(
        failure.code(), failure.message(), failure.hint(), failure.argument(), null);
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
