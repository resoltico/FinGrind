package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Contract tests for non-report artifact publication across JSON and text CLI success surfaces. */
class FinGrindCliArtifactSurfaceContractTest extends CliPublicDocsContractSupport {
  @Test
  void generateBookKeyFile_usesArtifactsAsItsOnlyJsonArtifactHomeAndKeepsTextReadable()
      throws IOException {
    Path jsonBookKeyFilePath = tempDirectory.resolve("artifacts").resolve("entity-json.book-key");
    Path textBookKeyFilePath = tempDirectory.resolve("artifacts").resolve("entity-text.book-key");

    JsonNode jsonEnvelope =
        runJsonCommand(
            "generate-book-key-file", "--new-book-key-file", jsonBookKeyFilePath.toString());
    assertSuccessEnvelope(jsonEnvelope);
    assertArtifactList(
        jsonEnvelope,
        List.of(
            artifactExpectation(ProtocolArtifactOutput.bookKeyFileFormat(), jsonBookKeyFilePath)));
    assertTrue(jsonEnvelope.path("payload").path("bookKeyFile").isMissingNode());
    assertTrue(jsonEnvelope.path("payload").path("bookKeyFilePath").isMissingNode());

    String textOutput =
        runPlainCommand(
            "generate-book-key-file", "--new-book-key-file", textBookKeyFilePath.toString());
    assertTrue(textOutput.contains("Book Key File Generated"), textOutput);
    assertTrue(textOutput.contains("entity-text.book-key"), textOutput);
    assertTrue(!textOutput.contains("\"status\""), textOutput);
    assertTrue(!textOutput.contains("\"artifacts\""), textOutput);
  }

