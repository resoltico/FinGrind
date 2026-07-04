package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliAccountStateViolationPayload;
import dev.erst.fingrind.cli.json.CliEntrySemanticsViolationPayload;
import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStepStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for plain-language deterministic CLI failure rendering. */
class CliFailureOutputRendererTest {
  @Test
  void renderFailureText_rendersArgumentHintAndStructuredErrorDetails() {
    String invalidJson =
        CliFailureOutputRenderer.renderFailureText(
            new CliFailure(
                "invalid-json",
                "Malformed request.",
                "Fix the JSON syntax and retry.",
                "--request-file",
                new CliErrorJsonModels.InvalidJsonDetails("Unexpected token", 3, 9)));
    String invalidRequest =
        CliFailureOutputRenderer.renderDeterministicFailureText(
            new CliFailure(
                "invalid-request",
                "Request violates the schema.",
                "Correct the listed fields and rerun.",
                "--request-file",
                new CliErrorJsonModels.InvalidRequestDetails(
                    List.of("accountCode is required", "amount must be positive"))));
    String warning =
        CliFailureOutputRenderer.renderWarningText(
            new CliFailure("warning-code", "Heads up.", "Review the output.", "--output"));
    String info =
        CliFailureOutputRenderer.renderInfoText(
            new CliFailure("info-code", "FYI.", "Continue with the next step.", "--book-file"));

    assertTrue(invalidJson.contains("Error"));
    assertTrue(invalidJson.contains("Argument"));
    assertTrue(invalidJson.contains("--request-file"));
    assertTrue(invalidJson.contains("Hint"));
    assertTrue(invalidJson.contains("Unexpected token"));
    assertTrue(invalidJson.contains("line 3, column 9"));
    assertTrue(invalidRequest.contains("Rejected"));
    assertTrue(invalidRequest.contains("Violations"));
    assertTrue(invalidRequest.contains("accountCode is required"));
    assertTrue(invalidRequest.contains("amount must be positive"));
    assertTrue(warning.contains("Warning"));
    assertTrue(warning.contains("warning-code"));
    assertTrue(info.contains("Info"));
    assertTrue(info.contains("info-code"));
  }

  @Test
  void renderFailureText_omitsHintRowWhenNoHintIsProvided() {
    String rendered =
        CliFailureOutputRenderer.renderFailureText(
            new CliFailure("runtime-failure", "Runtime exploded.", null, "--book-file"));

    assertTrue(rendered.contains("Argument"));
    assertTrue(rendered.contains("--book-file"));
    assertFalse(rendered.contains("Hint"), rendered);
  }

