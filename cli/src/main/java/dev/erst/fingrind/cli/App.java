package dev.erst.fingrind.cli;

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
}
