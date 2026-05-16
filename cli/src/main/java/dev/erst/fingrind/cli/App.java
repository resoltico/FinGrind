package dev.erst.fingrind.cli;

import java.io.InputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.util.Objects;

/** Process entrypoint for the FinGrind CLI adapter. */
public final class App {
  private final CliFactory cliFactory;
  private final ExitHandler exitHandler;
  private final LaunchArgumentsResolver launchArgumentsResolver;

  /** Creates the production App wired to the default CLI factory and {@code System::exit}. */
  public App() {
    this(
        FinGrindCli::standardRunner,
        System::exit,
        LauncherInvocationArguments::resolveForCurrentProcess);
  }

  App(
      CliFactory cliFactory,
      ExitHandler exitHandler,
      LaunchArgumentsResolver launchArgumentsResolver) {
    this.cliFactory = Objects.requireNonNull(cliFactory, "cliFactory must not be null");
    this.exitHandler = Objects.requireNonNull(exitHandler, "exitHandler must not be null");
    this.launchArgumentsResolver =
        Objects.requireNonNull(launchArgumentsResolver, "launchArgumentsResolver must not be null");
  }

  /** Runs the FinGrind CLI and exits with its process status code. */
  public static void main(String[] args) {
    new App().run(args, CliRuntimeEnvironment.process());
  }

  void run(String[] args) {
    run(args, CliRuntimeEnvironment.process());
  }

  void run(String[] args, CliRuntimeEnvironment runtimeEnvironment) {
    Objects.requireNonNull(runtimeEnvironment, "runtimeEnvironment must not be null");
    String[] resolvedArguments;
    try {
      resolvedArguments = launchArgumentsResolver.resolve(args);
    } catch (LauncherInvocationArgumentsException exception) {
      runtimeEnvironment.errorStream().println("error: " + exception.getMessage());
      exitHandler.exit(1);
      return;
    }
    int exitCode = cliFactory.create(runtimeEnvironment).run(resolvedArguments);
    if (exitCode != 0) {
      exitHandler.exit(exitCode);
    }
  }

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

  /** Functional interface for resolving the process argument vector before CLI parsing. */
  @FunctionalInterface
  interface LaunchArgumentsResolver {
    /** Resolves the CLI arguments that should be presented to the FinGrind parser. */
    String[] resolve(String[] processArguments);
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
      return new CliRuntimeEnvironment(System.in, System.out, System.err, Clock.systemUTC());
    }
  }
}