  @Test
  void workflowArtifactCommands_publishArtifactsOnlyAtTopLevelInJsonSuccessEnvelopes()
      throws IOException {
    Path root = tempDirectory.resolve("workflow-artifacts-json");
    Path bookFilePath = root.resolve("books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path replacementBookKeyFilePath = root.resolve("json-replacement.key");
    Path backupBookFilePath = root.resolve("backup").resolve("entity.backup.sqlite");
    Path backupBookKeyFilePath = root.resolve("backup").resolve("entity.backup.key");
    Path restoredBookFilePath = root.resolve("restored").resolve("entity.sqlite");
    Path restoredBookKeyFilePath = root.resolve("restored").resolve("entity.book-key");
    Path rollbackArtifactPath = root.resolve("books").resolve("entity.rekey-rollback.sqlite");

    JsonNode rekeyEnvelope =
        runJsonWorkflowCommand(
            contractWorkflow(),
            "rekey-book",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            bookKeyFilePath.toString(),
            "--new-book-key-file",
            replacementBookKeyFilePath.toString());
    assertSuccessEnvelope(rekeyEnvelope);
    assertArtifactList(
        rekeyEnvelope,
        List.of(
            artifactExpectation(
                ProtocolArtifactOutput.bookKeyFileFormat(), replacementBookKeyFilePath)));
    assertTrue(rekeyEnvelope.path("payload").path("replacementBookKeyFile").isMissingNode());

    RecordingWorkflow backupWorkflow = contractWorkflow();
    backupWorkflow.setBackupBookResult(
        new BackupBookResult.BackedUp(
            hint(bookFilePath), hint(backupBookFilePath), hint(backupBookKeyFilePath)));
    JsonNode backupEnvelope =
        runJsonWorkflowCommand(
            backupWorkflow,
            "backup-book",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            bookKeyFilePath.toString(),
            "--backup-file",
            backupBookFilePath.toString(),
            "--new-backup-key-file",
            backupBookKeyFilePath.toString());
    assertSuccessEnvelope(backupEnvelope);
    assertArtifactList(
        backupEnvelope,
        List.of(
            artifactExpectation(ProtocolArtifactOutput.backupFileFormat(), backupBookFilePath),
            artifactExpectation(
                ProtocolArtifactOutput.backupKeyFileFormat(), backupBookKeyFilePath)));
    assertTrue(backupEnvelope.path("payload").path("backupBookFile").isMissingNode());
    assertTrue(backupEnvelope.path("payload").path("backupBookKeyFile").isMissingNode());

    RecordingWorkflow restoreWorkflow = contractWorkflow();
    restoreWorkflow.setRestoreBookResult(
        new RestoreBookResult.Restored(hint(restoredBookFilePath), hint(restoredBookKeyFilePath)));
    JsonNode restoreEnvelope =
        runJsonWorkflowCommand(
            restoreWorkflow,
            "restore-book",
            "--book-file",
            restoredBookFilePath.toString(),
            "--new-book-key-file",
            restoredBookKeyFilePath.toString(),
            "--backup-file",
            backupBookFilePath.toString(),
            "--backup-key-file",
            backupBookKeyFilePath.toString());
    assertSuccessEnvelope(restoreEnvelope);
    assertArtifactList(
        restoreEnvelope,
        List.of(
            artifactExpectation(ProtocolArtifactOutput.bookFileFormat(), restoredBookFilePath),
            artifactExpectation(
                ProtocolArtifactOutput.bookKeyFileFormat(), restoredBookKeyFilePath)));
    assertTrue(restoreEnvelope.path("payload").path("bookKeyFile").isMissingNode());
    assertEquals(
        CliPublicPaths.absoluteValue(hint(restoredBookKeyFilePath)),
        restoreEnvelope.path("payload").path("bookKeyFilePath").asText());

    RecordingWorkflow inspectRollbackWorkflow = contractWorkflow();
    inspectRollbackWorkflow.setRekeyRollbackResult(
        new RekeyRollbackResult.Inspected(hint(bookFilePath), List.of(hint(rollbackArtifactPath))));
    JsonNode inspectRollbackEnvelope =
        runJsonWorkflowCommand(
            inspectRollbackWorkflow,
            "inspect-rekey-rollback",
            "--book-file",
            bookFilePath.toString());
    assertSuccessEnvelope(inspectRollbackEnvelope);
    assertArtifactList(
        inspectRollbackEnvelope,
        List.of(
            artifactExpectation(
                ProtocolArtifactOutput.rollbackBookFileFormat(), rollbackArtifactPath)));
    assertTrue(inspectRollbackEnvelope.path("payload").path("rollbackArtifacts").isMissingNode());

    RecordingWorkflow restoreRollbackWorkflow = contractWorkflow();
    restoreRollbackWorkflow.setRekeyRollbackResult(
        new RekeyRollbackResult.Restored(hint(bookFilePath), hint(rollbackArtifactPath)));
    JsonNode restoreRollbackEnvelope =
        runJsonWorkflowCommand(
            restoreRollbackWorkflow,
            "restore-rekey-rollback",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            bookKeyFilePath.toString(),
            "--rollback-book-file",
            rollbackArtifactPath.toString());
    assertSuccessEnvelope(restoreRollbackEnvelope);
    assertArtifactList(
        restoreRollbackEnvelope,
        List.of(
            artifactExpectation(
                ProtocolArtifactOutput.rollbackBookFileFormat(), rollbackArtifactPath)));
    assertTrue(restoreRollbackEnvelope.path("payload").path("rollbackArtifact").isMissingNode());

    RecordingWorkflow deleteRollbackWorkflow = contractWorkflow();
    deleteRollbackWorkflow.setRekeyRollbackResult(
        new RekeyRollbackResult.Deleted(hint(bookFilePath), hint(rollbackArtifactPath)));
    JsonNode deleteRollbackEnvelope =
        runJsonWorkflowCommand(
            deleteRollbackWorkflow,
            "delete-rekey-rollback",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            bookKeyFilePath.toString(),
            "--rollback-book-file",
            rollbackArtifactPath.toString());
    assertSuccessEnvelope(deleteRollbackEnvelope);
    assertArtifactList(
        deleteRollbackEnvelope,
        List.of(
            artifactExpectation(
                ProtocolArtifactOutput.rollbackBookFileFormat(), rollbackArtifactPath)));
    assertTrue(deleteRollbackEnvelope.path("payload").path("rollbackArtifact").isMissingNode());
  }

  @Test
  void workflowArtifactCommands_publishReadableTextSuccessSurfaces() throws IOException {
    Path root = tempDirectory.resolve("workflow-artifacts-text");
    Path bookFilePath = root.resolve("books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path replacementBookKeyFilePath = root.resolve("text-replacement.key");
    Path backupBookFilePath = root.resolve("backup").resolve("entity.backup.sqlite");
    Path backupBookKeyFilePath = root.resolve("backup").resolve("entity.backup.key");
    Path restoredBookFilePath = root.resolve("restored").resolve("entity.sqlite");
    Path restoredBookKeyFilePath = root.resolve("restored").resolve("entity.book-key");
    Path rollbackArtifactPath = root.resolve("books").resolve("entity.rekey-rollback.sqlite");

    String rekeyText =
        runTextWorkflowCommand(
            contractWorkflow(),
            "rekey-book",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            bookKeyFilePath.toString(),
            "--new-book-key-file",
            replacementBookKeyFilePath.toString());
    assertTrue(rekeyText.contains("Book Rekeyed"), rekeyText);
    assertTrue(rekeyText.contains("text-replacement.key"), rekeyText);
    assertTrue(!rekeyText.contains("\"artifacts\""), rekeyText);

    RecordingWorkflow backupWorkflow = contractWorkflow();
    backupWorkflow.setBackupBookResult(
        new BackupBookResult.BackedUp(
            hint(bookFilePath), hint(backupBookFilePath), hint(backupBookKeyFilePath)));
    String backupText =
        runTextWorkflowCommand(
            backupWorkflow,
            "backup-book",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            bookKeyFilePath.toString(),
            "--backup-file",
            backupBookFilePath.toString(),
            "--new-backup-key-file",
            backupBookKeyFilePath.toString());
    assertTrue(backupText.contains("Book Backed Up"), backupText);
    assertTrue(backupText.contains("entity.backup.sqlite"), backupText);
    assertTrue(backupText.contains("entity.backup.key"), backupText);
    assertTrue(!backupText.contains("\"status\""), backupText);

    RecordingWorkflow restoreWorkflow = contractWorkflow();
    restoreWorkflow.setRestoreBookResult(
        new RestoreBookResult.Restored(hint(restoredBookFilePath), hint(restoredBookKeyFilePath)));
    String restoreText =
        runTextWorkflowCommand(
            restoreWorkflow,
            "restore-book",
            "--book-file",
            restoredBookFilePath.toString(),
            "--new-book-key-file",
            restoredBookKeyFilePath.toString(),
            "--backup-file",
            backupBookFilePath.toString(),
            "--backup-key-file",
            backupBookKeyFilePath.toString());
    assertTrue(restoreText.contains("Book Restored"), restoreText);
    assertTrue(restoreText.contains("entity.book-key"), restoreText);
    assertTrue(!restoreText.contains("\"artifacts\""), restoreText);

    RecordingWorkflow inspectRollbackWorkflow = contractWorkflow();
    inspectRollbackWorkflow.setRekeyRollbackResult(
        new RekeyRollbackResult.Inspected(hint(bookFilePath), List.of(hint(rollbackArtifactPath))));
    String inspectRollbackText =
        runTextWorkflowCommand(
            inspectRollbackWorkflow,
            "inspect-rekey-rollback",
            "--book-file",
            bookFilePath.toString());
    assertTrue(inspectRollbackText.contains("Rekey Rollback Artifacts"), inspectRollbackText);
    assertTrue(inspectRollbackText.contains("entity.rekey-rollback.sqlite"), inspectRollbackText);
    assertTrue(!inspectRollbackText.contains("\"status\""), inspectRollbackText);

    RecordingWorkflow restoreRollbackWorkflow = contractWorkflow();
    restoreRollbackWorkflow.setRekeyRollbackResult(
        new RekeyRollbackResult.Restored(hint(bookFilePath), hint(rollbackArtifactPath)));
    String restoreRollbackText =
        runTextWorkflowCommand(
            restoreRollbackWorkflow,
            "restore-rekey-rollback",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            bookKeyFilePath.toString(),
            "--rollback-book-file",
            rollbackArtifactPath.toString());
    assertTrue(restoreRollbackText.contains("Book Restored From Rollback"), restoreRollbackText);
    assertTrue(restoreRollbackText.contains("entity.rekey-rollback.sqlite"), restoreRollbackText);
    assertTrue(!restoreRollbackText.contains("\"artifacts\""), restoreRollbackText);

    RecordingWorkflow deleteRollbackWorkflow = contractWorkflow();
    deleteRollbackWorkflow.setRekeyRollbackResult(
        new RekeyRollbackResult.Deleted(hint(bookFilePath), hint(rollbackArtifactPath)));
    String deleteRollbackText =
        runTextWorkflowCommand(
            deleteRollbackWorkflow,
            "delete-rekey-rollback",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            bookKeyFilePath.toString(),
            "--rollback-book-file",
            rollbackArtifactPath.toString());
    assertTrue(deleteRollbackText.contains("Rollback Artifact Deleted"), deleteRollbackText);
    assertTrue(deleteRollbackText.contains("entity.rekey-rollback.sqlite"), deleteRollbackText);
    assertTrue(!deleteRollbackText.contains("\"status\""), deleteRollbackText);
  }

  private JsonNode runJsonWorkflowCommand(CliBookWorkflow workflow, String... arguments)
      throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);
    String[] jsonArguments = jsonArguments(arguments);
    int exitCode = cli.run(jsonArguments);
    assertEquals(
        0,
        exitCode,
        () ->
            "command failed: "
                + String.join(" ", jsonArguments)
                + "\n"
                + outputStream.toString(StandardCharsets.UTF_8));
    return OBJECT_MAPPER.readTree(outputStream.toByteArray());
  }

