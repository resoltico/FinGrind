package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Field-level protected-book no-clobber and key-isolation workflow coverage. */
class FinGrindCliProtectedBookWorkflowTest extends CliWorkflowFixtureSupport {
  @Test
  void run_openBookRefusesAnExistingDestinationBeforeResolvingItsKey() throws IOException {
    Path existingBookFilePath = tempDirectory.resolve("open-book-occupied.sqlite");
    Files.writeString(existingBookFilePath, "unrelated-book-content");
    byte[] existingBookBefore = Files.readAllBytes(existingBookFilePath);
    Path missingBookKeyFilePath = tempDirectory.resolve("missing-open-book.key");

    JsonCliRun result =
        runAttestedJson(
            founderAttestedArguments(
                existingBookFilePath,
                "open-book",
                "--book-file",
                existingBookFilePath.toString(),
                "--book-key-file",
                missingBookKeyFilePath.toString(),
                "--entity-name",
                "Occupied Destination",
                "--functional-currency",
                "EUR",
                "--fiscal-year-start",
                "01-01",
                "--book-start-effective-date",
                "2026-01-01",
                "--book-template-id",
                "OWNER_MANAGED_SERVICE",
                "--accounting-basis",
                "CASH"));

    assertEquals(7, result.exitCode(), result.output());
    assertEquals("error", result.envelope().path("status").stringValue(), result.output());
    assertEquals(
        ContractErrors.Descriptor.BOOK_DESTINATION_OCCUPIED.code(),
        result.envelope().path("code").stringValue(),
        result.output());
    assertEquals("precondition", result.envelope().path("category").stringValue(), result.output());
    assertEquals("--book-file", result.envelope().path("argument").stringValue(), result.output());
    assertEquals(
        "The selected --book-file destination already exists; open-book will not access or replace it.",
        result.envelope().path("message").stringValue(),
        result.output());
    assertEquals(
        "Choose a missing --book-file destination before opening a new book.",
        result.envelope().path("hint").stringValue(),
        result.output());
    assertFalse(
        result
            .envelope()
            .path("message")
            .stringValue()
            .contains(existingBookFilePath.toAbsolutePath().normalize().toString()),
        result.output());
    assertEquals(
        existingBookFilePath.toAbsolutePath().normalize().toString(),
        result.envelope().path("path").stringValue(),
        result.output());
    assertTrue(result.envelope().has("relatedPaths"), result.output());
    assertTrue(result.envelope().path("relatedPaths").isArray(), result.output());
    assertEquals(0, result.envelope().path("relatedPaths").size(), result.output());
    assertArrayEquals(existingBookBefore, Files.readAllBytes(existingBookFilePath));
    assertFalse(Files.exists(missingBookKeyFilePath));
  }

  @Test
  void run_restoreBookRejectsAnUnmanifestedLegacyBackupPair() throws IOException {
    Path root = tempDirectory.resolve("legacy-backup-restore");
    Path sourceBookFilePath = root.resolve("source").resolve("entity.sqlite");
    Path sourceBookKeyFilePath = writeBookKey(sourceBookFilePath);
    assertEquals(
        0, runJson(openBookKeyFileArguments(sourceBookFilePath, sourceBookKeyFilePath)).exitCode());

    Path legacyBackupFilePath = root.resolve("legacy-backup").resolve("entity.sqlite");
    Path legacyBackupKeyFilePath = legacyBackupFilePath.resolveSibling("entity.key");
    Path legacyBackupDirectory =
        Objects.requireNonNull(legacyBackupKeyFilePath.getParent(), "legacy backup directory");
    Files.createDirectories(legacyBackupDirectory);
    CliTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(legacyBackupDirectory);
    Files.copy(sourceBookFilePath, legacyBackupFilePath);
    Files.copy(sourceBookKeyFilePath, legacyBackupKeyFilePath, StandardCopyOption.COPY_ATTRIBUTES);
    assertArrayEquals(
        Files.readAllBytes(sourceBookKeyFilePath), Files.readAllBytes(legacyBackupKeyFilePath));
    assertEquals(0, openBookForRead(legacyBackupFilePath, legacyBackupKeyFilePath).exitCode());

    Path restoredBookFilePath = root.resolve("restored").resolve("entity.sqlite");
    Path restoredBookKeyFilePath = root.resolve("restored").resolve("entity.key");
    JsonCliRun restored =
        runAttestedJson(
            attestedArgumentsForBook(
                sourceBookFilePath,
                "restore-book",
                "--book-file",
                restoredBookFilePath.toString(),
                "--new-book-key-file",
                restoredBookKeyFilePath.toString(),
                "--backup-file",
                legacyBackupFilePath.toString(),
                "--backup-key-file",
                legacyBackupKeyFilePath.toString()));

    assertEquals(6, restored.exitCode(), restored.output());
    assertEquals("artifact-verification-failed", restored.envelope().path("code").stringValue());
    assertEquals(0, openBookForRead(legacyBackupFilePath, legacyBackupKeyFilePath).exitCode());
    assertFalse(Files.exists(restoredBookFilePath));
    assertFalse(Files.exists(restoredBookKeyFilePath));
  }

