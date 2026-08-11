package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical protected-book maintenance operations for the public FinGrind protocol catalog. */
final class ProtocolBookMaintenanceOperations {
  private static final String PAIR_PUBLICATION_COMPLETION_NOTE =
      "Successful backup-book, restore-book, and rekey-book responses include "
          + "pairPublicationCompletion: published when this invocation durably publishes the "
          + "pair, recovered when it verifies the same completed publication transaction, or already-published "
          + "when an exact backup acknowledgement retry verifies an existing complete pair "
          + "without publishing again. Published and recovered results expose only final paths and "
          + "ID-only publication-transaction evidence; they never expose a private stage path.";
  private static final String PAIR_PUBLICATION_TRANSACTION_RECOVERY_NOTE =
      "If FinGrind reports publication-transaction-incomplete, preserve its reported final candidate "
          + "and rerun only the exact same operation with its admitted recovery inputs so FinGrind can "
          + "verify the transaction. Do not rename, overwrite, delete, recreate, or manually clean "
          + "either final member; do not start a fresh pair. Legacy sidecar evidence is blocked rather "
          + "than recovered: preserve it and investigate independently.";
  private static final String CANONICAL_MAINTENANCE_PATH_NOTE =
      "FinGrind scans every selected maintenance path from its lexical root through its final "
          + "name without following links before canonicalization. Any symbolic-link or non-directory "
          + "component, including a direct-parent alias, is refused. A lifecycle source must already "
          + "be a regular non-symlink file beneath an existing real private owner-only parent before "
          + "FinGrind prepares any output. Existing parents are validation-only; only an eligible missing "
          + "final-target parent is preflighted and atomically created with POSIX 0700 or fails closed. "
          + "FinGrind never repairs a selected parent directory. The complete selected file-backed "
          + "source set, including every selected key file, must resolve to distinct physical artifacts; "
          + "a later duplicate source is rejected as source-artifact-identity-duplicated before "
          + "destination admission. Each selected source must remain the admitted physical artifact "
          + "through source locking; a changed source is rejected as source-artifact-identity-changed "
          + "before destination admission.";

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
                ProtocolOptionSyntax.Presentation.optionalOutputSyntax(
                    List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(ProtocolArtifactOutput.generatedBookKeyFile()),
            "Create a new owner-only UTF-8 book key file with a generated high-entropy passphrase.",
            List.of(
                ProtocolExampleStep.note(
                    "Create the parent directory with owner-only access before FinGrind publishes the new key file."),
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
                ProtocolOptionSyntax.BookAccess.currentPassphraseSourceSyntax(),
                ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE + " <path>",
                ProtocolOptionSyntax.Attestation.requiredCredentialSyntax(),
                ProtocolOptionSyntax.Presentation.optionalOutputSyntax(
                    List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(ProtocolArtifactOutput.newBookKeyFile()),
            "Re-encrypt an existing book under a newly generated, absent-target key file.",
            List.of(
                ProtocolExampleStep.note(CANONICAL_MAINTENANCE_PATH_NOTE),
                ProtocolExampleStep.note(PAIR_PUBLICATION_COMPLETION_NOTE),
                ProtocolExampleStep.note(PAIR_PUBLICATION_TRANSACTION_RECOVERY_NOTE),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./secrets/acme-replacement.book-key %s file-pkcs8 %s 123e4567-e89b-12d3-a456-426614174000 %s ./secrets/operator.fgatk %s ./secrets/operator.passphrase"
                        .formatted(
                            OperationId.REKEY_BOOK.wireName(),
                            ProtocolBookAccessOptions.BOOK_FILE,
                            ProtocolBookAccessOptions.BOOK_KEY_FILE,
                            ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE,
                            ProtocolOptions.Attestation.CUSTODIAN,
                            ProtocolOptions.Attestation.PRINCIPAL_ID,
                            ProtocolOptions.Attestation.KEY_FILE,
                            ProtocolOptions.Attestation.PASSPHRASE_FILE)),
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
                ProtocolOptionSyntax.BookAccess.currentPassphraseSourceSyntax(),
                ProtocolBookAccessOptions.BACKUP_FILE + " <path>",
                ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE + " <path>",
                ProtocolBookAccessOptions.BACKUP_ID + " <uuid>",
                ProtocolOptionSyntax.Attestation.requiredCredentialSyntax(),
                ProtocolOptionSyntax.Presentation.optionalOutputSyntax(
                    List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(ProtocolArtifactOutput.backupFile(), ProtocolArtifactOutput.newBackupKeyFile()),
            "Export a manifest-attested encrypted-book backup artifact without overwriting any existing destination.",
            List.of(
                ProtocolExampleStep.note(CANONICAL_MAINTENANCE_PATH_NOTE),
                ProtocolExampleStep.note(PAIR_PUBLICATION_COMPLETION_NOTE),
                ProtocolExampleStep.note(PAIR_PUBLICATION_TRANSACTION_RECOVERY_NOTE),
                ProtocolExampleStep.note(
                    "backup-book refuses to run when the live book has SQLite sidecars beside it."),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./backups/acme-2026-05-18.fgat %s ./backups/acme-2026-05-18.book-key %s 86ba4e4e-e08d-45e5-9c42-631d0121d6ef %s file-pkcs8 %s 123e4567-e89b-12d3-a456-426614174000 %s ./secrets/operator.fgatk %s ./secrets/operator.passphrase"
                        .formatted(
                            OperationId.BACKUP_BOOK.wireName(),
                            ProtocolBookAccessOptions.BOOK_FILE,
                            ProtocolBookAccessOptions.BOOK_KEY_FILE,
                            ProtocolBookAccessOptions.BACKUP_FILE,
                            ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE,
                            ProtocolBookAccessOptions.BACKUP_ID,
                            ProtocolOptions.Attestation.CUSTODIAN,
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
                ProtocolOptionSyntax.Attestation.requiredCredentialSyntax(),
                ProtocolOptionSyntax.Presentation.optionalOutputSyntax(
                    List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(ProtocolArtifactOutput.bookFile(), ProtocolArtifactOutput.newBookKeyFile()),
            "Restore a manifest-attested backup artifact to a missing destination as a signed derived continuation.",
            List.of(
                ProtocolExampleStep.note(CANONICAL_MAINTENANCE_PATH_NOTE),
                ProtocolExampleStep.note(PAIR_PUBLICATION_COMPLETION_NOTE),
                ProtocolExampleStep.note(PAIR_PUBLICATION_TRANSACTION_RECOVERY_NOTE),
                ProtocolExampleStep.note(
                    "restore-book verifies the internal chain and BACKUP manifest with the supplied backup key file; an existing destination always refuses publication."),
                ProtocolExampleStep.note(
                    "restore-book re-encrypts the restored live book under a new destination book key file. After restore completes, reopen the live book with that new --book-key-file instead of the backup key file."),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme-restored.book-key %s ./backups/acme-2026-05-18.fgat %s ./backups/acme-2026-05-18.book-key %s file-pkcs8 %s 123e4567-e89b-12d3-a456-426614174000 %s ./secrets/operator.fgatk %s ./secrets/operator.passphrase"
                        .formatted(
                            OperationId.RESTORE_BOOK.wireName(),
                            ProtocolBookAccessOptions.BOOK_FILE,
                            ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE,
                            ProtocolBookAccessOptions.BACKUP_FILE,
                            ProtocolBookAccessOptions.BACKUP_KEY_FILE,
                            ProtocolOptions.Attestation.CUSTODIAN,
                            ProtocolOptions.Attestation.PRINCIPAL_ID,
                            ProtocolOptions.Attestation.KEY_FILE,
                            ProtocolOptions.Attestation.PASSPHRASE_FILE)))));
  }
}
