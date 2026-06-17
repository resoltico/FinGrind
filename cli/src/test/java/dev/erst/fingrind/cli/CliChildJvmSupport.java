package dev.erst.fingrind.cli;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Shared child-JVM command assembly for CLI tests that must observe real process state. */
final class CliChildJvmSupport {
  private CliChildJvmSupport() {}

  static List<String> childJavaCommand(Class<?> mainClass, String... arguments) {
    List<String> command = new ArrayList<>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    jacocoAppendAgentArgument().ifPresent(command::add);
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add(mainClass.getName());
    command.addAll(List.of(arguments));
    return List.copyOf(command);
  }

  private static Optional<String> jacocoAppendAgentArgument() {
    return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
        .filter(argument -> argument.startsWith("-javaagent:") && argument.contains("jacoco"))
        .findFirst()
        .map(CliChildJvmSupport::appendJacocoAgentArgument);
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
