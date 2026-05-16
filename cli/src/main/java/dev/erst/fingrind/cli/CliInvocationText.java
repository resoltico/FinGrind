package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** User-facing command-text helpers for neutral launcher examples and hints. */
final class CliInvocationText {
  private static final String NEUTRAL_LAUNCHER_COMMAND = "fingrind";
  private static final Pattern COMMAND_PREFIX_PATTERN =
      Pattern.compile(
          "(^|\\|\\s*)(?:fingrind|\\./bin/fingrind|\\.\\\\bin\\\\fingrind\\.ps1|\\./scripts/source-checkout-cli\\.sh|\\.\\\\scripts\\\\source-checkout-cli\\.ps1|\\./scripts/direct-java-cli\\.sh|\\.\\\\scripts\\\\direct-java-cli\\.ps1)(?=\\s|$)");

  private CliInvocationText() {}

  static String commandExample(OperationId operationId) {
    Objects.requireNonNull(operationId, "operationId");
    return launcherCommandForCurrentRuntime() + " " + ProtocolCatalog.operationName(operationId);
  }

  static String rewriteInvocationPrefix(String text) {
    Objects.requireNonNull(text, "text");
    Matcher matcher = COMMAND_PREFIX_PATTERN.matcher(text);
    if (!matcher.find()) {
      return text;
    }
    return matcher.replaceFirst(
        Matcher.quoteReplacement(matcher.group(1) + launcherCommandForCurrentRuntime()));
  }

  static String helpSyntaxHint() {
    return "Run '"
        + commandExample(OperationId.HELP)
        + "' to inspect the supported command syntax.";
  }

  static String helpSyntaxHint(OperationId operationId) {
    Objects.requireNonNull(operationId, "operationId");
    return "Run '"
        + commandExample(OperationId.HELP)
        + " "
        + ProtocolCatalog.operationName(operationId)
        + "' to inspect the supported command syntax.";
  }

  static String helpExamplesHint() {
    return "Run '"
        + commandExample(OperationId.HELP)
        + "' to inspect the supported commands and examples.";
  }

  static String launcherCommandFor(String runtimeDistribution, String osName) {
    Objects.requireNonNull(runtimeDistribution, "runtimeDistribution");
    Objects.requireNonNull(osName, "osName");
    boolean windows = osName.toLowerCase(Locale.ROOT).contains("win");
    RuntimeDistribution distribution;
    try {
      distribution = RuntimeDistribution.fromWireValue(runtimeDistribution);
    } catch (IllegalArgumentException ignored) {
      return NEUTRAL_LAUNCHER_COMMAND;
    }
    return switch (distribution) {
      case DIRECT_JAVA_INVOCATION -> ProtocolCatalog.directJavaLauncherCommand(windows);
      case SOURCE_CHECKOUT_GRADLE -> ProtocolCatalog.sourceCheckoutLauncherCommand(windows);
      case CONTAINER_IMAGE -> ProtocolCatalog.containerLauncherCommand();
      case SELF_CONTAINED_BUNDLE -> NEUTRAL_LAUNCHER_COMMAND;
    };
  }

  private static String launcherCommandForCurrentRuntime() {
    return launcherCommandFor(FinGrindCli.runtimeDistribution(), System.getProperty("os.name", ""));
  }
}
