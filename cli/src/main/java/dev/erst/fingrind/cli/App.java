package dev.erst.fingrind.cli;

import java.io.InputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.util.Objects;

/** Process entrypoint for the FinGrind CLI adapter. */
public final class App {
  private final CliFactory cliFactory;
  private final ExitHandler exitHandler;
  private final ClockFactory clockFactory;

  /** Creates the production App wired to the default CLI factory and {@code System::exit}. */
  public App() {
    this(
        (inputStream, outputStream, clock) ->
            new FinGrindCli(inputStream, outputStream, clock)::run,
        System::exit,
        Clock::systemUTC);
  }

  App(CliFactory cliFactory, ExitHandler exitHandler, ClockFactory clockFactory) {
    this.cliFactory = Objects.requireNonNull(cliFactory, "cliFactory must not be null");
    this.exitHandler = Objects.requireNonNull(exitHandler, "exitHandler must not be null");
    this.clockFactory = Objects.requireNonNull(clockFactory, "clockFactory must not be null");
  }

  /** Runs the FinGrind CLI and exits with its process status code. */
  public static void main(String[] args) {
    new App().run(args);
  }

  void run(String[] args) {
    int exitCode = cliFactory.create(System.in, System.out, clockFactory.create()).run(args);
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
    CliRunner create(InputStream inputStream, PrintStream outputStream, Clock clock);
  }

  /** Functional interface for terminating the process with an exit code. */
  @FunctionalInterface
  interface ExitHandler {
    /** Terminates the process with the supplied exit code. */
    void exit(int exitCode);
  }

  /** Functional interface for supplying the runtime clock. */
  @FunctionalInterface
  interface ClockFactory {
    /** Creates the clock that should back this process invocation. */
    Clock create();
  }
}
