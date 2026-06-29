package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
