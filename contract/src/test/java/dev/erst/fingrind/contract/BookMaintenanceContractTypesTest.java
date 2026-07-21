package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenancePathFailure;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.BookMigrationPolicy;
import dev.erst.fingrind.contract.runtime.BookMigrationPolicyMode;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Contract tests for maintenance result, rejection, and migration-policy types. */
class BookMaintenanceContractTypesTest extends ContractTestSupport {
  @Test
  void maintenanceRejections_publishCanonicalDescriptorsAndWireCodes() {
    Path bookFile = Path.of("books/acme.sqlite");
    Path backupFile = Path.of("backup/acme.sqlite");
    Path backupKeyFile = Path.of("backup/acme.book-key");
    List<BookMaintenanceRejection> rejections =
        List.of(
            new BookMaintenanceRejection.BookHasBlockingArtifacts(
                hint(bookFile), List.of(hint(bookFile.resolveSibling("acme.sqlite-wal")))),
            new BookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                hint(backupFile), List.of(hint(backupFile.resolveSibling("acme.sqlite-wal")))),
            new BookMaintenanceRejection.BackupSourceMatchesLiveBook(
                hint(bookFile), hint(backupFile)),
            new BookMaintenanceRejection.ArtifactPathInvalid(
                BookMaintenanceArtifactRole.BACKUP_TARGET,
                hint(backupFile),
                BookMaintenancePathFailure.PARENT_PATH_COLLISION),
            new BookMaintenanceRejection.ArtifactBusy(
                BookMaintenanceArtifactRole.LIVE_BOOK, hint(bookFile)),
            new BookMaintenanceRejection.BackupAcknowledgementConflict(UUID.randomUUID()),
            new BookMaintenanceRejection.BackupDestinationAlreadyExists(hint(backupFile)),
            new BookMaintenanceRejection.SecretTargetOccupied(hint(backupKeyFile)),
            new BookMaintenanceRejection.BookDestinationOccupied(hint(bookFile)),
            new BookMaintenanceRejection.ArtifactVerificationFailed(
                BookMaintenanceArtifactRole.BACKUP_SOURCE,
                hint(backupFile),
                BookMaintenanceVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED));

