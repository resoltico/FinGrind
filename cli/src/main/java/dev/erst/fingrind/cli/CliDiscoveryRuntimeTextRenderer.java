package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.SqliteLibraryMode;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentDistributionDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentStorageDescriptor;
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
                List.of("Runtime status", displayRuntimeStatus(runtime.status().wireValue())),
                List.of(
                    "Distribution",
                    displayRuntimeDistribution(environmentDescriptor.distribution())),
                List.of("Book storage", displayStorage(environmentDescriptor.storage())),
                List.of("Book protection", displayProtection(environmentDescriptor.storage())),
                List.of(
                    "Book format",
                    "format v"
                        + environmentDescriptor
                            .storage()
                            .defaultProtectedBookFormat()
                            .formatVersion()
                        + " using "
                        + displayCipher(environmentDescriptor.storage())),
                List.of(
                    "SQLite runtime",
                    displaySqliteRuntimeMode(environmentDescriptor.sqlite().libraryMode())),
                List.of("SQLite", loadedSqliteVersion(runtime)),
                List.of("SQLite3MC", loadedSqlite3mcVersion(runtime)),
                List.of("Issue", runtimeIssue(runtime)),
                List.of(
                    "Full machine inventory",
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

  private static String displayRuntimeStatus(String wireValue) {
    return CliTextDisplay.wireLabel(wireValue);
  }

  private static String displayRuntimeDistribution(
      EnvironmentDistributionDescriptor distributionDescriptor) {
    return switch (distributionDescriptor.runtimeDistribution()) {
      case SELF_CONTAINED_BUNDLE -> "Self-contained public bundle";
      case SOURCE_CHECKOUT_GRADLE -> "Source checkout launcher";
      case DIRECT_JAVA_INVOCATION -> "Developer direct-Java launcher";
      case CONTAINER_IMAGE -> "Container image launcher";
    };
  }

  private static String displayStorage(EnvironmentStorageDescriptor ignored) {
    return "Protected SQLite book file via the managed SQLite native bridge.";
  }

  private static String displayProtection(EnvironmentStorageDescriptor ignored) {
    return "Encrypted keyed protected book";
  }

  private static String displayCipher(EnvironmentStorageDescriptor ignored) {
    return "ChaCha20";
  }

  private static String displaySqliteRuntimeMode(SqliteLibraryMode ignored) {
    return "Managed protected-book runtime";
  }
}
