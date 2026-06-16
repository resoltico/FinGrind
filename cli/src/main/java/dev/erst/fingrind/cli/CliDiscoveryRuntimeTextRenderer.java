package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.PublicCliBundleTarget;
import dev.erst.fingrind.contract.protocol.SqliteLibraryMode;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentPublicationDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentRuntimeDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import java.util.List;
import java.util.stream.Collectors;

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
                    "Current launcher",
                    displayRuntimeDistribution(
                        environmentDescriptor.runtime(), environmentDescriptor.publication())),
                List.of(
                    "Default output mode",
                    displayDefaultOutputMode(environmentDescriptor.runtime())),
                List.of(
                    "Public package surface",
                    displayPublicationSurface(environmentDescriptor.publication())),
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
      EnvironmentRuntimeDescriptor runtimeDescriptor,
      EnvironmentPublicationDescriptor publicationDescriptor) {
    return switch (runtimeDescriptor.runtimeDistribution()) {
      case SELF_CONTAINED_BUNDLE ->
          "Self-contained bundle"
              + activeBundleTargetSuffix(publicationDescriptor)
              + publicationStatusSuffix(publicationDescriptor);
      case SOURCE_CHECKOUT_GRADLE -> "Source checkout launcher";
      case DIRECT_JAVA_INVOCATION -> "Developer direct-Java launcher";
      case CONTAINER_IMAGE -> "Container image launcher";
    };
  }

  private static String displayDefaultOutputMode(EnvironmentRuntimeDescriptor runtimeDescriptor) {
    String defaultOutputMode = runtimeDescriptor.defaultOutputMode().wireValue();
    return runtimeDescriptor.defaultOutputModeSource() == null
        ? defaultOutputMode + " (built in)"
        : defaultOutputMode + " via " + runtimeDescriptor.defaultOutputModeSource();
  }

  private static String displayPublicationSurface(
      EnvironmentPublicationDescriptor publicationDescriptor) {
    String activeBundleTarget =
        publicationDescriptor.currentBundleTarget() == null
            ? ""
            : "; current bundle target: "
                + displayBundleTarget(publicationDescriptor.currentBundleTarget())
                + publicationStatusSuffix(publicationDescriptor);
    String targets =
        publicationDescriptor.supportedPublicCliBundleTargets().isEmpty()
            ? "(no public self-contained bundle targets)"
            : publicationDescriptor.supportedPublicCliBundleTargets().stream()
                .map(CliDiscoveryRuntimeTextRenderer::displayBundleTarget)
                .collect(Collectors.joining(", "));
    return "Public "
        + CliTextDisplay.wireLabel(publicationDescriptor.publicCliDistribution().wireValue())
        + "; supported bundle targets: "
        + targets
        + activeBundleTarget;
  }

  private static String activeBundleTargetSuffix(
      EnvironmentPublicationDescriptor publicationDescriptor) {
    return publicationDescriptor.currentBundleTarget() == null
        ? ""
        : " for " + displayBundleTarget(publicationDescriptor.currentBundleTarget());
  }

  private static String publicationStatusSuffix(
      EnvironmentPublicationDescriptor publicationDescriptor) {
    if (publicationDescriptor.currentBundleTarget() == null) {
      return "";
    }
    return publicationDescriptor
            .supportedPublicCliBundleTargets()
            .contains(publicationDescriptor.currentBundleTarget())
        ? " (published target)"
        : " (unsupported target)";
  }

  private static String displayBundleTarget(PublicCliBundleTarget bundleTarget) {
    return bundleTarget.wireValue().replace('-', ' ');
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
