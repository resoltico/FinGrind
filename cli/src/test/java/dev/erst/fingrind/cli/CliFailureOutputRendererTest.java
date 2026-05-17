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

/** Unit tests for human-readable deterministic CLI failure rendering. */
class CliFailureOutputRendererTest {
  @Test
  void renderFailureHuman_rendersArgumentHintAndStructuredErrorDetails() {
    String invalidJson =
        CliFailureOutputRenderer.renderFailureHuman(
            new CliFailure(
                "invalid-json",
                "Malformed request.",
                "Fix the JSON syntax and retry.",
                "--request-file",
                new CliErrorJsonModels.InvalidJsonDetails("Unexpected token", 3, 9)));
    String invalidRequest =
        CliFailureOutputRenderer.renderDeterministicFailureHuman(
            new CliFailure(
                "invalid-request",
                "Request violates the schema.",
                "Correct the listed fields and rerun.",
                "--request-file",
                new CliErrorJsonModels.InvalidRequestDetails(
                    List.of("accountCode is required", "amount must be positive"))));
    String warning =
        CliFailureOutputRenderer.renderWarningHuman(
            new CliFailure("warning-code", "Heads up.", "Review the output.", "--output"));
    String info =
        CliFailureOutputRenderer.renderInfoHuman(
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
  void renderRejectedHuman_rendersEveryStructuredRejectionShape() {
    assertRenderedRejection(
        new CliRejectionJsonModels.AccountStateViolationsDetails(
            List.of(
                new CliRejectionJsonModels.AccountStateViolationPayload("unknown-account", "1000"),
                new CliRejectionJsonModels.AccountStateViolationPayload(
                    "inactive-account", "2000"))),
        "Violations",
        "unknown-account (1000)",
        "inactive-account (2000)");
    assertRenderedRejection(
        new CliRejectionJsonModels.PriorPostingDetails("posting-9"),
        "Prior posting id",
        "posting-9");
    assertRenderedRejection(
        new CliRejectionJsonModels.AccountRoleConflictDetails("3200", "ORDINARY", "CONTRA"),
        "Existing account role",
        "Requested account role",
        "CONTRA");
    assertRenderedRejection(
        new CliRejectionJsonModels.AccountTypeConflictDetails("3200", "EQUITY", "LIABILITY"),
        "Existing account type",
        "Requested account type",
        "LIABILITY");
    assertRenderedRejection(
        new CliRejectionJsonModels.AccountTaxonomyConflictDetails(
            "3200",
            new CliRejectionJsonModels.AccountTaxonomyDetails("3000", "OTHER_EQUITY", null),
            new CliRejectionJsonModels.AccountTaxonomyDetails("3010", "RETAINED_EARNINGS", null)),
        "Existing parent account",
        "3000",
        "Existing financial position classification",
        "OTHER_EQUITY",
        "Requested parent account",
        "3010",
        "Requested financial position classification",
        "RETAINED_EARNINGS");
    assertRenderedRejection(
        new CliRejectionJsonModels.AccountTaxonomyConflictDetails(
            "4100",
            new CliRejectionJsonModels.AccountTaxonomyDetails(null, null, "COST_OF_SALES"),
            new CliRejectionJsonModels.AccountTaxonomyDetails("4000", null, "OPERATING_EXPENSE")),
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
        new CliRejectionJsonModels.ParentAccountTaxonomyConflictDetails(
            "4100",
            new CliRejectionJsonModels.AccountTaxonomyDetails("4050", null, "OPERATING_EXPENSE"),
            "4000",
            new CliRejectionJsonModels.AccountTaxonomyDetails(null, null, "COST_OF_SALES")),
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
        new CliRejectionJsonModels.PostingKindDetails("opening-balance"),
        "Posting kind",
        "opening-balance");
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
        new CliRejectionJsonModels.ClosingEquityAccountDetails("3200"), "Account code", "3200");
    assertRenderedRejection(
        new CliRejectionJsonModels.ClosingEquityAccountClassificationMismatchDetails(
            "3200", "RETAINED_EARNINGS", "OTHER_EQUITY"),
        "Required financial position classification",
        "RETAINED_EARNINGS",
        "Actual financial position classification",
        "OTHER_EQUITY");
    assertRenderedRejection(
        new CliRejectionJsonModels.PeriodCloseStartDetails("2026-04-01"),
        "Required start date",
        "2026-04-01");
    assertRenderedRejection(
        new CliRejectionJsonModels.PeriodCloseFutureDateDetails("2026-05-01"),
        "Attempted end date",
        "2026-05-01");
    assertRenderedRejection(
        new CliRejectionJsonModels.PeriodCloseFiscalYearDetails(
            "2026-12-15", "2027-01-15", "01-01"),
        "Attempted start date",
        "Attempted end date",
        "Fiscal year start");
    assertRenderedRejection(
        new CliRejectionJsonModels.ClosedPeriodViolationDetails("2026-04-30", "2026-05-01"),
        "Closed through",
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

  private static void assertRenderedRejection(
      CliRejectionJsonModels.RejectionDetails details, String... expectedFragments) {
    String rendered =
        CliFailureOutputRenderer.renderRejectedHuman(
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
            List.of(
                new CliPlanJsonModels.LedgerStepDigestPayload(
                    "step-1",
                    LedgerJournalKind.POST_ENTRY,
                    LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS,
                    null,
                    LedgerStepStatus.REJECTED,
                    List.of("result=no"),
                    "rejected-code",
                    "Rejected message.")),
            "step-1",
            "rejected-code",
            "Rejected message."),
        new CliPlanJsonModels.LedgerExecutionJournalPayload(
            "2026-05-13T10:15:30Z",
            "2026-05-13T10:15:31Z",
            List.of(
                new CliPlanJsonModels.LedgerJournalEntryPayload(
                    "step-1",
                    LedgerJournalKind.POST_ENTRY,
                    LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS,
                    null,
                    LedgerStepStatus.REJECTED,
                    "2026-05-13T10:15:30Z",
                    "2026-05-13T10:15:31Z",
                    List.of(new CliPlanJsonModels.TextLedgerFactPayload("text", "result", "no")),
                    null))));
  }
}
