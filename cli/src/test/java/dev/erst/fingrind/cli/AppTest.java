package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Tests for App process entry point and exit handler wiring. */
class AppTest {
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
            Clock::systemUTC);

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
            Clock::systemUTC);

    app.run(new String[] {"post-entry"});

    assertEquals(3, observedExitCode.get());
  }

  @Test
  void defaultConstructorInitializesWithProductionDefaults() {
    assertNotNull(new App());
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void mainMethodRunsEndToEndWithHelpCommand() throws IOException {
    InputStream previousIn = System.in;
    PrintStream previousOut = System.out;
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      System.setIn(new ByteArrayInputStream(new byte[0]));
      System.setOut(new PrintStream(outputStream, false, StandardCharsets.UTF_8));
      App.main(new String[] {"help"});
    } finally {
      System.setIn(previousIn);
      System.setOut(previousOut);
    }

    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"application\" : \"FinGrind\""));
  }
}
