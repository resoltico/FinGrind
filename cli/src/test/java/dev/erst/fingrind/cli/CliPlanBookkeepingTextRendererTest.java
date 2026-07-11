package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import org.junit.jupiter.api.Test;

/** Focused coverage for bookkeeping payload sections inside full plan text output. */
class CliPlanBookkeepingTextRendererTest extends CliFixtureSupport {
  @Test
  void renderPosting_rendersCallerAuthoredEntryFactsWhenPresent() {
    String rendered =
        CliPlanBookkeepingTextRenderer.renderPosting(
            CliBookPayloadMapper.postingDetailsPayload(bookIdentity(), salePostingFact())
                .posting());

    assertTrue(rendered.contains("Entry facts"));
    assertTrue(rendered.contains("Cash account"));
    assertTrue(rendered.contains("service-revenue"));
  }

  @Test
  void renderPosting_omitsCallerAuthoredEntryFactsWhenAbsent() {
    String rendered =
        CliPlanBookkeepingTextRenderer.renderPosting(
            CliBookPayloadMapper.postingDetailsPayload(bookIdentity(), reversalPostingFact())
                .posting());

    assertFalse(rendered.contains("Entry facts"));
    assertTrue(rendered.contains("Reversal"));
  }

  @Test
  void renderDeclaredAccount_rendersInventoryUnitOfMeasureWhenPresent() {
    String rendered =
        CliPlanBookkeepingTextRenderer.renderDeclaredAccount(
            "declared",
            new CliBookQueryJsonModels.DeclaredAccountPayload(
                "1400",
                "Inventory",
                "ASSET",
                "POSTABLE",
                null,
                "INVENTORY",
                "NON_CASH",
                null,
                new CliBookQueryJsonModels.UnitOfMeasurePayload("kg", 3),
                "DEBIT",
                true,
                "2026-04-23T10:15:30Z"));

    assertTrue(rendered.contains("Unit of measure"));
    assertTrue(rendered.contains("kg (scale 3)"));
  }
}
