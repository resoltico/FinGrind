package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAccountRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AccountRegistryLifecycleRejection;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import org.jspecify.annotations.Nullable;

/** Projects account-registry lifecycle rejections into their machine detail payloads. */
final class CliAccountLifecycleRejectionDetails {
  private CliAccountLifecycleRejectionDetails() {}

  static CliRejectionJsonModels.@Nullable RejectionDetails details(
      BookAdministrationRejection rejection) {
    return switch (rejection) {
      case AccountRegistryLifecycleRejection.AccountNotFound missing ->
          new CliAccountRejectionJsonModels.AccountCodeDetails(missing.accountCode().value());
      case AccountRegistryLifecycleRejection.AccountHasDependents dependents ->
          new CliAccountRejectionJsonModels.AccountDependenciesDetails(
              dependents.accountCode().value(),
              dependents.dependencies().stream()
                  .map(dependency -> dependency.wireValue())
                  .toList());
      case AccountRegistryLifecycleRejection.AccountBalanceNotZero balance ->
          new CliAccountRejectionJsonModels.AccountCodeDetails(balance.accountCode().value());
      default -> null;
    };
  }
}
