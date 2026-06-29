package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical administration-operation registry for the public FinGrind protocol catalog. */
final class ProtocolAdministrationOperations {
  private ProtocolAdministrationOperations() {}

  static List<ProtocolOperation> operations() {
    return List.of(
        generateBookKeyFileOperation(),
        openBookOperation(),
        rekeyBookOperation(),
        backupBookOperation(),
        restoreBookOperation(),
        inspectRekeyRollbackOperation(),
        deleteRekeyRollbackOperation(),
        restoreRekeyRollbackOperation(),
        declareAccountOperation(),
        declareTaxRegistrationOperation(),
        ProtocolPeriodCloseOperations.interimResultSweepOperation(),
        ProtocolPeriodCloseOperations.fiscalYearCloseOperation());
  }

  private static ProtocolOperation generateBookKeyFileOperation() {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            OperationId.GENERATE_BOOK_KEY_FILE,
            OperationCategory.ADMINISTRATION,
            "Generate Book Key File",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_KEY_FILE + " <path>",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(ProtocolArtifactOutput.generatedBookKeyFile()),
            "Create one new owner-only UTF-8 book key file with a generated high-entropy passphrase.",
            List.of(
                ProtocolExampleStep.note(
                    "Choose one missing private parent directory so FinGrind can create it securely, or tighten one existing parent directory to owner-only permissions before rerunning the command."),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./secrets/acme.book-key"
                        .formatted(
                            OperationId.GENERATE_BOOK_KEY_FILE.wireName(),
                            ProtocolOptions.BOOK_KEY_FILE)))));
  }

  private static ProtocolOperation openBookOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.OPEN_BOOK,
        OperationCategory.ADMINISTRATION,
        "Open Book",
        List.of(),
        List.of(
            ProtocolOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.ENTITY_NAME + " <text>",
            ProtocolOptions.FUNCTIONAL_CURRENCY + " <currency-code>",
            ProtocolOptions.FISCAL_YEAR_START + " <MM-DD>",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
        "Initialize a new book file with the canonical schema.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s \"Acme Studio\" %s EUR %s 01-01"
                    .formatted(
                        OperationId.OPEN_BOOK.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.ENTITY_NAME,
                        ProtocolOptions.FUNCTIONAL_CURRENCY,
                        ProtocolOptions.FISCAL_YEAR_START)),
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s \"Acme Studio\" %s EUR %s 01-01 %s"
                    .formatted(
                        OperationId.OPEN_BOOK.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.ENTITY_NAME,
                        ProtocolOptions.FUNCTIONAL_CURRENCY,
                        ProtocolOptions.FISCAL_YEAR_START,
                        ProtocolOptions.BOOK_PASSPHRASE_PROMPT)),
            ProtocolExampleStep.command(
                "cat ./secrets/acme.book-key | fingrind %s %s ./books/acme.sqlite %s \"Acme Studio\" %s EUR %s 01-01 %s"
                    .formatted(
                        OperationId.OPEN_BOOK.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.ENTITY_NAME,
                        ProtocolOptions.FUNCTIONAL_CURRENCY,
                        ProtocolOptions.FISCAL_YEAR_START,
                        ProtocolOptions.BOOK_PASSPHRASE_STDIN))));
  }

  private static ProtocolOperation rekeyBookOperation() {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            OperationId.REKEY_BOOK,
            OperationCategory.ADMINISTRATION,
            "Rekey Book",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.newPassphraseSourceSyntax(),
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(ProtocolArtifactOutput.replacementBookKeyFile()),
            "Rotate the passphrase that protects one existing book.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s ./secrets/acme-replacement.book-key"
                        .formatted(
                            OperationId.GENERATE_BOOK_KEY_FILE.wireName(),
                            ProtocolOptions.BOOK_KEY_FILE)),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./secrets/acme-replacement.book-key"
                        .formatted(
                            OperationId.REKEY_BOOK.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.NEW_BOOK_KEY_FILE)),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s"
                        .formatted(
                            OperationId.REKEY_BOOK.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.NEW_BOOK_PASSPHRASE_PROMPT)))));
  }

  private static ProtocolOperation backupBookOperation() {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            OperationId.BACKUP_BOOK,
            OperationCategory.ADMINISTRATION,
            "Backup Book",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.BACKUP_BOOK_FILE_OUT + " <path>",
                ProtocolOptions.BACKUP_BOOK_KEY_FILE_OUT + " <path>",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(
                ProtocolArtifactOutput.backupBookFile(),
                ProtocolArtifactOutput.backupBookKeyFile()),
            "Export one closed encrypted-book backup pair without overwriting any existing destination.",
            List.of(
                ProtocolExampleStep.note(
                    "backup-book refuses to run when the live book has SQLite sidecars or stale rekey rollback artifacts beside it."),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./backups/acme-2026-05-18.sqlite %s ./backups/acme-2026-05-18.book-key"
                        .formatted(
                            OperationId.BACKUP_BOOK.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.BACKUP_BOOK_FILE_OUT,
                            ProtocolOptions.BACKUP_BOOK_KEY_FILE_OUT)))));
  }

  private static ProtocolOperation restoreBookOperation() {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            OperationId.RESTORE_BOOK,
            OperationCategory.ADMINISTRATION,
            "Restore Book",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.BACKUP_BOOK_FILE + " <path>",
                ProtocolOptions.BACKUP_BOOK_KEY_FILE + " <path>",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(
                ProtocolArtifactOutput.selectedBackupBookFile(),
                ProtocolArtifactOutput.selectedBackupBookKeyFile()),
            "Restore one verified encrypted-book backup pair onto the selected live book path.",
            List.of(
                ProtocolExampleStep.note(
                    "restore-book verifies the backup with the supplied backup key file before replacing the live book path."),
                ProtocolExampleStep.note(
                    "After restore completes, reopen the restored live book with that same backup key file because the restored encrypted pair keeps that secret."),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./backups/acme-2026-05-18.sqlite %s ./backups/acme-2026-05-18.book-key"
                        .formatted(
                            OperationId.RESTORE_BOOK.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BACKUP_BOOK_FILE,
                            ProtocolOptions.BACKUP_BOOK_KEY_FILE)))));
  }

  private static ProtocolOperation inspectRekeyRollbackOperation() {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            OperationId.INSPECT_REKEY_ROLLBACK,
            OperationCategory.ADMINISTRATION,
            "Inspect Rekey Rollback",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(ProtocolArtifactOutput.discoveredRollbackBookFile()),
            "Inspect stale sibling rekey rollback artifacts for the selected book path.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite"
                        .formatted(
                            OperationId.INSPECT_REKEY_ROLLBACK.wireName(),
                            ProtocolOptions.BOOK_FILE)))));
  }

  private static ProtocolOperation deleteRekeyRollbackOperation() {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            OperationId.DELETE_REKEY_ROLLBACK,
            OperationCategory.ADMINISTRATION,
            "Delete Rekey Rollback",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                "[%s <path>]".formatted(ProtocolOptions.ROLLBACK_BOOK_FILE),
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(ProtocolArtifactOutput.rollbackBookFile()),
            "Delete one selected stale sibling rekey rollback artifact.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./books/acme.sqlite.rekey-rollback-1234.sqlite %s ./secrets/acme.book-key"
                        .formatted(
                            OperationId.DELETE_REKEY_ROLLBACK.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.ROLLBACK_BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE)))));
  }

  private static ProtocolOperation restoreRekeyRollbackOperation() {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            OperationId.RESTORE_REKEY_ROLLBACK,
            OperationCategory.ADMINISTRATION,
            "Restore Rekey Rollback",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                "[%s <path>]".formatted(ProtocolOptions.ROLLBACK_BOOK_FILE),
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(ProtocolArtifactOutput.rollbackBookFile()),
            "Restore one selected stale sibling rekey rollback artifact onto the live book path.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./books/acme.sqlite.rekey-rollback-1234.sqlite %s ./secrets/acme.book-key"
                        .formatted(
                            OperationId.RESTORE_REKEY_ROLLBACK.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.ROLLBACK_BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE)))));
  }

  private static ProtocolOperation declareAccountOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.DECLARE_ACCOUNT,
        OperationCategory.ADMINISTRATION,
        "Declare Account",
        List.of(),
        List.of(
            ProtocolOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.REQUEST_FILE + " <path|->",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
        "Declare or reactivate one account in the selected book.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./declare-account-supplemental-cash-reserve.json"
                    .formatted(
                        OperationId.DECLARE_ACCOUNT.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.REQUEST_FILE)),
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./declare-account-supplemental-misc-revenue.json"
                    .formatted(
                        OperationId.DECLARE_ACCOUNT.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.REQUEST_FILE))));
  }

  private static ProtocolOperation declareTaxRegistrationOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.DECLARE_TAX_REGISTRATION,
        OperationCategory.ADMINISTRATION,
        "Declare Tax Registration",
        List.of(),
        List.of(
            ProtocolOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.REQUEST_FILE + " <path|->",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
        "Declare or update one owned tax registration in the selected book.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./declare-tax-registration-vat-lv.json"
                    .formatted(
                        OperationId.DECLARE_TAX_REGISTRATION.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.REQUEST_FILE))));
  }
}
