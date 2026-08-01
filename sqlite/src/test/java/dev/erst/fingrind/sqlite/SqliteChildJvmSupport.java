package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Shared child-JVM command assembly for SQLite tests that must observe real process state. */
final class SqliteChildJvmSupport {
  private static final List<String> INHERITED_RUNTIME_PROPERTIES =
      List.of(
          "java.io.tmpdir", "fingrind.source-checkout.root", "fingrind.source-checkout.build-root");

  private SqliteChildJvmSupport() {}

  static List<String> childJavaCommand(Class<?> mainClass, String... arguments) {
    List<String> command = new ArrayList<>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    command.add("--enable-native-access=ALL-UNNAMED");
    jacocoAppendAgentArgument().ifPresent(command::add);
    INHERITED_RUNTIME_PROPERTIES.forEach(
        property ->
            Optional.ofNullable(System.getProperty(property))
                .ifPresent(value -> command.add("-D" + property + "=" + value)));
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add(mainClass.getName());
    command.addAll(List.of(arguments));
    return List.copyOf(command);
  }

  private static Optional<String> jacocoAppendAgentArgument() {
    return ProcessHandle.current().info().arguments().stream()
        .flatMap(Arrays::stream)
        .filter(argument -> argument.startsWith("-javaagent:") && argument.contains("jacoco"))
        .findFirst()
        .map(SqliteChildJvmSupport::appendJacocoAgentArgument);
  }

  private static String appendJacocoAgentArgument(String agentArgument) {
    int optionsSeparator = agentArgument.indexOf('=');
    if (optionsSeparator < 0) {
      return agentArgument + "=append=true";
    }
    String agentPrefix = agentArgument.substring(0, optionsSeparator + 1);
    String agentOptions = agentArgument.substring(optionsSeparator + 1);
    String appendNormalizedOptions =
        agentOptions.contains("append=")
            ? agentOptions.replaceFirst("append=(true|false)", "append=true")
            : agentOptions + ",append=true";
    return agentPrefix + appendNormalizedOptions;
  }
}
