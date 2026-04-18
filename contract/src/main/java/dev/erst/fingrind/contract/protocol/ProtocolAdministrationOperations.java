package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical administration-operation registry for the public FinGrind protocol catalog. */
final class ProtocolAdministrationOperations {
  private ProtocolAdministrationOperations() {}

  static List<ProtocolOperation> operations() {
    return List.of(
        ProtocolOperationSupport.operation(
            OperationId.GENERATE_BOOK_KEY_FILE,
            OperationCategory.ADMINISTRATION,
            "Generate Book Key File",
            List.of(),
            List.of(ProtocolOptions.BOOK_KEY_FILE + " <path>"),
            ExecutionMode.JSON_ENVELOPE,
            "Create one new owner-only UTF-8 book key file with a generated high-entropy passphrase.",
            List.of(
                "fingrind %s %s ./secrets/acme.book-key"
                    .formatted(
                        OperationId.GENERATE_BOOK_KEY_FILE.wireName(),
                        ProtocolOptions.BOOK_KEY_FILE))),
        ProtocolOperationSupport.operation(
            OperationId.OPEN_BOOK,
            OperationCategory.ADMINISTRATION,
            "Open Book",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax()),
            ExecutionMode.JSON_ENVELOPE,
            "Initialize a new book file with the canonical schema.",
            List.of(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key"
                    .formatted(
                        OperationId.OPEN_BOOK.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE),
                "fingrind %s %s ./books/acme.sqlite %s"
                    .formatted(
                        OperationId.OPEN_BOOK.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_PASSPHRASE_PROMPT),
                "printf '%s\\n' 'acme-demo-passphrase' | fingrind %s %s ./books/acme.sqlite %s"
                    .formatted(
                        "%s",
                        OperationId.OPEN_BOOK.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_PASSPHRASE_STDIN))),
        ProtocolOperationSupport.operation(
            OperationId.REKEY_BOOK,
            OperationCategory.ADMINISTRATION,
            "Rekey Book",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.replacementPassphraseSourceSyntax()),
            ExecutionMode.JSON_ENVELOPE,
            "Rotate the passphrase that protects one existing book.",
            List.of(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s"
                    .formatted(
                        OperationId.REKEY_BOOK.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.NEW_BOOK_PASSPHRASE_PROMPT))),
        ProtocolOperationSupport.operation(
            OperationId.DECLARE_ACCOUNT,
            OperationCategory.ADMINISTRATION,
            "Declare Account",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.REQUEST_FILE + " <path|->"),
            ExecutionMode.JSON_ENVELOPE,
            "Declare or reactivate one account in the selected book.",
            List.of(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./docs/examples/declare-account-cash.json"
                    .formatted(
                        OperationId.DECLARE_ACCOUNT.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.REQUEST_FILE),
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./docs/examples/declare-account-revenue.json"
                    .formatted(
                        OperationId.DECLARE_ACCOUNT.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.REQUEST_FILE))));
  }
}
