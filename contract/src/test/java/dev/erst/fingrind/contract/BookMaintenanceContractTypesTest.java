package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.RecoverRekeyResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRecoveryAction;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.BookMigrationPolicy;
import dev.erst.fingrind.contract.runtime.BookMigrationPolicyMode;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Contract tests for maintenance result, rejection, and migration-policy types. */
class BookMaintenanceContractTypesTest extends ContractTestSupport {
  @Test
  void maintenanceRejections_publishCanonicalDescriptorsAndWireCodes() {
    Path bookFile = Path.of("books/acme.sqlite");
    Path backupFile = Path.of("backup/acme.sqlite");
    Path backupKeyFile = Path.of("backup/acme.book-key");
    Path rollbackArtifact = Path.of("books/acme.rekey-rollback-1.sqlite");
    Path secondRollbackArtifact = Path.of("books/acme.rekey-rollback-2.sqlite");
    List<BookMaintenanceRejection> rejections =
        List.of(
            new BookMaintenanceRejection.BookHasBlockingArtifacts(
                bookFile, List.of(rollbackArtifact)),
            new BookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                backupFile, List.of(rollbackArtifact)),
            new BookMaintenanceRejection.BackupDestinationAlreadyExists(backupFile),
            new BookMaintenanceRejection.BackupKeyFileAlreadyExists(backupKeyFile),
            new BookMaintenanceRejection.NoRollbackArtifactsFound(bookFile),
            new BookMaintenanceRejection.RollbackArtifactSelectionRequired(
                bookFile, List.of(rollbackArtifact, secondRollbackArtifact)),
            new BookMaintenanceRejection.RollbackArtifactNotFound(rollbackArtifact),
            new BookMaintenanceRejection.RollbackArtifactNotForBook(bookFile, rollbackArtifact));

