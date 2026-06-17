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
