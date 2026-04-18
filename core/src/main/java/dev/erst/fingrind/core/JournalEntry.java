package dev.erst.fingrind.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Balanced journal entry ready to cross the application write boundary. */
public record JournalEntry(LocalDate effectiveDate, List<JournalLine> lines) {
  /** Validates the journal grammar and enforces balanced entry discipline. */
  public JournalEntry {
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    if (lines.isEmpty()) {
      throw new IllegalArgumentException("Journal entry must contain at least one line.");
    }
    ensureSingleCurrency(lines);
    ensureBothSidesPresent(lines);
    ensureBalanced(lines);
  }

  private static void ensureSingleCurrency(List<JournalLine> lines) {
    CurrencyCode expectedCurrency = lines.getFirst().amount().currencyCode();
    boolean mixedCurrency =
        lines.stream()
            .map(line -> line.amount().currencyCode())
            .anyMatch(currency -> !currency.equals(expectedCurrency));
    if (mixedCurrency) {
      throw new IllegalArgumentException("Journal entry lines must share one currency.");
    }
  }

  private static void ensureBalanced(List<JournalLine> lines) {
    BigDecimal debitTotal = totalFor(lines, JournalLine.EntrySide.DEBIT);
    BigDecimal creditTotal = totalFor(lines, JournalLine.EntrySide.CREDIT);
    if (debitTotal.compareTo(creditTotal) != 0) {
      throw new IllegalArgumentException("Journal entry must balance debits and credits.");
    }
  }

  private static void ensureBothSidesPresent(List<JournalLine> lines) {
    boolean hasDebit = lines.stream().anyMatch(line -> line.side() == JournalLine.EntrySide.DEBIT);
    boolean hasCredit =
        lines.stream().anyMatch(line -> line.side() == JournalLine.EntrySide.CREDIT);
    if (!hasDebit || !hasCredit) {
      throw new IllegalArgumentException(
          "Journal entry must contain at least one debit line and one credit line.");
    }
  }

  private static BigDecimal totalFor(List<JournalLine> lines, JournalLine.EntrySide side) {
    return lines.stream()
        .filter(line -> line.side() == side)
        .map(line -> line.amount().amount())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
