package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.reportmodel.ReportContext;
import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.contract.reportmodel.ReportSection;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.NormalBalance;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers live helper branches that remain after the shared-report-model cutover. */
class CliSurfaceHelperCoverageTest extends CliFixtureSupport {
  @Test
  void temporalScopeHelpers_delegateToThePublishedRequestSurface() {
    assertEquals(
        ProtocolCatalog.domain()
            .requestSurface()
            .temporalScopeFor(OperationId.LIST_POSTINGS)
            .lowerLabel(),
        CliTemporalScopeText.lowerLabel(OperationId.LIST_POSTINGS));
    assertEquals(
        ProtocolCatalog.domain()
            .requestSurface()
            .temporalScopeFor(OperationId.LIST_POSTINGS)
            .upperLabel(),
        CliTemporalScopeText.upperLabel(OperationId.LIST_POSTINGS));
    assertEquals(
        "Zero across all currencies.", CliQueryTextFormatAccess.zeroAcrossCurrenciesLabel());
  }

  @Test
  void postingFormatters_coverSelfBranchesForReadableAndStructuredHelpers() {
    var cashAccount = declaredAccount("1000", "Cash", NormalBalance.DEBIT);
    var selfPosting = selfPostingFact();
    var reversalPosting = reversalPostingFact();

    assertEquals("(self)", CliQueryRowFormatAccess.counterpartAccounts(cashAccount, selfPosting));
    assertEquals(List.of(), CliBookPayloadMapper.counterpartAccounts(cashAccount, selfPosting));
    assertEquals(
        List.of("2000"), CliBookPayloadMapper.counterpartAccounts(cashAccount, reversalPosting));
    assertEquals("(none)", CliQueryRowFormatAccess.postingApprovalsText(selfPosting));
  }

  @Test
  void balanceHelpers_coverAllBalanceSides_andExplicitStateLabels() {
    CurrencyBalance debitBalance = eurDebitBalance();
    CurrencyBalance creditBalance =
        CurrencyBalance.ofTotals(money("EUR", "4.00"), money("EUR", "10.00"));
    CurrencyBalance zeroBalance =
        CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "0.00"));

    assertEquals(
        List.of("EUR", "10.00", "4.00", "6.00", "Debit"),
        CliQueryRowFormatAccess.balanceTextRow(debitBalance));
    assertEquals(
        List.of("EUR", "4.00", "10.00", "6.00", "CREDIT"),
        CliQueryRowFormatAccess.balanceCsvRow(creditBalance));
    assertEquals("(none)", CliQueryRowFormatAccess.joinedBalances(List.of()));
    assertEquals("EUR 6.00 Credit", CliQueryRowFormatAccess.displayBalanceText(creditBalance));
    assertEquals("Zero", CliQueryRowFormatAccess.displayBalanceSideLabel(BalanceSide.ZERO));
    assertEquals("Balanced", CliQueryRowFormatAccess.displayBalanceStateLabel(true));
    assertEquals("Imbalanced", CliQueryRowFormatAccess.displayBalanceStateLabel(false));
    assertEquals("EUR 0.00 ZERO", CliQueryRowFormatAccess.displayBalance(zeroBalance));
  }

  @Test
  void dateAndWireLabelHelpers_coverSelectedDateAndFallbackLabelBranches() {
    LocalDate lower = LocalDate.parse("2026-05-01");
    LocalDate upper = LocalDate.parse("2026-05-31");

    assertEquals("2026-05-01", CliQueryTextFormatAccess.lowerDateBoundaryLabel(lower));
    assertEquals("2026-05-31", CliQueryTextFormatAccess.upperDateBoundaryLabel(upper));
    assertEquals(
        "2026-05-31",
        CliQueryTextFormatAccess.upperDateBoundaryLabel(
            LocalDate.parse("2026-05-31"), LocalDate.parse("2026-05-14")));
    assertEquals("Debit", CliPostingOutputRenderer.displayWireLabel("DEBIT"));
    assertEquals("Credit", CliPostingOutputRenderer.displayWireLabel("CREDIT"));
    assertEquals("mystery label", CliPostingOutputRenderer.displayWireLabel("MYSTERY_LABEL"));
  }

  @Test
  void reportTextHelpers_coverBoundedComparativesAndEmptyProjectedSections() {
    assertEquals(
        "2026-01-01 to 2026-03-31",
        CliReportRenderSupport.comparativeReferenceLine(
            EffectiveDateRange.of(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-03-31"))));

    ReportModel model =
        new ReportModel(
            "sample-report",
            "Sample Report",
            ReportModel.Orientation.PORTRAIT,
            new ReportContext(
                "Acme Studio",
                "Owner-managed services",
                "Cash",
                "EUR",
                "01-01",
                "2026-01-01",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()),
            List.of(),
            List.of(
                new ReportSection(
                    "empty", "Empty section", List.of(), List.of(), List.of(), List.of())));

    String rendered = TextReportProjector.render(model);

    assertTrue(rendered.contains("Sample Report"));
    assertTrue(rendered.contains("Empty section"));
    assertTrue(rendered.contains("No projected facts."));
    assertTrue(rendered.contains("Context"));
  }
}
