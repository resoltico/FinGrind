package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.SystemUtcClock;
import java.io.InputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.util.Objects;

/** Functional interface for running one CLI invocation. */
@FunctionalInterface
interface CliRunner {
  /** Runs the CLI and returns the process exit code for this invocation. */
  int run(String[] args);
}

/** Functional interface for creating one CLI runner. */
@FunctionalInterface
interface CliFactory {
  /** Creates one CLI runner bound to the supplied process streams and clock. */
  CliRunner create(CliRuntimeEnvironment runtimeEnvironment);
}

/** Functional interface for terminating the process with an exit code. */
@FunctionalInterface
interface ExitHandler {
  /** Terminates the process with the supplied exit code. */
  void exit(int exitCode);
}

/** Runtime-owned console and clock inputs for one CLI process invocation. */
record CliRuntimeEnvironment(
    InputStream inputStream, PrintStream outputStream, PrintStream errorStream, Clock clock) {
  CliRuntimeEnvironment {
    Objects.requireNonNull(inputStream, "inputStream");
    Objects.requireNonNull(outputStream, "outputStream");
    Objects.requireNonNull(errorStream, "errorStream");
    Objects.requireNonNull(clock, "clock");
  }

  static CliRuntimeEnvironment process() {
    return new CliRuntimeEnvironment(System.in, System.out, System.err, SystemUtcClock.instance());
  }
}
