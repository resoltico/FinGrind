package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.ContractErrors;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Unit tests for {@link FinGrindCli}. */
@NullUnmarked
class FinGrindCliMutationWorkflowTest extends FinGrindCliTestSupport {
  @Test
  void run_rekeyBookThroughDefaultSqliteWorkflowRotatesBookKey() throws IOException {
    Path bookFilePath = tempDirectory.resolve("rekey-books").resolve("entity.sqlite");
    Path currentBookKeyFilePath = writeBookKey(bookFilePath, TEST_BOOK_KEY);
    Path replacementBookKeyFilePath = writeNamedBookKey("replacement-book.key", "replacement-key");

    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    FinGrindCli openCli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(openOutput), fixedClock());
    assertEquals(
        0,
        openCli.run(
            new String[] {
              "open-book",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              currentBookKeyFilePath.toString()
            }));

    ByteArrayOutputStream rekeyOutput = new ByteArrayOutputStream();
    FinGrindCli rekeyCli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(rekeyOutput), fixedClock());
    assertEquals(
        0,
        rekeyCli.run(
            new String[] {
              "rekey-book",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              currentBookKeyFilePath.toString(),
              "--new-book-key-file",
              replacementBookKeyFilePath.toString()
            }));
    assertTrue(rekeyOutput.toString(StandardCharsets.UTF_8).contains("\"bookFile\""));

    ByteArrayOutputStream oldKeyOutput = new ByteArrayOutputStream();
    FinGrindCli oldKeyCli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(oldKeyOutput), fixedClock());
    assertEquals(
        2,
        oldKeyCli.run(
            new String[] {
              "list-accounts",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              currentBookKeyFilePath.toString()
            }));
    JsonNode oldKeyFailureEnvelope = new ObjectMapper().readTree(oldKeyOutput.toByteArray());
    assertEquals(
        ContractErrors.Descriptor.BOOK_AUTHENTICATION_FAILED.code(),
        oldKeyFailureEnvelope.path("code").asString());
    assertFalse(oldKeyFailureEnvelope.path("message").asString().contains("SQLITE_NOTADB"));

    ByteArrayOutputStream newKeyOutput = new ByteArrayOutputStream();
    FinGrindCli newKeyCli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(newKeyOutput), fixedClock());
    assertEquals(
        0,
        newKeyCli.run(
            new String[] {
              "list-accounts",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              replacementBookKeyFilePath.toString()
            }));
    assertTrue(newKeyOutput.toString(StandardCharsets.UTF_8).contains("\"status\":\"ok\""));
  }

  @Test
  void run_openBookDeclareAccountListAccountsAndCommitThroughDefaultSqliteWorkflow()
      throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path declareCashFile =
        writeNamedRequest("declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path declareRevenueFile =
        writeNamedRequest("declare-revenue.json", declareAccountJson("2000", "Revenue", "CREDIT"));
    Path bookFilePath = tempDirectory.resolve("committed-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    FinGrindCli cli;

    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(openOutput), fixedClock());
    assertEquals(
        0,
        cli.run(
            new String[] {
              "open-book",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString()
            }));
    assertTrue(openOutput.toString(StandardCharsets.UTF_8).contains("\"initializedAt\""));

    ByteArrayOutputStream declareCashOutput = new ByteArrayOutputStream();
    cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(declareCashOutput),
            fixedClock());
    assertEquals(
        0,
        cli.run(
            new String[] {
              "declare-account",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              declareCashFile.toString()
            }));
    assertTrue(
        declareCashOutput.toString(StandardCharsets.UTF_8).contains("\"accountCode\":\"1000\""));

    ByteArrayOutputStream declareRevenueOutput = new ByteArrayOutputStream();
    cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(declareRevenueOutput),
            fixedClock());
    assertEquals(
        0,
        cli.run(
            new String[] {
              "declare-account",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              declareRevenueFile.toString()
            }));

    ByteArrayOutputStream listOutput = new ByteArrayOutputStream();
    cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(listOutput), fixedClock());
    assertEquals(
        0,
        cli.run(
            new String[] {
              "list-accounts",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString()
            }));
    assertTrue(listOutput.toString(StandardCharsets.UTF_8).contains("\"accountName\":\"Cash\""));
    assertTrue(listOutput.toString(StandardCharsets.UTF_8).contains("\"accountName\":\"Revenue\""));

    ByteArrayOutputStream preflightOutput = new ByteArrayOutputStream();
    cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(preflightOutput), fixedClock());
    assertEquals(
        0,
        cli.run(
            new String[] {
              "preflight-entry",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            }));
    assertTrue(
        preflightOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"status\":\"preflight-accepted\""));

    ByteArrayOutputStream commitOutput = new ByteArrayOutputStream();
    cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(commitOutput), fixedClock());
    assertEquals(
        0,
        cli.run(
            new String[] {
              "post-entry",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            }));

    JsonNode envelope = new ObjectMapper().readTree(commitOutput.toString(StandardCharsets.UTF_8));
    assertEquals("committed", envelope.path("status").asString());
    UUID postingId = UUID.fromString(envelope.path("postingId").asString());
    assertEquals(7, postingId.version());
    assertEquals(2, postingId.variant());
    assertTrue(Files.exists(bookFilePath));
  }
}
