package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.NormalBalance;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Focused coverage for the split full-plan detail and bookkeeping text renderers. */
class CliPlanDetailTextRendererTest extends CliFixtureSupport {
  @Test
  void renderStepData_rendersScalarPlanPayloadVariants() {
    assertContainsAll(
        CliPlanDetailTextRenderer.renderStepData(
            new CliPlanJsonModels.AccountDeclarationStepDataPayload(
                "reactivated",
                CliBookPayloadMapper.accountPayload(
                    declaredAccount("1000", "Cash", NormalBalance.DEBIT)))),
        "Outcome",
        "reactivated",
        "Account code",
        "1000",
        "Cash",
        "Normal balance");
    assertContainsAll(
        CliPlanDetailTextRenderer.renderStepData(
            new CliPlanJsonModels.PreflightEntryStepDataPayload("idem-1", "2026-04-17")),
        "Idempotency key",
        "idem-1",
        "Effective date");
    assertContainsAll(
        CliPlanDetailTextRenderer.renderStepData(
            new CliPlanJsonModels.CommittedEntryStepDataPayload(
                "posting-1", "idem-1", "2026-04-17", "2026-04-17T10:15:32Z")),
        "Posting id",
        "Recorded at");
    assertContainsAll(
        CliPlanDetailTextRenderer.renderStepData(
            new CliPlanJsonModels.BookInspectionStepDataPayload("initialized", true, false)),
        "State",
        "Initialized",
        "Yes",
        "No");
    assertContainsAll(
        CliPlanDetailTextRenderer.renderStepData(
            new CliPlanJsonModels.AccountCodeAssertionStepDataPayload("1000")),
        "Account code",
        "1000");
    assertContainsAll(
        CliPlanDetailTextRenderer.renderStepData(
            new CliPlanJsonModels.PostingIdAssertionStepDataPayload("posting-1")),
        "Posting id",
        "posting-1");
    assertContainsAll(
        CliPlanDetailTextRenderer.renderStepData(
            new CliPlanJsonModels.PlanBoundaryStepDataPayload("commit")),
        "Checkpoint",
        "commit");
  }

  @Test
  void renderStepData_rendersAccountAndPostingPayloadsAcrossEmptyAndPopulatedBranches() {
    DeclaredAccount cashAccount = declaredAccount("1000", "Cash", NormalBalance.DEBIT);
    CliBookQueryJsonModels.DeclaredAccountPayload accountPayload =
        CliBookPayloadMapper.accountPayload(cashAccount);
    PostingFact reversalPosting = CliResponseWriterTestSupport.postingFactWithApproval();
    PostingFact directPosting = selfPostingFact();
    AccountBalanceSnapshot snapshot =
        accountBalanceSnapshot(
            cashAccount,
            CliResponseWriterTestSupport.currencyBalance(
                "EUR", "10.00", "4.00", "6.00", BalanceSide.DEBIT));
    assertContainsAll(
        CliPlanDetailTextRenderer.renderStepData(
            new CliPlanJsonModels.AccountPageStepDataPayload(
                1, 50, "cursor-1", true, List.of(accountPayload))),
        "Next cursor",
        "cursor-1",
        "Accounts",
        "Cash");
    assertContainsAll(
        CliPlanDetailTextRenderer.renderStepData(
            new CliPlanJsonModels.AccountPageStepDataPayload(0, 50, null, false, List.of())),
        "No accounts matched the selected scope.");
    assertContainsAll(
        CliPlanDetailTextRenderer.renderStepData(
            new CliPlanJsonModels.PostingStepDataPayload(
                CliBookPayloadMapper.postingDetailsPayload(bookIdentity(), reversalPosting)
                    .posting())),
        "Posting id",
        "Evidence",
        "Approvals",
        "Reversal");
    String directPostingText =
        CliPlanDetailTextRenderer.renderStepData(
            new CliPlanJsonModels.PostingStepDataPayload(
                CliBookPayloadMapper.postingDetailsPayload(bookIdentity(), directPosting)
                    .posting()));
    assertContainsAll(directPostingText, "Posting id", "Evidence", "Source documents");
    assertFalse(directPostingText.contains("Prior posting id"));
    assertFalse(directPostingText.contains("Correction"));
    assertContainsAll(
        CliPlanDetailTextRenderer.renderStepData(
            new CliPlanJsonModels.PostingPageStepDataPayload(
                1,
                50,
                "cursor-1",
                true,
                List.of(CliBookPayloadMapper.postingSummaryPayload(reversalPosting)))),
        "Postings",
        "bdc03c47-a16c-3688-a18f-2445894bbc69",
        "cursor-1");
    assertContainsAll(
        CliPlanDetailTextRenderer.renderStepData(
            new CliPlanJsonModels.PostingPageStepDataPayload(0, 50, null, false, List.of())),
        "No postings matched the selected scope.");
    assertContainsAll(
        CliPlanDetailTextRenderer.renderStepData(
            new CliPlanJsonModels.AccountBalanceStepDataPayload(
                accountPayload,
                snapshot.effectiveDateFrom().map(Object::toString).orElse(null),
                snapshot.effectiveDateTo().map(Object::toString).orElse(null),
                snapshot.balances().size(),
                snapshot.balances().stream().map(CliPayloadAssembler::balancePayload).toList())),
        "Balances",
        "Debit total",
        "6.00");
    assertContainsAll(
        CliPlanDetailTextRenderer.renderStepData(
            new CliPlanJsonModels.AccountBalanceStepDataPayload(
                accountPayload, null, null, 0, List.of())),
        "No balances matched the selected scope.");
  }

