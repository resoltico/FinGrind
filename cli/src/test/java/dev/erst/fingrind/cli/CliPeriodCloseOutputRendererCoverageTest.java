package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Coverage for close-output titles and idempotent replay rendering. */
class CliPeriodCloseOutputRendererCoverageTest extends CliFixtureSupport {
  @Test
  void renderClosedFiscalYearText_marksIdempotentReplayAsAlreadyClosed() {
    String rendered =
        CliPeriodCloseOutputRenderer.renderClosedFiscalYearText(sampleClosedFiscalYear(), true);

    assertTrue(rendered.contains("Fiscal Year Already Closed"), rendered);
    assertTrue(rendered.contains("Idempotent replay"), rendered);
    assertTrue(rendered.contains("Yes"), rendered);
  }
}
