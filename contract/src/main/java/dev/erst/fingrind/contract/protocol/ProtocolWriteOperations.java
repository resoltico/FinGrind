package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical write-operation registry for the public FinGrind protocol catalog. */
final class ProtocolWriteOperations {
  private ProtocolWriteOperations() {}

  static List<ProtocolOperation> operations() {
    return List.of(
        ProtocolOperationDefinitions.operation(
            OperationId.EXECUTE_PLAN,
            OperationCategory.WRITE,
            "Execute Plan",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.REQUEST_FILE + " <path|->",
                ProtocolOptions.optionalResultDetailSyntax()),
            ExecutionMode.JSON_ENVELOPE,
            "Execute one ordered AI-agent ledger plan inside a single atomic book transaction. Summary output is the default; request the full execution journal explicitly when needed.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s > plan.json"
                        .formatted(OperationId.PRINT_PLAN_TEMPLATE.wireName())),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s plan.json %s full"
                        .formatted(
                            OperationId.EXECUTE_PLAN.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.REQUEST_FILE,
                            ProtocolOptions.RESULT_DETAIL)),
                ProtocolExampleStep.note(
                    "Replace scaffold placeholders such as effectiveDate and every nested replace-before-commit-* provenance value in plan.json before execution."))),
        ProtocolOperationDefinitions.operation(
            OperationId.PREFLIGHT_ENTRY,
            OperationCategory.WRITE,
            "Preflight Entry",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.REQUEST_FILE + " <path|->",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.HUMAN))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
            "Validate one posting request without committing it.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s > request.json"
                        .formatted(OperationId.PRINT_REQUEST_TEMPLATE.wireName())),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s request.json"
                        .formatted(
                            OperationId.PREFLIGHT_ENTRY.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.REQUEST_FILE)),
                ProtocolExampleStep.note(
                    "Replace scaffold placeholders such as effectiveDate and every replace-before-commit-* provenance value in request.json before submission."))),
        ProtocolOperationDefinitions.operation(
            OperationId.POST_ENTRY,
            OperationCategory.WRITE,
            "Post Entry",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.REQUEST_FILE + " <path|->",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.HUMAN))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
            "Commit one posting request into the selected SQLite book.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s > request.json"
                        .formatted(OperationId.PRINT_REQUEST_TEMPLATE.wireName())),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s request.json"
                        .formatted(
                            OperationId.POST_ENTRY.wireName(),
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.REQUEST_FILE)),
                ProtocolExampleStep.note(
                    "Replace scaffold placeholders such as effectiveDate and every replace-before-commit-* provenance value in request.json before submission."))));
  }
}