  @Test
  void renderRejectedText_rendersEveryStructuredRejectionShape() {
    assertRenderedNestedRepairableRejection(
        "account-state-violations",
        "Posting rejected with 3 account-state issues.",
        new CliRejectionJsonModels.AccountStateViolationsDetails(
            List.of(
                new CliAccountStateViolationPayload(
                    "unknown-account",
                    "lines[].accountCode",
                    "Journal line references undeclared account '1000'.",
                    "account-registry",
                    "Declare the missing account before retrying the posting.",
                    "1000",
                    null),
                new CliAccountStateViolationPayload(
                    "inactive-account",
                    "lines[].accountCode",
                    "Journal line references inactive account '2000'.",
                    "account-activation",
                    "Reactivate the account or replace it with an active posting account before retrying.",
                    "2000",
                    null),
                new CliAccountStateViolationPayload(
                    "non-postable-account",
                    "lines[].accountCode",
                    "Journal line references header account '3000', declared as 'HEADER', which cannot accept direct postings.",
                    "account-node-kind",
                    "Replace the header account with a postable account before retrying.",
                    "3000",
                    "HEADER"))),
        "Summary",
        "Issue 1 | unknown-account",
        "Issue 2 | inactive-account",
        "Issue 3 | non-postable-account",
        "Account node kind",
        "HEADER",
        "Why");
    assertRenderedRejection(
        new CliRejectionJsonModels.PriorPostingDetails("posting-9"),
        "Prior posting id",
        "posting-9");
    assertRenderedRejection(
        new CliRejectionJsonModels.AccountTypeConflictDetails("3200", "EQUITY", "LIABILITY"),
        "Existing account type",
        "Requested account type",
        "LIABILITY");
    assertRenderedRejection(
        new CliRejectionJsonModels.AccountTaxonomyConflictDetails(
            "3200",
            new CliRejectionJsonModels.AccountTaxonomyDetails(
                "POSTABLE", "3000", "OTHER_EQUITY", null),
            new CliRejectionJsonModels.AccountTaxonomyDetails(
                "POSTABLE", "3010", "RESULT_HOLDING", null)),
        "Existing parent account",
        "3000",
        "Existing financial position classification",
        "OTHER_EQUITY",
        "Requested parent account",
        "3010",
        "Requested financial position classification",
        "RESULT_HOLDING");
    assertRenderedRejection(
        new CliRejectionJsonModels.AccountTaxonomyConflictDetails(
            "4100",
            new CliRejectionJsonModels.AccountTaxonomyDetails(
                "POSTABLE", null, null, "COST_OF_SALES"),
            new CliRejectionJsonModels.AccountTaxonomyDetails(
                "POSTABLE", "4000", null, "OPERATING_EXPENSE")),
        "Existing parent account",
        "(none)",
        "Existing financial position classification",
        "(none)",
        "Existing profit-and-loss classification",
        "COST_OF_SALES",
        "Requested parent account",
        "4000",
        "Requested financial position classification",
        "(none)",
        "Requested profit-and-loss classification",
        "OPERATING_EXPENSE");
    assertRenderedRejection(
        new CliRejectionJsonModels.ParentAccountDetails("4100", "4000"),
        "Account code",
        "4100",
        "Parent account code",
        "4000");
    assertRenderedRejection(
        new CliRejectionJsonModels.ParentAccountTypeConflictDetails(
            "4100", "EXPENSE", "4000", "REVENUE"),
        "Requested account type",
        "EXPENSE",
        "Parent account type",
        "REVENUE");
    assertRenderedRejection(
        new CliRejectionJsonModels.ParentAccountNodeKindDetails("4100", "4000", "POSTABLE"),
        "Parent account code",
        "4000",
        "Parent account node kind",
        "POSTABLE");
    assertRenderedRejection(
        new CliRejectionJsonModels.ParentAccountTaxonomyConflictDetails(
            "4100",
            new CliRejectionJsonModels.AccountTaxonomyDetails(
                "POSTABLE", "4050", null, "OPERATING_EXPENSE"),
            "4000",
            new CliRejectionJsonModels.AccountTaxonomyDetails(
                "POSTABLE", null, null, "COST_OF_SALES")),
        "Requested parent account",
        "4050",
        "Requested profit-and-loss classification",
        "OPERATING_EXPENSE",
        "Parent account code",
        "4000",
        "Parent parent account",
        "(none)",
        "Parent profit-and-loss classification",
        "COST_OF_SALES");
    assertRenderedRejection(
        new CliRejectionJsonModels.FunctionalCurrencyMismatchDetails("EUR", "USD"),
        "Functional currency",
        "Attempted currency",
        "USD");
    assertRenderedRejection(
        new CliRejectionJsonModels.PostingEffectiveDateInFutureDetails("2026-07-01", "2026-06-30"),
        "Attempted effective date",
        "2026-07-01",
        "Current UTC date",
        "2026-06-30");
    assertRenderedRejection(
        new CliRejectionJsonModels.OpeningPositionWindowClosedDetails("STANDARD", "2026-04-07"),
        "First blocking posting kind",
        "STANDARD",
        "First blocking effective date",
        "2026-04-07");
    assertRenderedRejection(
        new CliRejectionJsonModels.OpeningPositionNominalAccountDetails("4000", "REVENUE"),
        "Account code",
        "4000",
        "Account type",
        "REVENUE");
    assertRenderedRejection(
        new CliRejectionJsonModels.ReservedResultClassificationDetails("3200", "result-holding"),
        "Account code",
        "3200",
        "Financial position classification",
        "result-holding");
    assertRenderedRejection(
        new CliRejectionJsonModels.CloseTargetAccountCandidateMissingDetails(
            "retained-earnings", List.of("3200")),
        "Required financial position classification",
        "retained-earnings",
        "Inactive candidate account codes",
        "3200");
    assertRenderedRejection(
        new CliRejectionJsonModels.CloseTargetAccountCandidateAmbiguousDetails(
            "other-equity", List.of("3200", "3210")),
        "Required financial position classification",
        "other-equity",
        "Candidate account codes",
        "3200, 3210");
    assertRenderedRejection(
        new CliRejectionJsonModels.InterimResultSweepStartDetails("2026-04-01"),
        "Required start date",
        "2026-04-01");
    assertRenderedRejection(
        new CliRejectionJsonModels.InterimResultSweepFutureDateDetails("2026-05-01"),
        "Attempted end date",
        "2026-05-01");
    assertRenderedRejection(
        new CliRejectionJsonModels.InterimResultSweepFiscalYearDetails(
            "2026-12-15", "2027-01-15", "01-01"),
        "Attempted start date",
        "Attempted end date",
        "Fiscal year start");
    assertRenderedRejection(
        new CliRejectionJsonModels.FiscalYearCloseStartDetails("2026-01-01"),
        "Required start date",
        "2026-01-01");
    assertRenderedRejection(
        new CliRejectionJsonModels.FiscalYearCloseEndDetails("2026-12-31"),
        "Required end date",
        "2026-12-31");
    assertRenderedRejection(
        new CliRejectionJsonModels.FiscalYearCloseTransferredThroughDetails(
            "2025-12-31", "2026-03-31"),
        "Attempted end date",
        "2025-12-31",
        "Transferred-through date",
        "2026-03-31");
    assertRenderedRejection(
        new CliRejectionJsonModels.FiscalYearCloseFutureDateDetails("2027-01-01"),
        "Attempted end date",
        "2027-01-01");
    assertRenderedRejection(
        new CliRejectionJsonModels.SweptInterimResultViolationDetails("2026-04-30", "2026-05-01"),
        "Transferred through",
        "Attempted effective date",
        "2026-05-01");
    assertRenderedRejection(
        new CliRejectionJsonModels.UnknownAccountDetails("9999"), "Account code", "9999");
    assertRenderedRejection(
        new CliRejectionJsonModels.PostingNotFoundDetails("posting-404"),
        "Posting id",
        "posting-404");
    assertRenderedRejection(
        new CliRejectionJsonModels.PlanRejectionDetails(samplePlan()), "Plan id", "plan-1");
  }

