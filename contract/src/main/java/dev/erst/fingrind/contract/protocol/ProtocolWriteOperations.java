package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical write-operation registry for the public FinGrind protocol catalog. */
final class ProtocolWriteOperations {
  private ProtocolWriteOperations() {}

  static List<ProtocolOperation> operations() {
    return List.of(
        ProtocolOperationSupport.operation(
            OperationId.EXECUTE_PLAN,
            OperationCategory.WRITE,
            "Execute Plan",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.REQUEST_FILE + " <path|->"),
            ExecutionMode.JSON_ENVELOPE,
            "Execute one ordered AI-agent ledger plan inside a single atomic book transaction.",
            List.of(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s plan.json"
                    .formatted(
                        OperationId.EXECUTE_PLAN.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.REQUEST_FILE))),
        ProtocolOperationSupport.operation(
            OperationId.PREFLIGHT_ENTRY,
            OperationCategory.WRITE,
            "Preflight Entry",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.REQUEST_FILE + " <path|->"),
            ExecutionMode.JSON_ENVELOPE,
            "Validate one posting request without committing it.",
            List.of(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s request.json"
                    .formatted(
                        OperationId.PREFLIGHT_ENTRY.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.REQUEST_FILE))),
        ProtocolOperationSupport.operation(
            OperationId.POST_ENTRY,
            OperationCategory.WRITE,
            "Post Entry",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.REQUEST_FILE + " <path|->"),
            ExecutionMode.JSON_ENVELOPE,
            "Commit one posting request into the selected SQLite book.",
            List.of(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s request.json"
                    .formatted(
                        OperationId.POST_ENTRY.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.REQUEST_FILE))));
  }
}
