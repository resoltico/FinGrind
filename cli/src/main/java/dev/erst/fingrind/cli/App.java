package dev.erst.fingrind.cli;

import dev.erst.fingrind.sqlite.SqliteRuntime;
import java.io.PrintStream;
import java.util.Objects;

/** Process entrypoint for the FinGrind CLI adapter. */
public final class App {
  private final CliFactory cliFactory;
  private final ExitHandler exitHandler;
  private final PrintStream errorStream;
  private final LaunchArgumentsResolver launchArgumentsResolver;
  private final ProcessResourceReleaser processResourceReleaser;

  /** Creates the production App wired to the default CLI factory and {@code System::exit}. */
  public App() {
    this(
        FinGrindCli::standardRunner,
        System::exit,
        System.err,
        LauncherInvocationArguments::resolveForCurrentProcess,
        SqliteRuntime::releaseProcessScopedRuntime);
  }

  App(CliFactory cliFactory, ExitHandler exitHandler) {
    this(
        cliFactory,
        exitHandler,
        System.err,
        LauncherInvocationArguments::resolveForCurrentProcess,
        () -> {});
  }

  App(
      CliFactory cliFactory,
      ExitHandler exitHandler,
      PrintStream errorStream,
      LaunchArgumentsResolver launchArgumentsResolver) {
    this(cliFactory, exitHandler, errorStream, launchArgumentsResolver, () -> {});
  }

  App(
      CliFactory cliFactory,
      ExitHandler exitHandler,
      PrintStream errorStream,
      LaunchArgumentsResolver launchArgumentsResolver,
      ProcessResourceReleaser processResourceReleaser) {
    this.cliFactory = Objects.requireNonNull(cliFactory, "cliFactory must not be null");
    this.exitHandler = Objects.requireNonNull(exitHandler, "exitHandler must not be null");
    this.errorStream = Objects.requireNonNull(errorStream, "errorStream must not be null");
    this.launchArgumentsResolver =
        Objects.requireNonNull(launchArgumentsResolver, "launchArgumentsResolver must not be null");
    this.processResourceReleaser =
        Objects.requireNonNull(processResourceReleaser, "processResourceReleaser must not be null");
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
    int exitCode;
    try {
      String[] resolvedArguments =
          launchArgumentsResolver.resolve(Objects.requireNonNull(args, "args must not be null"));
      exitCode = cliFactory.create(runtimeEnvironment).run(resolvedArguments);
    } catch (LauncherInvocationArgumentsException exception) {
      errorStream.println("error: " + exception.getMessage());
      exitHandler.exit(1);
      return;
    } finally {
      processResourceReleaser.release();
    }
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

  /** Releases native process resources after one terminal CLI invocation. */
  @FunctionalInterface
  interface ProcessResourceReleaser {
    /** Releases the resources that are safe to discard after the command has completed. */
    void release();
  }
}
