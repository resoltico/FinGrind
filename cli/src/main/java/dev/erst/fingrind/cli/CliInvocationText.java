package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import java.util.Locale;
import java.util.Objects;

/** User-facing command-text helpers that keep hints aligned with the active launcher surface. */
final class CliInvocationText {
  private CliInvocationText() {}

  static String commandExample(OperationId operationId) {
    return launcherCommand() + " " + ProtocolCatalog.operationName(operationId);
  }

  static String rewriteInvocationPrefix(String text) {
    Objects.requireNonNull(text, "text");
    String launcherCommand = launcherCommand();
    if ("fingrind".equals(launcherCommand) || !text.startsWith("fingrind ")) {
      return text;
    }
    return launcherCommand + text.substring("fingrind".length());
  }

  static String helpSyntaxHint() {
    return "Run '"
        + commandExample(OperationId.HELP)
        + "' to inspect the supported command syntax.";
  }

  static String helpExamplesHint() {
    return "Run '"
        + commandExample(OperationId.HELP)
        + "' to inspect the supported commands and examples.";
  }

  static String launcherCommand() {
    return launcherCommandFor(FinGrindCli.runtimeDistribution(), System.getProperty("os.name", ""));
  }

  static String launcherCommandFor(String runtimeDistribution, String osName) {
    Objects.requireNonNull(runtimeDistribution, "runtimeDistribution");
    Objects.requireNonNull(osName, "osName");
    if (FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION.equals(runtimeDistribution)) {
      return isWindows(osName)
          ? ProtocolCatalog.bundleLauncherCommand(
              dev.erst.fingrind.contract.protocol.PublicCliBundleTarget.WINDOWS_X86_64)
          : ProtocolCatalog.bundleLauncherCommand(
              dev.erst.fingrind.contract.protocol.PublicCliBundleTarget.MACOS_AARCH64);
    }
    return "fingrind";
  }

  private static boolean isWindows(String osName) {
    return osName.toLowerCase(Locale.ROOT).contains("win");
  }
}
