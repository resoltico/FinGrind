package dev.erst.fingrind.cli;

import java.util.Objects;

/** Shared execution context that routes one parsed CLI command to its concrete executor family. */
final class CliExecutionContext {
  private final CliAdministrativeCommandExecutor administrativeCommandExecutor;
  private final CliDiscoveryCommandExecutor discoveryCommandExecutor;
  private final CliMutationCommandExecutor mutationCommandExecutor;
  private final CliQueryCommandExecutor queryCommandExecutor;
  private final CliReportCommandExecutor reportCommandExecutor;

  CliExecutionContext(
      CliAdministrativeCommandExecutor administrativeCommandExecutor,
      CliDiscoveryCommandExecutor discoveryCommandExecutor,
      CliMutationCommandExecutor mutationCommandExecutor,
      CliQueryCommandExecutor queryCommandExecutor,
      CliReportCommandExecutor reportCommandExecutor) {
    this.administrativeCommandExecutor =
        Objects.requireNonNull(administrativeCommandExecutor, "administrativeCommandExecutor");
    this.discoveryCommandExecutor =
        Objects.requireNonNull(discoveryCommandExecutor, "discoveryCommandExecutor");
    this.mutationCommandExecutor =
        Objects.requireNonNull(mutationCommandExecutor, "mutationCommandExecutor");
    this.queryCommandExecutor =
        Objects.requireNonNull(queryCommandExecutor, "queryCommandExecutor");
    this.reportCommandExecutor =
        Objects.requireNonNull(reportCommandExecutor, "reportCommandExecutor");
  }

  CliAdministrativeCommandExecutor administrative() {
    return administrativeCommandExecutor;
  }

  CliDiscoveryCommandExecutor discovery() {
    return discoveryCommandExecutor;
  }

  CliMutationCommandExecutor mutation() {
    return mutationCommandExecutor;
  }

  CliQueryCommandExecutor query() {
    return queryCommandExecutor;
  }

  CliReportCommandExecutor report() {
    return reportCommandExecutor;
  }
}
