package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStepStatus;
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
  void renderRejectedText_rendersEveryStructuredRejectionShape() {
    assertRenderedRejection(
        new CliRejectionJsonModels.AccountStateViolationsDetails(
            List.of(
                new CliRejectionJsonModels.AccountStateViolationPayload(
                    "unknown-account", "1000", null),
                new CliRejectionJsonModels.AccountStateViolationPayload(
                    "inactive-account", "2000", null),
                new CliRejectionJsonModels.AccountStateViolationPayload(
                    "non-postable-account", "3000", "HEADER"))),
        "Violations",
        "unknown-account (1000)",
        "inactive-account (2000)",
        "non-postable-account (3000, HEADER)");
    assertRenderedRejection(
        new CliRejectionJsonModels.PriorPostingDetails("posting-9"),
        "Prior posting id",
        "posting-9");
    assertRenderedRejection(
        new CliRejectionJsonModels.AccountRoleConflictDetails(
            "3200", "ORDINARY", "POLARITY_INVERTED"),
        "Existing account role",
        "Requested account role",
        "POLARITY_INVERTED");
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
        new CliRejectionJsonModels.ParentAccountRoleConflictDetails(
            "4100", "ORDINARY", "4000", "POLARITY_INVERTED"),
        "Requested account role",
        "ORDINARY",
        "Parent account role",
        "POLARITY_INVERTED");
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
        new CliRejectionJsonModels.OpeningBalanceWindowClosedDetails("STANDARD", "2026-04-07"),
        "First blocking posting kind",
        "STANDARD",
        "First blocking effective date",
        "2026-04-07");
    assertRenderedRejection(
        new CliRejectionJsonModels.OpeningBalanceNominalAccountDetails("4000", "REVENUE"),
        "Account code",
        "4000",
        "Account type",
        "REVENUE");
    assertRenderedRejection(
        new CliRejectionJsonModels.ResultHoldingAccountDetails("3200"), "Account code", "3200");
    assertRenderedRejection(
        new CliRejectionJsonModels.ResultHoldingAccountCandidateMissingDetails(
            "retained-earnings", List.of("3200")),
        "Required financial position classification",
        "retained-earnings",
        "Inactive candidate account codes",
        "3200");
    assertRenderedRejection(
        new CliRejectionJsonModels.ResultHoldingAccountCandidateAmbiguousDetails(
            "other-equity", List.of("3200", "3210")),
        "Required financial position classification",
        "other-equity",
        "Candidate account codes",
        "3200, 3210");
    assertRenderedRejection(
        new CliRejectionJsonModels.PeriodResultTransferStartDetails("2026-04-01"),
        "Required start date",
        "2026-04-01");
    assertRenderedRejection(
        new CliRejectionJsonModels.PeriodResultTransferFutureDateDetails("2026-05-01"),
        "Attempted end date",
        "2026-05-01");
    assertRenderedRejection(
        new CliRejectionJsonModels.PeriodResultTransferFiscalYearDetails(
            "2026-12-15", "2027-01-15", "01-01"),
        "Attempted start date",
        "Attempted end date",
        "Fiscal year start");
    assertRenderedRejection(
        new CliRejectionJsonModels.TransferredPeriodResultViolationDetails(
            "2026-04-30", "2026-05-01"),
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
  void renderRejectedText_rendersEntrySemanticsViolationsWithAndWithoutField() {
    String rendered =
        CliFailureOutputRenderer.renderRejectedText(
            "entry-semantics-violations",
            "Typed entry contradicts declared account or evidence doctrine.",
            "Choose matching accounts and evidence.",
            "idem-semantics",
            new CliRejectionJsonModels.EntrySemanticsViolationsDetails(
                List.of(
                    new CliRejectionJsonModels.EntrySemanticsViolationPayload(
                        "account-type-mismatch",
                        "cashAccountCode",
                        "cash account must be declared as ASSET"),
                    new CliRejectionJsonModels.EntrySemanticsViolationPayload(
                        "source-document-type-not-accepted",
                        null,
                        "invoice does not prove cash receipt"))));

    assertTrue(rendered.contains("entry-semantics-violations"));
    assertTrue(
        rendered.contains(
            "account-type-mismatch (cashAccountCode: cash account must be declared as ASSET)"));
    assertTrue(
        rendered.contains(
            "source-document-type-not-accepted (invoice does not prove cash receipt)"));
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

  private static CliPlanJsonModels.LedgerPlanPayload samplePlan() {
    return new CliPlanJsonModels.LedgerPlanPayload(
        "plan-1",
        LedgerPlanStatus.REJECTED,
        PlanResultDetail.FULL,
        new CliPlanJsonModels.LedgerPlanSummaryPayload(
            "2026-05-13T10:15:30Z",
            "2026-05-13T10:15:31Z",
            1,
            0,
            1,
            "step-1",
            "rejected-code",
            "Rejected message."),
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
