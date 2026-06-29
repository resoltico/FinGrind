package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Unit tests for {@link FinGrindCli}. */
class FinGrindCliMutationWorkflowTest extends FinGrindCliTestSupport {
  @Test
  void run_rekeyBookThroughDefaultSqliteWorkflowRotatesBookKey() throws IOException {
    Path bookFilePath = tempDirectory.resolve("rekey-books").resolve("entity.sqlite");
    Path currentBookKeyFilePath = writeBookKey(bookFilePath, TEST_BOOK_KEY);
    Path replacementBookKeyFilePath = writeNamedBookKey("replacement-book.key", "replacement-key");
    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    FinGrindCli openCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(openOutput), fixedClock());
    assertEquals(
        0,
        openCli.run(jsonArguments(openBookKeyFileArguments(bookFilePath, currentBookKeyFilePath))));
    ByteArrayOutputStream rekeyOutput = new ByteArrayOutputStream();
    FinGrindCli rekeyCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(rekeyOutput), fixedClock());
    assertEquals(
        0,
        rekeyCli.run(
            jsonArguments(
                "rekey-book",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                currentBookKeyFilePath.toString(),
                "--new-book-key-file",
                replacementBookKeyFilePath.toString())));
    assertJsonContains(rekeyOutput, "\"bookFile\"");
    ByteArrayOutputStream oldKeyOutput = new ByteArrayOutputStream();
    FinGrindCli oldKeyCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(oldKeyOutput), fixedClock());
    assertEquals(
        6,
        oldKeyCli.run(
            jsonArguments(
                "list-accounts",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                currentBookKeyFilePath.toString())));
    JsonNode oldKeyFailureEnvelope = new ObjectMapper().readTree(oldKeyOutput.toByteArray());
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.code(),
        oldKeyFailureEnvelope.path("code").stringValue());
    assertFalse(oldKeyFailureEnvelope.path("message").stringValue().contains("SQLITE_NOTADB"));
    ByteArrayOutputStream newKeyOutput = new ByteArrayOutputStream();
    FinGrindCli newKeyCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(newKeyOutput), fixedClock());
    assertEquals(
        0,
        newKeyCli.run(
            jsonArguments(
                "list-accounts",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                replacementBookKeyFilePath.toString())));
    assertJsonContains(newKeyOutput, "\"status\":\"ok\"");
  }

  @Test
  void run_inspectBookOnCorruptedProtectedBook_reportsProtectedBookVerificationFailure()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("corrupted-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    FinGrindCli openCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(openOutput), fixedClock());
    assertEquals(
        0, openCli.run(jsonArguments(openBookKeyFileArguments(bookFilePath, bookKeyFilePath))));
    Path corruptedBookPath =
        tempDirectory.resolve("corrupted-books").resolve("entity-corrupted.sqlite");
    byte[] corruptedBytes = Files.readAllBytes(bookFilePath);
    corruptedBytes[Math.min(200, corruptedBytes.length - 1)] ^= 0x5A;
    Files.write(corruptedBookPath, corruptedBytes);
    ByteArrayOutputStream inspectOutput = new ByteArrayOutputStream();
    FinGrindCli inspectCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(inspectOutput), fixedClock());
    assertEquals(
        6,
        inspectCli.run(
            jsonArguments(
                "inspect-book",
                "--book-file",
                corruptedBookPath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString())));
    JsonNode failureEnvelope = new ObjectMapper().readTree(inspectOutput.toByteArray());
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.code(),
        failureEnvelope.path("code").stringValue());
    assertTrue(failureEnvelope.path("message").stringValue().contains("verify"));
    assertTrue(failureEnvelope.path("hint").stringValue().contains("damaged or truncated"));
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
    cli = cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(openOutput), fixedClock());
    assertEquals(
        0, cli.run(jsonArguments(openBookKeyFileArguments(bookFilePath, bookKeyFilePath))));
    assertJsonContains(openOutput, "\"initializedAt\"");
    assertJsonContains(openOutput, "\"entityName\":\"Acme Studio\"");
    ByteArrayOutputStream declareCashOutput = new ByteArrayOutputStream();
    cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(declareCashOutput),
            fixedClock());
    assertEquals(
        0,
        cli.run(
            jsonArguments(
                "declare-account",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                declareCashFile.toString())));
    assertJsonContains(declareCashOutput, "\"accountCode\":\"1000\"");
    ByteArrayOutputStream declareRevenueOutput = new ByteArrayOutputStream();
    cli =
        cli(
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
    cli = cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(listOutput), fixedClock());
    assertEquals(
        0,
        cli.run(
            jsonArguments(
                "list-accounts",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString())));
    assertJsonContains(listOutput, "\"accountName\":\"Cash\"");
    assertJsonContains(listOutput, "\"accountName\":\"Revenue\"");
    ByteArrayOutputStream preflightOutput = new ByteArrayOutputStream();
    cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(preflightOutput), fixedClock());
    assertEquals(
        0,
        cli.run(
            jsonArguments(
                "preflight-entry",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                requestFile.toString())));
    assertJsonContains(preflightOutput, "\"status\":\"ok\"");
    ByteArrayOutputStream commitOutput = new ByteArrayOutputStream();
    cli = cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(commitOutput), fixedClock());
    assertEquals(
        0,
        cli.run(
            jsonArguments(
                "record-sale",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                requestFile.toString())));
    JsonNode envelope = new ObjectMapper().readTree(commitOutput.toString(StandardCharsets.UTF_8));
    assertEquals("ok", envelope.path("status").stringValue());
    UUID postingId = UUID.fromString(envelope.path("payload").path("postingId").stringValue());
    assertEquals(7, postingId.version());
    assertEquals(2, postingId.variant());
    assertTrue(Files.exists(bookFilePath));
  }

  @Test
  void run_rejectsPlaceholderRequestScaffoldBeforePreflightOrCommit() throws IOException {
    Path bookFilePath = tempDirectory.resolve("placeholder-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream templateOutput = new ByteArrayOutputStream();
    FinGrindCli templateCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(templateOutput), fixedClock());
    assertEquals(0, templateCli.run(new String[] {"print-request-template"}));
    Path requestFile =
        writeNamedRequest(
            "placeholder-request.json", templateOutput.toString(StandardCharsets.UTF_8));
    FinGrindCli openCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(new ByteArrayOutputStream()),
            fixedClock());
    assertEquals(
        0, openCli.run(jsonArguments(openBookKeyFileArguments(bookFilePath, bookKeyFilePath))));

    ByteArrayOutputStream preflightOutput = new ByteArrayOutputStream();
    FinGrindCli preflightCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(preflightOutput), fixedClock());
    assertEquals(
        1,
        preflightCli.run(
            jsonArguments(
                "preflight-entry",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                requestFile.toString())));
    assertTrue(
        preflightOutput
            .toString(StandardCharsets.UTF_8)
            .contains("Scaffold placeholder must be replaced before submission"));

    ByteArrayOutputStream commitOutput = new ByteArrayOutputStream();
    FinGrindCli commitCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(commitOutput), fixedClock());
    assertEquals(
        1,
        commitCli.run(
            jsonArguments(
                "record-sale",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                requestFile.toString())));
    assertTrue(
        commitOutput
            .toString(StandardCharsets.UTF_8)
            .contains("Scaffold placeholder must be replaced before submission"));
  }

  @Test
  void run_rekeyBookWithWrongCurrentKey_doesNotEchoCurrentOrReplacementSecret() throws IOException {
    Path bookFilePath = tempDirectory.resolve("wrong-rekey-books").resolve("entity.sqlite");
    Path currentBookKeyFilePath = writeBookKey(bookFilePath, TEST_BOOK_KEY);
    Path wrongCurrentBookKeyFilePath =
        writeNamedBookKey("wrong-current-book.key", "wrong-current-secret");
    Path replacementBookKeyFilePath =
        writeNamedBookKey("replacement-secret-book.key", "replacement-secret");
    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    FinGrindCli openCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(openOutput), fixedClock());
    assertEquals(
        0,
        openCli.run(jsonArguments(openBookKeyFileArguments(bookFilePath, currentBookKeyFilePath))));
    ByteArrayOutputStream rekeyOutput = new ByteArrayOutputStream();
    FinGrindCli rekeyCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(rekeyOutput), fixedClock());
    assertEquals(
        6,
        rekeyCli.run(
            jsonArguments(
                "rekey-book",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                wrongCurrentBookKeyFilePath.toString(),
                "--new-book-key-file",
                replacementBookKeyFilePath.toString())));
    String outputText = rekeyOutput.toString(StandardCharsets.UTF_8);
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputText);
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.code(),
        failureEnvelope.path("code").stringValue());
    assertFalse(outputText.contains("wrong-current-secret"));
    assertFalse(outputText.contains("replacement-secret"));
  }

  @Test
  void run_backupBookToExistingDestination_returnsMaintenanceCollisionExit() throws IOException {
    Path bookFilePath = tempDirectory.resolve("backup-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path backupFilePath = tempDirectory.resolve("backup-books").resolve("entity-backup.sqlite");
    Path backupKeyFilePath = tempDirectory.resolve("backup-books").resolve("entity-backup.key");
    Path secondBackupKeyFilePath =
        tempDirectory.resolve("backup-books").resolve("entity-backup-second.key");
    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    FinGrindCli openCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(openOutput), fixedClock());
    assertEquals(
        0, openCli.run(jsonArguments(openBookKeyFileArguments(bookFilePath, bookKeyFilePath))));

    ByteArrayOutputStream firstBackupOutput = new ByteArrayOutputStream();
    FinGrindCli firstBackupCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(firstBackupOutput),
            fixedClock());
    assertEquals(
        0,
        firstBackupCli.run(
            new String[] {
              "backup-book",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--backup-book-file-out",
              backupFilePath.toString(),
              "--backup-book-key-file-out",
              backupKeyFilePath.toString()
            }));

    ByteArrayOutputStream secondBackupOutput = new ByteArrayOutputStream();
    FinGrindCli secondBackupCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(secondBackupOutput),
            fixedClock());
    assertEquals(
        7,
        secondBackupCli.run(
            jsonArguments(
                "backup-book",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--backup-book-file-out",
                backupFilePath.toString(),
                "--backup-book-key-file-out",
                secondBackupKeyFilePath.toString())));
    JsonNode failureEnvelope = new ObjectMapper().readTree(secondBackupOutput.toByteArray());
    assertEquals("rejected", failureEnvelope.path("status").stringValue());
    assertEquals("backup-destination-already-exists", failureEnvelope.path("code").stringValue());
  }
}