  private String runTextWorkflowCommand(CliBookWorkflow workflow, String... arguments) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);
    int exitCode = cli.run(arguments);
    assertEquals(
        0,
        exitCode,
        () ->
            "command failed: "
                + String.join(" ", arguments)
                + "\n"
                + outputStream.toString(StandardCharsets.UTF_8));
    return outputStream.toString(StandardCharsets.UTF_8);
  }

  private static RecordingWorkflow contractWorkflow() {
    return new RecordingWorkflow(
        openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
        new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
        new DeclareAccountResult.Declared(
            declaredAccount(
                "1000",
                "Cash",
                dev.erst.fingrind.core.AccountType.ASSET,
                NormalBalance.DEBIT,
                true,
                Instant.parse("2026-04-07T12:00:00Z"))),
        new ListAccountsResult.Listed(accountPage(List.of(), 50, java.util.Optional.empty())),
        CliPostEntryResultFixtures.preflightAccepted(
            new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
        CliPostEntryResultFixtures.committed(
            new PostingId("posting-1"),
            new IdempotencyKey("idem-1"),
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            false));
  }

  private static void assertSuccessEnvelope(JsonNode envelope) {
    assertEquals("ok", envelope.path("status").stringValue());
    assertTrue(envelope.path("payload").isObject());
    assertTrue(envelope.path("code").isMissingNode());
    assertTrue(envelope.path("message").isMissingNode());
  }

  private static void assertArtifactList(
      JsonNode envelope, List<ArtifactExpectation> expectedArtifacts) {
    assertEquals(expectedArtifacts.size(), envelope.path("artifacts").size());
    for (int index = 0; index < expectedArtifacts.size(); index++) {
      ArtifactExpectation expectedArtifact = expectedArtifacts.get(index);
      JsonNode actualArtifact = envelope.path("artifacts").get(index);
      assertEquals(expectedArtifact.format(), actualArtifact.path("format").stringValue());
      assertEquals(
          CliPublicPaths.absoluteValue(expectedArtifact.path()),
          actualArtifact.path("path").stringValue());
    }
  }

  private static ArtifactExpectation artifactExpectation(String format, Path path) {
    return new ArtifactExpectation(format, path);
  }

  private record ArtifactExpectation(String format, Path path) {}
}
