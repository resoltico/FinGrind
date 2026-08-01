package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliFixedAssetPostingJsonModels;
import dev.erst.fingrind.cli.json.CliForeignExchangeJsonModels;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.cli.json.CliTaxJsonModels;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests payroll-specific posting-entry text projection. */
class CliLatvianPayrollPostingEntryTextRendererTest {
  @Test
  void renderEntryFacts_preservesPayrollTaxBookAbsence() {
    String rendered =
        CliPostingEntryPayloadSupport.renderEntryFacts(monthlyPayrollWithoutTaxBook());

    assertTrue(rendered.contains("Payroll tax book held at employer"), rendered);
    assertTrue(rendered.contains("No"), rendered);
  }

  private static CliPostingEntryPayload monthlyPayrollWithoutTaxBook() {
    return new CliPostingEntryPayload(
        "LATVIAN_MONTHLY_PAYROLL",
        NullTestSupport.nullOf(String.class),
        NullTestSupport.nullOf(String.class),
        NullTestSupport.nullOf(String.class),
        NullTestSupport.nullOf(String.class),
        NullTestSupport.nullOf(String.class),
        NullTestSupport.nullOf(String.class),
        NullTestSupport.nullOf(String.class),
        NullTestSupport.nullOf(String.class),
        NullTestSupport.nullOf(String.class),
        NullTestSupport.nullOf(String.class),
        NullTestSupport.nullOf(MonetaryAmount.class),
        NullTestSupport.nullOf(String.class),
        NullTestSupport.nullOf(MonetaryAmount.class),
        NullTestSupport.nullOf(CliPostingEntryPayload.InventoryReliefPayload.class),
        NullTestSupport.nullOf(CliPostingEntryPayload.SettlementAdjunctPayload.class),
        NullTestSupport.nullOf(CliForeignExchangeJsonModels.ForeignExchangePayload.class),
        NullTestSupport.nullOf(CliTaxJsonModels.TaxSelectionPayload.class),
        NullTestSupport.nullOf(CliTaxJsonModels.AppliedTaxPayload.class),
        NullTestSupport.nullOf(CliBookQueryJsonModels.ReversalPayload.class),
        NullTestSupport.<List<dev.erst.fingrind.cli.json.CliOpeningBalancePayload>>nullOf(),
        NullTestSupport.nullOf(CliPostingEntryPayload.ResolvedInventoryCostingPayload.class),
        NullTestSupport.nullOf(CliPostingEntryPayload.AccrualCutoffPayload.class),
        new CliPostingEntryPayload.LatvianMonthlyPayrollPayload(
            "payroll-run-1",
            "employee-1",
            "2026-07",
            false,
            0,
            "5000",
            "5010",
            "2200",
            "2210",
            "2220",
            "2230",
            new MonetaryAmount("EUR", "200000"),
            NullTestSupport.nullOf(
                CliPostingEntryPayload.ResolvedLatvianMonthlyPayrollCalculationPayload.class)),
        NullTestSupport.nullOf(CliPostingEntryPayload.LatvianPayrollSettlementPayload.class),
        NullTestSupport.nullOf(CliFixedAssetPostingJsonModels.FixedAssetPayload.class));
  }
}
