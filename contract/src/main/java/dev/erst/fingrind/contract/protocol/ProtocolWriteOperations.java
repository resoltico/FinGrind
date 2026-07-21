package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.stream.Stream;

/** Canonical write-operation registry for the public FinGrind protocol catalog. */
final class ProtocolWriteOperations {
  private ProtocolWriteOperations() {}

  static List<ProtocolOperation> operations() {
    return Stream.of(
            baseWriteOperations(),
            ProtocolTypedRecordEntryOperations.operations(),
            List.of(rawPostEntryOperation()))
        .flatMap(List::stream)
        .toList();
  }

  private static List<ProtocolOperation> baseWriteOperations() {
    return List.of(
        ProtocolOperationDefinitions.operation(
            OperationId.EXECUTE_PLAN,
            OperationCategory.WRITE,
            "Execute Plan",
            List.of(),
            List.of(
                ProtocolBookAccessOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.Request.FILE + " <path|->",
                ProtocolOptions.requiredAttestationCredentialSyntax(),
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT)),
                ProtocolOptions.optionalResultDetailSyntax()),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            "Execute an ordered AI-agent ledger plan inside a single atomic book transaction. Summary output is the default; request the full execution journal explicitly when needed.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s > plan.json"
                        .formatted(OperationId.PRINT_PLAN_TEMPLATE.wireName())),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s plan.json %s full"
                        .formatted(
                            OperationId.EXECUTE_PLAN.wireName(),
                            ProtocolBookAccessOptions.BOOK_FILE,
                            ProtocolBookAccessOptions.BOOK_KEY_FILE,
                            ProtocolOptions.Request.FILE,
                            ProtocolOptions.Discovery.RESULT_DETAIL)),
                ProtocolExampleStep.note(
                    "plan.json starts as a scaffold. Replace every replace-before-commit token before submitting it to a live book."))),
        ProtocolOperationDefinitions.operation(
            OperationId.PREFLIGHT_ENTRY,
            OperationCategory.WRITE,
            "Preflight Entry",
            List.of(),
            List.of(
                ProtocolBookAccessOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.Request.FILE + " <path|->",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            "Validate a posting request from the typed business-entry family or the raw direct-journal path without committing it.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s > request.json"
                        .formatted(
                            OperationId.PRINT_REQUEST_TEMPLATE.wireName(),
                            OperationId.PREFLIGHT_ENTRY.wireName())),
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s request.json"
                        .formatted(
                            OperationId.PREFLIGHT_ENTRY.wireName(),
                            ProtocolBookAccessOptions.BOOK_FILE,
                            ProtocolBookAccessOptions.BOOK_KEY_FILE,
                            ProtocolOptions.Request.FILE)),
                ProtocolExampleStep.note(
                    "request.json starts as a scaffold. Replace every replace-before-commit token before submitting it to a live book."))));
  }

  private static ProtocolOperation rawPostEntryOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.POST_ENTRY,
        OperationCategory.WRITE,
        "Post Entry",
        List.of(),
        List.of(
            ProtocolBookAccessOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.Request.FILE + " <path|->",
            ProtocolOptions.requiredAttestationCredentialSyntax(),
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
        "Commit a raw direct-journal posting request into the selected SQLite book. Prefer the record-* commands when a typed business-entry command matches the operator's intent; raw direct-journal requests do not admit inventory accounts.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s > request.json"
                    .formatted(
                        OperationId.PRINT_REQUEST_TEMPLATE.wireName(),
                        OperationId.POST_ENTRY.wireName())),
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s request.json"
                    .formatted(
                        OperationId.POST_ENTRY.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolBookAccessOptions.BOOK_KEY_FILE,
                        ProtocolOptions.Request.FILE)),
            ProtocolExampleStep.note(
                "request.json starts as a scaffold. Replace every replace-before-commit token before submitting it to a live book.")));
  }
}
