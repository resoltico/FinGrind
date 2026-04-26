package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Tests for App process entry point and exit handler wiring. */
class AppTest {
  private record SystemStreams(InputStream in, PrintStream out) {}

  @Test
  void runDelegatesToCliAndDoesNotCallExitHandlerOnSuccess() {
    AtomicInteger observedExitCode = new AtomicInteger(-1);
    AtomicReference<String> observedArgs = new AtomicReference<>();
    App app =
        new App(
            (inputStream, outputStream, clock) ->
                args -> {
                  observedArgs.set(String.join(",", args));
                  return 0;
                },
            observedExitCode::set,
            Clock::systemUTC,
            System.err,
            args -> args);

    app.run(new String[] {"help"});

    assertEquals("help", observedArgs.get());
    assertEquals(-1, observedExitCode.get());
  }

  @Test
  void runCallsExitHandlerForNonZeroExitCodes() {
    AtomicInteger observedExitCode = new AtomicInteger(-1);
    App app =
        new App(
            (inputStream, outputStream, clock) -> args -> 3,
            observedExitCode::set,
            Clock::systemUTC,
            System.err,
            args -> args);

    app.run(new String[] {"post-entry"});

    assertEquals(3, observedExitCode.get());
  }

  @Test
  void defaultConstructorInitializesWithProductionDefaults() {
    assertNotNull(new App());
  }

  @Test
  void runReportsLauncherArgumentResolutionFailuresAndSkipsCliInvocation() {
    ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
    AtomicBoolean cliInvoked = new AtomicBoolean(false);
    AtomicInteger observedExitCode = new AtomicInteger(-1);
    try (PrintStream redirectedError =
        new PrintStream(errorStream, false, StandardCharsets.UTF_8)) {
      App app =
          new App(
              (inputStream, outputStream, clock) ->
                  args -> {
                    cliInvoked.set(true);
                    return 0;
                  },
              observedExitCode::set,
              Clock::systemUTC,
              redirectedError,
              args -> {
                throw new LauncherInvocationArgumentsException("staged launcher arguments failed");
              });
      app.run(new String[] {"help"});
    }

    assertEquals(1, observedExitCode.get());
    assertTrue(errorStream.toString(StandardCharsets.UTF_8).contains("staged launcher arguments"));
    assertFalse(cliInvoked.get());
  }

  @Test
  void mainMethodRunsEndToEndWithHelpCommand() throws IOException {
    SystemStreams previousStreams = new SystemStreams(System.in, System.out);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (PrintStream redirectedOut = new PrintStream(outputStream, false, StandardCharsets.UTF_8)) {
      System.setIn(new ByteArrayInputStream(new byte[0]));
      System.setOut(redirectedOut);
      App.main(new String[] {"help"});
    } finally {
      System.setIn(previousStreams.in());
      System.setOut(previousStreams.out());
    }

    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("FinGrind Help"));
  }
}
