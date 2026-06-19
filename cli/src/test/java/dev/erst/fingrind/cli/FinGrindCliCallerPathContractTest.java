package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenancePathFailure;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Public CLI contract coverage for caller-controlled path failures and maintenance refusals. */
class FinGrindCliCallerPathContractTest extends FinGrindCliTestSupport {
  @Test
  void run_openBookWithParentPathCollision_returnsInvalidBookFilePathOnDiagnostics()
      throws IOException {
    Path parentPathCollision = tempDirectory.resolve("live-parent-collision");
    Files.writeString(parentPathCollision, "collision");
    Path bookFilePath = parentPathCollision.resolve("entity.sqlite");
    Path bookKeyFilePath = writeNamedBookKey("open-book-path.key", TEST_BOOK_KEY);
    ObservedInvocation observed =
        runStandardCli(
            new String[] {
              "open-book",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--entity-name",
              "Acme Studio",
              "--functional-currency",
              "EUR",
              "--fiscal-year-start",
              "01-01",
              "--output",
              "text"
            });

    assertFailure(observed, 6, "error", ContractErrors.Descriptor.INVALID_BOOK_FILE_PATH.code());
    assertTrue(observed.stderr().contains("already exists as a non-directory"), observed.stderr());
    assertFalse(observed.stderr().contains("internal-error"), observed.stderr());
  }

  @Test
  void run_generateBookKeyFileWithParentPathCollision_returnsInvalidBookKeyFileOnDiagnostics()
      throws IOException {
    Path parentPathCollision = tempDirectory.resolve("key-parent-collision");
    Files.writeString(parentPathCollision, "collision");
    Path missingKeyFilePath = parentPathCollision.resolve("entity.book-key");
    ObservedInvocation observed =
        runStandardCli(
            new String[] {
              "generate-book-key-file",
              "--book-key-file",
              missingKeyFilePath.toString(),
              "--output",
              "text"
            });

    assertFailure(observed, 6, "error", ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.code());
    assertTrue(observed.stderr().contains("already exists as a non-directory"), observed.stderr());
    assertFalse(observed.stderr().contains("internal-error"), observed.stderr());
  }

  @Test
  void run_backupBookArtifactPathInvalid_returnsTypedMaintenanceRefusal() throws IOException {
    CliBookWorkflow workflow =
        new CliBookWorkflowAdapter() {
          @Override
          public dev.erst.fingrind.contract.runtime.ContractDecision<BackupBookResult> backupBook(
              dev.erst.fingrind.contract.runtime.BookAccess bookAccess,
              Path backupFilePath,
              Path backupBookKeyFilePath) {
            return accepted(
                new BackupBookResult.Rejected(
                    new BookMaintenanceRejection.ArtifactPathInvalid(
                        BookMaintenanceArtifactRole.BACKUP_TARGET,
                        PublicPathHint.fromPath(backupFilePath),
                        BookMaintenancePathFailure.PARENT_OWNER_ONLY_REQUIRED)));
          }
        };

    ObservedInvocation observed =
        runWorkflowCli(
            workflow,
            new String[] {
              "backup-book",
              "--book-file",
              "book.sqlite",
              "--book-key-file",
              "book.key",
              "--backup-book-file-out",
              "backup/entity.sqlite",
              "--backup-book-key-file-out",
              "backup/entity.key",
              "--output",
              "json"
            });

    assertFailure(observed, 6, "rejected", "artifact-path-invalid");
    assertEquals(
        "parent-owner-only-required",
        failureEnvelope(observed).path("details").path("pathFailure").stringValue(),
        observed.stderr());
  }

  @Test
  void run_restoreBookArtifactPathInvalid_returnsTypedMaintenanceRefusal() throws IOException {
    CliBookWorkflow workflow =
        new CliBookWorkflowAdapter() {
          @Override
          public dev.erst.fingrind.contract.runtime.ContractDecision<RestoreBookResult> restoreBook(
              Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath) {
            return accepted(
                new RestoreBookResult.Rejected(
                    new BookMaintenanceRejection.ArtifactPathInvalid(
                        BookMaintenanceArtifactRole.RESTORED_TARGET,
                        PublicPathHint.fromPath(bookFilePath),
                        BookMaintenancePathFailure.TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE)));
          }
        };

    ObservedInvocation observed =
        runWorkflowCli(
            workflow,
            new String[] {
              "restore-book",
              "--book-file",
              "book.sqlite",
              "--backup-book-file",
              "backup/entity.sqlite",
              "--backup-book-key-file",
              "backup/entity.key",
              "--output",
              "json"
            });

    assertFailure(observed, 6, "rejected", "artifact-path-invalid");
    assertEquals(
        "target-must-be-regular-non-symlink-file",
        failureEnvelope(observed).path("details").path("pathFailure").stringValue(),
        observed.stderr());
  }

  private ObservedInvocation runStandardCli(String[] arguments) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsStream),
            fixedClock());
    int exitCode = cli.run(arguments);
    return new ObservedInvocation(
        exitCode,
        outputStream.toString(StandardCharsets.UTF_8),
        diagnosticsStream.toString(StandardCharsets.UTF_8));
  }

  private ObservedInvocation runWorkflowCli(CliBookWorkflow workflow, String[] arguments) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsStream),
            fixedClock(),
            workflow);
    int exitCode = cli.run(arguments);
    return new ObservedInvocation(
        exitCode,
        outputStream.toString(StandardCharsets.UTF_8),
        diagnosticsStream.toString(StandardCharsets.UTF_8));
  }

  private static void assertFailure(
      ObservedInvocation observed, int expectedExitCode, String expectedStatus, String expectedCode)
      throws IOException {
    assertEquals(expectedExitCode, observed.exitCode(), observed.stderr());
    assertEquals("", observed.stdout(), observed.stdout());
    JsonNode envelope = failureEnvelope(observed);
    assertEquals(expectedStatus, envelope.path("status").stringValue(), observed.stderr());
    assertEquals(expectedCode, envelope.path("code").stringValue(), observed.stderr());
  }

  private static JsonNode failureEnvelope(ObservedInvocation observed) throws IOException {
    return CliJsonObjectMappers.configuredObjectMapper().readTree(observed.stderr());
  }

  private record ObservedInvocation(int exitCode, String stdout, String stderr) {}
}
