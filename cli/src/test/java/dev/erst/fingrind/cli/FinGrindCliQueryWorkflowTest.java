package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Unit tests for {@link FinGrindCli}. */
class FinGrindCliQueryWorkflowTest extends FinGrindCliTestSupport {
  @Test
  void run_queryCommandsThroughDefaultSqliteWorkflow() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path declareCashFile =
        writeNamedRequest("query-declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path declareRevenueFile =
        writeNamedRequest(
            "query-declare-revenue.json", declareAccountJson("2000", "Revenue", "CREDIT"));
    Path bookFilePath = tempDirectory.resolve("query-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(openBookKeyFileArguments(bookFilePath, bookKeyFilePath)));
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                new String[] {
                  "declare-account",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  declareCashFile.toString()
                }));
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                new String[] {
                  "declare-account",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  declareRevenueFile.toString()
                }));
    ByteArrayOutputStream commitOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(commitOutput), fixedClock())
            .run(
                jsonArguments(
                    "post-entry",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--request-file",
                    requestFile.toString())));
    String postingId =
        new ObjectMapper()
            .readTree(commitOutput.toString(StandardCharsets.UTF_8))
            .path("payload")
            .path("postingId")
            .stringValue();
    ByteArrayOutputStream inspectOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(inspectOutput), fixedClock())
            .run(
                jsonArguments(
                    "inspect-book",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString())));
    var inspectEnvelope = new ObjectMapper().readTree(inspectOutput.toByteArray());
    assertEquals(
        CliPublicPaths.redactedValue(bookFilePath),
        inspectEnvelope.path("payload").path("bookFile").stringValue());
    assertEquals("initialized", inspectEnvelope.path("payload").path("state").stringValue());
    ByteArrayOutputStream getPostingOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(getPostingOutput), fixedClock())
            .run(
                jsonArguments(
                    "get-posting",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--posting-id",
                    postingId)));
    assertTrue(getPostingOutput.toString(StandardCharsets.UTF_8).contains(postingId));
    ByteArrayOutputStream listPostingsOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(listPostingsOutput),
                fixedClock())
            .run(
                jsonArguments(
                    "list-postings",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--limit",
                    "10")));
    assertTrue(listPostingsOutput.toString(StandardCharsets.UTF_8).contains(postingId));
    ByteArrayOutputStream balanceOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(balanceOutput), fixedClock())
            .run(
                jsonArguments(
                    "account-balance",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--account-code",
                    "1000")));
    assertJsonContains(balanceOutput, "\"accountCode\":\"1000\"");
    assertTrue(balanceOutput.toString(StandardCharsets.UTF_8).contains("\"balances\""));
  }
}
