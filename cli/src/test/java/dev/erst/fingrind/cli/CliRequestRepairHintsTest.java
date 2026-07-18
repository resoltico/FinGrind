package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.contract.protocol.OperationId;
import org.junit.jupiter.api.Test;

/** Focused regression tests for request-repair hint formatting. */
class CliRequestRepairHintsTest {
  @Test
  void refineLedgerPlan_preservesDottedFieldPaths() {
    String hint =
        CliRequestRepairHints.refineLedgerPlan(
            "Expected one canonical YYYY-MM-DD local date for query.effectiveDateFrom.",
            "fallback");

    assertTrue(hint.contains("Replace query.effectiveDateFrom with one canonical date"), hint);
    assertTrue(
        hint.contains(CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE)), hint);
  }

  @Test
  void refine_rewritesDirectRequestFailuresBeforeScaffoldFallback() {
    String missingFieldHint =
        CliRequestRepairHints.refine(
            "Missing required field: entry.journalEntry",
            CliJsonRequestHints.postEntryRequestHint(OperationId.POST_ENTRY),
            null);
    String optionalStringHint =
        CliRequestRepairHints.refine(
            "Field must be a string when present: evidence.memo",
            CliJsonRequestHints.declareAccountRequestHint(),
            null);
    String stringHint =
        CliRequestRepairHints.refine(
            "Field must be a string: evidence.memo",
            CliJsonRequestHints.declareAccountRequestHint(),
            null);
    String integerHint =
        CliRequestRepairHints.refine(
            "Field must be an integer when present: evidence.sequence",
            CliJsonRequestHints.declareAccountRequestHint(),
            null);
    String lineHint =
        CliRequestRepairHints.refine(
            "Invalid request document.",
            CliJsonRequestHints.postEntryRequestHint(OperationId.PREFLIGHT_ENTRY),
            new CliErrorJsonModels.InvalidRequestDetails(
                java.util.List.of("entry.journalEntry.lines must contain at least one line")));

    assertTrue(missingFieldHint.contains("Add entry.journalEntry to the request document"));
    assertTrue(optionalStringHint.contains("Replace evidence.memo with one JSON string value"));
    assertTrue(stringHint.contains("Replace evidence.memo with one JSON string value"));
    assertTrue(integerHint.contains("Replace evidence.sequence with one JSON integer value"));
    assertTrue(lineHint.contains("Add at least one balanced journal line"));
    assertTrue(
        lineHint.contains(CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)));
    assertTrue(
        optionalStringHint.contains(
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + OperationId.DECLARE_ACCOUNT.wireName()),
        optionalStringHint);
    assertTrue(
        optionalStringHint.contains(
            CliInvocationText.commandExample(OperationId.HELP)
                + " "
                + OperationId.DECLARE_ACCOUNT.wireName()
                + " --output json --detail full"),
        optionalStringHint);
  }

  @Test
  void refineAndRefineLedgerPlan_fallBackWhenNoDirectRepairExists() {
    String requestHint = CliRequestRepairHints.refine("Opaque message", "fallback", null);
    String multiViolationHint =
        CliRequestRepairHints.refine(
            "Invalid request document.",
            "fallback-multi",
            new CliErrorJsonModels.InvalidRequestDetails(
                java.util.List.of(
                    "entry.journalEntry.lines must contain at least one line", "other")));
    String unrelatedViolationHint =
        CliRequestRepairHints.refine(
            "Invalid request document.",
            "fallback-unrelated",
            new CliErrorJsonModels.InvalidRequestDetails(java.util.List.of("other violation")));
    String ledgerHint = CliRequestRepairHints.refineLedgerPlan("Opaque message", "fallback-ledger");
    String missingFieldHint =
        CliRequestRepairHints.refineLedgerPlan(
            "Missing required field: query.effectiveDateFrom", "fallback-ledger");
    String noPeriodDateHint =
        CliRequestRepairHints.refineLedgerPlan(
            "Expected one canonical YYYY-MM-DD local date for query.effectiveDateFrom",
            "fallback-ledger");

    assertTrue(requestHint.contains("fallback"));
    assertTrue(multiViolationHint.contains("fallback-multi"));
    assertTrue(unrelatedViolationHint.contains("fallback-unrelated"));
    assertTrue(ledgerHint.contains("fallback-ledger"));
    assertTrue(
        missingFieldHint.contains("Add query.effectiveDateFrom to the ledger plan document"));
    assertTrue(
        noPeriodDateHint.contains("Replace query.effectiveDateFrom with one canonical date"));
  }
}
