package dev.erst.fingrind.report.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Focused branch coverage tests for {@link PdfValueFormatter}. */
class PdfValueFormatterTest {
  @Test
  void displayMoneyUsesCanonicalCurrencyScale() {
    assertEquals("12.50", PdfValueFormatter.displayMoney(Money.parse("EUR", "12.50")));
    assertEquals("42.00", PdfValueFormatter.displayMoney(Money.parse("EUR", "42.00")));
    assertEquals("100", PdfValueFormatter.displayMoney(Money.parse("JPY", "100")));
    assertEquals("1.250", PdfValueFormatter.displayMoney(Money.parse("BHD", "1.25")));
  }

  @Test
  void displayBalanceSideFormatsEveryVariant() {
    assertEquals("Debit", PdfValueFormatter.displayBalanceSide(BalanceSide.DEBIT));
    assertEquals("Credit", PdfValueFormatter.displayBalanceSide(BalanceSide.CREDIT));
    assertEquals("Balanced", PdfValueFormatter.displayBalanceSide(BalanceSide.ZERO));
  }

  @Test
  void displayAccountTypeSectionFormatsEveryVariant() {
    assertEquals(
        "Assets",
        PdfValueFormatter.displayAccountTypeSection(dev.erst.fingrind.core.AccountType.ASSET));
    assertEquals(
        "Liabilities",
        PdfValueFormatter.displayAccountTypeSection(dev.erst.fingrind.core.AccountType.LIABILITY));
    assertEquals(
        "Equity",
        PdfValueFormatter.displayAccountTypeSection(dev.erst.fingrind.core.AccountType.EQUITY));
    assertEquals(
        "Revenue",
        PdfValueFormatter.displayAccountTypeSection(dev.erst.fingrind.core.AccountType.REVENUE));
    assertEquals(
        "Expenses",
        PdfValueFormatter.displayAccountTypeSection(dev.erst.fingrind.core.AccountType.EXPENSE));
  }

  @Test
  void displayRowKindFormatsAccountAndSyntheticRows() {
    assertEquals("Account", PdfValueFormatter.displayRowKind(false));
    assertEquals("Synthetic", PdfValueFormatter.displayRowKind(true));
  }

  @Test
  void optionalDateFormatsNullAndConcreteDates() {
    assertEquals("(current)", PdfValueFormatter.optionalDate(null));
    assertEquals("2026-05-07", PdfValueFormatter.optionalDate(LocalDate.parse("2026-05-07")));
  }

  @Test
  void optionalDateRangeFormatsOpenAndBoundedRanges() {
    LocalDate from = LocalDate.parse("2026-05-01");
    LocalDate to = LocalDate.parse("2026-05-31");

    assertEquals("(start) to (current)", PdfValueFormatter.optionalDateRange(null, null));
    assertEquals("2026-05-01 to (current)", PdfValueFormatter.optionalDateRange(from, null));
    assertEquals("(start) to 2026-05-31", PdfValueFormatter.optionalDateRange(null, to));
    assertEquals("2026-05-01 to 2026-05-31", PdfValueFormatter.optionalDateRange(from, to));
  }

  @Test
  void effectiveDateRangeFormatsEveryStructuralVariant() {
    LocalDate from = LocalDate.parse("2026-05-01");
    LocalDate to = LocalDate.parse("2026-05-31");

    assertEquals(
        "(start) to (current)",
        PdfValueFormatter.effectiveDateRange(EffectiveDateRange.unbounded()));
    assertEquals(
        "2026-05-01 to (current)",
        PdfValueFormatter.effectiveDateRange(new EffectiveDateRange.From(from)));
    assertEquals(
        "(start) to 2026-05-31",
        PdfValueFormatter.effectiveDateRange(new EffectiveDateRange.To(to)));
    assertEquals(
        "2026-05-01 to 2026-05-31",
        PdfValueFormatter.effectiveDateRange(new EffectiveDateRange.Bounded(from, to)));
  }

  @Test
  void reversalTargetFormatsDirectAndReversalPostings() {
    PostingFact direct = postingFact("posting-1", "idem-1", PostingLineage.direct());
    PostingFact reversal =
        new PostingFact(
            new PostingId("posting-2"),
            journalEntry(),
            PostingLineage.reversal(
                new ReversalReference(new PostingId("posting-1")),
                new ReversalReason("undo test posting")),
            PostingKind.STANDARD,
            direct.provenance());

    assertEquals("(direct)", PdfValueFormatter.reversalTarget(direct));
    assertEquals("posting-1", PdfValueFormatter.reversalTarget(reversal));
  }

  private static PostingFact postingFact(
      String postingId, String idempotencyKey, PostingLineage postingLineage) {
    return new PostingFact(
        new PostingId(postingId),
        journalEntry(),
        postingLineage,
        PostingKind.STANDARD,
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-1"),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.empty()),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  private static JournalEntry journalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            new JournalLine(new AccountCode("1000"), JournalLine.EntrySide.DEBIT, money("10.00")),
            new JournalLine(
                new AccountCode("2000"), JournalLine.EntrySide.CREDIT, money("10.00"))));
  }

  private static Money money(String amount) {
    return Money.parse("EUR", amount);
  }
}
