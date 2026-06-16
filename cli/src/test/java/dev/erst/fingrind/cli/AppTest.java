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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Tests for App process entry point and exit handler wiring. */
class AppTest {
  private record SystemStreams(InputStream in, PrintStream out, PrintStream err) {}

  private static final CliRuntimeEnvironment RUNTIME_ENVIRONMENT =
      new CliRuntimeEnvironment(
          new ByteArrayInputStream(new byte[0]),
          new PrintStream(new ByteArrayOutputStream(), false, StandardCharsets.UTF_8),
          new PrintStream(new ByteArrayOutputStream(), false, StandardCharsets.UTF_8),
          Clock.fixed(Instant.parse("2026-05-15T10:00:00Z"), ZoneOffset.UTC));

  @Test
  void runDelegatesToCliAndDoesNotCallExitHandlerOnSuccess() {
    AtomicInteger observedExitCode = new AtomicInteger(-1);
    AtomicReference<String> observedArgs = new AtomicReference<>();
    App app =
        new App(
            runtimeEnvironment ->
                args -> {
                  observedArgs.set(String.join(",", args));
                  return 0;
                },
            observedExitCode::set);

    app.run(new String[] {"help"}, RUNTIME_ENVIRONMENT);

    assertEquals("help", observedArgs.get());
    assertEquals(-1, observedExitCode.get());
  }

  @Test
  void runCallsExitHandlerForNonZeroExitCodes() {
    AtomicInteger observedExitCode = new AtomicInteger(-1);
    App app =
        new App(runtimeEnvironment -> args -> 3, observedExitCode::set, System.err, args -> args);

    app.run(new String[] {"post-entry"}, RUNTIME_ENVIRONMENT);

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
              runtimeEnvironment ->
                  args -> {
                    cliInvoked.set(true);
                    return 0;
                  },
              observedExitCode::set,
              redirectedError,
              args -> {
                throw new LauncherInvocationArgumentsException("staged launcher arguments failed");
              });

      app.run(new String[] {"help"}, RUNTIME_ENVIRONMENT);
    }

    assertEquals(1, observedExitCode.get());
    assertTrue(errorStream.toString(StandardCharsets.UTF_8).contains("staged launcher arguments"));
    assertFalse(cliInvoked.get());
  }

  @Test
  void runWithoutExplicitRuntimeEnvironmentUsesTheCurrentProcessStreamsAndClock() {
    SystemStreams previousStreams = new SystemStreams(System.in, System.out, System.err);
    ByteArrayInputStream redirectedInput = new ByteArrayInputStream(new byte[0]);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
    AtomicReference<CliRuntimeEnvironment> observedRuntime = new AtomicReference<>();
    try (PrintStream redirectedOut = new PrintStream(outputStream, false, StandardCharsets.UTF_8);
        PrintStream redirectedError = new PrintStream(errorStream, false, StandardCharsets.UTF_8)) {
      App app =
          new App(
              runtimeEnvironment -> {
                observedRuntime.set(runtimeEnvironment);
                return args -> {
                  runtimeEnvironment.outputStream().print("runtime-out");
                  runtimeEnvironment.errorStream().print("runtime-err");
                  return 0;
                };
              },
              exitCode -> {},
              System.err,
              args -> args);
      System.setIn(redirectedInput);
      System.setOut(redirectedOut);
      System.setErr(redirectedError);

      app.run(new String[] {"help"});
    } finally {
      System.setIn(previousStreams.in());
      System.setOut(previousStreams.out());
      System.setErr(previousStreams.err());
    }

    CliRuntimeEnvironment runtimeEnvironment = observedRuntime.get();
    assertNotNull(runtimeEnvironment);
    assertEquals(redirectedInput, runtimeEnvironment.inputStream());
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("runtime-out"));
    assertTrue(errorStream.toString(StandardCharsets.UTF_8).contains("runtime-err"));
    assertEquals(Clock.systemUTC().getZone(), runtimeEnvironment.clock().getZone());
  }

  @Test
  void mainMethodRunsEndToEndWithHelpCommand() throws IOException {
    SystemStreams previousStreams = new SystemStreams(System.in, System.out, System.err);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (PrintStream redirectedOut = new PrintStream(outputStream, false, StandardCharsets.UTF_8)) {
      System.setIn(new ByteArrayInputStream(new byte[0]));
      System.setOut(redirectedOut);
      App.main(new String[] {"help", "--output", "text"});
    } finally {
      System.setIn(previousStreams.in());
      System.setOut(previousStreams.out());
      System.setErr(previousStreams.err());
    }

    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("FinGrind Help"));
  }
}
