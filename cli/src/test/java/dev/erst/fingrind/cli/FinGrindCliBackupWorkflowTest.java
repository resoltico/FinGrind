package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** End-to-end backup publication coverage through the default SQLite workflow. */
class FinGrindCliBackupWorkflowTest extends CliWorkflowFixtureSupport {
  @Test
  void run_backupBookWithMismatchedSigningPrincipal_returnsExactAuthorizationRejection()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("authorization-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path backupFilePath = tempDirectory.resolve("authorization-books").resolve("entity.fgba");
    Path backupKeyFilePath = tempDirectory.resolve("authorization-books").resolve("entity.key");
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(jsonArguments(openBookKeyFileArguments(bookFilePath, bookKeyFilePath))));

    String bookName = bookFilePath.getFileName().toString();
    ByteArrayOutputStream rejectedOutput = new ByteArrayOutputStream();
    assertEquals(
        2,
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(rejectedOutput), fixedClock())
            .run(
                attestedJsonArguments(
                    "backup-book",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--backup-file",
                    backupFilePath.toString(),
                    "--new-backup-key-file",
                    backupKeyFilePath.toString(),
                    "--backup-id",
                    "018f0000-0000-7000-8000-0000000000a1",
                    "--attestation-principal-id",
                    "00000000-0000-7000-8000-000000000001",
                    "--attestation-key-file",
                    bookFilePath.resolveSibling(bookName + ".founder.fgatk").toString(),
                    "--attestation-passphrase-file",
                    bookFilePath.resolveSibling(bookName + ".founder-passphrase").toString())));
    JsonNode rejectedEnvelope = new ObjectMapper().readTree(rejectedOutput.toByteArray());
    assertEquals("rejected", rejectedEnvelope.path("status").stringValue());
    assertEquals("attestation-key-principal-mismatch", rejectedEnvelope.path("code").stringValue());
    assertFalse(Files.exists(backupFilePath));
    assertFalse(Files.exists(backupKeyFilePath));
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
            attestedJsonArguments(
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

  @Test
  void run_backupBookToOrdinaryCallerOwnedDestination_reportsNoClobberBeforeArtifactAdmission()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("collision-source").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path collisionTargetDirectory = tempDirectory.resolve("collision-target");
    Path backupFilePath = collisionTargetDirectory.resolve("ordinary-existing.sqlite");
    Path backupKeyFilePath = collisionTargetDirectory.resolve("new-backup.key");
    Files.createDirectories(collisionTargetDirectory);
    CliTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(collisionTargetDirectory);
    Files.writeString(backupFilePath, "caller-owned ordinary destination");

    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(jsonArguments(openBookKeyFileArguments(bookFilePath, bookKeyFilePath))));

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int exitCode =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(output), fixedClock())
            .run(
                attestedJsonArguments(
                    "backup-book",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--backup-file",
                    backupFilePath.toString(),
                    "--backup-id",
                    "018f0000-0000-7000-8000-000000000003",
                    "--new-backup-key-file",
                    backupKeyFilePath.toString()));
    assertEquals(7, exitCode, output::toString);

    JsonNode rejection = new ObjectMapper().readTree(output.toByteArray());
    assertEquals("rejected", rejection.path("status").stringValue());
    assertEquals("backup-destination-already-exists", rejection.path("code").stringValue());
    assertEquals("caller-owned ordinary destination", Files.readString(backupFilePath));
    assertFalse(Files.exists(backupKeyFilePath));
  }
}