  @Test
  void run_generatedSecretAndRestoreSafetyMatrix_preservesUnacknowledgedTargets()
      throws IOException {
    Path root = tempDirectory.resolve("protected-book-safety");
    Path sourceBookFilePath = root.resolve("source").resolve("entity.sqlite");
    Path sourceBookKeyFilePath = writeBookKey(sourceBookFilePath);
    assertEquals(
        0, runJson(openBookKeyFileArguments(sourceBookFilePath, sourceBookKeyFilePath)).exitCode());
    byte[] sourceBookBefore = Files.readAllBytes(sourceBookFilePath);
    byte[] sourceKeyBefore = Files.readAllBytes(sourceBookKeyFilePath);

    Path occupiedGeneratedKeyFilePath = writeNamedBookKey("occupied-generated.key", "occupied-key");
    byte[] occupiedKeyBefore = Files.readAllBytes(occupiedGeneratedKeyFilePath);
    assertMaintenanceCollision(
        runJson(
            "generate-book-key-file",
            "--new-book-key-file",
            occupiedGeneratedKeyFilePath.toString()),
        "error",
        "secret-target-occupied");
    assertArrayEquals(occupiedKeyBefore, Files.readAllBytes(occupiedGeneratedKeyFilePath));

    Path wrongSourceKeyFilePath = writeNamedBookKey("wrong-safety-source.key", "wrong-source-key");
    JsonCliRun rejectedWrongSource =
        runAttestedJson(
            "rekey-book",
            "--book-file",
            sourceBookFilePath.toString(),
            "--book-key-file",
            wrongSourceKeyFilePath.toString(),
            "--new-book-key-file",
            occupiedGeneratedKeyFilePath.toString());
    assertEquals(6, rejectedWrongSource.exitCode(), rejectedWrongSource.output());
    assertEquals(
        "artifact-verification-failed",
        rejectedWrongSource.envelope().path("code").stringValue(),
        rejectedWrongSource.output());
    assertArrayEquals(sourceBookBefore, Files.readAllBytes(sourceBookFilePath));
    assertArrayEquals(occupiedKeyBefore, Files.readAllBytes(occupiedGeneratedKeyFilePath));

    Path rejectedBackupFilePath = root.resolve("backup").resolve("rejected.sqlite");
    assertMaintenanceCollision(
        runAttestedJson(
            "backup-book",
            "--book-file",
            sourceBookFilePath.toString(),
            "--book-key-file",
            sourceBookKeyFilePath.toString(),
            "--backup-file",
            rejectedBackupFilePath.toString(),
            "--backup-id",
            "018f0000-0000-7000-8000-000000000001",
            "--new-backup-key-file",
            occupiedGeneratedKeyFilePath.toString()),
        "rejected",
        "secret-target-occupied");
    assertFalse(Files.exists(rejectedBackupFilePath));
    assertArrayEquals(sourceBookBefore, Files.readAllBytes(sourceBookFilePath));
    assertArrayEquals(occupiedKeyBefore, Files.readAllBytes(occupiedGeneratedKeyFilePath));

    Path backupFilePath = root.resolve("backup").resolve("entity.sqlite");
    Path backupKeyFilePath = root.resolve("backup").resolve("entity.key");
    JsonCliRun backup =
        runAttestedJson(
            "backup-book",
            "--book-file",
            sourceBookFilePath.toString(),
            "--book-key-file",
            sourceBookKeyFilePath.toString(),
            "--backup-file",
            backupFilePath.toString(),
            "--backup-id",
            "018f0000-0000-7000-8000-000000000002",
            "--new-backup-key-file",
            backupKeyFilePath.toString());
    assertEquals(0, backup.exitCode(), backup.output());
    assertArtifactPaths(backup, backupFilePath, backupKeyFilePath);
    assertFalse(Arrays.equals(sourceBookBefore, Files.readAllBytes(sourceBookFilePath)));
    assertFalse(Arrays.equals(sourceKeyBefore, Files.readAllBytes(backupKeyFilePath)));
    assertEquals(0, openBookForRead(sourceBookFilePath, sourceBookKeyFilePath).exitCode());
    assertProtectedBookVerificationFailure(openBookForRead(sourceBookFilePath, backupKeyFilePath));
    assertEquals(0, openBookForRead(backupFilePath, backupKeyFilePath).exitCode());
    assertProtectedBookVerificationFailure(openBookForRead(backupFilePath, sourceBookKeyFilePath));

    Path destinationBookFilePath = root.resolve("destination").resolve("entity.sqlite");
    Path destinationBookKeyFilePath = writeBookKey(destinationBookFilePath, "destination-key-2026");
    assertEquals(
        0,
        runJson(openBookKeyFileArguments(destinationBookFilePath, destinationBookKeyFilePath))
            .exitCode());
    byte[] destinationBookBefore = Files.readAllBytes(destinationBookFilePath);
    byte[] destinationKeyBefore = Files.readAllBytes(destinationBookKeyFilePath);
    Path replacementKeyFilePath = root.resolve("destination").resolve("replacement.key");

    JsonCliRun restoreWithoutConsent =
        runAttestedJson(
            attestedArgumentsForBook(
                sourceBookFilePath,
                "restore-book",
                "--book-file",
                destinationBookFilePath.toString(),
                "--new-book-key-file",
                replacementKeyFilePath.toString(),
                "--backup-file",
                backupFilePath.toString(),
                "--backup-key-file",
                backupKeyFilePath.toString()));
    assertMaintenanceCollision(restoreWithoutConsent, "rejected", "book-destination-occupied");
    assertEquals(
        destinationBookFilePath.toAbsolutePath().normalize().toString(),
        failureDetails(restoreWithoutConsent).path("bookFile").stringValue(),
        restoreWithoutConsent.output());
    assertArrayEquals(destinationBookBefore, Files.readAllBytes(destinationBookFilePath));
    assertArrayEquals(destinationKeyBefore, Files.readAllBytes(destinationBookKeyFilePath));
    assertFalse(Files.exists(replacementKeyFilePath));

    JsonCliRun deprecatedReplacement =
        runAttestedJson(
            attestedArgumentsForBook(
                sourceBookFilePath,
                "restore-book",
                "--book-file",
                destinationBookFilePath.toString(),
                "--new-book-key-file",
                replacementKeyFilePath.toString(),
                "--backup-file",
                backupFilePath.toString(),
                "--backup-key-file",
                backupKeyFilePath.toString(),
                "--replace-existing-book"));
    assertEquals(1, deprecatedReplacement.exitCode(), deprecatedReplacement.output());
    assertEquals("invalid-request", deprecatedReplacement.envelope().path("code").stringValue());
    assertArrayEquals(destinationBookBefore, Files.readAllBytes(destinationBookFilePath));

    Path restoredBookFilePath = root.resolve("restored").resolve("entity.sqlite");
    Path restoredBookKeyFilePath = root.resolve("restored").resolve("entity.key");
    JsonCliRun restored =
        runAttestedJson(
            attestedArgumentsForBook(
                sourceBookFilePath,
                "restore-book",
                "--book-file",
                restoredBookFilePath.toString(),
                "--new-book-key-file",
                restoredBookKeyFilePath.toString(),
                "--backup-file",
                backupFilePath.toString(),
                "--backup-key-file",
                backupKeyFilePath.toString()));
    assertEquals(0, restored.exitCode(), restored.output());
    assertArtifactPaths(restored, restoredBookFilePath, restoredBookKeyFilePath);
    assertEquals(
        restoredBookFilePath.toAbsolutePath().normalize().toString(),
        restored.envelope().path("payload").path("bookFile").stringValue(),
        restored.output());
    assertEquals(
        restoredBookKeyFilePath.toAbsolutePath().normalize().toString(),
        restored.envelope().path("payload").path("bookKeyFilePath").stringValue(),
        restored.output());
    assertTrue(Files.isRegularFile(restoredBookKeyFilePath));
    assertEquals(
        0, openBookForRead(destinationBookFilePath, destinationBookKeyFilePath).exitCode());
    assertProtectedBookVerificationFailure(
        openBookForRead(restoredBookFilePath, backupKeyFilePath));
    assertEquals(0, openBookForRead(restoredBookFilePath, restoredBookKeyFilePath).exitCode());
    assertFalse(Arrays.equals(sourceBookBefore, Files.readAllBytes(sourceBookFilePath)));
    assertArrayEquals(sourceKeyBefore, Files.readAllBytes(sourceBookKeyFilePath));
  }