    Map<String, ContractResponse.RejectionDescriptor> descriptorsByCode =
        BookMaintenanceRejection.descriptors().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    ContractResponse.RejectionDescriptor::code, descriptor -> descriptor));

    assertEquals(8, descriptorsByCode.size());
    for (BookMaintenanceRejection rejection : rejections) {
      String code = BookMaintenanceRejection.wireCode(rejection);
      ContractResponse.RejectionDescriptor descriptor = descriptorsByCode.get(code);
      assertTrue(descriptor != null, () -> "Missing descriptor for code " + code);
      assertTrue(!descriptor.description().isBlank(), () -> "Blank description for " + code);
    }
  }

  @Test
  void maintenanceRejections_validateConstructorState() {
    Path bookFile = Path.of("books/acme.sqlite");
    Path backupFile = Path.of("backup/acme.sqlite");
    Path rollbackArtifact = Path.of("books/acme.rekey-rollback-1.sqlite");

    assertThrows(
        IllegalArgumentException.class,
        () -> new BookMaintenanceRejection.BookHasBlockingArtifacts(bookFile, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new BookMaintenanceRejection.BackupSourceHasBlockingArtifacts(backupFile, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookMaintenanceRejection.RollbackArtifactSelectionRequired(
                bookFile, List.of(rollbackArtifact)));

    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.BackupDestinationAlreadyExists(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.BackupKeyFileAlreadyExists(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.NoRollbackArtifactsFound(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.RollbackArtifactNotFound(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.RollbackArtifactNotForBook(nullOf(), rollbackArtifact));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.RollbackArtifactNotForBook(bookFile, nullOf()));
  }

  @Test
  void maintenanceResults_andMigrationPolicy_exposeCanonicalState() {
    Path bookFile = Path.of("books/acme.sqlite");
    Path backupFile = Path.of("backup/acme.sqlite");
    Path backupKeyFile = Path.of("backup/acme.book-key");
    Path rollbackArtifact = Path.of("books/acme.rekey-rollback-1.sqlite");
    BookMaintenanceRejection rejection =
        new BookMaintenanceRejection.BackupDestinationAlreadyExists(backupFile);

    BackupBookResult.BackedUp backedUp =
        new BackupBookResult.BackedUp(bookFile, backupFile, backupKeyFile);
    BackupBookResult.Rejected backupRejected = new BackupBookResult.Rejected(rejection);
    RestoreBookResult.Restored restored =
        new RestoreBookResult.Restored(bookFile, backupFile, backupKeyFile);
    RestoreBookResult.Rejected restoreRejected = new RestoreBookResult.Rejected(rejection);
    RecoverRekeyResult.Inspected inspected =
        new RecoverRekeyResult.Inspected(bookFile, List.of(rollbackArtifact));
    RecoverRekeyResult.Restored recovered =
        new RecoverRekeyResult.Restored(bookFile, rollbackArtifact);
    RecoverRekeyResult.Deleted deleted = new RecoverRekeyResult.Deleted(bookFile, rollbackArtifact);
    RecoverRekeyResult.Rejected recoverRejected = new RecoverRekeyResult.Rejected(rejection);
    BookMigrationPolicy migrationPolicy = BookMigrationPolicy.current(8);

    assertEquals(bookFile, backedUp.bookFilePath());
    assertEquals(backupFile, backedUp.backupFilePath());
    assertEquals(backupKeyFile, backedUp.backupBookKeyFilePath());
    assertEquals(rejection, backupRejected.rejection());
    assertEquals(bookFile, restored.bookFilePath());
    assertEquals(backupFile, restored.backupFilePath());
    assertEquals(backupKeyFile, restored.backupBookKeyFilePath());
    assertEquals(rejection, restoreRejected.rejection());
    assertEquals(bookFile, inspected.bookFilePath());
    assertIterableEquals(List.of(rollbackArtifact), inspected.rollbackArtifactPaths());
    assertEquals(bookFile, recovered.bookFilePath());
    assertEquals(rollbackArtifact, recovered.rollbackArtifactPath());
    assertEquals(bookFile, deleted.bookFilePath());
    assertEquals(rollbackArtifact, deleted.rollbackArtifactPath());
    assertEquals(rejection, recoverRejected.rejection());
    assertEquals(BookMigrationPolicyMode.HARD_BREAK_REJECT_OLDER_FORMATS, migrationPolicy.mode());
    assertEquals(8, migrationPolicy.supportedBookFormatVersion());
    assertEquals(List.of("hard-break-reject-older-formats"), BookMigrationPolicyMode.wireValues());
    assertEquals(
        BookMigrationPolicyMode.HARD_BREAK_REJECT_OLDER_FORMATS,
        BookMigrationPolicyMode.fromWireValue("hard-break-reject-older-formats"));
    assertEquals(List.of("inspect", "restore", "delete"), RekeyRecoveryAction.wireValues());
    assertEquals(RekeyRecoveryAction.RESTORE, RekeyRecoveryAction.fromWireValue("restore"));

    assertThrows(
        NullPointerException.class,
        () -> new BackupBookResult.BackedUp(nullOf(), backupFile, backupKeyFile));
    assertThrows(
        NullPointerException.class,
        () -> new BackupBookResult.BackedUp(bookFile, nullOf(), backupKeyFile));
    assertThrows(
        NullPointerException.class,
        () -> new BackupBookResult.BackedUp(bookFile, backupFile, nullOf()));
    assertThrows(NullPointerException.class, () -> new BackupBookResult.Rejected(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new RestoreBookResult.Restored(nullOf(), backupFile, backupKeyFile));
    assertThrows(
        NullPointerException.class,
        () -> new RestoreBookResult.Restored(bookFile, nullOf(), backupKeyFile));
    assertThrows(
        NullPointerException.class,
        () -> new RestoreBookResult.Restored(bookFile, backupFile, nullOf()));
    assertThrows(NullPointerException.class, () -> new RestoreBookResult.Rejected(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new RecoverRekeyResult.Inspected(nullOf(), List.of(rollbackArtifact)));
    assertThrows(
        NullPointerException.class, () -> new RecoverRekeyResult.Inspected(bookFile, nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new RecoverRekeyResult.Restored(nullOf(), rollbackArtifact));
    assertThrows(
        NullPointerException.class, () -> new RecoverRekeyResult.Restored(bookFile, nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new RecoverRekeyResult.Deleted(nullOf(), rollbackArtifact));
    assertThrows(
        NullPointerException.class, () -> new RecoverRekeyResult.Deleted(bookFile, nullOf()));
    assertThrows(NullPointerException.class, () -> new RecoverRekeyResult.Rejected(nullOf()));
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
    assertThrows(IllegalArgumentException.class, () -> RekeyRecoveryAction.fromWireValue("rotate"));
  }
}
