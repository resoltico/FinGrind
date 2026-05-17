package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

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
    return rewriteInvocationPrefix(text, launcherCommandForCurrentRuntime());
  }

  static String rewriteInvocationPrefix(String text, String launcherCommand) {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(launcherCommand, "launcherCommand");
    Matcher matcher = COMMAND_PREFIX_PATTERN.matcher(text);
    if (!matcher.find()) {
      return text;
    }
    return matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + launcherCommand));
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
    return launcherCommandForCurrentRuntime(
        configuredRuntimeDistribution(),
        System.getProperty("os.name", ""),
        CliInvocationText.class.getModule(),
        currentCodeSourceFileName(CliInvocationText.class.getProtectionDomain().getCodeSource()));
  }

  static String launcherCommandForCurrentRuntime(
      @Nullable String configuredRuntimeDistribution,
      String osName,
      Module launchModule,
      @Nullable String codeSourceFileName) {
    Objects.requireNonNull(osName, "osName");
    Objects.requireNonNull(launchModule, "launchModule");
    String normalizedDistribution =
        normalizeConfiguredRuntimeDistribution(configuredRuntimeDistribution);
    if (normalizedDistribution != null) {
      return launcherCommandFor(normalizedDistribution, osName);
    }
    String normalizedJarFileName = normalizeCodeSourceJarFileName(codeSourceFileName);
    if (!launchModule.isNamed() && normalizedJarFileName != null) {
      return rawJarLauncherCommand(normalizedJarFileName);
    }
    return NEUTRAL_LAUNCHER_COMMAND;
  }

  private static @Nullable String configuredRuntimeDistribution() {
    return normalizeConfiguredRuntimeDistribution(
        System.getProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY));
  }

  static @Nullable String normalizeConfiguredRuntimeDistribution(
      @Nullable String configuredRuntimeDistribution) {
    if (configuredRuntimeDistribution == null) {
      return null;
    }
    String normalizedDistribution = configuredRuntimeDistribution.strip();
    return normalizedDistribution.isEmpty() ? null : normalizedDistribution;
  }

  static @Nullable String currentCodeSourceFileName(@Nullable CodeSource codeSource) {
    if (codeSource == null || codeSource.getLocation() == null) {
      return null;
    }
    try {
      Path codeSourcePath = Path.of(codeSource.getLocation().toURI());
      Path fileName = codeSourcePath.getFileName();
      return fileName == null ? null : normalizeCodeSourceJarFileName(fileName.toString());
    } catch (RuntimeException | URISyntaxException ignored) {
      return null;
    }
  }

  static @Nullable String normalizeCodeSourceJarFileName(@Nullable String codeSourceFileName) {
    if (codeSourceFileName == null) {
      return null;
    }
    String normalizedFileName = codeSourceFileName.strip();
    if (normalizedFileName.isEmpty() || !normalizedFileName.endsWith(".jar")) {
      return null;
    }
    return normalizedFileName;
  }

  private static String rawJarLauncherCommand(String jarFileName) {
    return "java --enable-native-access=ALL-UNNAMED -jar " + jarFileName;
  }
}
