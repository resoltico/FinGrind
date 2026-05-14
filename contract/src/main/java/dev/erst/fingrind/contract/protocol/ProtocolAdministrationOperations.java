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
                ProtocolOptions.FUNCTIONAL_CURRENCY + " <currency-code>",
                ProtocolOptions.FISCAL_YEAR_START + " <MM-DD>",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.HUMAN))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
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
                ProtocolOptions.RETAINED_EARNINGS_ACCOUNT + " <account-code>",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.HUMAN))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
            "Close one contiguous reporting period into one selected retained-earnings account.",
            List.of(
                ProtocolExampleStep.note(
                    "Declare at least one active retained-earnings account before running close-period. Use accountType EQUITY with accountRole RETAINED_EARNINGS, then choose the target with --retained-earnings-account."),
                ProtocolExampleStep.note(
                    "The first close may begin before the earliest posting date. After one close is recorded, later closes must start on the day after the closed-through horizon and remain inside one fiscal year."),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 2026-04-01 %s 2026-04-30 %s 3200"
                        .formatted(
                            OperationId.CLOSE_PERIOD.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.EFFECTIVE_DATE_FROM,
                            ProtocolOptions.EFFECTIVE_DATE_TO,
                            ProtocolOptions.RETAINED_EARNINGS_ACCOUNT)))));
  }
}
