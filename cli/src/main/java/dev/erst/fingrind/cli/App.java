package dev.erst.fingrind.cli;

import java.io.PrintStream;
import java.util.Objects;

/** Process entrypoint for the FinGrind CLI adapter. */
public final class App {
  private final CliFactory cliFactory;
  private final ExitHandler exitHandler;
  private final PrintStream errorStream;
  private final LaunchArgumentsResolver launchArgumentsResolver;

  /** Creates the production App wired to the default CLI factory and {@code System::exit}. */
  public App() {
    this(
        FinGrindCli::standardRunner,
        System::exit,
        System.err,
        LauncherInvocationArguments::resolveForCurrentProcess);
  }

  App(CliFactory cliFactory, ExitHandler exitHandler) {
    this(
        cliFactory, exitHandler, System.err, LauncherInvocationArguments::resolveForCurrentProcess);
  }

  App(
      CliFactory cliFactory,
      ExitHandler exitHandler,
      PrintStream errorStream,
      LaunchArgumentsResolver launchArgumentsResolver) {
    this.cliFactory = Objects.requireNonNull(cliFactory, "cliFactory must not be null");
    this.exitHandler = Objects.requireNonNull(exitHandler, "exitHandler must not be null");
    this.errorStream = Objects.requireNonNull(errorStream, "errorStream must not be null");
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
    final String[] resolvedArguments;
    try {
      resolvedArguments =
          launchArgumentsResolver.resolve(Objects.requireNonNull(args, "args must not be null"));
    } catch (LauncherInvocationArgumentsException exception) {
      errorStream.println("error: " + exception.getMessage());
      exitHandler.exit(1);
      return;
    }
    int exitCode = cliFactory.create(runtimeEnvironment).run(resolvedArguments);
    if (exitCode != 0) {
      exitHandler.exit(exitCode);
    }
  }

  /** Functional interface for resolving the process argument vector before CLI parsing. */
  @FunctionalInterface
  interface LaunchArgumentsResolver {
    /** Resolves the CLI arguments that should be presented to the FinGrind parser. */
    String[] resolve(String[] processArguments);
  }
}
