package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Exercises every owned lifecycle register through the protected SQLite CLI workflow. */
class FinGrindCliLifecycleRegisterCommandTest extends FinGrindCliTestSupport {
  @Test
  void run_projectsEveryOwnedLifecycleRegisterFromOneProtectedBook() throws IOException {
    Path bookFilePath = tempDirectory.resolve("lifecycle-register-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);

    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(openBookKeyFileArguments(bookFilePath, bookKeyFilePath)));

    assertRegisterSucceeds(bookFilePath, bookKeyFilePath, "fixed-asset-register");
    assertRegisterSucceeds(bookFilePath, bookKeyFilePath, "financing-register");
    assertRegisterSucceeds(bookFilePath, bookKeyFilePath, "realized-foreign-exchange-register");
  }

  private void assertRegisterSucceeds(Path bookFilePath, Path bookKeyFilePath, String command) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int exitCode =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(output), fixedClock())
            .run(
                jsonArguments(
                    command,
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString()));

    assertEquals(0, exitCode);
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("\"status\":\"ok\""));
  }
}
