package dev.erst.fingrind.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Balanced journal entry ready to cross the application write boundary. */
public record JournalEntry(LocalDate effectiveDate, List<JournalLine> lines) {
  /** Validates the journal grammar and enforces balanced entry discipline. */
  public JournalEntry {
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    List<String> violations = validate(lines);
    if (!violations.isEmpty()) {
      throw new JournalEntryValidationException(violations);
    }
  }

  /** Returns the one shared currency unit guaranteed by the journal-entry invariant. */
  public CurrencyUnit currencyUnit() {
    return lines.getFirst().amount().currencyUnit();
  }

  private static List<String> validate(List<JournalLine> lines) {
    if (lines.isEmpty()) {
      return List.of("Journal entry must contain at least one line.");
    }
    List<String> violations = new ArrayList<>();
    CurrencyUnit expectedCurrency = lines.getFirst().amount().currencyUnit();
    boolean mixedCurrency = false;
    boolean hasDebit = false;
    boolean hasCredit = false;
    long debitTotal = 0L;
    long creditTotal = 0L;
    for (JournalLine line : lines) {
      CurrencyUnit currency = line.amount().currencyUnit();
      if (!currency.equals(expectedCurrency)) {
        mixedCurrency = true;
      }
      if (line.side() == JournalLine.EntrySide.DEBIT) {
        hasDebit = true;
        debitTotal = Math.addExact(debitTotal, line.amount().minorUnits());
      } else {
        hasCredit = true;
        creditTotal = Math.addExact(creditTotal, line.amount().minorUnits());
      }
    }
    if (mixedCurrency) {
      violations.add("Journal entry lines must share one currency.");
    }
    if (!hasDebit || !hasCredit) {
      violations.add("Journal entry must contain at least one debit line and one credit line.");
    }
    if (debitTotal != creditTotal) {
      violations.add("Journal entry must balance debits and credits.");
    }
    return List.copyOf(violations);
  }
}