  @Test
  void renderRejectedText_rendersTaxRejectionDetails() {
    assertRenderedRejection(
        new dev.erst.fingrind.cli.json.CliTaxRejectionJsonModels.TaxDefinitionViolationsDetails(
            List.of(
                new dev.erst.fingrind.cli.json.CliTaxRejectionJsonModels
                    .TaxDefinitionViolationDetails(
                    "missing-tax-code", "taxCodes[0].taxCode", "Tax code is required."),
                new dev.erst.fingrind.cli.json.CliTaxRejectionJsonModels
                    .TaxDefinitionViolationDetails(
                    "invalid-jurisdiction", null, "Jurisdiction must be ISO 3166-1 alpha-2."))),
        "Violation 1",
        "taxCodes[0].taxCode [missing-tax-code]: Tax code is required.",
        "Violation 2",
        "invalid-jurisdiction [invalid-jurisdiction]: Jurisdiction must be ISO 3166-1 alpha-2.");
    assertRenderedRejection(
        new dev.erst.fingrind.cli.json.CliTaxRejectionJsonModels.UnknownTaxRegistrationDetails(
            "vat-missing"),
        "Tax registration id",
        "vat-missing");
    assertRenderedRejection(
        new dev.erst.fingrind.cli.json.CliTaxRejectionJsonModels.ObligationPeriodMismatchDetails(
            "MONTHLY", "2026-04-01", "2026-04-15"),
        "Obligation frequency",
        "Monthly",
        "Requested period start",
        "2026-04-01",
        "Requested period end",
        "2026-04-15");
  }

  @Test
  void renderRejectedText_rendersEntrySemanticsViolationsWithAndWithoutField() {
    String rendered =
        CliFailureOutputRenderer.renderRejectedText(
            "entry-semantics-violations",
            "Posting rejected with 2 entry-semantics issues.",
            null,
            null,
            new CliRejectionJsonModels.EntrySemanticsViolationsDetails(
                List.of(
                    new CliEntrySemanticsViolationPayload(
                        "account-type-mismatch",
                        "cashAccountCode",
                        "cash account must be declared as ASSET",
                        "account-type",
                        "Use accounts whose declared account type matches the violated field requirement."),
                    new CliEntrySemanticsViolationPayload(
                        "source-document-type-not-accepted",
                        null,
                        "invoice does not prove cash receipt",
                        "source-document-type",
                        "Use an accepted source document type for the selected entry kind's source-document policy."))));

    assertTrue(rendered.contains("entry-semantics-violations"));
    assertTrue(rendered.contains("Summary"));
    assertTrue(rendered.contains("Issue 1 | account-type-mismatch"));
    assertTrue(rendered.contains("Issue 2 | source-document-type-not-accepted"));
    assertTrue(rendered.contains("Field"));
    assertTrue(rendered.contains("cashAccountCode"));
    assertTrue(rendered.contains("Repair"));
    assertTrue(rendered.contains("Why"));
    assertTrue(rendered.contains("invoice does not prove cash receipt"));
    assertFalse(rendered.contains("Hint"));
    assertFalse(rendered.contains("Idempotency key"));
  }

