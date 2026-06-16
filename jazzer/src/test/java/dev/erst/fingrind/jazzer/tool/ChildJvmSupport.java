package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Launches JaCoCo-instrumented child JVMs for public entrypoint coverage tests. */
final class ChildJvmSupport {
  private ChildJvmSupport() {}

  static ChildProcessResult runMainClass(Class<?> mainClass, List<String> arguments)
      throws IOException {
    return runMainClass(mainClass, arguments, environment -> {});
  }

  static ChildProcessResult runMainClass(
      Class<?> mainClass,
      List<String> arguments,
      Consumer<Map<String, String>> environmentCustomizer)
      throws IOException {
    List<String> command = new ArrayList<>();
    command.add(javaCommand());
    command.addAll(jacocoAgentArguments());
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add(mainClass.getName());
    command.addAll(arguments);
    ProcessBuilder processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
    environmentCustomizer.accept(processBuilder.environment());
    Process process = processBuilder.start();
    try (process;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        var processOutput = process.getInputStream()) {
      processOutput.transferTo(output);
      return new ChildProcessResult(waitFor(process, command), output.toString(UTF_8));
    }
  }

  private static List<String> jacocoAgentArguments() {
    List<String> agentArguments = new ArrayList<>();
    for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
      if (!argument.startsWith("-javaagent:")) {
        continue;
      }
      if (argument.contains("jacoco")) {
        agentArguments.add(argument.contains("append=") ? argument : argument + ",append=true");
      }
    }
    return List.copyOf(agentArguments);
  }

  private static String javaCommand() {
    return System.getProperty("java.home") + "/bin/java";
  }

  private static int waitFor(Process process, List<String> command) throws IOException {
    try {
      return process.waitFor();
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      throw new IOException(
          "Interrupted while running child JVM: " + String.join(" ", command),
          interruptedException);
    }
  }

  record ChildProcessResult(int exitCode, String output) {}
}
