package dev.erst.fingrind.cli;

import java.util.Objects;

/** Process entrypoint for the FinGrind CLI adapter. */
public final class App {
  private final CliFactory cliFactory;
  private final ExitHandler exitHandler;

  /** Creates the production App wired to the default CLI factory and {@code System::exit}. */
  public App() {
    this(FinGrindCli::standardRunner, System::exit);
  }

  App(CliFactory cliFactory, ExitHandler exitHandler) {
    this.cliFactory = Objects.requireNonNull(cliFactory, "cliFactory must not be null");
    this.exitHandler = Objects.requireNonNull(exitHandler, "exitHandler must not be null");
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
    int exitCode = cliFactory.create(runtimeEnvironment).run(args.clone());
    if (exitCode != 0) {
      exitHandler.exit(exitCode);
    }
  }
}
