package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Real-workflow CLI tests for missing-book deterministic rejections. */
@NullUnmarked
class FinGrindCliMissingBookWorkflowTest extends FinGrindCliTestSupport {
  @Test
  void run_returnsAdministrationBookNotInitializedWhenDeclareAccountTargetsMissingBook()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("missing-admin.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path declareAccountFile =
        writeNamedRequest(
            "missing-admin-declare.json", declareAccountJson("1000", "Cash", "DEBIT"));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    int exitCode =
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock())
            .run(
                new String[] {
                  "declare-account",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  declareAccountFile.toString()
                });

    assertEquals(2, exitCode);
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"administration-book-not-initialized\""));
  }

  @Test
  void run_returnsQueryBookNotInitializedWhenListAccountsTargetsMissingBook() {
    Path bookFilePath = tempDirectory.resolve("missing-query.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    int exitCode =
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock())
            .run(
                new String[] {
                  "list-accounts",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString()
                });

    assertEquals(2, exitCode);
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"query-book-not-initialized\""));
  }

  @Test
  void run_returnsPostingBookNotInitializedWhenPreflightTargetsMissingBook() throws IOException {
    Path bookFilePath = tempDirectory.resolve("missing-posting.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path requestFile = writeRequest(validRequestJson());
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    int exitCode =
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock())
            .run(
                new String[] {
                  "preflight-entry",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  requestFile.toString()
                });

    assertEquals(2, exitCode);
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"posting-book-not-initialized\""));
  }
}