  @Test
  void renderStepData_rendersEmptyEvidenceBranchesForPostingPayloads() {
    CliBookQueryJsonModels.PostingPayload posting =
        new CliBookQueryJsonModels.PostingPayload(
            "posting-empty-evidence",
            "STANDARD",
            "DIRECT_JOURNAL",
            "ORIGINAL",
            null,
            null,
            "2026-04-17",
            "2026-04-17T10:15:32Z",
            "command-1",
            "idem-1",
            "cause-1",
            null,
            "CLI",
            new CliBookQueryJsonModels.AccountingEvidencePayload(List.of(), List.of()),
            null,
            null,
            List.of(
                new CliBookQueryJsonModels.JournalLinePayload(
                    "1000", "DEBIT", new MonetaryAmount("EUR", "1000")),
                new CliBookQueryJsonModels.JournalLinePayload(
                    "2000", "CREDIT", new MonetaryAmount("EUR", "1000"))));

    String rendered =
        CliPlanDetailTextRenderer.renderStepData(
            new CliPlanJsonModels.PostingStepDataPayload(posting));

    assertContainsAll(
        rendered,
        "No source documents matched the selected scope.",
        "No approvals matched the selected scope.");
  }

  @Test
  void renderFailure_rendersEveryFactKindAndNestedGroups() {
    String rendered =
        CliPlanDetailTextRenderer.renderFailure(
            new LedgerStepFailure(
                "assertion-failed",
                "Balance mismatch.",
                List.of(
                    LedgerFact.text("accountCode", "1000"),
                    LedgerFact.flag("balanced", true),
                    LedgerFact.count("lineCount", 2),
                    LedgerFact.money("actualNetAmount", new MonetaryAmount("EUR", "1000")),
                    LedgerFact.group(
                        "expected-balance", List.of(LedgerFact.text("currencyCode", "EUR"))))));

    assertContainsAll(
        rendered,
        "Failure code",
        "assertion-failed",
        "Failure details",
        "Account Code",
        "Balanced",
        "Yes",
        "Line Count",
        "2",
        "Actual Net Amount",
        "10.00 EUR",
        "expected-balance",
        "Currency Code");
  }

  @Test
  void displayLabel_normalizesMixedIdentifierStyles() {
    assertEquals("Camel Case Label", CliPlanDetailTextRenderer.displayLabel("camelCase_label"));
    assertEquals("Checkpoint Name", CliPlanDetailTextRenderer.displayLabel("  checkpoint-name  "));
    assertEquals("", CliPlanDetailTextRenderer.displayLabel("___"));
  }

  private static void assertContainsAll(String rendered, String... tokens) {
    for (String token : tokens) {
      assertTrue(rendered.contains(token), () -> "Expected token not found: " + token);
    }
  }
}