  @Test
  void run_rekeyBackupRestore_reusesOnlyOneDeliberatelyReleasedFormerKeyPath() throws IOException {
    Path root = tempDirectory.resolve("rekey-backup-restore");
    Path sourceBookFilePath = root.resolve("source").resolve("entity.sqlite");
    Path originalSourceKeyFilePath = writeBookKey(sourceBookFilePath);
    assertEquals(
        0,
        runJson(openBookKeyFileArguments(sourceBookFilePath, originalSourceKeyFilePath))
            .exitCode());

    Path rotatedSourceKeyFilePath = root.resolve("source").resolve("entity.rotated.key");
    JsonCliRun rekeyed =
        runAttestedJson(
            "rekey-book",
            "--book-file",
            sourceBookFilePath.toString(),
            "--book-key-file",
            originalSourceKeyFilePath.toString(),
            "--new-book-key-file",
            rotatedSourceKeyFilePath.toString());
    assertEquals(0, rekeyed.exitCode(), rekeyed.output());
    assertArtifactPaths(rekeyed, rotatedSourceKeyFilePath);
    assertProtectedBookVerificationFailure(
        openBookForRead(sourceBookFilePath, originalSourceKeyFilePath));
    assertEquals(0, openBookForRead(sourceBookFilePath, rotatedSourceKeyFilePath).exitCode());

    Path occupiedLaterKeyFilePath = root.resolve("collision").resolve("occupied-later.key");
    writeSecureKey(occupiedLaterKeyFilePath, "caller-owned-later-key");
    byte[] rekeyedBookBeforeCollision = Files.readAllBytes(sourceBookFilePath);
    byte[] occupiedLaterKeyBefore = Files.readAllBytes(occupiedLaterKeyFilePath);
    assertMaintenanceCollision(
        runAttestedJson(
            "rekey-book",
            "--book-file",
            sourceBookFilePath.toString(),
            "--book-key-file",
            rotatedSourceKeyFilePath.toString(),
            "--new-book-key-file",
            occupiedLaterKeyFilePath.toString()),
        "rejected",
        "secret-target-occupied");
    assertArrayEquals(rekeyedBookBeforeCollision, Files.readAllBytes(sourceBookFilePath));
    assertArrayEquals(occupiedLaterKeyBefore, Files.readAllBytes(occupiedLaterKeyFilePath));
    assertEquals(0, openBookForRead(sourceBookFilePath, rotatedSourceKeyFilePath).exitCode());

    Files.delete(originalSourceKeyFilePath);
    Path backupFilePath = root.resolve("backup").resolve("entity.sqlite");
    Path backupKeyFilePath = root.resolve("backup").resolve("entity.key");
    JsonCliRun backedUp =
        runAttestedJson(
            "backup-book",
            "--book-file",
            sourceBookFilePath.toString(),
            "--book-key-file",
            rotatedSourceKeyFilePath.toString(),
            "--backup-file",
            backupFilePath.toString(),
            "--backup-id",
            "018f0000-0000-7000-8000-000000000003",
            "--new-backup-key-file",
            backupKeyFilePath.toString());
    assertEquals(0, backedUp.exitCode(), backedUp.output());
    assertArtifactPaths(backedUp, backupFilePath, backupKeyFilePath);

    Path restoredBookFilePath = root.resolve("restored").resolve("entity.sqlite");
    JsonCliRun restored =
        runAttestedJson(
            attestedArgumentsForBook(
                sourceBookFilePath,
                "restore-book",
                "--book-file",
                restoredBookFilePath.toString(),
                "--new-book-key-file",
                originalSourceKeyFilePath.toString(),
                "--backup-file",
                backupFilePath.toString(),
                "--backup-key-file",
                backupKeyFilePath.toString()));
    assertEquals(0, restored.exitCode(), restored.output());
    assertArtifactPaths(restored, restoredBookFilePath, originalSourceKeyFilePath);
    assertEquals(0, openBookForRead(sourceBookFilePath, rotatedSourceKeyFilePath).exitCode());
    assertEquals(0, openBookForRead(restoredBookFilePath, originalSourceKeyFilePath).exitCode());
    assertProtectedBookVerificationFailure(
        openBookForRead(restoredBookFilePath, rotatedSourceKeyFilePath));
    assertProtectedBookVerificationFailure(
        openBookForRead(sourceBookFilePath, originalSourceKeyFilePath));
  }

