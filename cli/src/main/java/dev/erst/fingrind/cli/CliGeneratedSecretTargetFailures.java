package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import java.nio.file.Path;
import java.util.Objects;

/** Builds deterministic failures for generated secret targets that alias required input paths. */
final class CliGeneratedSecretTargetFailures {
  private CliGeneratedSecretTargetFailures() {}

  static CliArgumentsException occupiedByRequiredSource(
      String targetOption, String sourceOption, Path existingSourcePath) {
    Objects.requireNonNull(targetOption, "targetOption");
    Objects.requireNonNull(sourceOption, "sourceOption");
    Objects.requireNonNull(existingSourcePath, "existingSourcePath");
    return new CliArgumentsException(
        ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED.code(),
        targetOption,
        targetOption
            + " resolves to an existing required source "
            + sourceOption
            + "; generated secret targets must not already exist and are never overwritten.",
        "Choose an absent "
            + targetOption
            + " path distinct from "
            + sourceOption
            + ", then rerun the command.",
        existingSourcePath);
  }
}
