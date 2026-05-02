package dev.erst.fingrind.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Balanced journal entry ready to cross the application write boundary. */
public record JournalEntry(LocalDate effectiveDate, List<JournalLine> lines) {
  /** Validates the journal grammar and enforces balanced entry discipline. */
  public JournalEntry {
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    lines = lines == null ? List.of() : List.copyOf(lines);
    List<String> violations = validate(lines);
    if (!violations.isEmpty()) {
      throw new JournalEntryValidationException(violations);
    }
  }

  private static List<String> validate(List<JournalLine> lines) {
    if (lines.isEmpty()) {
      return List.of("Journal entry must contain at least one line.");
    }
    List<String> violations = new ArrayList<>();
    CurrencyCode expectedCurrency = lines.getFirst().amount().currencyCode();
    boolean mixedCurrency = false;
    boolean hasDebit = false;
    boolean hasCredit = false;
    BigDecimal debitTotal = BigDecimal.ZERO;
    BigDecimal creditTotal = BigDecimal.ZERO;
    for (JournalLine line : lines) {
      CurrencyCode currency = line.amount().currencyCode();
      if (!currency.equals(expectedCurrency)) {
        mixedCurrency = true;
      }
      if (line.side() == JournalLine.EntrySide.DEBIT) {
        hasDebit = true;
        debitTotal = debitTotal.add(line.amount().amount());
      } else {
        hasCredit = true;
        creditTotal = creditTotal.add(line.amount().amount());
      }
    }
    if (mixedCurrency) {
      violations.add("Journal entry lines must share one currency.");
    }
    if (!hasDebit || !hasCredit) {
      violations.add("Journal entry must contain at least one debit line and one credit line.");
    }
    if (debitTotal.compareTo(creditTotal) != 0) {
      violations.add("Journal entry must balance debits and credits.");
    }
    return List.copyOf(violations);
  }
}
