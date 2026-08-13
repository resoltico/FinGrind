package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Proves pair targets are distinct and every caller-path failure remains assigned to its member.
 */
class SqlitePairTargetAdmissionTest extends SqliteNativeBridgeTestSupport {
  @Test
  void distinguishesPhysicalTargetsAndConservativelyRejectsAbsentAliases() throws Exception {
    Path exact = tempDirectory.resolve("exact.sqlite");
    assertTrue(SqlitePairTargetIdentity.sameFinalTargetIdentity(exact, exact));

    Path caseVariant = tempDirectory.resolve("Case.sqlite");
    Path normalizedVariant =
        tempDirectory.resolve(Normalizer.normalize("caf\u00e9.sqlite", Form.NFD));
    assertTrue(
        SqlitePairTargetIdentity.sameFinalTargetIdentity(
            caseVariant, tempDirectory.resolve("case.sqlite")));
    assertTrue(
        SqlitePairTargetIdentity.sameFinalTargetIdentity(
            tempDirectory.resolve("caf\u00e9.sqlite"), normalizedVariant));
    assertFalse(
        SqlitePairTargetIdentity.sameFinalTargetIdentity(
            tempDirectory.resolve("book.sqlite"), tempDirectory.resolve("book.key")));

    Path existingBook = Files.writeString(tempDirectory.resolve("existing.sqlite"), "book");
    Path existingSecret = Files.writeString(tempDirectory.resolve("existing.key"), "secret");
    assertFalse(SqlitePairTargetIdentity.sameFinalTargetIdentity(existingBook, existingSecret));
    assertFalse(
        SqlitePairTargetIdentity.sameFinalTargetIdentity(
            existingBook, tempDirectory.resolve("absent.key")));

    Path linkedSecret = tempDirectory.resolve("linked.key");
    try {
      Files.createLink(linkedSecret, existingBook);
    } catch (UnsupportedOperationException | FileSystemException unsupported) {
      Assumptions.abort("The test filesystem does not support hard links: " + unsupported);
    }
    assertTrue(SqlitePairTargetIdentity.sameFinalTargetIdentity(existingBook, linkedSecret));
  }

  @Test
  void mapsEachExplicitSecurityValidatorFailureToItsSelectedArtifactRole() {
    Path book = tempDirectory.resolve("book.sqlite");
    Path secret = tempDirectory.resolve("book.key");
    SqliteCallerPathContractException bookFailure = callerFailure(book);
    ProtectedBookMaintenanceRejectionException mappedBook =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                requireRecoveryTargetSecurity(
                    book,
                    secret,
                    ignored -> {
                      throw bookFailure;
                    },
                    ignored -> {}));
    assertArtifactRole(mappedBook, ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET);

    SqliteCallerPathContractException secretFailure = callerFailure(secret);
    ProtectedBookMaintenanceRejectionException mappedSecret =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                requireRecoveryTargetSecurity(
                    book,
                    secret,
                    ignored -> {},
                    ignored -> {
                      throw secretFailure;
                    }));
    assertArtifactRole(mappedSecret, ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET);
  }

  @Test
  void failsClosedWhenSecurityValidationCannotFinishAndWhenTargetsConflict() {
    Path book = tempDirectory.resolve("unreadable-book.sqlite");
    Path secret = tempDirectory.resolve("unreadable-book.key");
    assertThrows(
        IllegalStateException.class,
        () ->
            requireRecoveryTargetSecurity(
                book,
                secret,
                ignored -> {
                  throw new IOException("book I/O");
                },
                ignored -> {}));
    assertThrows(
        IllegalStateException.class,
        () ->
            requireRecoveryTargetSecurity(
                book,
                secret,
                ignored -> {},
                ignored -> {
                  throw new IOException("key I/O");
                }));

    assertThrows(
        ProtectedBookMaintenanceRejectionException.class,
        () ->
            SqliteProtectedBookPairTargetSecurity.requirePrepublicationPairTargetAdmission(
                book,
                book,
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));
    assertDoesNotThrow(
        () ->
            SqliteProtectedBookPairTargetSecurity.requirePrepublicationPairTargetAdmission(
                book,
                secret,
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));
  }

  private static void requireRecoveryTargetSecurity(
      Path book,
      Path secret,
      SqliteProtectedBookPairTargetSecurity.TargetSecurityValidator bookValidator,
      SqliteProtectedBookPairTargetSecurity.TargetSecurityValidator secretValidator) {
    SqliteProtectedBookPairTargetSecurity.requireRecoveryTargetSecurity(
        book,
        secret,
        ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
        ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
        bookValidator,
        secretValidator);
  }

  private static SqliteCallerPathContractException callerFailure(Path path) {
    return new SqliteCallerPathContractException(
        path,
        SqliteCallerPathFailure.TARGET_OWNER_ONLY_REQUIRED,
        "target must be private",
        new IOException("injected validation failure"));
  }

  private static void assertArtifactRole(
      ProtectedBookMaintenanceRejectionException rejection,
      ProtectedBookMaintenanceArtifactRole expectedRole) {
    ProtectedBookMaintenanceRejection.ArtifactPathInvalid invalid =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class, rejection.rejection());
    assertSame(expectedRole, invalid.artifactRole());
  }
}
