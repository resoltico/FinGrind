package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical protected-book maintenance operations for the public FinGrind protocol catalog. */
final class ProtocolBookMaintenanceOperations {
  private ProtocolBookMaintenanceOperations() {}

  static ProtocolOperation generateBookKeyFileOperation() {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            OperationId.GENERATE_BOOK_KEY_FILE,
            OperationCategory.ADMINISTRATION,
            "Generate Book Key File",
            List.of(),
            List.of(
                ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE + " <path>",
                "[" + ProtocolOptions.BookDefinition.TIGHTEN_PARENTS + "]",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(ProtocolArtifactOutput.generatedBookKeyFile()),
            "Create a new owner-only UTF-8 book key file with a generated high-entropy passphrase.",
            List.of(
                ProtocolExampleStep.note(
                    "Choose a missing private parent directory so FinGrind can create it securely, or rerun with --tighten-parents to tighten the existing named parent directory first."),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./secrets/acme.book-key"
                        .formatted(
                            OperationId.GENERATE_BOOK_KEY_FILE.wireName(),
                            ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE)))));
  }

  static ProtocolOperation rekeyBookOperation() {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            OperationId.REKEY_BOOK,
            OperationCategory.ADMINISTRATION,
            "Rekey Book",
            List.of(),
            List.of(
                ProtocolBookAccessOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE + " <path>",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(ProtocolArtifactOutput.newBookKeyFile()),
            "Re-encrypt an existing book under a newly generated, absent-target key file.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./secrets/acme-replacement.book-key"
                        .formatted(
                            OperationId.REKEY_BOOK.wireName(),
                            ProtocolBookAccessOptions.BOOK_FILE,
                            ProtocolBookAccessOptions.BOOK_KEY_FILE,
                            ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE)),
                ProtocolExampleStep.note(
                    "The generated --new-book-key-file target must not already exist; rekey-book never accepts a replacement passphrase from a flag, standard input, or prompt."))));
  }

  static ProtocolOperation backupBookOperation() {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            OperationId.BACKUP_BOOK,
            OperationCategory.ADMINISTRATION,
            "Backup Book",
            List.of(),
            List.of(
                ProtocolBookAccessOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolBookAccessOptions.BACKUP_FILE + " <path>",
                ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE + " <path>",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(ProtocolArtifactOutput.backupFile(), ProtocolArtifactOutput.newBackupKeyFile()),
            "Export a closed encrypted-book backup pair without overwriting any existing destination.",
            List.of(
                ProtocolExampleStep.note(
                    "backup-book refuses to run when the live book has SQLite sidecars or stale rekey rollback artifacts beside it."),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./backups/acme-2026-05-18.sqlite %s ./backups/acme-2026-05-18.book-key"
                        .formatted(
                            OperationId.BACKUP_BOOK.wireName(),
                            ProtocolBookAccessOptions.BOOK_FILE,
                            ProtocolBookAccessOptions.BOOK_KEY_FILE,
                            ProtocolBookAccessOptions.BACKUP_FILE,
                            ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE)))));
  }

  static ProtocolOperation restoreBookOperation() {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            OperationId.RESTORE_BOOK,
            OperationCategory.ADMINISTRATION,
            "Restore Book",
            List.of(),
            List.of(
                ProtocolBookAccessOptions.BOOK_FILE + " <path>",
                ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE + " <path>",
                ProtocolBookAccessOptions.BACKUP_FILE + " <path>",
                ProtocolBookAccessOptions.BACKUP_KEY_FILE + " <path>",
                "[" + ProtocolBookAccessOptions.REPLACE_EXISTING_BOOK + "]",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(ProtocolArtifactOutput.bookFile(), ProtocolArtifactOutput.newBookKeyFile()),
            "Restore a verified encrypted-book backup pair under a newly generated destination key file.",
            List.of(
                ProtocolExampleStep.note(
                    "restore-book verifies the backup with the supplied backup key file before replacing an existing live book path; --replace-existing-book is required when that path already exists."),
                ProtocolExampleStep.note(
                    "restore-book re-encrypts the restored live book under a new destination book key file. After restore completes, reopen the live book with that new --book-key-file instead of the backup key file."),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme-restored.book-key %s ./backups/acme-2026-05-18.sqlite %s ./backups/acme-2026-05-18.book-key"
                        .formatted(
                            OperationId.RESTORE_BOOK.wireName(),
                            ProtocolBookAccessOptions.BOOK_FILE,
                            ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE,
                            ProtocolBookAccessOptions.BACKUP_FILE,
                            ProtocolBookAccessOptions.BACKUP_KEY_FILE)))));
  }

  static ProtocolOperation inspectRekeyRollbackOperation() {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            OperationId.INSPECT_REKEY_ROLLBACK,
            OperationCategory.ADMINISTRATION,
            "Inspect Rekey Rollback",
            List.of(),
            List.of(
                ProtocolBookAccessOptions.BOOK_FILE + " <path>",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(ProtocolArtifactOutput.discoveredRollbackBookFile()),
            "Inspect stale sibling rekey rollback artifact paths without opening the protected book; no passphrase source is required. Restoring or deleting a selected artifact requires the current book passphrase source.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite"
                        .formatted(
                            OperationId.INSPECT_REKEY_ROLLBACK.wireName(),
                            ProtocolBookAccessOptions.BOOK_FILE)))));
  }

  static ProtocolOperation deleteRekeyRollbackOperation() {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            OperationId.DELETE_REKEY_ROLLBACK,
            OperationCategory.ADMINISTRATION,
            "Delete Rekey Rollback",
            List.of(),
            List.of(
                ProtocolBookAccessOptions.BOOK_FILE + " <path>",
                "[%s <path>]".formatted(ProtocolBookAccessOptions.ROLLBACK_BOOK_FILE),
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(ProtocolArtifactOutput.rollbackBookFile()),
            "Delete a selected stale sibling rekey rollback artifact.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./books/acme.sqlite.rekey-rollback-1234.sqlite %s ./secrets/acme.book-key"
                        .formatted(
                            OperationId.DELETE_REKEY_ROLLBACK.wireName(),
                            ProtocolBookAccessOptions.BOOK_FILE,
                            ProtocolBookAccessOptions.ROLLBACK_BOOK_FILE,
                            ProtocolBookAccessOptions.BOOK_KEY_FILE)))));
  }

  static ProtocolOperation restoreRekeyRollbackOperation() {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            OperationId.RESTORE_REKEY_ROLLBACK,
            OperationCategory.ADMINISTRATION,
            "Restore Rekey Rollback",
            List.of(),
            List.of(
                ProtocolBookAccessOptions.BOOK_FILE + " <path>",
                "[%s <path>]".formatted(ProtocolBookAccessOptions.ROLLBACK_BOOK_FILE),
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(ProtocolArtifactOutput.rollbackBookFile()),
            "Restore a selected stale sibling rekey rollback artifact onto the live book path.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./books/acme.sqlite.rekey-rollback-1234.sqlite %s ./secrets/acme.book-key"
                        .formatted(
                            OperationId.RESTORE_REKEY_ROLLBACK.wireName(),
                            ProtocolBookAccessOptions.BOOK_FILE,
                            ProtocolBookAccessOptions.ROLLBACK_BOOK_FILE,
                            ProtocolBookAccessOptions.BOOK_KEY_FILE)))));
  }
}
