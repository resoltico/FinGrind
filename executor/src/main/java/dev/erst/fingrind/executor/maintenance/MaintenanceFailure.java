package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.contract.runtime.ContractFailurePaths;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Local maintenance-layer failure value that isolates the workflow from published decisions. */
public record MaintenanceFailure(
    ContractErrors.Descriptor descriptor,
    String message,
    @Nullable String hint,
    @Nullable String argument,
    @Nullable ContractFailurePaths paths,
    @Nullable ContractFailureDetails details) {
  public MaintenanceFailure {
    ContractFailure normalized =
        new ContractFailure(descriptor, message, hint, argument, paths, details, null);
    descriptor = normalized.descriptor();
    message = normalized.message();
    hint = normalized.hint();
    argument = normalized.argument();
    paths = normalized.paths();
    details = normalized.details();
  }

  /** Projects one published contract failure into the local maintenance failure shape. */
  public static MaintenanceFailure fromContractFailure(ContractFailure failure) {
    Objects.requireNonNull(failure, "failure");
    return new MaintenanceFailure(
        failure.descriptor(),
        failure.message(),
        failure.hint(),
        failure.argument(),
        failure.paths(),
        failure.details());
  }

  /** Projects this local maintenance failure back into the published contract failure shape. */
  public ContractFailure toContractFailure() {
    return new ContractFailure(descriptor, message, hint, argument, paths, details, null);
  }
}
