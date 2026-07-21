package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.ClosedFiscalYear;
import dev.erst.fingrind.contract.bookkeeping.SweptInterimResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Coverage for close-output titles and idempotent replay rendering. */
class CliPeriodCloseOutputRendererCoverageTest extends CliFixtureSupport {
  @Test
  void renderSweptInterimResultText_rendersMovementsAndTheNoMovementOutcome() {
    String renderedWithMovements =
        CliPeriodCloseOutputRenderer.renderSweptInterimResultText(sampleSweptInterimResult());

    assertTrue(renderedWithMovements.contains("Interim Result Swept"), renderedWithMovements);
    assertTrue(renderedWithMovements.contains("EUR 10.00 Credit"), renderedWithMovements);
    assertTrue(renderedWithMovements.contains("98be232b-af01-324d-b4fc-6f62636fae68"));

    SweptInterimResult noMovementResult =
        new SweptInterimResult(
            2,
            new ReportingPeriod(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31")),
            new AccountCode("3200"),
            List.of(),
            Instant.parse("2026-05-31T12:00:00Z"),
            List.of());
    String renderedWithoutMovements =
        CliPeriodCloseOutputRenderer.renderSweptInterimResultText(noMovementResult);

    assertTrue(renderedWithoutMovements.contains("Generated interim-result-sweep postings"));
    assertTrue(renderedWithoutMovements.contains("(none)"));
    assertTrue(
        renderedWithoutMovements.contains(
            "No closing movements were required for the selected reporting period."));

    SweptInterimResult nonemptyPostingIdsWithoutTotals =
        new SweptInterimResult(
            3,
            new ReportingPeriod(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-30")),
            new AccountCode("3200"),
            List.of(),
            Instant.parse("2026-06-30T12:00:00Z"),
            List.of(new PostingId("98be232b-af01-324d-b4fc-6f62636fae68")));
    assertTrue(
        CliPeriodCloseOutputRenderer.renderSweptInterimResultText(nonemptyPostingIdsWithoutTotals)
            .contains("98be232b-af01-324d-b4fc-6f62636fae68"));
  }

  @Test
  void renderClosedFiscalYearText_marksIdempotentReplayAsAlreadyClosed() {
    String rendered =
        CliPeriodCloseOutputRenderer.renderClosedFiscalYearText(sampleClosedFiscalYear(), true);

    assertTrue(rendered.contains("Fiscal Year Already Closed"), rendered);
    assertTrue(rendered.contains("Idempotent replay"), rendered);
    assertTrue(rendered.contains("Yes"), rendered);
  }

  @Test
  void renderClosedFiscalYearText_rendersFreshCloseAndAllGeneratedPostings() {
    String rendered =
        CliPeriodCloseOutputRenderer.renderClosedFiscalYearText(sampleClosedFiscalYear(), false);

    assertTrue(rendered.contains("Fiscal Year Closed"), rendered);
    assertTrue(rendered.contains("Generated fiscal-year-close postings"), rendered);
    assertTrue(rendered.contains("98be232b-af01-324d-b4fc-6f62636fae68"), rendered);
    assertTrue(rendered.contains("548200b1-9743-3000-a75c-17a99ebf79b7"), rendered);
    assertTrue(rendered.contains("Idempotent replay"), rendered);
    assertTrue(rendered.contains("No"), rendered);
  }

  @Test
  void renderClosedFiscalYearText_rendersNoGeneratedPostingMarkerWhenNoPostingWasNeeded() {
    ClosedFiscalYear noPostingClose =
        new ClosedFiscalYear(
            2,
            new ReportingPeriod(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31")),
            new AccountCode("3000"),
            new AccountCode("3200"),
            new AccountCode("3300"),
            Instant.parse("2025-12-31T12:00:00Z"),
            List.of());

    String rendered =
        CliPeriodCloseOutputRenderer.renderClosedFiscalYearText(noPostingClose, false);

    assertTrue(rendered.contains("Generated fiscal-year-close postings"), rendered);
    assertTrue(rendered.contains("(none)"), rendered);
  }
}
