package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import java.util.List;

/** Renders runtime and version discovery text for operators. */
final class CliDiscoveryRuntimeTextRenderer {
  private CliDiscoveryRuntimeTextRenderer() {}

  static String renderEnvironmentText(EnvironmentDescriptor environmentDescriptor) {
    EnvironmentSqliteDescriptor.RuntimeState runtime = environmentDescriptor.sqlite().runtime();
    String summary =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Runtime status", runtime.status().wireValue()),
                List.of(
                    "Runtime",
                    environmentDescriptor.distribution().runtimeDistribution().wireValue()),
                List.of(
                    "Storage",
                    environmentDescriptor.storage().storageDriver().wireValue()
                        + " / "
                        + environmentDescriptor.storage().storageEngine().wireValue()),
                List.of(
                    "Protection", environmentDescriptor.storage().bookProtectionMode().wireValue()),
                List.of(
                    "Book format",
                    "v"
                        + environmentDescriptor
                            .storage()
                            .defaultProtectedBookFormat()
                            .formatVersion()
                        + " / "
                        + environmentDescriptor
                            .storage()
                            .defaultProtectedBookFormat()
                            .cipher()
                            .wireValue()),
                List.of("SQLite runtime", environmentDescriptor.sqlite().libraryMode().wireValue()),
                List.of("SQLite", loadedSqliteVersion(runtime)),
                List.of("SQLite3MC", loadedSqlite3mcVersion(runtime)),
                List.of("Issue", runtimeIssue(runtime)),
                List.of(
                    "Full inventory",
                    CliInvocationText.commandExample(OperationId.ENVIRONMENT) + " --output json")),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    return CliTextFormat.renderTitledBlock("FinGrind Environment", summary);
  }

  static String renderVersionText(VersionDescriptor versionDescriptor) {
    return CliTextFormat.renderTitledBlock(
        versionDescriptor.application(),
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Version", versionDescriptor.version()),
                List.of("Description", versionDescriptor.description())),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH));
  }

  private static String loadedSqliteVersion(EnvironmentSqliteDescriptor.RuntimeState runtime) {
    return switch (runtime) {
      case EnvironmentSqliteDescriptor.ReadyRuntime ready -> ready.loadedSqliteVersion();
      case EnvironmentSqliteDescriptor.IncompatibleRuntime incompatible ->
          incompatible.loadedSqliteVersion();
      case EnvironmentSqliteDescriptor.FailedRuntime ignored -> "(none)";
      case EnvironmentSqliteDescriptor.UnavailableRuntime ignored -> "(none)";
    };
  }

  private static String loadedSqlite3mcVersion(EnvironmentSqliteDescriptor.RuntimeState runtime) {
    return switch (runtime) {
      case EnvironmentSqliteDescriptor.ReadyRuntime ready -> ready.loadedSqlite3mcVersion();
      case EnvironmentSqliteDescriptor.IncompatibleRuntime incompatible ->
          incompatible.loadedSqlite3mcVersion();
      case EnvironmentSqliteDescriptor.FailedRuntime ignored -> "(none)";
      case EnvironmentSqliteDescriptor.UnavailableRuntime ignored -> "(none)";
    };
  }

  private static String runtimeIssue(EnvironmentSqliteDescriptor.RuntimeState runtime) {
    return switch (runtime) {
      case EnvironmentSqliteDescriptor.ReadyRuntime ignored -> "(none)";
      case EnvironmentSqliteDescriptor.UnavailableRuntime unavailable -> unavailable.runtimeIssue();
      case EnvironmentSqliteDescriptor.FailedRuntime failed -> failed.runtimeIssue();
      case EnvironmentSqliteDescriptor.IncompatibleRuntime incompatible ->
          incompatible.runtimeIssue();
    };
  }
}
