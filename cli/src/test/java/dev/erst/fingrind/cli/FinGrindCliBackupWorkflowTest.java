package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** End-to-end backup publication coverage through the default SQLite workflow. */
class FinGrindCliBackupWorkflowTest extends FinGrindCliTestSupport {
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
            attestedArguments(
                "backup-book",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--backup-file",
                backupFilePath.toString(),
                "--backup-id",
                "018f0000-0000-7000-8000-000000000001",
                "--new-backup-key-file",
                backupKeyFilePath.toString())));

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
                "--backup-file",
                backupFilePath.toString(),
                "--backup-id",
                "018f0000-0000-7000-8000-000000000002",
                "--new-backup-key-file",
                secondBackupKeyFilePath.toString())));
    JsonNode failureEnvelope = new ObjectMapper().readTree(secondBackupOutput.toByteArray());
    assertEquals("rejected", failureEnvelope.path("status").stringValue());
    assertEquals("backup-destination-already-exists", failureEnvelope.path("code").stringValue());
  }
}
