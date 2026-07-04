package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.stream.Stream;

/** Canonical write-operation registry for the public FinGrind protocol catalog. */
final class ProtocolWriteOperations {
  private ProtocolWriteOperations() {}

  static List<ProtocolOperation> operations() {
    return Stream.of(
            baseWriteOperations(), typedRecordEntryOperations(), List.of(rawPostEntryOperation()))
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
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.REQUEST_FILE + " <path|->",
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
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.REQUEST_FILE,
                            ProtocolOptions.RESULT_DETAIL)),
                ProtocolExampleStep.note(
                    "plan.json starts as a scaffold. Replace every replace-before-commit token before submitting it to a live book."))),
        ProtocolOperationDefinitions.operation(
            OperationId.PREFLIGHT_ENTRY,
            OperationCategory.WRITE,
            "Preflight Entry",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.REQUEST_FILE + " <path|->",
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
                            ProtocolOptions.BOOK_FILE,
                            ProtocolOptions.BOOK_KEY_FILE,
                            ProtocolOptions.REQUEST_FILE)),
                ProtocolExampleStep.note(
                    "request.json starts as a scaffold. Replace every replace-before-commit token before submitting it to a live book."))));
  }

  private static List<ProtocolOperation> typedRecordEntryOperations() {
    return List.of(
        recordEntryOperation(
            OperationId.RECORD_SALE_SETTLED,
            "Record Settled Sale",
            "Commit a settled sale entry into the selected SQLite book.",
            "Settled-sale request scaffolds publish the cash, revenue, amount, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_SALE_ON_CREDIT,
            "Record Sale On Credit",
            "Commit a sale-on-credit entry into the selected SQLite book.",
            "Sale-on-credit request scaffolds publish the receivable, revenue, amount, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_PURCHASE_SETTLED,
            "Record Settled Purchase",
            "Commit a settled inventory purchase entry into the selected trading-template SQLite book.",
            "Settled-purchase request scaffolds publish the trading-template inventory, cash, amount, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_PURCHASE_ON_CREDIT,
            "Record Purchase On Credit",
            "Commit a purchase-on-credit inventory entry into the selected trading-template SQLite book.",
            "Purchase-on-credit request scaffolds publish the trading-template inventory, payable, amount, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_EXPENSE_SETTLED,
            "Record Settled Expense",
            "Commit a settled expense entry into the selected SQLite book.",
            "Settled-expense request scaffolds publish the expense, cash, amount, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_EXPENSE_ON_CREDIT,
            "Record Expense On Credit",
            "Commit an expense-on-credit entry into the selected SQLite book.",
            "Expense-on-credit request scaffolds publish the expense, payable, amount, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_RECEIPT,
            "Record Receipt",
            "Commit a trade-receivable settlement entry into the selected SQLite book.",
            "Receipt request scaffolds publish the cash, receivable, amount, optional settlementAdjunct, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_PAYMENT,
            "Record Payment",
            "Commit a trade-payable settlement entry into the selected SQLite book.",
            "Payment request scaffolds publish the payable, cash, amount, optional settlementAdjunct, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_OWNER_CONTRIBUTION,
            "Record Owner Contribution",
            "Commit an owner-contribution entry into the selected SQLite book.",
            "Owner-contribution request scaffolds publish the contribution-first request language with cash, equity, amount, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_OWNER_WITHDRAWAL,
            "Record Owner Withdrawal",
            "Commit an owner-withdrawal entry into the selected SQLite book.",
            "Owner-withdrawal request scaffolds publish the withdrawal-first request language with equity, cash, amount, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_OPENING_POSITION,
            "Record Opening Position",
            "Commit an opening-position entry into the selected SQLite book.",
            "Opening-position request scaffolds publish the opening-only request language with openingBalances, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_REVERSAL,
            "Record Reversal",
            "Commit a reversal entry into the selected SQLite book.",
            "Reversal request scaffolds publish the reversal-first request language with reversal target facts, evidence, and provenance fields."));
  }

  private static ProtocolOperation rawPostEntryOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.POST_ENTRY,
        OperationCategory.WRITE,
        "Post Entry",
        List.of(),
        List.of(
            ProtocolOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.REQUEST_FILE + " <path|->",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
        "Commit a raw direct-journal posting request into the selected SQLite book. Prefer the record-* commands when a typed business-entry command matches the operator's intent.",
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
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.REQUEST_FILE)),
            ProtocolExampleStep.note(
                "request.json starts as a scaffold. Replace every replace-before-commit token before submitting it to a live book.")));
  }

  private static ProtocolOperation recordEntryOperation(
      OperationId operationId, String title, String summary, String scaffoldNote) {
    return ProtocolOperationDefinitions.operation(
        operationId,
        OperationCategory.WRITE,
        title,
        List.of(),
        List.of(
            ProtocolOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.REQUEST_FILE + " <path|->",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
        summary,
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s > request.json"
                    .formatted(
                        OperationId.PRINT_REQUEST_TEMPLATE.wireName(), operationId.wireName())),
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s request.json"
                    .formatted(
                        operationId.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.REQUEST_FILE)),
            ProtocolExampleStep.note("request.json starts as a scaffold. " + scaffoldNote)));
  }
}