  @Test
  void renderRejectedText_rejectsRoutingNestedPostingDetailsThroughTheSharedRowAppender() {
    assertThrows(
        IllegalStateException.class,
        () ->
            CliPostingRejectionTextRenderer.appendRows(
                new ArrayList<>(),
                new CliRejectionJsonModels.AccountStateViolationsDetails(
                    List.of(
                        new CliAccountStateViolationPayload(
                            "unknown-account",
                            "lines[].accountCode",
                            "Journal line references undeclared account '1000'.",
                            "account-registry",
                            "Declare the missing account before retrying the posting.",
                            "1000",
                            null)))));
    assertThrows(
        IllegalStateException.class,
        () ->
            CliPostingRejectionTextRenderer.appendRows(
                new ArrayList<>(),
                new CliRejectionJsonModels.EntrySemanticsViolationsDetails(
                    List.of(
                        new CliEntrySemanticsViolationPayload(
                            "account-type-mismatch",
                            "cashAccountCode",
                            "cash account must be declared as ASSET",
                            "account-type",
                            "Use accounts whose declared account type matches the violated field requirement.")))));
  }

  private static void assertRenderedRejection(
      CliRejectionJsonModels.RejectionDetails details, String... expectedFragments) {
    String rendered =
        CliFailureOutputRenderer.renderRejectedText(
            "rejected-code", "Rejected message.", "Repair hint.", "idem-1", details);
    assertTrue(rendered.contains("Rejected"));
    assertTrue(rendered.contains("Idempotency key"));
    assertTrue(rendered.contains("idem-1"));
    assertTrue(rendered.contains("Repair hint."));
    for (String expectedFragment : expectedFragments) {
      assertTrue(rendered.contains(expectedFragment));
    }
  }

  private static void assertRenderedNestedRepairableRejection(
      String code,
      String summary,
      CliRejectionJsonModels.RejectionDetails details,
      String... expectedFragments) {
    String rendered =
        CliFailureOutputRenderer.renderRejectedText(code, summary, null, "idem-1", details);
    assertTrue(rendered.contains("Rejected"));
    assertTrue(rendered.contains("Idempotency key"));
    assertTrue(rendered.contains("idem-1"));
    assertTrue(rendered.contains("Summary"));
    assertTrue(rendered.contains(summary));
    assertFalse(rendered.contains("Hint"));
    for (String expectedFragment : expectedFragments) {
      assertTrue(rendered.contains(expectedFragment));
    }
  }

  private static CliPlanJsonModels.LedgerPlanPayload samplePlan() {
    return new CliPlanJsonModels.LedgerPlanPayload(
        "plan-1",
        LedgerPlanStatus.REJECTED,
        PlanResultDetail.FULL,
        new CliPlanJsonModels.LedgerPlanSummaryPayload(
            "2026-05-13T10:15:30Z", "2026-05-13T10:15:31Z", 1, 0, 1, "step-1"),
        new CliPlanJsonModels.LedgerExecutionJournalPayload(
            "2026-05-13T10:15:30Z",
            "2026-05-13T10:15:31Z",
            List.of(
                new CliPlanJsonModels.LedgerJournalEntryPayload(
                    "step-1",
                    LedgerJournalKind.ASSERT,
                    LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS,
                    null,
                    LedgerStepStatus.ASSERTION_FAILED,
                    "2026-05-13T10:15:30Z",
                    "2026-05-13T10:15:31Z",
                    new CliPlanJsonModels.AccountCodeAssertionStepDataPayload("1000"),
                    new CliPlanJsonModels.LedgerStepFailurePayload(
                        "assertion-failed", "Rejected message.", List.of())))));
  }
}
