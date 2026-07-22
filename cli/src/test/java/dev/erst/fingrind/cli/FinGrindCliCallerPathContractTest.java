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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Public CLI contract coverage for caller-controlled path failures and maintenance refusals. */
class FinGrindCliCallerPathContractTest extends FinGrindCliTestSupport {
  private static final Set<PosixFilePermission> LOOSE_POSIX_DIRECTORY_PERMISSIONS =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE,
          PosixFilePermission.GROUP_READ,
          PosixFilePermission.GROUP_EXECUTE,
          PosixFilePermission.OTHERS_READ,
          PosixFilePermission.OTHERS_EXECUTE);

  @Test
  void run_openBookWithParentPathCollision_honorsResolvedTextAndJsonFailureModes()
      throws IOException {
    Path parentPathCollision = tempDirectory.resolve("live-parent-collision");
    Files.writeString(parentPathCollision, "collision");
    Path bookFilePath = parentPathCollision.resolve("entity.sqlite");
    Path bookKeyFilePath = writeNamedBookKey("open-book-path.key", TEST_BOOK_KEY);
    String[] textArguments =
        new String[] {
          "open-book",
          "--book-file",
          bookFilePath.toString(),
          "--book-key-file",
          bookKeyFilePath.toString(),
          "--entity-name",
          "Acme Studio",
          "--book-template-id",
          "OWNER_MANAGED_SERVICE",
          "--accounting-basis",
          "CASH",
          "--functional-currency",
          "EUR",
          "--fiscal-year-start",
          "01-01",
          "--book-start-effective-date",
          "2026-01-01",
          "--book-start-effective-date",
          "2026-01-01",
          "--output",
          "text"
        };
    ObservedInvocation textObserved = runStandardCli(textArguments);
    ObservedInvocation jsonObserved =
        runStandardCli(
            new String[] {
              "open-book",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--entity-name",
              "Acme Studio",
              "--book-template-id",
              "OWNER_MANAGED_SERVICE",
              "--accounting-basis",
              "CASH",
              "--functional-currency",
              "EUR",
              "--fiscal-year-start",
              "01-01",
              "--book-start-effective-date",
              "2026-01-01",
              "--book-start-effective-date",
              "2026-01-01",
              "--output",
              "json"
            });

    assertTextFailure(textObserved, 6, ContractErrors.Descriptor.INVALID_BOOK_FILE_PATH.code());
    assertTrue(
        textObserved.stderr().contains("already exists as a non-directory"), textObserved.stderr());
    assertFalse(textObserved.stderr().contains("internal-error"), textObserved.stderr());
    assertJsonFailure(
        jsonObserved, 6, "error", ContractErrors.Descriptor.INVALID_BOOK_FILE_PATH.code());
    assertTrue(
        jsonObserved.stderr().contains("already exists as a non-directory"), jsonObserved.stderr());
    assertFalse(jsonObserved.stderr().contains("internal-error"), jsonObserved.stderr());
  }

  @Test
  void run_generateBookKeyFileWithoutTightenParents_rejectsLooseParentDirectory()
      throws IOException {
    Path looseSecretsDirectory = createLoosePosixDirectory("loose-secrets-without-tighten");
    Path keyFilePath = looseSecretsDirectory.resolve("entity.book-key");

    ObservedInvocation observed =
        runStandardCli(
            new String[] {
              "generate-book-key-file",
              "--new-book-key-file",
              keyFilePath.toString(),
              "--output",
              "text"
            });

    assertTextFailure(observed, 6, ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.code());
    assertTrue(
        observed.stderr().contains("parent-owner-only-required")
            || observed.stderr().contains("owner-only"),
        observed.stderr());
    assertTrue(
        observed
            .stderr()
            .contains(
                "Create a private owner-only parent directory yourself, tighten it if needed, then choose a regular non-symlink key file path beneath it and rerun the command."),
        observed.stderr());
    assertFalse(observed.stderr().contains("Choose one regular non-symlink"), observed.stderr());
    assertFalse(Files.exists(keyFilePath));
  }

  @Test
  void run_openBookWithoutTightenParents_rejectsLooseParentDirectoryWithFinalHintText()
      throws IOException {
    Path looseBooksDirectory = createLoosePosixDirectory("loose-books-without-tighten");
    Path bookFilePath = looseBooksDirectory.resolve("entity.sqlite");
    Path bookKeyFilePath = writeNamedBookKey("open-book-without-tighten.key", TEST_BOOK_KEY);

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
              "--book-template-id",
              "OWNER_MANAGED_SERVICE",
              "--accounting-basis",
              "CASH",
              "--functional-currency",
              "EUR",
              "--fiscal-year-start",
              "01-01",
              "--book-start-effective-date",
              "2026-01-01",
              "--book-start-effective-date",
              "2026-01-01",
              "--output",
              "text"
            });

    assertTextFailure(observed, 6, ContractErrors.Descriptor.INVALID_BOOK_FILE_PATH.code());
    assertTrue(
        observed.stderr().contains("parent-owner-only-required")
            || observed.stderr().contains("owner-only"),
        observed.stderr());
    assertTrue(
        observed
            .stderr()
            .contains(
                "Choose a regular non-symlink protected-book path beneath a private owner-only parent directory."),
        observed.stderr());
    assertFalse(observed.stderr().contains("Choose one regular non-symlink"), observed.stderr());
    assertFalse(Files.exists(bookFilePath));
  }

  @Test
  void run_generateBookKeyFileWithTightenParents_hardensLooseParentAndReportsTheChange()
      throws IOException {
    Path looseSecretsDirectory = createLoosePosixDirectory("loose-secrets-with-tighten");
    Path keyFilePath = looseSecretsDirectory.resolve("entity.book-key");

    ObservedInvocation observed =
        runStandardCli(
            new String[] {
              "generate-book-key-file",
              "--new-book-key-file",
              keyFilePath.toString(),
              "--tighten-parents",
              "--output",
              "json"
            });

    assertEquals(0, observed.exitCode(), observed.stderr());
    assertEquals("", observed.stderr(), observed.stderr());
    JsonNode envelope = CliJsonObjectMappers.configuredObjectMapper().readTree(observed.stdout());
    assertEquals("ok", envelope.path("status").stringValue(), observed.stdout());
    assertEquals(
        CliPublicPaths.absoluteValue(looseSecretsDirectory),
        envelope.path("payload").path("tightenedParentDirectories").get(0).stringValue(),
        observed.stdout());
    assertTrue(Files.exists(keyFilePath));
    assertEquals(
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE),
        Files.getPosixFilePermissions(looseSecretsDirectory));
  }

  @Test
  void run_openBookWithTightenParents_hardensLooseParentAndPublishesItInSuccessPayload()
      throws IOException {
    Path looseBooksDirectory = createLoosePosixDirectory("loose-books-with-tighten");
    Path bookFilePath = looseBooksDirectory.resolve("entity.sqlite");
    Path bookKeyFilePath = writeNamedBookKey("tighten-open-book.key", TEST_BOOK_KEY);

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
              "--book-template-id",
              "OWNER_MANAGED_SERVICE",
              "--accounting-basis",
              "CASH",
              "--functional-currency",
              "EUR",
              "--fiscal-year-start",
              "01-01",
              "--book-start-effective-date",
              "2026-01-01",
              "--book-start-effective-date",
              "2026-01-01",
              "--tighten-parents",
              "--output",
              "json"
            });

    assertEquals(0, observed.exitCode(), observed.stderr());
    assertEquals("", observed.stderr(), observed.stderr());
    JsonNode envelope = CliJsonObjectMappers.configuredObjectMapper().readTree(observed.stdout());
    assertEquals("ok", envelope.path("status").stringValue(), observed.stdout());
    assertEquals(
        CliPublicPaths.absoluteValue(looseBooksDirectory),
        envelope.path("payload").path("tightenedParentDirectories").get(0).stringValue(),
        observed.stdout());
    assertTrue(Files.exists(bookFilePath));
    assertEquals(
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE),
        Files.getPosixFilePermissions(looseBooksDirectory));
  }

  @Test
  void run_generateBookKeyFileWithTightenParents_doesNotReportAlreadyPrivateParents()
      throws IOException {
    Assumptions.assumeTrue(supportsPosix(tempDirectory));
    Path privateSecretsDirectory = tempDirectory.resolve("private-secrets");
    Files.createDirectories(privateSecretsDirectory);
    CliTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(privateSecretsDirectory);
    Path keyFilePath = privateSecretsDirectory.resolve("entity.book-key");

    ObservedInvocation observed =
        runStandardCli(
            new String[] {
              "generate-book-key-file",
              "--new-book-key-file",
              keyFilePath.toString(),
              "--tighten-parents",
              "--output",
              "json"
            });

    assertEquals(0, observed.exitCode(), observed.stderr());
    JsonNode envelope = CliJsonObjectMappers.configuredObjectMapper().readTree(observed.stdout());
    assertEquals(
        0, envelope.path("payload").path("tightenedParentDirectories").size(), observed.stdout());
  }

  @Test
  void run_generateBookKeyFileWithParentPathCollision_honorsResolvedTextAndJsonFailureModes()
      throws IOException {
    Path parentPathCollision = tempDirectory.resolve("key-parent-collision");
    Files.writeString(parentPathCollision, "collision");
    Path missingKeyFilePath = parentPathCollision.resolve("entity.book-key");
    ObservedInvocation textObserved =
        runStandardCli(
            new String[] {
              "generate-book-key-file",
              "--new-book-key-file",
              missingKeyFilePath.toString(),
              "--output",
              "text"
            });
    ObservedInvocation jsonObserved =
        runStandardCli(
            new String[] {
              "generate-book-key-file",
              "--new-book-key-file",
              missingKeyFilePath.toString(),
              "--output",
              "json"
            });

    assertTextFailure(textObserved, 6, ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.code());
    assertTrue(
        textObserved.stderr().contains("already exists as a non-directory"), textObserved.stderr());
    String absoluteKeyFile = CliPublicPaths.absoluteValue(missingKeyFilePath);
    assertTrue(textObserved.stderr().contains("<redacted>"), textObserved.stderr());
    assertFalse(textObserved.stderr().contains(absoluteKeyFile), textObserved.stderr());
    assertFalse(textObserved.stderr().contains("internal-error"), textObserved.stderr());
    assertJsonFailure(
        jsonObserved, 6, "error", ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.code());
    assertTrue(
        jsonObserved.stderr().contains("already exists as a non-directory"), jsonObserved.stderr());
    JsonNode jsonEnvelope =
        CliJsonObjectMappers.configuredObjectMapper().readTree(jsonObserved.stderr());
    assertEquals(absoluteKeyFile, jsonEnvelope.path("path").stringValue());
    assertEquals(0, jsonEnvelope.path("relatedPaths").size());
    assertFalse(
        jsonEnvelope.path("message").stringValue().contains(absoluteKeyFile),
        jsonObserved.stderr());
    assertFalse(jsonObserved.stderr().contains("internal-error"), jsonObserved.stderr());
  }

  @Test
  void run_backupBookArtifactPathInvalid_returnsTypedMaintenanceRefusal() throws IOException {
    CliBookWorkflow workflow =
        new CliBookWorkflowAdapter() {
          @Override
          public dev.erst.fingrind.contract.runtime.ContractDecision<BackupBookResult> backupBook(
              dev.erst.fingrind.contract.runtime.BookAccess bookAccess,
              Path backupFilePath,
              Path backupBookKeyFilePath,
              java.util.UUID backupId) {
            return accepted(
                new BackupBookResult.Rejected(
                    new BookMaintenanceRejection.ArtifactPathInvalid(
                        BookMaintenanceArtifactRole.BACKUP_TARGET,
                        backupFilePath,
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
              "--backup-file",
              "backup/entity.sqlite",
              "--new-backup-key-file",
              "backup/entity.key",
              "--backup-id",
              "018f0000-0000-7000-8000-000000000001",
              "--output",
              "json"
            });

    assertJsonFailure(observed, 6, "rejected", "artifact-path-invalid");
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
              Path bookFilePath,
              Path newBookKeyFilePath,
              Path backupFilePath,
              Path backupKeyFilePath,
              java.util.List<dev.erst.fingrind.core.attestation.AttestationCredentialSource>
                  attestationCredentialSources) {
            return accepted(
                new RestoreBookResult.Rejected(
                    new BookMaintenanceRejection.ArtifactPathInvalid(
                        BookMaintenanceArtifactRole.RESTORED_TARGET,
                        bookFilePath,
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
              "--new-book-key-file",
              "book.key",
              "--backup-file",
              "backup/entity.sqlite",
              "--backup-key-file",
              "backup/entity.key",
              "--output",
              "json"
            });

    assertJsonFailure(observed, 6, "rejected", "artifact-path-invalid");
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
    int exitCode = cli.run(authenticatedArguments(arguments));
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
    int exitCode = cli.run(authenticatedArguments(arguments));
    return new ObservedInvocation(
        exitCode,
        outputStream.toString(StandardCharsets.UTF_8),
        diagnosticsStream.toString(StandardCharsets.UTF_8));
  }

  private static void assertJsonFailure(
      ObservedInvocation observed, int expectedExitCode, String expectedStatus, String expectedCode)
      throws IOException {
    assertEquals(expectedExitCode, observed.exitCode(), observed.stderr());
    assertEquals("", observed.stdout(), observed.stdout());
    JsonNode envelope = failureEnvelope(observed);
    assertEquals(expectedStatus, envelope.path("status").stringValue(), observed.stderr());
    assertEquals(expectedCode, envelope.path("code").stringValue(), observed.stderr());
  }

  private static void assertTextFailure(
      ObservedInvocation observed, int expectedExitCode, String expectedCode) {
    assertEquals(expectedExitCode, observed.exitCode(), observed.stderr());
    assertEquals("", observed.stdout(), observed.stdout());
    assertTrue(observed.stderr().contains("Error"), observed.stderr());
    assertTrue(observed.stderr().contains(expectedCode), observed.stderr());
  }

  private static JsonNode failureEnvelope(ObservedInvocation observed) throws IOException {
    return CliJsonObjectMappers.configuredObjectMapper().readTree(observed.stderr());
  }

  private String[] authenticatedArguments(String[] arguments) {
    if (arguments.length == 0 || !"open-book".equals(arguments[0])) {
      return attestedArguments(arguments);
    }
    int bookFileOption = java.util.List.of(arguments).indexOf("--book-file");
    if (bookFileOption < 0 || bookFileOption + 1 == arguments.length) {
      return arguments;
    }
    String[] founderCredentials =
        founderAttestationArguments(tempDirectory.resolve("caller-path-attestation.sqlite"));
    String[] authenticated =
        java.util.Arrays.copyOf(arguments, arguments.length + founderCredentials.length);
    System.arraycopy(
        founderCredentials, 0, authenticated, arguments.length, founderCredentials.length);
    return authenticated;
  }

  private Path createLoosePosixDirectory(String name) throws IOException {
    Assumptions.assumeTrue(supportsPosix(tempDirectory));
    Path directoryPath = tempDirectory.resolve(name);
    Files.createDirectories(directoryPath);
    Files.setPosixFilePermissions(directoryPath, LOOSE_POSIX_DIRECTORY_PERMISSIONS);
    return directoryPath;
  }

  private record ObservedInvocation(int exitCode, String stdout, String stderr) {}
}
