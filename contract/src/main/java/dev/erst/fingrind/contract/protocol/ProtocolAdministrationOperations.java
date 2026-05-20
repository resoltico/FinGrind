package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical administration-operation registry for the public FinGrind protocol catalog. */
final class ProtocolAdministrationOperations {
  private ProtocolAdministrationOperations() {}

  static List<ProtocolOperation> operations() {
    return List.of(
        ProtocolOperationDefinitions.operation(
            OperationId.GENERATE_BOOK_KEY_FILE,
            OperationCategory.ADMINISTRATION,
            "Generate Book Key File",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_KEY_FILE + " <path>",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.HUMAN))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
            "Create one new owner-only UTF-8 book key file with a generated high-entropy passphrase.",
            List.of(
                ProtocolExampleStep.note(
                    "Choose one missing private parent directory so FinGrind can create it securely, or tighten one existing parent directory to owner-only permissions before rerunning the command."),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./secrets/acme.book-key"
                        .formatted(
                            OperationId.GENERATE_BOOK_KEY_FILE.wireName(),
                            ProtocolOptions.BOOK_KEY_FILE)))),
        ProtocolOperationDefinitions.operation(
            OperationId.OPEN_BOOK,
            OperationCategory.ADMINISTRATION,
            "Open Book",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.ENTITY_NAME + " <text>",
                ProtocolOptions.ENTITY_FORM + " <entity-form>",
                ProtocolOptions.OWNER_MODEL + " <owner-model>",
                ProtocolOptions.REPORTING_OBLIGATION_STATUS + " <reporting-obligation-status>",
                ProtocolOptions.BUSINESS_ACTIVITY_TAG + " <business-activity-tag> ...",
                ProtocolOptions.FUNCTIONAL_CURRENCY + " <currency-code>",
                ProtocolOptions.FISCAL_YEAR_START + " <MM-DD>",
                ProtocolOptions.ACCOUNTING_BASIS + " <accounting-basis>",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.HUMAN))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
            "Initialize a new book file with the canonical schema.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s \"Acme Studio\" %s FREELANCER %s SOLE_OWNER %s INTERNAL_MANAGEMENT_ONLY %s translation-services %s EUR %s 01-01 %s ACCRUAL"
                        .formatted(
                            OperationId.OPEN_BOOK.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.ENTITY_NAME,
                            ProtocolOptions.ENTITY_FORM,
                            ProtocolOptions.OWNER_MODEL,
                            ProtocolOptions.REPORTING_OBLIGATION_STATUS,
                            ProtocolOptions.BUSINESS_ACTIVITY_TAG,
                            ProtocolOptions.FUNCTIONAL_CURRENCY,
                            ProtocolOptions.FISCAL_YEAR_START,
                            ProtocolOptions.ACCOUNTING_BASIS)),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s \"Acme Studio\" %s FREELANCER %s SOLE_OWNER %s INTERNAL_MANAGEMENT_ONLY %s translation-services %s EUR %s 01-01 %s ACCRUAL %s"
                        .formatted(
                            OperationId.OPEN_BOOK.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.ENTITY_NAME,
                            ProtocolOptions.ENTITY_FORM,
                            ProtocolOptions.OWNER_MODEL,
                            ProtocolOptions.REPORTING_OBLIGATION_STATUS,
                            ProtocolOptions.BUSINESS_ACTIVITY_TAG,
                            ProtocolOptions.FUNCTIONAL_CURRENCY,
                            ProtocolOptions.FISCAL_YEAR_START,
                            ProtocolOptions.ACCOUNTING_BASIS,
                            ProtocolOptions.BOOK_PASSPHRASE_PROMPT)),
                ProtocolExampleStep.command(
                    "cat ./secrets/acme.book-key | fingrind %s %s ./books/acme.sqlite %s \"Acme Studio\" %s FREELANCER %s SOLE_OWNER %s INTERNAL_MANAGEMENT_ONLY %s translation-services %s EUR %s 01-01 %s ACCRUAL %s"
                        .formatted(
                            OperationId.OPEN_BOOK.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.ENTITY_NAME,
                            ProtocolOptions.ENTITY_FORM,
                            ProtocolOptions.OWNER_MODEL,
                            ProtocolOptions.REPORTING_OBLIGATION_STATUS,
                            ProtocolOptions.BUSINESS_ACTIVITY_TAG,
                            ProtocolOptions.FUNCTIONAL_CURRENCY,
                            ProtocolOptions.FISCAL_YEAR_START,
                            ProtocolOptions.ACCOUNTING_BASIS,
                            ProtocolOptions.BOOK_PASSPHRASE_STDIN)))),
        ProtocolOperationDefinitions.operation(
            OperationId.REKEY_BOOK,
            OperationCategory.ADMINISTRATION,
            "Rekey Book",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.replacementPassphraseSourceSyntax(),
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.HUMAN))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
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
                            ProtocolOptions.REPLACEMENT_BOOK_KEY_FILE)),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s"
                        .formatted(
                            OperationId.REKEY_BOOK.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.REPLACEMENT_BOOK_PASSPHRASE_PROMPT)))),
        ProtocolOperationDefinitions.operation(
            OperationId.BACKUP_BOOK,
            OperationCategory.ADMINISTRATION,
            "Backup Book",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.BACKUP_FILE + " <path>",
                ProtocolOptions.BACKUP_BOOK_KEY_FILE + " <path>",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.HUMAN))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
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
                            ProtocolOptions.BACKUP_FILE,
                            ProtocolOptions.BACKUP_BOOK_KEY_FILE)))),
        ProtocolOperationDefinitions.operation(
            OperationId.RESTORE_BOOK,
            OperationCategory.ADMINISTRATION,
            "Restore Book",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.BACKUP_FILE + " <path>",
                ProtocolOptions.BACKUP_BOOK_KEY_FILE + " <path>",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.HUMAN))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
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
                            ProtocolOptions.BACKUP_FILE,
                            ProtocolOptions.BACKUP_BOOK_KEY_FILE)))),
        ProtocolOperationDefinitions.operation(
            OperationId.INSPECT_REKEY_ROLLBACK,
            OperationCategory.ADMINISTRATION,
            "Inspect Rekey Rollback",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.HUMAN))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
            "Inspect stale sibling rekey rollback artifacts for the selected book path.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite"
                        .formatted(
                            OperationId.INSPECT_REKEY_ROLLBACK.wireName(),
                            ProtocolOptions.BOOK_FILE)))),
        ProtocolOperationDefinitions.operation(
            OperationId.DELETE_REKEY_ROLLBACK,
            OperationCategory.ADMINISTRATION,
            "Delete Rekey Rollback",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                "[%s <path>]".formatted(ProtocolOptions.ROLLBACK_FILE),
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.HUMAN))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
            "Delete one selected stale sibling rekey rollback artifact.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./books/acme.sqlite.rekey-rollback-1234.sqlite %s ./secrets/acme.book-key"
                        .formatted(
                            OperationId.DELETE_REKEY_ROLLBACK.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.ROLLBACK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE)))),
        ProtocolOperationDefinitions.operation(
            OperationId.RESTORE_REKEY_ROLLBACK,
            OperationCategory.ADMINISTRATION,
            "Restore Rekey Rollback",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                "[%s <path>]".formatted(ProtocolOptions.ROLLBACK_FILE),
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.HUMAN))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
            "Restore one selected stale sibling rekey rollback artifact onto the live book path.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./books/acme.sqlite.rekey-rollback-1234.sqlite %s ./secrets/acme.book-key"
                        .formatted(
                            OperationId.RESTORE_REKEY_ROLLBACK.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.ROLLBACK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE)))),
        ProtocolOperationDefinitions.operation(
            OperationId.DECLARE_ACCOUNT,
            OperationCategory.ADMINISTRATION,
            "Declare Account",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.REQUEST_FILE + " <path|->",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.HUMAN))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
            "Declare or reactivate one account in the selected book.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./declare-account-cash.json"
                        .formatted(
                            OperationId.DECLARE_ACCOUNT.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.REQUEST_FILE)),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./declare-account-revenue.json"
                        .formatted(
                            OperationId.DECLARE_ACCOUNT.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.REQUEST_FILE)))),
        ProtocolOperationDefinitions.operation(
            OperationId.CLOSE_PERIOD,
            OperationCategory.ADMINISTRATION,
            "Close Period",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.EFFECTIVE_DATE_FROM + " <YYYY-MM-DD>",
                ProtocolOptions.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.HUMAN))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
            "Close one contiguous reporting period into one policy-selected closing equity account.",
            List.of(
                ProtocolExampleStep.note(
                    "Built-in closing-equity mapping is entity-form specific: FREELANCER and SOLE_PROPRIETORSHIP require OWNER_CAPITAL; COMPANY and BRANCH require RETAINED_EARNINGS; PARTNERSHIP requires PARTNER_CURRENT; NONPROFIT requires ACCUMULATED_SURPLUS; OTHER requires OTHER_EQUITY."),
                ProtocolExampleStep.note(
                    "Declare exactly one active equity account that satisfies the active closing-equity policy for the selected entity form. Zero matching active accounts or multiple matching active accounts produce deterministic rejections."),
                ProtocolExampleStep.note(
                    "The first close may begin before the earliest posting date. After one close is recorded, later closes must start on the day after the closed-through horizon and remain inside one fiscal year."),
                ProtocolExampleStep.command(
                    "fingrind close-period --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --effective-date-from 2026-04-01 --effective-date-to 2026-04-30"))));
  }
}
