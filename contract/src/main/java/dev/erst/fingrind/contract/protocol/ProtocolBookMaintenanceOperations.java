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
                ProtocolBookAccessOptions.BACKUP_ID + " <uuid>",
                ProtocolOptions.Attestation.PRINCIPAL_ID + " <uuid>",
                ProtocolOptions.Attestation.KEY_FILE + " <path>",
                ProtocolOptions.Attestation.PASSPHRASE_FILE + " <path>",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(ProtocolArtifactOutput.backupFile(), ProtocolArtifactOutput.newBackupKeyFile()),
            "Export one manifest-attested encrypted-book backup artifact without overwriting any existing destination.",
            List.of(
                ProtocolExampleStep.note(
                    "backup-book refuses to run when the live book has SQLite sidecars beside it."),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./backups/acme-2026-05-18.fgat %s ./backups/acme-2026-05-18.book-key %s 86ba4e4e-e08d-45e5-9c42-631d0121d6ef %s 123e4567-e89b-12d3-a456-426614174000 %s ./secrets/operator.fgatk %s ./secrets/operator.passphrase"
                        .formatted(
                            OperationId.BACKUP_BOOK.wireName(),
                            ProtocolBookAccessOptions.BOOK_FILE,
                            ProtocolBookAccessOptions.BOOK_KEY_FILE,
                            ProtocolBookAccessOptions.BACKUP_FILE,
                            ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE,
                            ProtocolBookAccessOptions.BACKUP_ID,
                            ProtocolOptions.Attestation.PRINCIPAL_ID,
                            ProtocolOptions.Attestation.KEY_FILE,
                            ProtocolOptions.Attestation.PASSPHRASE_FILE)))));
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
                ProtocolOptions.Attestation.PRINCIPAL_ID + " <uuid>",
                ProtocolOptions.Attestation.KEY_FILE + " <path>",
                ProtocolOptions.Attestation.PASSPHRASE_FILE + " <path>",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(ProtocolArtifactOutput.bookFile(), ProtocolArtifactOutput.newBookKeyFile()),
            "Restore a manifest-attested backup artifact to a missing destination as a signed derived continuation.",
            List.of(
                ProtocolExampleStep.note(
                    "restore-book verifies the internal chain and BACKUP manifest with the supplied backup key file; an existing destination always refuses publication."),
                ProtocolExampleStep.note(
                    "restore-book re-encrypts the restored live book under a new destination book key file. After restore completes, reopen the live book with that new --book-key-file instead of the backup key file."),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme-restored.book-key %s ./backups/acme-2026-05-18.fgat %s ./backups/acme-2026-05-18.book-key %s 123e4567-e89b-12d3-a456-426614174000 %s ./secrets/operator.fgatk %s ./secrets/operator.passphrase"
                        .formatted(
                            OperationId.RESTORE_BOOK.wireName(),
                            ProtocolBookAccessOptions.BOOK_FILE,
                            ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE,
                            ProtocolBookAccessOptions.BACKUP_FILE,
                            ProtocolBookAccessOptions.BACKUP_KEY_FILE,
                            ProtocolOptions.Attestation.PRINCIPAL_ID,
                            ProtocolOptions.Attestation.KEY_FILE,
                            ProtocolOptions.Attestation.PASSPHRASE_FILE)))));
  }
}