    Map<String, ContractResponse.RejectionDescriptor> descriptorsByCode =
        BookMaintenanceRejection.descriptors().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    ContractResponse.RejectionDescriptor::code, descriptor -> descriptor));

    assertEquals(10, descriptorsByCode.size());
    for (BookMaintenanceRejection rejection : rejections) {
      String code = BookMaintenanceRejection.wireCode(rejection);
      ContractResponse.RejectionDescriptor descriptor = descriptorsByCode.get(code);
      assertTrue(descriptor != null, () -> "Missing descriptor for code " + code);
      assertTrue(!descriptor.description().isBlank(), () -> "Blank description for " + code);
    }
    assertFalse(
        descriptorsByCode.values().stream()
            .flatMap(descriptor -> descriptor.detailFields().stream())
            .anyMatch(field -> field.description().contains("Redacted public hint")));
    assertTrue(
        descriptorsByCode.values().stream()
            .flatMap(descriptor -> descriptor.detailFields().stream())
            .filter(
                field ->
                    List.of(
                            "bookFile",
                            "backupFile",
                            "artifactPath",
                            "secretTarget",
                            "blockingArtifacts")
                        .contains(field.name()))
            .allMatch(field -> field.description().startsWith("Canonical absolute")));
  }

  @Test
  void maintenanceRejections_validateConstructorState() {
    Path bookFile = Path.of("books/acme.sqlite");
    Path backupFile = Path.of("backup/acme.sqlite");

    assertThrows(
        IllegalArgumentException.class,
        () -> new BookMaintenanceRejection.BookHasBlockingArtifacts(hint(bookFile), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                hint(backupFile), List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookMaintenanceRejection.ArtifactPathInvalid(
                nullOf(), hint(backupFile), BookMaintenancePathFailure.PARENT_PATH_COLLISION));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookMaintenanceRejection.ArtifactPathInvalid(
                BookMaintenanceArtifactRole.BACKUP_TARGET,
                nullOf(),
                BookMaintenancePathFailure.PARENT_PATH_COLLISION));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookMaintenanceRejection.ArtifactPathInvalid(
                BookMaintenanceArtifactRole.BACKUP_TARGET, hint(backupFile), nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.ArtifactBusy(nullOf(), hint(bookFile)));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookMaintenanceRejection.ArtifactBusy(
                BookMaintenanceArtifactRole.LIVE_BOOK, nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.BackupSourceMatchesLiveBook(nullOf(), hint(backupFile)));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.BackupSourceMatchesLiveBook(hint(bookFile), nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.BackupDestinationAlreadyExists(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.SecretTargetOccupied(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.BookDestinationOccupied(nullOf()));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookMaintenanceRejection.ArtifactVerificationFailed(
                nullOf(),
                hint(backupFile),
                BookMaintenanceVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookMaintenanceRejection.ArtifactVerificationFailed(
                BookMaintenanceArtifactRole.BACKUP_SOURCE,
                nullOf(),
                BookMaintenanceVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookMaintenanceRejection.ArtifactVerificationFailed(
                BookMaintenanceArtifactRole.BACKUP_SOURCE, hint(backupFile), nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.BackupAcknowledgementConflict(nullOf()));
  }

  @Test
  void maintenanceResults_andMigrationPolicy_exposeCanonicalState() {
    Path bookFile = Path.of("books/acme.sqlite");
    Path backupFile = Path.of("backup/acme.sqlite");
    Path backupKeyFile = Path.of("backup/acme.book-key");
    BookMaintenanceRejection rejection =
        new BookMaintenanceRejection.BackupDestinationAlreadyExists(hint(backupFile));

    UUID backupId = UUID.randomUUID();
    BackupBookResult.BackedUp backedUp =
        new BackupBookResult.BackedUp(
            hint(bookFile), hint(backupFile), hint(backupKeyFile), backupId, false);
    BackupBookResult.AcknowledgementPending acknowledgementPending =
        new BackupBookResult.AcknowledgementPending(
            hint(bookFile), hint(backupFile), hint(backupKeyFile), backupId);
    BackupBookResult.Rejected backupRejected = new BackupBookResult.Rejected(rejection);
    RestoreBookResult.Restored restored =
        new RestoreBookResult.Restored(
            hint(bookFile), hint(bookFile.resolveSibling("acme-restored.book-key")));
    RestoreBookResult.Rejected restoreRejected = new RestoreBookResult.Rejected(rejection);
    BookMigrationPolicy migrationPolicy = BookMigrationPolicy.current(9);

    assertEquals(hint(bookFile), backedUp.bookFilePath());
    assertEquals(hint(backupFile), backedUp.backupFilePath());
    assertEquals(hint(backupKeyFile), backedUp.backupBookKeyFilePath());
    assertEquals(backupId, backedUp.backupId());
    assertFalse(backedUp.acknowledgementResumed());
    assertEquals(backupId, acknowledgementPending.backupId());
    assertEquals(rejection, backupRejected.rejection());
    assertEquals(hint(bookFile), restored.bookFilePath());
    assertEquals(
        hint(bookFile.resolveSibling("acme-restored.book-key")), restored.bookKeyFilePath());
    assertEquals(rejection, restoreRejected.rejection());
    assertEquals(BookMigrationPolicyMode.HARD_BREAK_REJECT_OLDER_FORMATS, migrationPolicy.mode());
    assertEquals(9, migrationPolicy.supportedBookFormatVersion());
    assertEquals(List.of("hard-break-reject-older-formats"), BookMigrationPolicyMode.wireValues());
    assertEquals(
        BookMigrationPolicyMode.HARD_BREAK_REJECT_OLDER_FORMATS,
        BookMigrationPolicyMode.fromWireValue("hard-break-reject-older-formats"));
    assertThrows(
        NullPointerException.class,
        () ->
            new BackupBookResult.BackedUp(
                nullOf(), hint(backupFile), hint(backupKeyFile), backupId, false));
    assertThrows(
        NullPointerException.class,
        () ->
            new BackupBookResult.BackedUp(
                hint(bookFile), nullOf(), hint(backupKeyFile), backupId, false));
    assertThrows(
        NullPointerException.class,
        () ->
            new BackupBookResult.BackedUp(
                hint(bookFile), hint(backupFile), nullOf(), backupId, false));
    assertThrows(
        NullPointerException.class,
        () ->
            new BackupBookResult.BackedUp(
                hint(bookFile), hint(backupFile), hint(backupKeyFile), nullOf(), false));
    assertThrows(NullPointerException.class, () -> new BackupBookResult.Rejected(nullOf()));
    assertThrows(
        NullPointerException.class,
        () ->
            new RestoreBookResult.Restored(
                nullOf(), hint(bookFile.resolveSibling("acme-restored.book-key"))));
    assertThrows(
        NullPointerException.class, () -> new RestoreBookResult.Restored(hint(bookFile), nullOf()));
    assertThrows(NullPointerException.class, () -> new RestoreBookResult.Rejected(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookMigrationPolicy(nullOf(), false, false, false, 8));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookMigrationPolicy(
                BookMigrationPolicyMode.HARD_BREAK_REJECT_OLDER_FORMATS, false, false, false, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> BookMigrationPolicyMode.fromWireValue("unsupported-mode"));
  }

  @Test
  void maintenanceEnums_andPublicPathHints_publishCanonicalWireVocabulary() {
    assertIterableEquals(
        List.of(
            "live-book", "backup-source", "backup-target", "backup-key-target", "restored-target"),
        BookMaintenanceArtifactRole.wireValues());
    assertEquals("live-book", BookMaintenanceArtifactRole.LIVE_BOOK.wireValue());
    assertEquals("backup-source", BookMaintenanceArtifactRole.BACKUP_SOURCE.wireValue());
    assertEquals("backup-target", BookMaintenanceArtifactRole.BACKUP_TARGET.wireValue());
    assertEquals("backup-key-target", BookMaintenanceArtifactRole.BACKUP_KEY_TARGET.wireValue());
    assertEquals("restored-target", BookMaintenanceArtifactRole.RESTORED_TARGET.wireValue());

    assertIterableEquals(
        List.of(
            "missing-parent-directory",
            "parent-path-collision",
            "parent-owner-access-required",
            "parent-owner-only-required",
            "target-must-be-regular-non-symlink-file",
            "unsupported-secure-filesystem",
            "atomic-secret-publication-unsupported"),
        BookMaintenancePathFailure.wireValues());
    assertEquals(
        "missing-parent-directory",
        BookMaintenancePathFailure.MISSING_PARENT_DIRECTORY.wireValue());
    assertEquals(
        "parent-path-collision", BookMaintenancePathFailure.PARENT_PATH_COLLISION.wireValue());
    assertEquals(
        "parent-owner-access-required",
        BookMaintenancePathFailure.PARENT_OWNER_ACCESS_REQUIRED.wireValue());
    assertEquals(
        "parent-owner-only-required",
        BookMaintenancePathFailure.PARENT_OWNER_ONLY_REQUIRED.wireValue());
    assertEquals(
        "target-must-be-regular-non-symlink-file",
        BookMaintenancePathFailure.TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE.wireValue());
    assertEquals(
        "unsupported-secure-filesystem",
        BookMaintenancePathFailure.UNSUPPORTED_SECURE_FILESYSTEM.wireValue());
    assertEquals(
        "atomic-secret-publication-unsupported",
        BookMaintenancePathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED.wireValue());

    assertIterableEquals(
        List.of(
            "missing",
            "blank-sqlite",
            "foreign-sqlite",
            "unsupported-format-version",
            "incomplete-fingrind",
            "protected-book-verification-failed"),
        BookMaintenanceVerificationFailure.wireValues());
    assertEquals("missing", BookMaintenanceVerificationFailure.MISSING.wireValue());
    assertEquals("blank-sqlite", BookMaintenanceVerificationFailure.BLANK_SQLITE.wireValue());
    assertEquals("foreign-sqlite", BookMaintenanceVerificationFailure.FOREIGN_SQLITE.wireValue());
    assertEquals(
        "unsupported-format-version",
        BookMaintenanceVerificationFailure.UNSUPPORTED_FORMAT_VERSION.wireValue());
    assertEquals(
        "incomplete-fingrind", BookMaintenanceVerificationFailure.INCOMPLETE_FINGRIND.wireValue());
    assertEquals(
        "protected-book-verification-failed",
        BookMaintenanceVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED.wireValue());

    assertEquals(
        "<redacted>/books/acme.sqlite", new PublicPathHint("<redacted>/books/acme.sqlite").value());
    assertEquals("<redacted>", new PublicPathHint("<redacted>").value());
    assertEquals(
        "<redacted>/contract/books/acme.sqlite",
        PublicPathHint.fromPath(Path.of("books/acme.sqlite")).value());
    assertEquals("<redacted>", PublicPathHint.fromPath(Path.of("/")).value());
    assertIterableEquals(
        List.of(
            "<redacted>/work-volume/books/main.sqlite",
            "<redacted>/backup/books/main.sqlite",
            "<redacted>/backup/secrets/main.book-key"),
        PublicPathHint.disambiguate(
                List.of(
                    Path.of("/tmp/field-audit/work-volume/books/main.sqlite"),
                    Path.of("/tmp/field-audit/work-volume/backup/books/main.sqlite"),
                    Path.of("/tmp/field-audit/work-volume/backup/secrets/main.book-key")))
            .stream()
            .map(PublicPathHint::value)
            .toList());
    assertIterableEquals(List.of(), PublicPathHint.disambiguate(List.of()));
    assertIterableEquals(
        List.of(
            "<redacted>/field-audit/books/acme.sqlite", "<redacted>/field-audit/books/acme.sqlite"),
        PublicPathHint.disambiguate(
                List.of(
                    Path.of("/tmp/field-audit/books/acme.sqlite"),
                    Path.of("/tmp/field-audit/books/acme.sqlite")))
            .stream()
            .map(PublicPathHint::value)
            .toList());
    assertIterableEquals(
        List.of("<redacted>", "<redacted>/acme.sqlite"),
        PublicPathHint.disambiguate(
                List.of(Path.of("/"), Path.of("/tmp/field-audit/books/acme.sqlite")))
            .stream()
            .map(PublicPathHint::value)
            .toList());
    assertIterableEquals(
        List.of("<redacted>/field-audit/books/acme.sqlite", "<redacted>/books/acme.sqlite"),
        PublicPathHint.disambiguate(
                List.of(
                    Path.of("/tmp/field-audit/books/acme.sqlite"), Path.of("/books/acme.sqlite")))
            .stream()
            .map(PublicPathHint::value)
            .toList());

    assertThrows(NullPointerException.class, () -> PublicPathHint.disambiguate(nullOf()));
    assertThrows(
        NullPointerException.class,
        () ->
            PublicPathHint.disambiguate(
                List.of(Path.of("/tmp/field-audit/books/acme.sqlite"), nullOf())));
    assertThrows(NullPointerException.class, () -> new PublicPathHint(nullOf()));
    assertThrows(IllegalArgumentException.class, () -> new PublicPathHint("books/acme.sqlite"));
  }

  private static Path hint(Path path) {
    return path.toAbsolutePath().normalize();
  }
}