  private JsonCliRun openBookForRead(Path bookFilePath, Path bookKeyFilePath) throws IOException {
    return runJson(
        "list-accounts",
        "--book-file",
        bookFilePath.toString(),
        "--book-key-file",
        bookKeyFilePath.toString());
  }

  private JsonCliRun runJson(String... arguments) throws IOException {
    return runJsonWithArguments(jsonArguments(arguments));
  }

  private JsonCliRun runAttestedJson(String... arguments) throws IOException {
    return runJsonWithArguments(attestedJsonArguments(arguments));
  }

  private JsonCliRun runJsonWithArguments(String... arguments) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    int exitCode =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock())
            .run(arguments);
    return new JsonCliRun(
        exitCode,
        new ObjectMapper().readTree(outputStream.toByteArray()),
        outputStream.toString(StandardCharsets.UTF_8));
  }

  private static void assertMaintenanceCollision(
      JsonCliRun result, String expectedStatus, String expectedCode) {
    assertEquals(7, result.exitCode(), result.output());
    assertEquals(expectedStatus, result.envelope().path("status").stringValue(), result.output());
    assertEquals(expectedCode, result.envelope().path("code").stringValue(), result.output());
  }

  private static JsonNode failureDetails(JsonCliRun result) {
    return result.envelope().path("details");
  }

  private static void assertArtifactPaths(JsonCliRun result, Path... expectedPaths) {
    assertEquals(expectedPaths.length, result.envelope().path("artifacts").size(), result.output());
    for (int index = 0; index < expectedPaths.length; index++) {
      assertEquals(
          expectedPaths[index].toAbsolutePath().normalize().toString(),
          result.envelope().path("artifacts").get(index).path("path").stringValue(),
          result.output());
    }
  }

  private static void assertProtectedBookVerificationFailure(JsonCliRun result) {
    assertEquals(6, result.exitCode(), result.output());
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.code(),
        result.envelope().path("code").stringValue(),
        result.output());
  }

  private record JsonCliRun(int exitCode, JsonNode envelope, String output) {}
}
