package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OperationId;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Unit tests for canonical CLI temporal-scope labels, options, and meaning tokens. */
class CliTemporalScopeTextTest {
  @Test
  void temporalScopeTextPublishesCanonicalOptionsAndBoundaryMeanings() {
    assertEquals(
        "Effective date range", CliTemporalScopeText.summaryLabel(OperationId.ACCOUNT_LEDGER));
    assertEquals(
        "--effective-date-from", CliTemporalScopeText.firstOption(OperationId.ACCOUNT_LEDGER));
    assertEquals(
        "--effective-date-to", CliTemporalScopeText.secondOption(OperationId.ACCOUNT_LEDGER));
    assertEquals("book-start", CliTemporalScopeText.lowerDateBoundaryMeaning(null));
    assertEquals(
        "selected-date",
        CliTemporalScopeText.lowerDateBoundaryMeaning(LocalDate.parse("2026-04-07")));
    assertEquals("current-book-horizon", CliTemporalScopeText.upperDateBoundaryMeaning(null));
    assertEquals(
        "selected-date",
        CliTemporalScopeText.upperDateBoundaryMeaning(LocalDate.parse("2026-04-30")));
    assertEquals(
        "selected-date",
        CliTemporalScopeText.resolvedUpperDateBoundaryMeaning(
            LocalDate.parse("2026-04-30"), LocalDate.parse("2026-04-30")));
    assertEquals(
        "latest-posting-effective-date",
        CliTemporalScopeText.resolvedUpperDateBoundaryMeaning(null, LocalDate.parse("2026-04-30")));
    assertEquals("no-postings", CliTemporalScopeText.resolvedUpperDateBoundaryMeaning(null, null));
    assertEquals("ranged-filter", CliTemporalScopeText.scopeKind(OperationId.ACCOUNT_LEDGER));
    assertEquals("bounded-period", CliTemporalScopeText.scopeKind(OperationId.PERIOD_SUMMARY));
    assertEquals("as-of-date", CliTemporalScopeText.scopeKind(OperationId.FINANCIAL_POSITION));
    assertEquals(
        "Optional lower and upper effective-date filters over committed postings. Omit the lower boundary to start at book start; omit the upper boundary to end at the current book horizon.",
        CliTemporalScopeText.boundarySemantics(OperationId.ACCOUNT_LEDGER));
    assertEquals(
        "One explicit closed reporting window. Both boundaries must be supplied, and neither boundary falls back to book start or the current book horizon.",
        CliTemporalScopeText.boundarySemantics(OperationId.PERIOD_SUMMARY));
    assertEquals(
        "One point-in-time effective-date cutoff. Supply --effective-date-as-of to pin that cutoff explicitly, or omit it to resolve the current book horizon for the selected report.",
        CliTemporalScopeText.boundarySemantics(OperationId.FINANCIAL_POSITION));
  }

  @Test
  void secondOptionRejectsSingleOptionTemporalScopes() {
    IllegalStateException missingSecondOption =
        assertThrows(
            IllegalStateException.class,
            () -> CliTemporalScopeText.secondOption(OperationId.FINANCIAL_POSITION));

    assertEquals(
        OperationId.FINANCIAL_POSITION.wireName() + " does not publish a second temporal option.",
        missingSecondOption.getMessage());
  }
}
