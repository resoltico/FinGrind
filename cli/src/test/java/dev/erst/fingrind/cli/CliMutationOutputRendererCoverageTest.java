package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.ResolvedJournal;
import dev.erst.fingrind.core.ClassificationResult;
import dev.erst.fingrind.core.EconomicEventClass;
import dev.erst.fingrind.core.EvidenceClass;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.StructuralContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Covers mutation text branches not exercised by higher-level CLI workflow tests. */
class CliMutationOutputRendererCoverageTest {
  @Test
  void renderMutationText_usesNoneWhenContainedTypedEventsAreAbsent() {
    ResolvedJournal resolvedJournal =
        new ResolvedJournal(
            new JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new JournalLine(
                        new dev.erst.fingrind.core.AccountCode("1000"),
                        JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "10.00")),
                    new JournalLine(
                        new dev.erst.fingrind.core.AccountCode("2000"),
                        JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "10.00")))),
            null,
            null,
            new ClassificationResult(
                EconomicEventClass.ADJUSTMENT,
                Set.of(),
                Set.of(),
                false,
                EvidenceClass.OTHER,
                StructuralContext.ordinary()));

    String preflight =
        CliMutationOutputRenderer.renderPreflightAcceptedText(
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-none"), LocalDate.parse("2026-04-07"), resolvedJournal));
    String committed =
        CliMutationOutputRenderer.renderCommittedText(
            new PostEntryResult.Committed(
                new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                new IdempotencyKey("idem-none"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z"),
                true,
                resolvedJournal,
                null));

    assertTrue(preflight.contains("Contained typed events"));
    assertTrue(preflight.contains("(none)"));
    assertTrue(preflight.contains("Journal lines"));
    assertTrue(preflight.contains("1000"));
    assertTrue(committed.contains("Contained typed events"));
    assertTrue(committed.contains("(none)"));
    assertTrue(committed.contains("Journal lines"));
    assertTrue(committed.contains("2000"));
  }

  @Test
  void renderMutationText_omitsRedundantSingletonContainedTypedEventsForTypedEntries() {
    String preflight =
        CliMutationOutputRenderer.renderPreflightAcceptedText(
            CliPostEntryResultFixtures.preflightAccepted(
                new IdempotencyKey("idem-singleton"), LocalDate.parse("2026-04-07")));
    String committed =
        CliMutationOutputRenderer.renderCommittedText(
            CliPostEntryResultFixtures.committed(
                new PostingId("d5340e27-063d-32a4-a977-01932c57e4be"),
                new IdempotencyKey("idem-singleton"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z"),
                false));

    assertTrue(preflight.contains("Event class"));
    assertFalse(preflight.contains("Contained typed events"));
    assertTrue(committed.contains("Event class"));
    assertFalse(committed.contains("Contained typed events"));
  }

  @Test
  void renderMutationText_rendersTheExactExpandedJournalTableForPreflightAndCommit() {
    String preflight =
        CliMutationOutputRenderer.renderPreflightAcceptedText(
            CliPostEntryResultFixtures.preflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")));
    String committed =
        CliMutationOutputRenderer.renderCommittedText(
            CliPostEntryResultFixtures.committed(
                new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z"),
                false));
    String expectedJournalSection =
        CliReportRenderSupport.section(
                "Journal lines",
                CliJournalLineTextRenderer.renderLines(
                    CliPostEntryResultFixtures.resolvedJournal().expandedLines().lines()))
            .trim();

    assertEquals(expectedJournalSection, renderedJournalSection(preflight));
    assertEquals(expectedJournalSection, renderedJournalSection(committed));
    assertEquals(renderedJournalSection(preflight), renderedJournalSection(committed));
  }

  @Test
  void renderMutationText_preflightRendersDerivedCostOfSalesLines() {
    ResolvedJournal resolvedJournal =
        new ResolvedJournal(
            new JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new JournalLine(
                        new dev.erst.fingrind.core.AccountCode("1000"),
                        JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "70.00")),
                    new JournalLine(
                        new dev.erst.fingrind.core.AccountCode("4000"),
                        JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "70.00")),
                    new JournalLine(
                        new dev.erst.fingrind.core.AccountCode("5000"),
                        JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "10.00")),
                    new JournalLine(
                        new dev.erst.fingrind.core.AccountCode("1400"),
                        JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "10.00")))),
            null,
            null,
            new ClassificationResult(
                EconomicEventClass.SETTLED_SALE,
                Set.of(),
                Set.of(EconomicEventClass.SETTLED_SALE),
                false,
                EvidenceClass.CASH_SETTLEMENT,
                StructuralContext.ordinary()));

    String rendered =
        CliMutationOutputRenderer.renderPreflightAcceptedText(
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-derived-cogs"),
                LocalDate.parse("2026-04-07"),
                resolvedJournal));

    assertTrue(rendered.contains("Journal lines"), rendered);
    assertTrue(rendered.contains("5000"), rendered);
    assertTrue(rendered.contains("1400"), rendered);
    assertTrue(rendered.contains("10.00"), rendered);
  }

  @Test
  void renderMutationText_listsCompoundContainedTypedEventsWhenTheyAddMeaning() {
    ResolvedJournal resolvedJournal =
        new ResolvedJournal(
            new JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new JournalLine(
                        new dev.erst.fingrind.core.AccountCode("1000"),
                        JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "10.00")),
                    new JournalLine(
                        new dev.erst.fingrind.core.AccountCode("2000"),
                        JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "10.00")))),
            null,
            null,
            new ClassificationResult(
                EconomicEventClass.COMPOUND_OPERATIONAL,
                Set.of(),
                Set.of(EconomicEventClass.AP_SETTLEMENT, EconomicEventClass.CREDIT_SALE),
                false,
                EvidenceClass.OTHER,
                StructuralContext.ordinary()));

    String preflight =
        CliMutationOutputRenderer.renderPreflightAcceptedText(
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-compound"),
                LocalDate.parse("2026-04-07"),
                resolvedJournal));
    String committed =
        CliMutationOutputRenderer.renderCommittedText(
            new PostEntryResult.Committed(
                new PostingId("a98b2463-0b2b-3b3c-986c-be2cb3d845c2"),
                new IdempotencyKey("idem-compound"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z"),
                false,
                resolvedJournal,
                null));

    assertTrue(preflight.contains("Contained typed events"));
    assertTrue(preflight.contains("AP_SETTLEMENT, CREDIT_SALE"), preflight);
    assertTrue(committed.contains("Contained typed events"));
    assertTrue(committed.contains("AP_SETTLEMENT, CREDIT_SALE"), committed);
  }

  @Test
  void renderMutationText_rendersSingletonContainedTypedEventWhenItDiffersFromEventClass() {
    ResolvedJournal resolvedJournal =
        new ResolvedJournal(
            new JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new JournalLine(
                        new dev.erst.fingrind.core.AccountCode("1000"),
                        JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "10.00")),
                    new JournalLine(
                        new dev.erst.fingrind.core.AccountCode("2000"),
                        JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "10.00")))),
            null,
            null,
            new ClassificationResult(
                EconomicEventClass.ADJUSTMENT,
                Set.of(),
                Set.of(EconomicEventClass.CREDIT_SALE),
                false,
                EvidenceClass.OTHER,
                StructuralContext.ordinary()));

    String rendered =
        CliMutationOutputRenderer.renderPreflightAcceptedText(
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-shadow-singleton"),
                LocalDate.parse("2026-04-07"),
                resolvedJournal));

    assertTrue(rendered.contains("Contained typed events"));
    assertTrue(rendered.contains("CREDIT_SALE"), rendered);
  }

  @Test
  void renderAccountDeclarationText_rendersInventoryUnitOfMeasureWhenPresent() {
    String rendered =
        CliMutationOutputRenderer.renderAccountDeclarationText(
            "declared", CliIoFixtureSupport.inventoryDeclaredAccount("1400", "Inventory", "kg", 3));

    assertTrue(rendered.contains("Unit of measure"));
    assertTrue(rendered.contains("kg (scale 3)"));
  }

  private static String renderedJournalSection(String rendered) {
    int sectionIndex = rendered.indexOf("Journal lines");
    assertTrue(sectionIndex >= 0, rendered);
    return rendered.substring(sectionIndex).trim();
  }
}
